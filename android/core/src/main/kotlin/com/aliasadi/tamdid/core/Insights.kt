package com.aliasadi.tamdid.core

import java.time.LocalDate

data class UpcomingCharge(val subscription: Subscription, val date: LocalDate, val isTrialEnd: Boolean) {
    val id: String get() = "${subscription.id}-$date"
}

data class CategorySpend(val category: SubscriptionCategory, val monthlyMinor: Long, val count: Int)

data class MonthSpend(val year: Int, val month: Int, val total: MoneyTotal)

data class RankedSubscription(val subscription: Subscription, val monthlyMinor: Long)

data class CategoryOverlap(val category: SubscriptionCategory, val subscriptions: List<Subscription>)

/** Read-only views over the user's subscriptions, used by the dashboard, insights and calendar. */
class Insights(
    val subscriptions: List<Subscription>,
    settings: AppSettings,
    val today: LocalDate,
) {
    private val converter = CurrencyConverter(settings)

    val active: List<Subscription> get() = subscriptions.filter { it.isActive }

    /**
     * Recurring cost per average month of all active subscriptions (trials included,
     * since they will be charged unless cancelled).
     */
    val monthlyTotal: MoneyTotal
        get() = active.fold(MoneyTotal.zero(converter.primary)) { total, s ->
            total.plus(s.monthlyCostMinor, s.price.currency, converter)
        }

    val yearlyTotal: MoneyTotal
        get() = active.fold(MoneyTotal.zero(converter.primary)) { total, s ->
            total.plus(s.yearlyCostMinor, s.price.currency, converter)
        }

    /** Average spend per day in the primary currency. */
    val dailyAverageMinor: Long get() = Subscription.roundHalfUp(yearlyTotal.primary.amountMinor / 365.2425)

    /** Charges in the next [days] days (today included), soonest first. */
    fun upcoming(days: Int): List<UpcomingCharge> {
        val end = today.plusDays(days - 1L)
        return subscriptions
            .flatMap { s -> s.charges(today, end).map { UpcomingCharge(s, it, it == s.trialEndDate) } }
            .sortedWith(compareBy({ it.date }, { it.subscription.name }))
    }

    /** Active trials that end within [days] days. */
    fun endingTrials(days: Int): List<UpcomingCharge> = upcoming(days).filter { it.isTrialEnd }

    /** Every charge between two dates, for the calendar. */
    fun charges(from: LocalDate, to: LocalDate): Map<LocalDate, List<Subscription>> {
        val result = mutableMapOf<LocalDate, MutableList<Subscription>>()
        for (s in subscriptions) for (date in s.charges(from, to)) result.getOrPut(date) { mutableListOf() } += s
        return result.mapValues { (_, list) -> list.sortedBy { it.name } }
    }

    /**
     * Monthly cost by category in the primary currency, largest first.
     * Subscriptions whose currency has no exchange rate are left out.
     */
    val byCategory: List<CategorySpend>
        get() {
            val spend = linkedMapOf<SubscriptionCategory, CategorySpend>()
            for (s in active) {
                val monthly = converter.toPrimary(s.monthlyCostMinor, s.price.currency) ?: continue
                val current = spend[s.category] ?: CategorySpend(s.category, 0, 0)
                spend[s.category] = current.copy(monthlyMinor = current.monthlyMinor + monthly, count = current.count + 1)
            }
            return spend.values.sortedWith(compareByDescending<CategorySpend> { it.monthlyMinor }.thenBy { it.category.key })
        }

    /** The most expensive active subscriptions by monthly cost in the primary currency. */
    fun mostExpensive(limit: Int): List<RankedSubscription> = active
        .mapNotNull { s -> converter.toPrimary(s.monthlyCostMinor, s.price.currency)?.let { RankedSubscription(s, it) } }
        .sortedByDescending { it.monthlyMinor }
        .take(limit)

    /**
     * Actual charges per month for the last [count] months (oldest first), bucketed by
     * month in [calendar] so Jalali users see Farvardin, Ordibehesht, …
     */
    fun spendByMonth(count: Int, calendar: CalendarSystem): List<MonthSpend> {
        val current = CalendarMonth.containing(today, calendar)
        val months = (count - 1 downTo 0).map { current.plusMonths(-it) }
        if (months.isEmpty()) return emptyList()
        val buckets = months.associateWith { MoneyTotal.zero(converter.primary) }.toMutableMap()
        for (s in subscriptions) {
            for (date in s.charges(months.first().firstDay, today)) {
                val month = CalendarMonth.containing(date, calendar)
                val bucket = buckets[month] ?: continue
                buckets[month] = bucket.plus(s.priceOn(date), s.price.currency, converter)
            }
        }
        return months.map { MonthSpend(it.year, it.month, buckets.getValue(it)) }
    }

    /** Categories where the user pays for more than one service that does the same job. */
    val overlaps: List<CategoryOverlap>
        get() {
            val overlapping = setOf(
                SubscriptionCategory.Streaming, SubscriptionCategory.Music, SubscriptionCategory.Cloud,
                SubscriptionCategory.Ai, SubscriptionCategory.Reading,
            )
            return active.filter { it.category in overlapping }
                .groupBy { it.category }
                .filter { it.value.size > 1 }
                .map { (category, list) -> CategoryOverlap(category, list.sortedBy { it.name }) }
                .sortedBy { it.category.key }
        }
}

data class PlannedReminder(
    val subscriptionId: String,
    val subscriptionName: String,
    val kind: Kind,
    val chargeDate: LocalDate,
    val fireDate: LocalDate,
    val amount: Money,
) {
    enum class Kind { Renewal, TrialEnding }

    /** Stable across rebuilds, so platforms can diff scheduled notifications. */
    val id: String get() = "$subscriptionId-$chargeDate"
}

object ReminderPlanner {
    /**
     * The reminders to schedule, soonest first. Capped (iOS allows 64 pending local
     * notifications) and rebuilt whenever data changes or the app opens.
     */
    fun plan(
        subscriptions: List<Subscription>,
        today: LocalDate,
        horizonDays: Int = 60,
        limit: Int = 60,
    ): List<PlannedReminder> {
        val end = today.plusDays(horizonDays.toLong())
        val result = mutableListOf<PlannedReminder>()
        for (s in subscriptions) {
            if (!s.isActive) continue
            val daysBefore = s.reminderDaysBefore ?: continue
            for (charge in s.charges(today, end.plusDays(daysBefore.toLong()))) {
                val fire = charge.minusDays(daysBefore.toLong())
                if (fire < today || fire > end) continue
                result += PlannedReminder(
                    subscriptionId = s.id,
                    subscriptionName = s.name,
                    kind = if (charge == s.trialEndDate) PlannedReminder.Kind.TrialEnding else PlannedReminder.Kind.Renewal,
                    chargeDate = charge,
                    fireDate = fire,
                    amount = Money(s.priceOn(charge), s.price.currency),
                )
            }
        }
        return result
            .sortedWith(compareBy({ it.fireDate }, { it.subscriptionName }, { it.chargeDate }))
            .take(limit)
    }
}
