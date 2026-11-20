package com.aliasadi.tamdid.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.LocalDate

private fun d(iso: String) = LocalDate.parse(iso)

private fun sub(
    name: String = "Service",
    amount: Long = 1000,
    currency: CurrencyCode = CurrencyCode.UsDollar,
    cycle: BillingCycle = BillingCycle.Monthly,
    start: String = "2026-01-10",
    trial: String? = null,
    category: SubscriptionCategory = SubscriptionCategory.Other,
    reminder: Int? = 1,
) = Subscription(
    name = name, category = category, price = Money(amount, currency), cycle = cycle,
    startDate = d(start), trialEndDate = trial?.let(::d), reminderDaysBefore = reminder,
)

class BillingTest {
    @Test
    fun approximationNeverSkipsACharge() {
        val cycles = listOf(
            BillingCycle.Weekly, BillingCycle.Monthly, BillingCycle.Quarterly, BillingCycle.Yearly,
            BillingCycle(CycleUnit.Day, 10), BillingCycle(CycleUnit.Year, 2),
        )
        for (cycle in cycles) {
            val s = sub(cycle = cycle, start = "2024-02-29")
            val all = generateSequence(0) { it + 1 }.map { cycle.date(it, s.anchorDate) }.takeWhile { it <= d("2036-12-31") }.toList()
            for (offset in 0 until 2400 step 37) {
                val from = d("2024-01-01").plusDays(offset.toLong())
                val to = from.plusDays(95)
                assertEquals("$cycle $from", all.filter { it in from..to }, s.charges(from, to))
                assertEquals("$cycle $from", all.firstOrNull { it >= from }, s.nextRenewal(from))
            }
        }
    }

    @Test
    fun priceChangesApplyFromTheirEffectiveDate() {
        val s = sub(amount = 1000).changePrice(1200, d("2026-03-01")).changePrice(1500, d("2026-05-01"))
        assertEquals(1500, s.price.amountMinor)
        assertEquals(1000, s.priceOn(d("2026-01-10")))
        assertEquals(1200, s.priceOn(d("2026-03-10")))
        assertEquals(1500, s.priceOn(d("2026-05-10")))
        assertEquals(2 * 1000L + 2 * 1200 + 2 * 1500, s.totalSpent(d("2026-06-30")))
        assertEquals(6, s.paymentCount(d("2026-06-30")))
        assertTrue(sub(amount = 1000).changePrice(900, d("2026-01-10")).priceHistory.isEmpty())
    }

    @Test
    fun pauseAndResumeDoNotBillThePausedMonths() {
        val paused = sub(amount = 1000).pause(d("2026-03-15"))
        assertNull(paused.nextRenewal(d("2026-03-16")))
        assertEquals(3000, paused.totalSpent(d("2026-08-01")))
        val resumed = paused.resume(d("2026-07-01"))
        assertTrue(resumed.isActive)
        assertEquals(d("2026-07-01"), resumed.nextRenewal(d("2026-06-20")))
        assertEquals(5000, resumed.totalSpent(d("2026-08-31")))
        assertEquals(5, resumed.paymentCount(d("2026-08-31")))
    }

    @Test
    fun cancelling() {
        val cancelled = sub(amount = 500).cancel(d("2026-04-01"))
        assertEquals(SubscriptionStatus.Cancelled, cancelled.status)
        assertNull(cancelled.nextRenewal(d("2026-04-02")))
        assertEquals(1500, cancelled.totalSpent(d("2027-01-01")))
        assertEquals(d("2026-02-15"), sub().pause(d("2026-02-15")).cancel(d("2026-06-01")).endDate)
    }

    @Test
    fun trials() {
        val s = sub(start = "2026-11-01", trial = "2026-11-15")
        assertTrue(s.isInTrial(d("2026-11-14")))
        assertFalse(s.isInTrial(d("2026-11-15")))
        assertEquals(0, s.totalSpent(d("2026-11-14")))
        assertEquals(1000, s.totalSpent(d("2026-11-15")))
    }
}

