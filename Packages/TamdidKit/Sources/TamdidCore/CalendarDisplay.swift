/// A month in either calendar system.
public struct CalendarMonth: Hashable, Sendable, Comparable {
    public let system: CalendarSystem
    public let year: Int
    public let month: Int

    public init(system: CalendarSystem, year: Int, month: Int) {
        self.system = system
        self.year = year
        self.month = month
    }

    public static func containing(_ date: CivilDate, in system: CalendarSystem) -> CalendarMonth {
        switch system {
        case .gregorian:
            return CalendarMonth(system: system, year: date.year, month: date.month)
        case .jalali:
            let j = Jalali.fromGregorian(date)
            return CalendarMonth(system: system, year: j.year, month: j.month)
        }
    }

    public var numberOfDays: Int {
        switch system {
        case .gregorian: CivilDate.daysInMonth(year: year, month: month)
        case .jalali: Jalali.daysInMonth(year: year, month: month)
        }
    }

    public func date(day: Int) -> CivilDate {
        switch system {
        case .gregorian: CivilDate(uncheckedYear: year, month: month, day: day)
        case .jalali: Jalali.toGregorian(JalaliDate(year: year, month: month, day: day))
        }
    }

    public var firstDay: CivilDate { date(day: 1) }
    public var lastDay: CivilDate { date(day: numberOfDays) }

    public func adding(months: Int) -> CalendarMonth {
        let total = year * 12 + (month - 1) + months
        let y = total >= 0 ? total / 12 : (total - 11) / 12
        return CalendarMonth(system: system, year: y, month: total - y * 12 + 1)
    }

    /// Week rows for a month grid. `nil` cells pad the first and last week.
    /// - Parameter firstWeekday: ISO weekday the week starts on (6 = Saturday for Jalali, 1 = Monday).
    public func weeks(firstWeekday: Int) -> [[CivilDate?]] {
        let leading = (firstDay.isoWeekday - firstWeekday + 7) % 7
        var cells: [CivilDate?] = Array(repeating: nil, count: leading)
        cells += (1...numberOfDays).map { Optional(date(day: $0)) }
        while cells.count % 7 != 0 { cells.append(nil) }
        return stride(from: 0, to: cells.count, by: 7).map { Array(cells[$0..<$0 + 7]) }
    }

    public static func < (lhs: CalendarMonth, rhs: CalendarMonth) -> Bool {
        (lhs.year, lhs.month) < (rhs.year, rhs.month)
    }
}

/// Date text in the user's calendar and language. Deterministic, like `MoneyFormatter`.
public enum DateText {
    public enum Style: Sendable {
        /// "Nov 20, 2026" / "۲۹ آبان ۱۴۰۵"
        case medium
        /// "Nov 20" / "۲۹ آبان"
        case dayMonth
        /// "November 2026" / "آبان ۱۴۰۵"
        case monthYear
        /// "Nov" / "آبان"
        case shortMonth
    }

    public static func format(_ date: CivilDate, calendar: CalendarSystem, language: AppLanguage, style: Style) -> String {
        let (year, month, day): (Int, Int, Int) = switch calendar {
        case .gregorian: (date.year, date.month, date.day)
        case .jalali: { let j = Jalali.fromGregorian(date); return (j.year, j.month, j.day) }()
        }
        return format(year: year, month: month, day: day, calendar: calendar, language: language, style: style)
    }

    public static func format(_ month: CalendarMonth, language: AppLanguage, style: Style = .monthYear) -> String {
        format(year: month.year, month: month.month, day: 1, calendar: month.system, language: language, style: style)
    }

    private static func format(year: Int, month: Int, day: Int, calendar: CalendarSystem, language: AppLanguage, style: Style) -> String {
        let full = monthName(month, calendar: calendar, language: language)
        let short = shortMonthName(month, calendar: calendar, language: language)
        let text: String = switch (language, style) {
        case (.english, .medium) where calendar == .gregorian: "\(short) \(day), \(year)"
        case (.english, .medium): "\(day) \(short) \(year)"
        case (.english, .dayMonth) where calendar == .gregorian: "\(short) \(day)"
        case (.english, .dayMonth): "\(day) \(short)"
        case (.german, .medium): "\(day). \(full) \(year)"
        case (.german, .dayMonth): "\(day). \(full)"
        case (_, .medium): "\(day) \(full) \(year)"
        case (_, .dayMonth): "\(day) \(full)"
        case (_, .monthYear): "\(full) \(year)"
        case (_, .shortMonth): short
        }
        return Digits.localize(text, language: language)
    }

