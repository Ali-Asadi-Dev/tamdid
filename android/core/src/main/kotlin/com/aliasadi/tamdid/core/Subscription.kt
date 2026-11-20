package com.aliasadi.tamdid.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.floor

@Serializable
enum class CycleUnit {
    @SerialName("day") Day,
    @SerialName("week") Week,
    @SerialName("month") Month,
    @SerialName("year") Year,
}

/** How often a subscription renews: every [interval] [unit]s. */
@Serializable
data class BillingCycle(val unit: CycleUnit, val interval: Int = 1) {
    init {
        require(interval >= 1) { "interval must be at least 1" }
    }

    /** Average number of billing periods per month (e.g. 4.348 for weekly, 1/12 for yearly). */
    val periodsPerMonth: Double
        get() {
            val perUnit = when (unit) {
                CycleUnit.Day -> 365.2425 / 12
                CycleUnit.Week -> 52.1775 / 12
                CycleUnit.Month -> 1.0
                CycleUnit.Year -> 1.0 / 12
            }
            return perUnit / interval
        }

    /**
     * The [n]th date of a series that starts at [anchor] (n = 0 is the anchor itself).
     * Always computed from the anchor, never by repeated addition, so month-end dates recover.
     */
    fun date(n: Int, anchor: LocalDate): LocalDate = when (unit) {
        CycleUnit.Day -> anchor.plusDays(n.toLong() * interval)
        CycleUnit.Week -> anchor.plusDays(7L * n * interval)
        CycleUnit.Month -> anchor.plusMonths(n.toLong() * interval)
        CycleUnit.Year -> anchor.plusMonths(12L * n * interval)
    }

    /** A safe lower bound for the period index whose date is on or after [date]. */
    internal fun approximateIndex(date: LocalDate, anchor: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(anchor, date)
        if (days <= 0) return 0
        val approxDaysPerPeriod = when (unit) {
            CycleUnit.Day -> interval.toDouble()
            CycleUnit.Week -> 7.0 * interval
            CycleUnit.Month -> 31.0 * interval
            CycleUnit.Year -> 366.0 * interval
        }
        return maxOf(0, (days / approxDaysPerPeriod).toInt() - 1)
    }

    companion object {
        val Weekly = BillingCycle(CycleUnit.Week)
        val Monthly = BillingCycle(CycleUnit.Month)
        val Quarterly = BillingCycle(CycleUnit.Month, 3)
        val Yearly = BillingCycle(CycleUnit.Year)
    }
}

@Serializable
enum class SubscriptionCategory {
    @SerialName("streaming") Streaming,
    @SerialName("music") Music,
    @SerialName("cloud") Cloud,
    @SerialName("productivity") Productivity,
    @SerialName("ai") Ai,
    @SerialName("education") Education,
    @SerialName("reading") Reading,
    @SerialName("gaming") Gaming,
    @SerialName("social") Social,
    @SerialName("shopping") Shopping,
    @SerialName("health") Health,
    @SerialName("utilities") Utilities,
    @SerialName("finance") Finance,
    @SerialName("other") Other;

    val key: String get() = name.lowercase()

    fun displayName(language: AppLanguage): String = when (language) {
        AppLanguage.English -> listOf("Video & TV", "Music", "Cloud storage", "Productivity", "AI tools", "Education", "Books & reading",
            "Gaming", "Social", "Shopping", "Health & fitness", "Internet & phone", "Finance", "Other")
        AppLanguage.Persian -> listOf("فیلم و سریال", "موسیقی", "فضای ابری", "بهره‌وری", "هوش مصنوعی", "آموزش", "کتاب و مطالعه",
            "بازی", "شبکه اجتماعی", "خرید", "سلامت و ورزش", "اینترنت و موبایل", "مالی", "سایر")
        AppLanguage.Arabic -> listOf("أفلام ومسلسلات", "موسيقى", "تخزين سحابي", "إنتاجية", "أدوات الذكاء الاصطناعي", "تعليم", "كتب وقراءة",
            "ألعاب", "تواصل اجتماعي", "تسوق", "صحة ولياقة", "إنترنت وهاتف", "مالية", "أخرى")
        AppLanguage.Turkish -> listOf("Film ve dizi", "Müzik", "Bulut depolama", "Verimlilik", "Yapay zekâ araçları", "Eğitim", "Kitap ve okuma",
            "Oyun", "Sosyal medya", "Alışveriş", "Sağlık ve spor", "İnternet ve telefon", "Finans", "Diğer")
        AppLanguage.German -> listOf("Filme & Serien", "Musik", "Cloud-Speicher", "Produktivität", "KI-Tools", "Bildung", "Bücher & Lesen",
            "Gaming", "Soziale Netzwerke", "Shopping", "Gesundheit & Fitness", "Internet & Telefon", "Finanzen", "Sonstiges")
    }[ordinal]