class MoneyParsingTest {
    @Test
    fun parsesWhatPeopleType() {
        assertEquals(999L, MoneyFormatter.parse("9.99", CurrencyCode.UsDollar))
        assertEquals(990L, MoneyFormatter.parse("9.9", CurrencyCode.UsDollar))
        assertEquals(50L, MoneyFormatter.parse(".5", CurrencyCode.UsDollar))
        assertEquals(129900L, MoneyFormatter.parse("1,299.00", CurrencyCode.Euro))
        assertEquals(120000L, MoneyFormatter.parse("۱۲۰٬۰۰۰", CurrencyCode.Toman))
        assertEquals(120000L, MoneyFormatter.parse("١٢٠ ٠٠٠", CurrencyCode.Toman))
        assertEquals(999L, MoneyFormatter.parse("۹٫۹۹", CurrencyCode.UsDollar))
        assertEquals(999L, MoneyFormatter.parse("9/99", CurrencyCode.UsDollar))
    }

    @Test
    fun rejectsNonsense() {
        for (input in listOf("", "abc", "9.999", "1.2.3", "-5", "99999999999999999999")) {
            assertNull(input, MoneyFormatter.parse(input, CurrencyCode.UsDollar))
        }
        assertNull(MoneyFormatter.parse("12.5", CurrencyCode.Toman))
    }

    @Test
    fun editableTextRoundTrips() {
        for (currency in CurrencyCode.entries) for (amount in listOf(0L, 5, 99, 100, 999, 123_456)) {
            assertEquals("$currency $amount", amount, MoneyFormatter.parse(MoneyFormatter.editableText(amount, currency), currency))
        }
    }
}

class InsightsTest {
    private val today = d("2026-11-20")

    @Test
    fun monthlyTotalConvertsWithRatesAndReportsTheRest() {
        val settings = AppSettings(primaryCurrency = CurrencyCode.Toman).withExchangeRates(mapOf(CurrencyCode.UsDollar to 90_000.0))
        val subs = listOf(
            sub("Filimo", 120_000, CurrencyCode.Toman),
            sub("Spotify", 1099, CurrencyCode.UsDollar),
            sub("Netflix", 1549, CurrencyCode.Euro),
        )
        val total = Insights(subs, settings, today).monthlyTotal
        assertEquals(Money(120_000 + 989_100, CurrencyCode.Toman), total.primary)
        assertEquals(mapOf(CurrencyCode.Euro to 1549L), total.unconverted)
    }

    @Test
    fun upcomingTrialsCategoriesAndMonths() {
        val subs = listOf(
            sub("Later", start = "2026-01-28", category = SubscriptionCategory.Music),
            sub("Soon", start = "2026-01-21", category = SubscriptionCategory.Streaming),
            sub("Trial", start = "2026-11-01", trial = "2026-11-22", category = SubscriptionCategory.Streaming),
        )
        val insights = Insights(subs, AppSettings(), today)
        assertEquals(listOf("Soon", "Trial"), insights.upcoming(7).map { it.subscription.name })
        assertEquals(listOf("Soon", "Trial", "Later"), insights.upcoming(9).map { it.subscription.name })
        assertEquals(listOf("Trial"), insights.endingTrials(7).map { it.subscription.name })
        assertEquals(listOf(SubscriptionCategory.Streaming, SubscriptionCategory.Music), insights.byCategory.map { it.category })
        assertEquals(listOf(SubscriptionCategory.Streaming), insights.overlaps.map { it.category })

        val months = Insights(listOf(sub("A")), AppSettings(), today).spendByMonth(3, CalendarSystem.Jalali)
        assertEquals(listOf(6, 7, 8), months.map { it.month })
        assertEquals(listOf(1000L, 1000L, 1000L), months.map { it.total.primary.amountMinor })
    }

    @Test
    fun mostExpensiveAndDailyAverage() {
        val subs = listOf(sub("Weekly", 300, cycle = BillingCycle.Weekly), sub("Yearly", 12000, cycle = BillingCycle.Yearly), sub("Monthly", 1100))
        assertEquals(listOf("Weekly", "Monthly"), Insights(subs, AppSettings(), today).mostExpensive(2).map { it.subscription.name })
        assertEquals(33, Insights(listOf(sub("A", 1000)), AppSettings(), today).dailyAverageMinor)
    }
}

class ReminderPlannerTest {
    private val today = d("2026-11-20")

    @Test
    fun remindsTheConfiguredDaysBefore() {
        val plan = ReminderPlanner.plan(listOf(sub(start = "2026-01-25", reminder = 3)), today, horizonDays = 40)
        assertEquals(listOf(d("2026-11-25"), d("2026-12-25")), plan.map { it.chargeDate })
        assertEquals(listOf(d("2026-11-22"), d("2026-12-22")), plan.map { it.fireDate })
    }

