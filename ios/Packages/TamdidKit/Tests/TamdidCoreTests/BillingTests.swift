import Foundation
import Testing
@testable import TamdidCore

private func d(_ iso: String) -> CivilDate { CivilDate(iso: iso)! }

private func sub(
    _ name: String = "Service",
    _ amount: Int64 = 1000,
    _ currency: CurrencyCode = .usDollar,
    cycle: BillingCycle = .monthly,
    start: String = "2026-01-10",
    trial: String? = nil,
    category: SubscriptionCategory = .other,
    reminder: Int? = 1
) -> Subscription {
    Subscription(
        name: name,
        category: category,
        price: Money(amountMinor: amount, currency: currency),
        cycle: cycle,
        startDate: d(start),
        trialEndDate: trial.map(d),
        reminderDaysBefore: reminder
    )
}

@Suite struct BillingTests {
    @Test func longRunningSeriesNeverDrifts() {
        // 30 years of month-end renewals computed from the anchor stay on the last day.
        let s = sub(start: "2000-01-31")
        let charges = s.charges(from: d("2000-01-01"), to: d("2029-12-31"))
        #expect(charges.count == 360)
        #expect(charges.allSatisfy { $0.day == CivilDate.daysInMonth(year: $0.year, month: $0.month) })
    }

    @Test func approximationNeverSkipsACharge() {
        // Compare the fast path with a brute-force walk for many cycles and windows.
        let cycles: [BillingCycle] = [.weekly, .monthly, .quarterly, .yearly,
                                      BillingCycle(unit: .day, interval: 10), BillingCycle(unit: .year, interval: 2)]
        for cycle in cycles {
            let s = sub(cycle: cycle, start: "2024-02-29")
            var all: [CivilDate] = []
            let limit = d("2036-12-31")
            for n in 0... {
                let date: CivilDate = cycle.date(n, from: s.anchorDate)
                if date > limit { break }
                all.append(date)
            }
            for offset in stride(from: 0, to: 2400, by: 37) {
                let from = d("2024-01-01").adding(days: offset)
                let to = from.adding(days: 95)
                let expectedCharges: [CivilDate] = all.filter { $0 >= from && $0 <= to }
                let expectedNext: CivilDate? = all.first { $0 >= from }
                #expect(s.charges(from: from, to: to) == expectedCharges, "\(cycle) \(from)")
                #expect(s.nextRenewal(onOrAfter: from) == expectedNext, "\(cycle) \(from)")
            }
        }
    }

    @Test func priceChangesApplyFromTheirEffectiveDate() {
        var s = sub("Service", 1000, start: "2026-01-10")
        s.changePrice(to: 1200, effectiveFrom: d("2026-03-01"))
        s.changePrice(to: 1500, effectiveFrom: d("2026-05-01"))
        #expect(s.price.amountMinor == 1500)
        #expect(s.price(on: d("2026-01-10")) == 1000)
        #expect(s.price(on: d("2026-03-10")) == 1200)
        #expect(s.price(on: d("2026-05-10")) == 1500)
        // Jan, Feb at 10.00; Mar, Apr at 12.00; May, Jun at 15.00.
        #expect(s.totalSpent(asOf: d("2026-06-30")) == 2 * 1000 + 2 * 1200 + 2 * 1500)
        #expect(s.paymentCount(asOf: d("2026-06-30")) == 6)
    }

    @Test func correctingAPriceOnItsStartDateDoesNotAddHistory() {
        var s = sub("Service", 1000)
        s.changePrice(to: 900, effectiveFrom: d("2026-01-10"))
        #expect(s.priceHistory.isEmpty)
        #expect(s.totalSpent(asOf: d("2026-02-10")) == 1800)
        s.changePrice(to: 900, effectiveFrom: d("2026-06-01")) // same price: no-op
        #expect(s.priceHistory.isEmpty)
    }

