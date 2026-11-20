import Foundation
import Testing
@testable import TamdidCore

private func d(_ iso: String) -> CivilDate { CivilDate(iso: iso)! }

@Suite struct CivilDateTests {
    @Test func rejectsImpossibleDates() {
        #expect(CivilDate(year: 2026, month: 2, day: 29) == nil)
        #expect(CivilDate(year: 2024, month: 2, day: 29) != nil)
        #expect(CivilDate(year: 2026, month: 13, day: 1) == nil)
        #expect(CivilDate(year: 2026, month: 4, day: 31) == nil)
        #expect(CivilDate(iso: "2026-11") == nil)
        #expect(CivilDate(iso: "not a date") == nil)
    }

    @Test func epochDayRoundTripsAndMatchesFoundation() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        let start = d("1899-12-25").epochDay
        for epochDay in stride(from: start, through: d("2101-01-05").epochDay, by: 1) {
            let date = CivilDate(epochDay: epochDay)
            #expect(date.epochDay == epochDay)
            if epochDay % 97 == 0 { // spot-check against Foundation without making the test slow
                let foundation = calendar.dateComponents([.year, .month, .day], from: Date(timeIntervalSince1970: Double(epochDay) * 86_400))
                #expect((foundation.year, foundation.month, foundation.day) == (date.year, date.month, date.day))
            }
        }
    }

    @Test func weekdays() {
        #expect(d("1970-01-01").isoWeekday == 4) // Thursday
        #expect(d("2026-11-20").isoWeekday == 5) // Friday
        #expect(d("2026-11-21").isoWeekday == 6) // Saturday
        #expect(d("1969-12-28").isoWeekday == 7) // Sunday, before the epoch
    }

    @Test func monthArithmeticClampsAndCrossesYears() {
        #expect(d("2026-01-31").adding(months: 1) == d("2026-02-28"))
        #expect(d("2024-01-31").adding(months: 1) == d("2024-02-29"))
        #expect(d("2026-11-30").adding(months: 3) == d("2027-02-28"))
        #expect(d("2026-03-31").adding(months: -1) == d("2026-02-28"))
        #expect(d("2026-01-15").adding(months: -13) == d("2024-12-15"))
        #expect(d("2026-12-31").adding(days: 1) == d("2027-01-01"))
    }

    @Test func codableUsesIsoStrings() throws {
        let data = try JSONEncoder().encode([d("2026-03-05")])
        #expect(String(decoding: data, as: UTF8.self) == "[\"2026-03-05\"]")
        #expect(try JSONDecoder().decode([CivilDate].self, from: data) == [d("2026-03-05")])
        #expect(throws: DecodingError.self) { try JSONDecoder().decode([CivilDate].self, from: Data("[\"2026-02-30\"]".utf8)) }
    }

    @Test func bridgesToFoundationInAGivenTimeZone() {
        let tehran = TimeZone(identifier: "Asia/Tehran")!
        // 2026-11-20 21:00 UTC is already the 21st in Tehran (UTC+3:30).
        let instant = Date(timeIntervalSince1970: Double(d("2026-11-20").epochDay) * 86_400 + 21 * 3600)
        #expect(CivilDate(instant, timeZone: tehran) == d("2026-11-21"))
        #expect(CivilDate(instant, timeZone: TimeZone(identifier: "UTC")!) == d("2026-11-20"))
        let nine = d("2026-11-21").date(hour: 9, timeZone: tehran)
        #expect(nine.timeIntervalSince1970 == Double(d("2026-11-21").epochDay) * 86_400 + 9 * 3600 - 3.5 * 3600)
    }
}

@Suite struct JalaliTests {
    @Test func roundTripsEveryDayFor200Years() {
        var previous: JalaliDate?
        for epochDay in d("1925-01-01").epochDay...d("2125-01-01").epochDay {
            let gregorian = CivilDate(epochDay: epochDay)
            let jalali = Jalali.fromGregorian(gregorian)
            #expect(Jalali.toGregorian(jalali) == gregorian)
            #expect((1...Jalali.daysInMonth(year: jalali.year, month: jalali.month)).contains(jalali.day))
            if let previous { // consecutive days advance by exactly one Jalali day
                let continues = jalali.day == previous.day + 1 && jalali.month == previous.month
                let newMonth = jalali.day == 1 && (jalali.month == previous.month + 1 || (jalali.month == 1 && previous.month == 12))
                #expect(continues || newMonth)
            }
            previous = jalali
        }
    }

    @Test func nowruzFallsOnTheMarchEquinox() {
        for year in 1390...1420 {
            let nowruz = Jalali.toGregorian(JalaliDate(year: year, month: 1, day: 1))
            #expect(nowruz.month == 3 && (20...21).contains(nowruz.day), "\(year) → \(nowruz)")
        }
    }
}