    @Test
    fun edgeCases() {
        assertEquals(listOf(d("2026-12-02")), ReminderPlanner.plan(listOf(sub(start = "2026-01-02", reminder = 3)), today, 10).map { it.chargeDate })
        assertTrue(ReminderPlanner.plan(listOf(sub(start = "2026-01-21", reminder = 3)), today, 5).isEmpty())
        val trial = ReminderPlanner.plan(listOf(sub(start = "2026-11-10", trial = "2026-11-24", reminder = 2)), today, 10)
        assertEquals(PlannedReminder.Kind.TrialEnding, trial.first().kind)
        val daily = sub("Daily", cycle = BillingCycle(CycleUnit.Day), start = "2026-01-01", reminder = 0)
        val plan = ReminderPlanner.plan(listOf(sub("Cancelled").cancel(d("2026-11-01")), sub("Muted", reminder = null), daily), today, 365, 60)
        assertEquals(60, plan.size)
        assertTrue(plan.all { it.subscriptionName == "Daily" })
        assertEquals(60, plan.map { it.id }.toSet().size)
    }
}

class CalendarTest {
    @Test
    fun grids() {
        val november = CalendarMonth(CalendarSystem.Gregorian, 2026, 11).weeks(1)
        assertEquals(d("2026-11-01"), november.first()[6])
        assertEquals(30, november.flatten().count { it != null })
        val aban = CalendarMonth(CalendarSystem.Jalali, 1405, 8)
        assertEquals(d("2026-10-23"), aban.firstDay)
        assertEquals(d("2026-11-21"), aban.lastDay)
        assertEquals(6, aban.weeks(6).first().indexOfFirst { it != null })
        assertEquals(CalendarMonth(CalendarSystem.Jalali, 1402, 12), CalendarMonth(CalendarSystem.Jalali, 1405, 1).plusMonths(-25))
    }

    @Test
    fun text() {
        val date = d("2026-11-20")
        assertEquals("Nov 20, 2026", DateText.format(date, CalendarSystem.Gregorian, AppLanguage.English, DateText.Style.Medium))
        assertEquals("۲۹ آبان ۱۴۰۵", DateText.format(date, CalendarSystem.Jalali, AppLanguage.Persian, DateText.Style.Medium))
        assertEquals("29 Aban 1405", DateText.format(date, CalendarSystem.Jalali, AppLanguage.English, DateText.Style.Medium))
        assertEquals("۲۰ نوامبر", DateText.format(date, CalendarSystem.Gregorian, AppLanguage.Persian, DateText.Style.DayMonth))
        assertEquals(listOf("ش", "ی", "د", "س", "چ", "پ", "ج"), DateText.weekdaySymbols(6, AppLanguage.Persian))
        assertEquals("۵ روز دیگر", DateText.relative(d("2026-11-25"), date, AppLanguage.Persian))
    }

    @Test
    fun jalaliRoundTripsEveryDayFor200Years() {
        var day = d("1925-01-01")
        while (day <= d("2125-01-01")) {
            val j = Jalali.fromGregorian(day)
            assertEquals(day, Jalali.toGregorian(j))
            day = day.plusDays(1)
        }
    }
}

class StorageTest {
    private fun sample(): AppData {
        val spotify = Subscription(
            name = "Spotify", catalogID = "spotify", category = SubscriptionCategory.Music, colorHex = "#30A46C",
            price = Money(1099, CurrencyCode.UsDollar), startDate = d("2025-03-31"),
            paymentMethod = "Visa •• 4242", notes = "Family plan, \"shared\"", reminderDaysBefore = 1,
        ).changePrice(1199, d("2026-01-01"))
        val filimo = Subscription(
            name = "فیلیمو", category = SubscriptionCategory.Streaming, price = Money(120_000, CurrencyCode.Toman),
            cycle = BillingCycle.Quarterly, startDate = d("2026-02-01"), trialEndDate = d("2026-02-08"),
        )
        val settings = AppSettings.defaults(AppLanguage.Persian)
            .withExchangeRates(mapOf(CurrencyCode.UsDollar to 90_000.0, CurrencyCode.Euro to 98_000.0))
            .copy(hasCompletedOnboarding = true)
        return AppData(subscriptions = listOf(spotify, filimo), settings = settings)
    }