    @Test func pauseAndResumeDoNotBillThePausedMonths() {
        var s = sub("Service", 1000, start: "2026-01-10")
        s.pause(on: d("2026-03-15")) // charged Jan, Feb, Mar
        #expect(s.nextRenewal(onOrAfter: d("2026-03-16")) == nil)
        #expect(s.totalSpent(asOf: d("2026-08-01")) == 3000)

        s.resume(on: d("2026-07-01"))
        #expect(s.isActive)
        #expect(s.nextRenewal(onOrAfter: d("2026-06-20")) == d("2026-07-01"))
        // 3 before the pause + Jul, Aug.
        #expect(s.totalSpent(asOf: d("2026-08-31")) == 5000)
        #expect(s.paymentCount(asOf: d("2026-08-31")) == 5)
    }

    @Test func cancellingKeepsHistoryAndStopsRenewals() {
        var s = sub("Service", 500, start: "2026-01-10")
        s.cancel(on: d("2026-04-01"))
        #expect(s.status == .cancelled)
        #expect(s.nextRenewal(onOrAfter: d("2026-04-02")) == nil)
        #expect(s.totalSpent(asOf: d("2027-01-01")) == 1500)

        var paused = sub("Service", 500, start: "2026-01-10")
        paused.pause(on: d("2026-02-15"))
        paused.cancel(on: d("2026-06-01")) // keeps the pause date
        #expect(paused.endDate == d("2026-02-15"))
    }

    @Test func trials() {
        let s = sub(start: "2026-11-01", trial: "2026-11-15")
        #expect(s.isInTrial(on: d("2026-11-14")))
        #expect(!s.isInTrial(on: d("2026-11-15")))
        #expect(s.totalSpent(asOf: d("2026-11-14")) == 0)
        #expect(s.totalSpent(asOf: d("2026-11-15")) == 1000)
    }

    @Test func yearlyCostIsTwelveMonths() {
        #expect(sub("Service", 999).yearlyCostMinor == 11988)
        #expect(sub("Service", 12000, cycle: .yearly).yearlyCostMinor == 12000)
    }

    @Test func tolerantDecodingFillsDefaults() throws {
        let json = """
        {"id":"7B0D8C1E-1C9B-4E8B-9A57-1E0C9C3F4A11","name":"Old","price":{"amountMinor":500,"currency":"USD"},
         "startDate":"2025-05-05","category":"something-new","status":"archived"}
        """
        let s = try JSONDecoder().decode(Subscription.self, from: Data(json.utf8))
        #expect(s.category == .other)
        #expect(s.status == .active)
        #expect(s.cycle == .monthly)
        #expect(s.priceHistory.isEmpty)
        #expect(s.reminderDaysBefore == nil)
    }
}

@Suite struct MoneyParsingTests {
    @Test func parsesWhatPeopleType() {
        #expect(MoneyFormatter.parse("9.99", currency: .usDollar) == 999)
        #expect(MoneyFormatter.parse("9.9", currency: .usDollar) == 990)
        #expect(MoneyFormatter.parse("10", currency: .usDollar) == 1000)
        #expect(MoneyFormatter.parse(".5", currency: .usDollar) == 50)
        #expect(MoneyFormatter.parse("1,299.00", currency: .euro) == 129900)
        #expect(MoneyFormatter.parse("۱۲۰٬۰۰۰", currency: .toman) == 120000)
        #expect(MoneyFormatter.parse("١٢٠ ٠٠٠", currency: .toman) == 120000)
        #expect(MoneyFormatter.parse("۹٫۹۹", currency: .usDollar) == 999)
        #expect(MoneyFormatter.parse("9/99", currency: .usDollar) == 999)
    }

    @Test func rejectsNonsense() {
        #expect(MoneyFormatter.parse("", currency: .usDollar) == nil)
        #expect(MoneyFormatter.parse("abc", currency: .usDollar) == nil)
        #expect(MoneyFormatter.parse("9.999", currency: .usDollar) == nil)
        #expect(MoneyFormatter.parse("12.5", currency: .toman) == nil)
        #expect(MoneyFormatter.parse("1.2.3", currency: .usDollar) == nil)
        #expect(MoneyFormatter.parse("-5", currency: .usDollar) == nil)
        #expect(MoneyFormatter.parse("99999999999999999999", currency: .usDollar) == nil)
    }

    @Test func parsesEveryLanguagesOwnFormatting() {
        // What each language's formatter prints must parse back (minus the currency word).
        #expect(MoneyFormatter.parse("١٢٠٬٠٠٠", currency: .toman) == 120000)
        #expect(MoneyFormatter.parse("١٩٫٩٩", currency: .usDollar) == 1999)
    }

