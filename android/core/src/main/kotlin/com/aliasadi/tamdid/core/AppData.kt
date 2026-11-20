package com.aliasadi.tamdid.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
enum class CalendarSystem {
    @SerialName("gregorian") Gregorian,
    @SerialName("jalali") Jalali,
}

@Serializable
enum class ThemePreference {
    @SerialName("system") System,
    @SerialName("light") Light,
    @SerialName("dark") Dark,
}

@Serializable
data class AppSettings(
    val primaryCurrency: CurrencyCode = CurrencyCode.UsDollar,
    val calendar: CalendarSystem = CalendarSystem.Gregorian,
    /** Reminder applied to new subscriptions; null means off. */
    val defaultReminderDays: Int? = null,
    val reminderHour: Int = 9,
    val theme: ThemePreference = ThemePreference.System,
    /**
     * 1 unit of the key currency = value units of [primaryCurrency]. Stored with string keys
     * so an unknown currency in a backup is skipped instead of failing the whole import.
     */
    @SerialName("exchangeRates") private val rawExchangeRates: Map<String, Double> = emptyMap(),
    val hasCompletedOnboarding: Boolean = false,
) {
    @Transient
    val exchangeRates: Map<CurrencyCode, Double> =
        rawExchangeRates.mapNotNull { (key, value) -> CurrencyCode.fromCode(key)?.let { it to value } }.toMap()

    fun withExchangeRates(rates: Map<CurrencyCode, Double>): AppSettings =
        copy(rawExchangeRates = rates.mapKeys { it.key.code })

    companion object {
        /** Sensible defaults for someone using the app in [language]. */
        fun defaults(language: AppLanguage): AppSettings = when (language) {
            AppLanguage.Persian -> AppSettings(CurrencyCode.Toman, CalendarSystem.Jalali, defaultReminderDays = 1)
            AppLanguage.English -> AppSettings(CurrencyCode.UsDollar, CalendarSystem.Gregorian, defaultReminderDays = 1)
        }
    }
}

/** Everything the app stores. Saved as one JSON document; also the backup format. */
@Serializable
data class AppData(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val subscriptions: List<Subscription> = emptyList(),
    val settings: AppSettings = AppSettings(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

class CurrencyConverter(val primary: CurrencyCode, private val rates: Map<CurrencyCode, Double>) {
    constructor(settings: AppSettings) : this(settings.primaryCurrency, settings.exchangeRates)

    /**
     * Converts minor units of [currency] into minor units of the primary currency,
     * or null when there is no rate. Never guesses.
     */
    fun toPrimary(amountMinor: Long, currency: CurrencyCode): Long? {
        if (currency == primary) return amountMinor
        val rate = rates[currency]?.takeIf { it > 0 } ?: return null
        val major = amountMinor.toDouble() / currency.minorUnitsPerMajor
        return Subscription.roundHalfUp(major * rate * primary.minorUnitsPerMajor)
    }
}

/** A total in the primary currency, plus whatever could not be converted. */
data class MoneyTotal(
    val primary: Money,
    val unconverted: Map<CurrencyCode, Long> = emptyMap(),
) {
    val isComplete: Boolean get() = unconverted.isEmpty()

    fun plus(amountMinor: Long, currency: CurrencyCode, converter: CurrencyConverter): MoneyTotal {
        val converted = converter.toPrimary(amountMinor, currency)
        return if (converted != null) {
            copy(primary = primary.copy(amountMinor = primary.amountMinor + converted))
        } else {
            copy(unconverted = unconverted + (currency to (unconverted[currency] ?: 0) + amountMinor))
        }
    }

    companion object {
        fun zero(currency: CurrencyCode) = MoneyTotal(Money.zero(currency))
    }
}