    @Test
    fun fileStoreRoundTrip() {
        val dir = Files.createTempDirectory("tamdid").toFile()
        val store = FileStore(File(dir, "data.json"))
        assertNull(store.load())
        val data = sample()
        store.save(data)
        assertEquals(data, store.load())
        val changed = data.copy(subscriptions = data.subscriptions.drop(1))
        store.save(changed)
        assertEquals(changed, store.load())
    }

    @Test
    fun readsBackupsWrittenByIos() {
        // Shape produced by the Swift BackupCodec (uppercase UUID, whole-second dates, omitted nils).
        val ios = """
            {
              "schemaVersion" : 1,
              "settings" : { "calendar" : "jalali", "exchangeRates" : { "USD" : 90000, "XXX" : 1 }, "hasCompletedOnboarding" : true,
                             "primaryCurrency" : "IRT", "reminderHour" : 9, "theme" : "system" },
              "subscriptions" : [ {
                "carriedPaymentCount" : 0, "carriedSpendMinor" : 0, "category" : "something-new", "colorHex" : "#30A46C",
                "createdAt" : "2026-11-20T14:38:40Z", "currentPriceSince" : "2026-01-01",
                "cycle" : { "interval" : 1, "unit" : "month" },
                "id" : "7B0D8C1E-1C9B-4E8B-9A57-1E0C9C3F4A11", "name" : "Spotify",
                "price" : { "amountMinor" : 1199, "currency" : "USD" },
                "priceHistory" : [ { "amountMinor" : 1099, "effectiveFrom" : "2025-03-31" } ],
                "startDate" : "2025-03-31", "status" : "active"
              } ]
            }
        """.trimIndent()
        val data = BackupCodec.decode(ios)
        val s = data.subscriptions.single()
        assertEquals(SubscriptionCategory.Other, s.category)
        assertNull(s.reminderDaysBefore)
        assertNull(data.settings.defaultReminderDays)
        assertEquals(mapOf(CurrencyCode.UsDollar to 90_000.0), data.settings.exchangeRates)
        assertEquals(1099L, s.priceOn(d("2025-12-31")))
        assertEquals(1199L, s.priceOn(d("2026-01-31")))
        // And what Android writes reads back identically.
        assertEquals(data, BackupCodec.decode(BackupCodec.encode(data)))
        assertFalse(BackupCodec.encode(data).contains("null"))
    }

    @Test
    fun refusesFutureAndGarbage() {
        val future = assertThrows(StorageException.UnsupportedSchemaVersion::class.java) {
            BackupCodec.decode("""{"schemaVersion": 99, "subscriptions": [], "settings": {}}""")
        }
        assertEquals(99, future.version)
        assertThrows(StorageException.UnreadableBackup::class.java) { BackupCodec.decode("hello") }
        assertThrows(StorageException.UnreadableBackup::class.java) {
            BackupCodec.decode("""{"schemaVersion": 1, "subscriptions": [{"name": 5}], "settings": {}}""")
        }
    }

    @Test
    fun csv() {
        val lines = BackupCodec.csv(sample().subscriptions, d("2026-11-20")).split("\r\n")
        assertTrue(lines[0].startsWith("name,category,price,currency,cycle_unit"))
        assertTrue(lines[1], lines[1].startsWith("Spotify,music,11.99,USD,month,1,2025-03-31,,active,,2026-11-30,11.99,"))
        assertTrue(lines[1].endsWith("\"Family plan, \"\"shared\"\"\""))
        assertTrue(lines[2].startsWith("فیلیمو,streaming,120000,IRT,month,3,2026-02-01,2026-02-08"))
        assertEquals("\"'=HYPERLINK(\"\"x\"\")\"", BackupCodec.escape("=HYPERLINK(\"x\")"))
    }

    @Test
    fun catalog() {
        val catalog = ServiceCatalog.fromJson(File(SharedVectorTest.repoRoot, "shared/catalog.json").readText())
        assertTrue(catalog.services.size >= 30)
        assertEquals(listOf("netflix"), catalog.search("NETF").map { it.id })
        assertEquals(listOf("filimo"), catalog.search("فيليمو").map { it.id })
        assertEquals(listOf("youtube-premium"), catalog.search("youtubepremium").map { it.id })
        assertEquals(listOf("icloud"), catalog.search("آی کلاد").map { it.id })
        assertTrue(catalog.search("zzzz").isEmpty())
    }
}