    @Test func editableTextRoundTrips() {
        for currency in CurrencyCode.allCases {
            for amount: Int64 in [0, 5, 99, 100, 999, 123_456] {
                let text = MoneyFormatter.editableText(amount, currency: currency)
                #expect(MoneyFormatter.parse(text, currency: currency) == amount, "\(currency) \(amount) → \(text)")
            }
        }
    }
}

@Suite struct InsightsTests {
    let today = d("2026-11-20")

    @Test func monthlyTotalConvertsWithRatesAndReportsTheRest() {
        var settings = AppSettings(primaryCurrency: .toman)
        settings.exchangeRates = [.usDollar: 90_000]
        let subs = [
            sub("Filimo", 120_000, .toman),
            sub("Spotify", 1099, .usDollar),
            sub("Netflix", 1549, .euro),
        ]
        let total = Insights(subscriptions: subs, settings: settings, today: today).monthlyTotal
        #expect(total.primary == Money(amountMinor: 120_000 + 989_100, currency: .toman))
        #expect(total.unconverted == [.euro: 1549])
        #expect(!total.isComplete)
    }

    @Test func inactiveSubscriptionsAreExcludedFromTotals() {
        var paused = sub("Paused", 5000)
        paused.pause(on: d("2026-10-01"))
        let insights = Insights(subscriptions: [sub("A", 1000), paused], settings: AppSettings(), today: today)
        #expect(insights.monthlyTotal.primary.amountMinor == 1000)
        #expect(insights.yearlyTotal.primary.amountMinor == 12000)
        #expect(insights.dailyAverageMinor == 33)
    }

    @Test func upcomingAndEndingTrials() {
        let subs = [
            sub("Later", start: "2026-01-28"),
            sub("Soon", start: "2026-01-21"),
            sub("Trial", start: "2026-11-01", trial: "2026-11-22"),
        ]
        let insights = Insights(subscriptions: subs, settings: AppSettings(), today: today)
        #expect(insights.upcoming(days: 7).map(\.subscription.name) == ["Soon", "Trial"])
        #expect(insights.upcoming(days: 9).map(\.subscription.name) == ["Soon", "Trial", "Later"])
        #expect(insights.endingTrials(days: 7).map(\.subscription.name) == ["Trial"])
        #expect(insights.upcoming(days: 1).isEmpty)
    }

    @Test func categoriesAreSortedBySpend() {
        let subs = [
            sub("A", 1000, category: .music),
            sub("B", 500, category: .streaming),
            sub("C", 700, category: .streaming),
            sub("D", 999, .euro, category: .ai), // no rate: excluded
        ]
        let byCategory = Insights(subscriptions: subs, settings: AppSettings(), today: today).byCategory
        #expect(byCategory.map(\.category) == [.streaming, .music])
        #expect(byCategory.first?.monthlyMinor == 1200)
        #expect(byCategory.first?.count == 2)
    }

    @Test func spendByJalaliMonth() {
        // Charges on the 10th of each Gregorian month; Aban 1405 spans Oct 23 – Nov 21.
        let insights = Insights(subscriptions: [sub("A", 1000)], settings: AppSettings(), today: today)
        let months = insights.spendByMonth(count: 3, calendar: .jalali)
        #expect(months.map(\.month) == [6, 7, 8])
        #expect(months.map(\.total.primary.amountMinor) == [1000, 1000, 1000])
        let gregorian = insights.spendByMonth(count: 12, calendar: .gregorian)
        #expect(gregorian.count == 12)
        #expect(gregorian.first?.month == 12 && gregorian.first?.year == 2025)
        #expect(gregorian.first?.total.primary.amountMinor == 0) // started in January
        #expect(gregorian.last?.total.primary.amountMinor == 1000)
    }

    @Test func overlapsOnlyCountSimilarServices() {
        let subs = [
            sub("Netflix", category: .streaming), sub("Filimo", category: .streaming),
            sub("Gym", category: .health), sub("Yoga", category: .health),
            sub("Spotify", category: .music),
        ]
        let overlaps = Insights(subscriptions: subs, settings: AppSettings(), today: today).overlaps
        #expect(overlaps.map(\.category) == [.streaming])
        #expect(overlaps.first?.subscriptions.map(\.name) == ["Filimo", "Netflix"])
    }