@Suite struct CalendarDisplayTests {
    @Test func gregorianMonthGridStartsOnMonday() {
        let month = CalendarMonth(system: .gregorian, year: 2026, month: 11)
        let weeks = month.weeks(firstWeekday: 1)
        // November 1st 2026 is a Sunday: six empty cells, then the 1st.
        #expect(weeks.first?.prefix(6).allSatisfy { $0 == nil } == true)
        #expect(weeks.first?[6] == d("2026-11-01"))
        #expect(weeks.allSatisfy { $0.count == 7 })
        #expect(weeks.flatMap { $0 }.compactMap { $0 }.count == 30)
    }

    @Test func jalaliMonthGridStartsOnSaturday() {
        let month = CalendarMonth(system: .jalali, year: 1405, month: 8) // Aban
        #expect(month.firstDay == d("2026-10-23"))
        #expect(month.lastDay == d("2026-11-21"))
        let weeks = month.weeks(firstWeekday: 6)
        // 2026-10-23 is a Friday: Sat, Sun, Mon, Tue, Wed, Thu are empty.
        #expect(weeks.first?.firstIndex { $0 != nil } == 6)
    }

    @Test func monthNavigationWrapsYears() {
        let farvardin = CalendarMonth(system: .jalali, year: 1405, month: 1)
        #expect(farvardin.adding(months: -1) == CalendarMonth(system: .jalali, year: 1404, month: 12))
        #expect(farvardin.adding(months: 12) == CalendarMonth(system: .jalali, year: 1406, month: 1))
        #expect(farvardin.adding(months: -25) == CalendarMonth(system: .jalali, year: 1402, month: 12))
        #expect(CalendarMonth.containing(d("2026-03-20"), in: .jalali) == CalendarMonth(system: .jalali, year: 1404, month: 12))
    }

    @Test func dateTextInBothCalendarsAndLanguages() {
        let date = d("2026-11-20")
        #expect(DateText.format(date, calendar: .gregorian, language: .english, style: .medium) == "Nov 20, 2026")
        #expect(DateText.format(date, calendar: .jalali, language: .persian, style: .medium) == "۲۹ آبان ۱۴۰۵")
        #expect(DateText.format(date, calendar: .jalali, language: .english, style: .medium) == "29 Aban 1405")
        #expect(DateText.format(date, calendar: .gregorian, language: .persian, style: .dayMonth) == "۲۰ نوامبر")
        #expect(DateText.format(date, calendar: .gregorian, language: .english, style: .monthYear) == "November 2026")
        #expect(DateText.format(CalendarMonth(system: .jalali, year: 1405, month: 1), language: .persian) == "فروردین ۱۴۰۵")
    }

    @Test func languagesMapFromLocaleCodes() {
        #expect(AppLanguage(code: "fa-IR") == .persian)
        #expect(AppLanguage(code: "de_CH") == .german)
        #expect(AppLanguage(code: "ja") == .english)
        #expect(AppLanguage(code: nil) == .english)
        #expect(AppLanguage.allCases.filter(\.isRightToLeft) == [.persian, .arabic])
        #expect(DateText.defaultFirstWeekday(for: .gregorian, language: .arabic) == 6)
        #expect(DateText.defaultFirstWeekday(for: .gregorian, language: .german) == 1)
        #expect(DateText.defaultFirstWeekday(for: .jalali, language: .english) == 6)
    }

    @Test func everyLanguageHasEveryName() {
        for language in AppLanguage.allCases {
            for category in SubscriptionCategory.allCases { #expect(!category.displayName(language).isEmpty) }
            for currency in CurrencyCode.allCases { #expect(!currency.displayName(language).isEmpty) }
            for calendar in CalendarSystem.allCases {
                for month in 1...12 { #expect(!DateText.monthName(month, calendar: calendar, language: language).isEmpty) }
            }
            #expect(Set(AppSettings.defaults(for: language).primaryCurrency.rawValue).count > 0)
        }
    }

    @Test func weekdaySymbolsFollowFirstWeekday() {
        #expect(DateText.weekdaySymbols(firstWeekday: 6, language: .persian) == ["ش", "ی", "د", "س", "چ", "پ", "ج"])
        #expect(DateText.weekdaySymbols(firstWeekday: 1, language: .english) == ["M", "T", "W", "T", "F", "S", "S"])
        #expect(DateText.weekdaySymbols(firstWeekday: 7, language: .english).first == "S")
    }

    @Test func relativeDays() {
        let today = d("2026-11-20")
        #expect(DateText.relative(today, today: today, language: .english) == "Today")
        #expect(DateText.relative(d("2026-11-21"), today: today, language: .english) == "Tomorrow")
        #expect(DateText.relative(d("2026-11-25"), today: today, language: .english) == "In 5 days")
        #expect(DateText.relative(d("2026-11-25"), today: today, language: .persian) == "۵ روز دیگر")
        #expect(DateText.relative(d("2026-11-18"), today: today, language: .english) == "2 days ago")
    }
}