    companion object {
        fun fromKey(key: String): SubscriptionCategory? = entries.firstOrNull { it.key == key }
    }
}

@Serializable
enum class SubscriptionStatus {
    @SerialName("active") Active,
    @SerialName("paused") Paused,
    @SerialName("cancelled") Cancelled,
}

/** A payment that already happened in an earlier billing series (before a pause or cancellation). */
@Serializable
data class Charge(
    @Serializable(LocalDateSerializer::class) val date: LocalDate,
    val amountMinor: Long,
)

/** A price that applied from [effectiveFrom] until the next change. */
@Serializable
data class PriceChange(
    @Serializable(LocalDateSerializer::class) val effectiveFrom: LocalDate,
    val amountMinor: Long,
)

/**
 * A subscription. Immutable: lifecycle changes return a new copy.
 *
 * Field names and JSON shape match the iOS model exactly, so a backup made on one
 * platform restores on the other.
 */
@Serializable
data class Subscription(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val catalogID: String? = null,
    val category: SubscriptionCategory = SubscriptionCategory.Other,
    /** `#RRGGBB`. */
    val colorHex: String = "#5B4BDB",
    /** The current price. Earlier prices live in [priceHistory]. */
    val price: Money,
    val cycle: BillingCycle = BillingCycle.Monthly,
    /** The first payment date (or the sign-up date when there is a free trial). */
    @Serializable(LocalDateSerializer::class) val startDate: LocalDate,
    /** When set, the first charge happens on this date instead of [startDate]. */
    @Serializable(LocalDateSerializer::class) val trialEndDate: LocalDate? = null,
    val status: SubscriptionStatus = SubscriptionStatus.Active,
    /** The day the subscription was paused or cancelled. Charges on this day still count. */
    @Serializable(LocalDateSerializer::class) val endDate: LocalDate? = null,
    /** null turns reminders off. */
    val reminderDaysBefore: Int? = null,
    val paymentMethod: String? = null,
    val url: String? = null,
    val notes: String? = null,
    /** Earlier prices, oldest first, each with the day it started. */
    val priceHistory: List<PriceChange> = emptyList(),
    /** The day the current [price] took effect; null means since the start. */
    @Serializable(LocalDateSerializer::class) val currentPriceSince: LocalDate? = null,
    /**
     * Payments from earlier billing series, kept when a paused or cancelled subscription
     * is resumed so history (totals, chart, calendar) survives and the pause is not billed.
     */
    val pastCharges: List<Charge> = emptyList(),
    @Serializable(InstantSerializer::class) val createdAt: Instant = Instant.now().truncatedTo(ChronoUnit.SECONDS),
) {
    /** The first charge date: the trial end if there is one, otherwise the start date. */
    val anchorDate: LocalDate get() = trialEndDate ?: startDate

    val isActive: Boolean get() = status == SubscriptionStatus.Active

    fun isInTrial(date: LocalDate): Boolean = trialEndDate?.let { date < it } ?: false

    /**
     * The last day a charge can happen, or null for no limit.
     * A paused or cancelled subscription without an end date never charges.
     */
    private val chargeLimit: LocalDate?
        get() = if (status == SubscriptionStatus.Active) endDate else endDate ?: anchorDate.minusDays(1)

    /** All charge dates in [from]..[to], oldest first. */
    fun charges(from: LocalDate, to: LocalDate): List<LocalDate> {
        val limit = chargeLimit
        val upper = if (limit != null && limit < to) limit else to
        if (from > upper) return emptyList()
        val result = mutableListOf<LocalDate>()
        var n = cycle.approximateIndex(from, anchorDate)
        while (true) {
            val date = cycle.date(n, anchorDate)
            if (date > upper) break
            if (date >= from) result += date
            n++
        }
        return result
    }

    /** The first charge on or after [date], or null if the subscription will not charge again. */
    fun nextRenewal(onOrAfter: LocalDate): LocalDate? {
        val limit = chargeLimit
        if (limit != null && limit < onOrAfter) return null
        var n = cycle.approximateIndex(onOrAfter, anchorDate)
        while (true) {
            val candidate = cycle.date(n, anchorDate)
            if (candidate >= onOrAfter) {
                return if (limit != null && candidate > limit) null else candidate
            }
            n++
        }
    }

    /** The price that applied on [date]. */
    fun priceOn(date: LocalDate): Long {
        val since = currentPriceSince
        if (since != null && date < since) {
            return priceHistory.lastOrNull { it.effectiveFrom <= date }?.amountMinor
                ?: priceHistory.firstOrNull()?.amountMinor
                ?: price.amountMinor
        }
        return price.amountMinor
    }

    /** Records a new price effective from [date]. Charges before [date] keep the old price. */
    fun changePrice(amountMinor: Long, effectiveFrom: LocalDate): Subscription {
        if (amountMinor == price.amountMinor) return this
        val previousSince = currentPriceSince ?: startDate
        return if (effectiveFrom > previousSince) {
            copy(
                priceHistory = priceHistory + PriceChange(previousSince, price.amountMinor),
                currentPriceSince = effectiveFrom,
                price = price.copy(amountMinor = amountMinor),
            )
        } else {
            // A change dated on or before the current price's start simply corrects it.
            copy(price = price.copy(amountMinor = amountMinor))
        }
    }

    /** Every payment in [from]..[to] with its amount: earlier series first, then the current one. */
    fun payments(from: LocalDate, to: LocalDate): List<Charge> =
        pastCharges.filter { it.date in from..to } + charges(from, to).map { Charge(it, priceOn(it)) }

    /** The earliest day anything was charged (or will be), for "all time" queries. */
    private val firstPaymentBound: LocalDate
        get() = minOf(pastCharges.firstOrNull()?.date ?: anchorDate, anchorDate)

    /** Total paid up to and including [asOf], using the price that applied at each charge. */
    fun totalSpent(asOf: LocalDate): Long = payments(firstPaymentBound, asOf).sumOf { it.amountMinor }

    fun paymentCount(asOf: LocalDate): Int = payments(firstPaymentBound, asOf).size

    /** The current price spread over an average month, in minor units, rounded half up. */
    val monthlyCostMinor: Long get() = roundHalfUp(price.amountMinor * cycle.periodsPerMonth)

    val yearlyCostMinor: Long get() = roundHalfUp(price.amountMinor * cycle.periodsPerMonth * 12)

    fun pause(on: LocalDate): Subscription =
        if (status != SubscriptionStatus.Active) this else copy(status = SubscriptionStatus.Paused, endDate = on)

    /** Cancels the subscription. A paused one keeps its pause date as the last charge day. */
    fun cancel(on: LocalDate): Subscription = copy(
        status = SubscriptionStatus.Cancelled,
        endDate = if (status == SubscriptionStatus.Active || endDate == null) on else endDate,
    )

    /**
     * Resumes a paused or cancelled subscription with the next charge on [on].
     * Payments so far are kept in [pastCharges], so the paused period is not billed.
     */
    fun resume(on: LocalDate): Subscription {
        if (status == SubscriptionStatus.Active) return this
        val lastDay = minOf(endDate ?: on.minusDays(1), on.minusDays(1))
        return copy(
            pastCharges = payments(firstPaymentBound, lastDay),
            status = SubscriptionStatus.Active,
            endDate = null,
            trialEndDate = null,
            startDate = on,
            priceHistory = emptyList(),
            currentPriceSince = null,
        )
    }

    companion object {
        fun roundHalfUp(value: Double): Long = floor(value + 0.5).toLong()
    }
}

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

/** ISO-8601 instants with whole seconds ("2026-11-20T14:38:40Z"), as iOS writes them. */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) =
        encoder.encodeString(value.truncatedTo(ChronoUnit.SECONDS).toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