    @Test func mostExpensive() {
        let subs = [sub("Weekly", 300, cycle: .weekly), sub("Yearly", 12000, cycle: .yearly), sub("Monthly", 1100)]
        let top = Insights(subscriptions: subs, settings: AppSettings(), today: today).mostExpensive(limit: 2)
        #expect(top.map(\.subscription.name) == ["Weekly", "Monthly"])
    }
}

@Suite struct ReminderPlannerTests {
    let today = d("2026-11-20")

    @Test func remindsTheConfiguredDaysBefore() {
        let plan = ReminderPlanner.plan([sub("A", start: "2026-01-25", reminder: 3)], today: today, horizonDays: 40)
        #expect(plan.map(\.chargeDate) == [d("2026-11-25"), d("2026-12-25")])
        #expect(plan.map(\.fireDate) == [d("2026-11-22"), d("2026-12-22")])
        #expect(plan.allSatisfy { $0.kind == .renewal })
    }

    @Test func includesAChargeJustBeyondTheHorizonIfItsReminderIsInside() {
        let plan = ReminderPlanner.plan([sub("A", start: "2026-01-02", reminder: 3)], today: today, horizonDays: 10)
        #expect(plan.map(\.chargeDate) == [d("2026-12-02")])
        #expect(plan.first?.fireDate == d("2026-11-29"))
    }

    @Test func skipsRemindersThatWouldFireInThePast() {
        let plan = ReminderPlanner.plan([sub("A", start: "2026-01-21", reminder: 3)], today: today, horizonDays: 5)
        #expect(plan.isEmpty)
    }

    @Test func trialEndsAreLabelled() {
        let plan = ReminderPlanner.plan([sub("T", start: "2026-11-10", trial: "2026-11-24", reminder: 2)], today: today, horizonDays: 10)
        #expect(plan.first?.kind == .trialEnding)
        #expect(plan.first?.fireDate == d("2026-11-22"))
    }

    @Test func skipsInactiveAndMutedAndCapsTheTotal() {
        var cancelled = sub("Cancelled")
        cancelled.cancel(on: d("2026-11-01"))
        let muted = sub("Muted", reminder: nil)
        let daily = sub("Daily", cycle: BillingCycle(unit: .day), start: "2026-01-01", reminder: 0)
        let plan = ReminderPlanner.plan([cancelled, muted, daily], today: today, horizonDays: 365, limit: 60)
        #expect(plan.count == 60)
        #expect(plan.allSatisfy { $0.subscriptionName == "Daily" })
        #expect(plan.first?.fireDate == today)
        #expect(Set(plan.map(\.id)).count == 60)
    }
}

@Suite struct CatalogTests {
    let catalog: ServiceCatalog = {
        let url = Vectors.repoRoot.appendingPathComponent("shared/catalog.json")
        return try! ServiceCatalog(jsonData: Data(contentsOf: url))
    }()

    @Test func sharedCatalogIsValid() {
        #expect(catalog.services.count >= 30)
        #expect(Set(catalog.services.map(\.id)).count == catalog.services.count)
        for service in catalog.services {
            #expect(service.color.count == 7 && service.color.hasPrefix("#"))
            #expect(!service.name.en.isEmpty && !service.name.fa.isEmpty)
        }
    }

    @Test func searchMatchesBothLanguagesAndKeyboardVariants() {
        #expect(catalog.search("netf").map(\.id) == ["netflix"])
        #expect(catalog.search("NETFLIX").map(\.id) == ["netflix"])
        #expect(catalog.search("فیلیمو").map(\.id) == ["filimo"])
        #expect(catalog.search("فيليمو").map(\.id) == ["filimo"]) // Arabic yeh
        #expect(catalog.search("youtubepremium").map(\.id) == ["youtube-premium"])
        #expect(catalog.search("آی کلاد").map(\.id) == ["icloud"]) // space instead of ZWNJ
        #expect(catalog.search("").count == catalog.services.count)
        #expect(catalog.search("zzzz").isEmpty)
    }
}
