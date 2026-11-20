package com.aliasadi.tamdid.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Runs `shared/test-vectors/business-rules.json`, the same fixtures the iOS suite runs. */
class SharedVectorTest {
    companion object {
        val repoRoot = File(System.getProperty("tamdid.repoRoot") ?: "../..")
        val vectors: JsonObject by lazy {
            Json.parseToJsonElement(File(repoRoot, "shared/test-vectors/business-rules.json").readText()).jsonObject
        }

        private fun JsonObject.str(key: String): String? =
            this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content

        fun subscription(v: JsonObject) = Subscription(
            name = v.str("name")!!,
            price = Money(100, CurrencyCode.UsDollar),
            cycle = BillingCycle(CycleUnit.valueOf(v.str("unit")!!.replaceFirstChar { it.uppercase() }), v["interval"]!!.jsonPrimitive.int),
            startDate = LocalDate.parse(v.str("start")),
            trialEndDate = v.str("trialEnd")?.let(LocalDate::parse),
            status = SubscriptionStatus.valueOf(v.str("status")!!.replaceFirstChar { it.uppercase() }),
            endDate = v.str("endDate")?.let(LocalDate::parse),
        )
    }

    @Test
    fun nextRenewal() {
        val cases = vectors["nextRenewal"]!!.jsonArray
        check(cases.size >= 10)
        for (case in cases.map { it.jsonObject }) {
            val expected = case.str("expected")
            val actual = subscription(case).nextRenewal(LocalDate.parse(case.str("onOrAfter")))
            assertEquals(case.str("name"), expected, actual?.toString())
        }
    }

    @Test
    fun chargesInAWindow() {
        for (case in vectors["charges"]!!.jsonArray.map { it.jsonObject }) {
            val expected = (case["expected"] as JsonArray).map { it.jsonPrimitive.content }
            val actual = subscription(case).charges(LocalDate.parse(case.str("from")), LocalDate.parse(case.str("to")))
            assertEquals(case.str("name"), expected, actual.map { it.toString() })
        }
    }

    @Test
    fun jalali() {
        val jalali = vectors["jalali"]!!.jsonObject
        for (case in jalali["conversions"]!!.jsonArray.map { it.jsonObject }) {
            val gregorian = LocalDate.parse(case.str("gregorian"))
            val converted = Jalali.fromGregorian(gregorian)
            assertEquals(case.str("jalali"), converted.toString())
            assertEquals(gregorian, Jalali.toGregorian(converted))
        }
        for ((year, isLeap) in jalali["leapYears"]!!.jsonObject) {
            assertEquals("year $year", isLeap.jsonPrimitive.boolean, Jalali.isLeap(year.toInt()))
        }
        for (case in jalali["monthLengths"]!!.jsonArray.map { it.jsonObject }) {
            assertEquals(case["days"]!!.jsonPrimitive.int, Jalali.daysInMonth(case["year"]!!.jsonPrimitive.int, case["month"]!!.jsonPrimitive.int))
        }
    }

    @Test
    fun moneyFormatting() {
        for (case in vectors["money"]!!.jsonArray.map { it.jsonObject }) {
            val money = Money(case["amountMinor"]!!.jsonPrimitive.long, CurrencyCode.fromCode(case.str("currency")!!)!!)
            val language = AppLanguage.fromCode(case.str("language")!!)!!
            assertEquals(case.str("expected"), MoneyFormatter.format(money, language))
        }
    }

    @Test
    fun monthlyCost() {
        for (case in vectors["monthlyCost"]!!.jsonArray.map { it.jsonObject }) {
            val s = Subscription(
                name = "x",
                price = Money(case["amountMinor"]!!.jsonPrimitive.long, CurrencyCode.UsDollar),
                cycle = BillingCycle(CycleUnit.valueOf(case.str("unit")!!.replaceFirstChar { it.uppercase() }), case["interval"]!!.jsonPrimitive.int),
                startDate = LocalDate.of(2026, 1, 1),
            )
            assertEquals(case.toString(), case["expected"]!!.jsonPrimitive.long, s.monthlyCostMinor)
        }
    }

    @Test
    fun dateTextRelativeAndWeekdays() {
        for (case in vectors["dateText"]!!.jsonArray.map { it.jsonObject }) {
            val style = when (case.str("style")) {
                "medium" -> DateText.Style.Medium
                "dayMonth" -> DateText.Style.DayMonth
                "monthYear" -> DateText.Style.MonthYear
                else -> DateText.Style.ShortMonth
            }
            val calendar = if (case.str("calendar") == "jalali") CalendarSystem.Jalali else CalendarSystem.Gregorian
            assertEquals(case.toString(), case.str("expected"),
                DateText.format(LocalDate.parse(case.str("date")), calendar, AppLanguage.fromCode(case.str("language")!!)!!, style))
        }
        for (case in vectors["relative"]!!.jsonArray.map { it.jsonObject }) {
            assertEquals(case.toString(), case.str("expected"),
                DateText.relative(LocalDate.parse(case.str("date")), LocalDate.parse(case.str("today")), AppLanguage.fromCode(case.str("language")!!)!!))
        }
        for (case in vectors["weekdays"]!!.jsonArray.map { it.jsonObject }) {
            assertEquals(case.toString(), case["expected"]!!.jsonArray.map { it.jsonPrimitive.content },
                DateText.weekdaySymbols(case["firstWeekday"]!!.jsonPrimitive.int, AppLanguage.fromCode(case.str("language")!!)!!))
        }
    }

    @Test
    fun everyLanguageHasEveryName() {
        for (language in AppLanguage.entries) {
            SubscriptionCategory.entries.forEach { check(it.displayName(language).isNotEmpty()) }
            CurrencyCode.entries.forEach { check(it.displayName(language).isNotEmpty()) }
            for (calendar in CalendarSystem.entries) for (m in 1..12) check(DateText.monthName(m, calendar, language).isNotEmpty())
        }
        assertEquals(AppLanguage.Persian, AppLanguage.forLocale("fa-IR"))
        assertEquals(AppLanguage.English, AppLanguage.forLocale("ja"))
        assertEquals(6, DateText.defaultFirstWeekday(CalendarSystem.Gregorian, AppLanguage.Arabic))
        assertEquals(120000L, MoneyFormatter.parse("١٢٠٬٠٠٠", CurrencyCode.Toman))
    }

    @Test
    fun parsingTypedPrices() {
        for (case in vectors["parse"]!!.jsonArray.map { it.jsonObject }) {
            val expected = case["expected"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.long
            assertEquals(case.toString(), expected, MoneyFormatter.parse(case.str("input")!!, CurrencyCode.fromCode(case.str("currency")!!)!!))
        }
    }
}