    public static func monthName(_ month: Int, calendar: CalendarSystem, language: AppLanguage) -> String {
        let names: [String] = switch (calendar, language) {
        case (.gregorian, .english):
            ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"]
        case (.gregorian, .persian):
            ["ژانویه", "فوریه", "مارس", "آوریل", "مه", "ژوئن", "ژوئیه", "اوت", "سپتامبر", "اکتبر", "نوامبر", "دسامبر"]
        case (.gregorian, .arabic):
            ["يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"]
        case (.gregorian, .turkish):
            ["Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"]
        case (.gregorian, .german):
            ["Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember"]
        case (.jalali, .persian):
            ["فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"]
        case (.jalali, .arabic):
            ["فروردين", "أرديبهشت", "خرداد", "تير", "مرداد", "شهريور", "مهر", "آبان", "آذر", "دي", "بهمن", "إسفند"]
        case (.jalali, _):
            ["Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar", "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand"]
        }
        return names[month - 1]
    }

    /// Three-letter month for Latin-script Gregorian months ("Nov", "Kas"); full names otherwise.
    public static func shortMonthName(_ month: Int, calendar: CalendarSystem, language: AppLanguage) -> String {
        let full = monthName(month, calendar: calendar, language: language)
        guard calendar == .gregorian, !language.isRightToLeft else { return full }
        return String(full.prefix(3))
    }

    /// Narrow weekday labels in grid order, starting from `firstWeekday` (ISO).
    public static func weekdaySymbols(firstWeekday: Int, language: AppLanguage) -> [String] {
        // ISO order: Monday … Sunday.
        let symbols: [String] = switch language {
        case .english: ["M", "T", "W", "T", "F", "S", "S"]
        case .persian: ["د", "س", "چ", "پ", "ج", "ش", "ی"]
        case .arabic: ["ن", "ث", "ر", "خ", "ج", "س", "ح"]
        case .turkish: ["P", "S", "Ç", "P", "C", "C", "P"]
        case .german: ["M", "D", "M", "D", "F", "S", "S"]
        }
        return (0..<7).map { symbols[(firstWeekday - 1 + $0) % 7] }
    }

    /// The conventional first day of the week: Saturday for the Jalali calendar and in Arabic, Monday otherwise.
    public static func defaultFirstWeekday(for calendar: CalendarSystem, language: AppLanguage) -> Int {
        calendar == .jalali || language == .arabic ? 6 : 1
    }

    /// "In 3 days", "Tomorrow", "Today" — relative to `today`.
    public static func relative(_ date: CivilDate, today: CivilDate, language: AppLanguage) -> String {
        let days = today.days(until: date)
        let n = abs(days)
        let text: String = switch language {
        case .english:
            days == 0 ? "Today" : days == 1 ? "Tomorrow" : days == -1 ? "Yesterday" : days > 1 ? "In \(n) days" : "\(n) days ago"
        case .persian:
            days == 0 ? "امروز" : days == 1 ? "فردا" : days == -1 ? "دیروز" : days > 1 ? "\(n) روز دیگر" : "\(n) روز پیش"
        case .arabic:
            days == 0 ? "اليوم" : days == 1 ? "غدًا" : days == -1 ? "أمس"
                : "\(days > 0 ? "بعد" : "قبل") \(n == 2 ? "يومين" : n <= 10 ? "\(n) أيام" : "\(n) يومًا")"
        case .turkish:
            days == 0 ? "Bugün" : days == 1 ? "Yarın" : days == -1 ? "Dün" : days > 1 ? "\(n) gün sonra" : "\(n) gün önce"
        case .german:
            days == 0 ? "Heute" : days == 1 ? "Morgen" : days == -1 ? "Gestern" : days > 1 ? "In \(n) Tagen" : "Vor \(n) Tagen"
        }
        return Digits.localize(text, language: language)
    }
}
