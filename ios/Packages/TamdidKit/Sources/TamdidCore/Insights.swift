import Foundation

public struct UpcomingCharge: Hashable, Sendable, Identifiable {
    public var subscription: Subscription
    public var date: CivilDate
    public var isTrialEnd: Bool

    public var id: String { "\(subscription.id.uuidString)-\(date.iso)" }
}

public struct CategorySpend: Hashable, Sendable, Identifiable {
    public var category: SubscriptionCategory
    public var monthlyMinor: Int64
    public var count: Int

    public var id: SubscriptionCategory { category }
}

public struct MonthSpend: Hashable, Sendable, Identifiable {
    /// Year and month in the chosen calendar system.
    public var year: Int
    public var month: Int
    public var total: MoneyTotal

    public var id: String { "\(year)-\(month)" }
}

/// Read-only views over the user's subscriptions, used by the dashboard, insights and calendar.
public struct Insights: Sendable {
    public let subscriptions: [Subscription]
    public let converter: CurrencyConverter
    public let today: CivilDate

    public init(subscriptions: [Subscription], settings: AppSettings, today: CivilDate) {
        self.subscriptions = subscriptions
        self.converter = CurrencyConverter(settings: settings)
        self.today = today
    }

    public var active: [Subscription] { subscriptions.filter(\.isActive) }

    /// Recurring cost per average month of all active subscriptions (trials included,
    /// since they will be charged unless cancelled).
    public var monthlyTotal: MoneyTotal {
        var total = MoneyTotal(primary: .zero(converter.primary))
        for subscription in active {
            total.add(subscription.monthlyCostMinor, subscription.price.currency, using: converter)
        }
        return total
    }

    public var yearlyTotal: MoneyTotal {
        var total = MoneyTotal(primary: .zero(converter.primary))
        for subscription in active {
            total.add(subscription.yearlyCostMinor, subscription.price.currency, using: converter)
        }
        return total
    }

    /// Average spend per day in the primary currency.
    public var dailyAverageMinor: Int64 {
        Subscription.roundHalfUp(Double(yearlyTotal.primary.amountMinor) / 365.2425)
    }

    /// Charges in the next `days` days (today included), soonest first.
    public func upcoming(days: Int) -> [UpcomingCharge] {
        let end = today.adding(days: days - 1)
        return subscriptions
            .flatMap { subscription in
                subscription.charges(from: today, to: end).map {
                    UpcomingCharge(subscription: subscription, date: $0, isTrialEnd: $0 == subscription.trialEndDate)
                }
            }
            .sorted { ($0.date, $0.subscription.name) < ($1.date, $1.subscription.name) }
    }

    /// Active trials that end within `days` days.
    public func endingTrials(days: Int) -> [UpcomingCharge] {
        upcoming(days: days).filter(\.isTrialEnd)
    }

    /// Every charge between two dates, for the calendar.
    public func charges(from: CivilDate, to: CivilDate) -> [CivilDate: [Subscription]] {
        var result: [CivilDate: [Subscription]] = [:]
        for subscription in subscriptions {
            for date in subscription.charges(from: from, to: to) {
                result[date, default: []].append(subscription)
            }
        }
        return result.mapValues { $0.sorted { $0.name < $1.name } }
    }

    /// Monthly cost by category in the primary currency, largest first.
    /// Subscriptions whose currency has no exchange rate are left out.
    public var byCategory: [CategorySpend] {
        var spend: [SubscriptionCategory: CategorySpend] = [:]
        for subscription in active {
            guard let monthly = converter.toPrimary(subscription.monthlyCostMinor, from: subscription.price.currency) else { continue }
            spend[subscription.category, default: CategorySpend(category: subscription.category, monthlyMinor: 0, count: 0)]
                .monthlyMinor += monthly
            spend[subscription.category]!.count += 1
        }
        return spend.values.sorted { ($0.monthlyMinor, $1.category.rawValue) > ($1.monthlyMinor, $0.category.rawValue) }
    }

