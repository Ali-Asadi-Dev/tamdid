package com.aliasadi.tamdid

import com.aliasadi.tamdid.core.AppData
import com.aliasadi.tamdid.core.AppLanguage
import com.aliasadi.tamdid.core.AppSettings
import com.aliasadi.tamdid.core.BillingCycle
import com.aliasadi.tamdid.core.CurrencyCode
import com.aliasadi.tamdid.core.Money
import com.aliasadi.tamdid.core.Subscription
import com.aliasadi.tamdid.core.SubscriptionCategory
import com.aliasadi.tamdid.data.Clock
import java.time.LocalDate

object SampleData {
    val today: LocalDate = LocalDate.of(2026, 11, 20)
    val clock = Clock { today }

    fun english(): AppData = AppData(
        subscriptions = listOf(
            Subscription(id = "netflix", name = "Netflix", catalogID = "netflix", category = SubscriptionCategory.Streaming, colorHex = "#E5484D",
                price = Money(1549, CurrencyCode.UsDollar), startDate = LocalDate.of(2025, 4, 22), reminderDaysBefore = 1),
            Subscription(id = "spotify", name = "Spotify", category = SubscriptionCategory.Music, colorHex = "#30A46C",
                price = Money(1199, CurrencyCode.UsDollar), startDate = LocalDate.of(2024, 9, 25), reminderDaysBefore = 1)
                .changePrice(1299, LocalDate.of(2026, 7, 25)),
            Subscription(id = "icloud", name = "iCloud+", category = SubscriptionCategory.Cloud, colorHex = "#0090FF",
                price = Money(299, CurrencyCode.UsDollar), startDate = LocalDate.of(2023, 1, 3)),
            Subscription(id = "claude", name = "Claude Pro", category = SubscriptionCategory.Ai, colorHex = "#AD7F58",
                price = Money(2000, CurrencyCode.UsDollar), startDate = LocalDate.of(2026, 11, 10), trialEndDate = LocalDate.of(2026, 11, 24), reminderDaysBefore = 2),
            Subscription(id = "gym", name = "City Gym", category = SubscriptionCategory.Health, colorHex = "#8E4EC6",
                price = Money(39000, CurrencyCode.UsDollar), cycle = BillingCycle.Yearly, startDate = LocalDate.of(2026, 2, 1)),
            Subscription(id = "disney", name = "Disney+", category = SubscriptionCategory.Streaming, colorHex = "#3E63DD",
                price = Money(999, CurrencyCode.Euro), startDate = LocalDate.of(2026, 5, 1)),
        ),
        settings = AppSettings.defaults(AppLanguage.English).copy(hasCompletedOnboarding = true)
            .withExchangeRates(mapOf(CurrencyCode.Euro to 1.08)),
    )

    fun persian(): AppData = AppData(
        subscriptions = listOf(
            Subscription(id = "filimo", name = "فیلیمو", category = SubscriptionCategory.Streaming, colorHex = "#E5484D",
                price = Money(159_000, CurrencyCode.Toman), startDate = LocalDate.of(2026, 3, 25), reminderDaysBefore = 1),
            Subscription(id = "namava", name = "نماوا", category = SubscriptionCategory.Streaming, colorHex = "#F76B15",
                price = Money(139_000, CurrencyCode.Toman), startDate = LocalDate.of(2026, 6, 23)),
            Subscription(id = "net", name = "اینترنت خانگی", category = SubscriptionCategory.Utilities, colorHex = "#0090FF",
                price = Money(480_000, CurrencyCode.Toman), startDate = LocalDate.of(2025, 12, 1), reminderDaysBefore = 3),
            Subscription(id = "taaghche", name = "طاقچه", category = SubscriptionCategory.Reading, colorHex = "#12A594",
                price = Money(89_000, CurrencyCode.Toman), startDate = LocalDate.of(2026, 11, 15), trialEndDate = LocalDate.of(2026, 11, 22)),
            Subscription(id = "spotify", name = "Spotify", category = SubscriptionCategory.Music, colorHex = "#30A46C",
                price = Money(1199, CurrencyCode.UsDollar), startDate = LocalDate.of(2025, 1, 27)),
        ),
        settings = AppSettings.defaults(AppLanguage.Persian).copy(hasCompletedOnboarding = true)
            .withExchangeRates(mapOf(CurrencyCode.UsDollar to 105_000.0)),
    )

    /** QA scenario: long names, missing exchange rates, paused and cancelled plans, many rows. */
    fun edgeCases(): AppData {
        val base = english()
        val extra = (1..12).map { i ->
            Subscription(id = "bulk$i", name = "Service number $i", category = SubscriptionCategory.entries[i % 14],
                price = Money(100L * i, CurrencyCode.UsDollar), startDate = LocalDate.of(2026, 1, i))
        }
        val long = Subscription(id = "long", name = "An extremely long subscription name that will not fit on one line",
            category = SubscriptionCategory.Productivity, price = Money(99_999_999, CurrencyCode.Pound), startDate = LocalDate.of(2026, 3, 3))
        val paused = Subscription(id = "paused", name = "Paused plan", price = Money(500, CurrencyCode.UsDollar),
            startDate = LocalDate.of(2026, 1, 5)).pause(LocalDate.of(2026, 6, 1))
        val cancelled = Subscription(id = "cancelled", name = "Cancelled plan", price = Money(700, CurrencyCode.Lira),
            startDate = LocalDate.of(2025, 1, 5)).cancel(LocalDate.of(2026, 2, 1))
        return base.copy(subscriptions = base.subscriptions + extra + long + paused + cancelled)
    }
}
