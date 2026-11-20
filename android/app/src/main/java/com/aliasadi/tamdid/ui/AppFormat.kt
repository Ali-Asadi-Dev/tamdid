package com.aliasadi.tamdid.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.aliasadi.tamdid.R
import com.aliasadi.tamdid.core.AppLanguage
import com.aliasadi.tamdid.core.BillingCycle
import com.aliasadi.tamdid.core.CalendarSystem
import com.aliasadi.tamdid.core.CycleUnit
import com.aliasadi.tamdid.core.DateText
import com.aliasadi.tamdid.core.Digits
import com.aliasadi.tamdid.core.Money
import com.aliasadi.tamdid.core.MoneyFormatter
import com.aliasadi.tamdid.core.MoneyTotal
import java.time.LocalDate

/** Formatting in the user's language and calendar, provided to every screen. */
@Immutable
data class AppFormat(val language: AppLanguage, val calendar: CalendarSystem, val today: LocalDate) {
    fun money(money: Money): String = MoneyFormatter.format(money, language)
    fun date(date: LocalDate, style: DateText.Style = DateText.Style.Medium): String =
        DateText.format(date, calendar, language, style)
    fun relative(date: LocalDate): String = DateText.relative(date, today, language)
    fun number(value: Int): String = Digits.localize(value.toString(), language)

    /** "+ €15.49" lines for amounts that could not be converted. */
    fun unconverted(total: MoneyTotal): String =
        total.unconverted.entries.joinToString(if (language.isRightToLeft) "، " else ", ") { (currency, amount) ->
            money(Money(amount, currency))
        }
}

val LocalAppFormat = staticCompositionLocalOf {
    AppFormat(AppLanguage.English, CalendarSystem.Gregorian, LocalDate.now())
}

@Composable
fun currentLanguage(): AppLanguage =
    AppLanguage.forLocale(LocalConfiguration.current.locales[0].language)

@Composable
fun cycleText(cycle: BillingCycle): String {
    val format = LocalAppFormat.current
    return when {
        cycle == BillingCycle.Weekly -> stringResource(R.string.cycle_weekly)
        cycle == BillingCycle.Monthly -> stringResource(R.string.cycle_monthly)
        cycle == BillingCycle.Yearly -> stringResource(R.string.cycle_yearly)
        else -> Digits.localize(stringResource(R.string.cycle_every, cycle.interval, unitText(cycle.unit, cycle.interval)), format.language)
    }
}

@Composable
fun unitText(unit: CycleUnit, count: Int): String = pluralStringResource(
    when (unit) {
        CycleUnit.Day -> R.plurals.unit_day
        CycleUnit.Week -> R.plurals.unit_week
        CycleUnit.Month -> R.plurals.unit_month
        CycleUnit.Year -> R.plurals.unit_year
    },
    count,
)

@Composable
fun reminderText(days: Int?): String {
    val language = LocalAppFormat.current.language
    return when (days) {
        null -> stringResource(R.string.reminder_off)
        0 -> stringResource(R.string.reminder_same_day)
        else -> Digits.localize(pluralStringResource(R.plurals.reminder_days_before, days, days), language)
    }
}