    /// The most expensive active subscriptions by monthly cost in the primary currency.
    public func mostExpensive(limit: Int) -> [(subscription: Subscription, monthlyMinor: Int64)] {
        active
            .compactMap { subscription in
                converter.toPrimary(subscription.monthlyCostMinor, from: subscription.price.currency)
                    .map { (subscription: subscription, monthlyMinor: $0) }
            }
            .sorted { $0.monthlyMinor > $1.monthlyMinor }
            .prefix(limit)
            .map { $0 }
    }

    /// Actual charges per month for the last `count` months (oldest first), bucketed by
    /// month in `calendar` so Jalali users see Farvardin, Ordibehesht, …
    public func spendByMonth(count: Int, calendar: CalendarSystem) -> [MonthSpend] {
        let current = CalendarMonth.containing(today, in: calendar)
        let months = (0..<count).reversed().map { current.adding(months: -$0) }
        guard let first = months.first else { return [] }
        let from = first.firstDay
        let to = today // only what has actually been charged
        var buckets: [CalendarMonth: MoneyTotal] = [:]
        for month in months { buckets[month] = MoneyTotal(primary: .zero(converter.primary)) }
        for subscription in subscriptions {
            for date in subscription.charges(from: from, to: to) {
                let month = CalendarMonth.containing(date, in: calendar)
                buckets[month]?.add(subscription.price(on: date), subscription.price.currency, using: converter)
            }
        }
        return months.map { MonthSpend(year: $0.year, month: $0.month, total: buckets[$0]!) }
    }

    /// Categories where the user pays for more than one service that does the same job.
    public var overlaps: [(category: SubscriptionCategory, subscriptions: [Subscription])] {
        let overlapping: Set<SubscriptionCategory> = [.streaming, .music, .cloud, .ai, .reading]
        return Dictionary(grouping: active.filter { overlapping.contains($0.category) }, by: \.category)
            .filter { $0.value.count > 1 }
            .map { (category: $0.key, subscriptions: $0.value.sorted { $0.name < $1.name }) }
            .sorted { $0.category.rawValue < $1.category.rawValue }
    }
}

// MARK: - Reminders

public struct PlannedReminder: Hashable, Sendable, Identifiable {
    public enum Kind: String, Sendable { case renewal, trialEnding }

    public var subscriptionID: UUID
    public var subscriptionName: String
    public var kind: Kind
    public var chargeDate: CivilDate
    public var fireDate: CivilDate
    public var amount: Money

    /// Stable across rebuilds, so platforms can diff scheduled notifications.
    public var id: String { "\(subscriptionID.uuidString)-\(chargeDate.iso)" }
}

public enum ReminderPlanner {
    /// The reminders to schedule, soonest first. iOS allows 64 pending local notifications,
    /// so the plan is capped and rebuilt whenever data changes or the app opens.
    public static func plan(
        _ subscriptions: [Subscription],
        today: CivilDate,
        horizonDays: Int = 60,
        limit: Int = 60
    ) -> [PlannedReminder] {
        let end = today.adding(days: horizonDays)
        var result: [PlannedReminder] = []
        for subscription in subscriptions where subscription.isActive {
            guard let daysBefore = subscription.reminderDaysBefore, daysBefore >= 0 else { continue }
            for charge in subscription.charges(from: today, to: end.adding(days: daysBefore)) {
                let fire = charge.adding(days: -daysBefore)
                guard fire >= today, fire <= end else { continue }
                result.append(PlannedReminder(
                    subscriptionID: subscription.id,
                    subscriptionName: subscription.name,
                    kind: charge == subscription.trialEndDate ? .trialEnding : .renewal,
                    chargeDate: charge,
                    fireDate: fire,
                    amount: Money(amountMinor: subscription.price(on: charge), currency: subscription.price.currency)
                ))
            }
        }
        return Array(result
            .sorted { ($0.fireDate, $0.subscriptionName, $0.chargeDate) < ($1.fireDate, $1.subscriptionName, $1.chargeDate) }
            .prefix(limit))
    }
}
