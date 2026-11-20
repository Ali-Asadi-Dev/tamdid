import SwiftUI
import TamdidCore

/// Formatting in the user's language and calendar, injected into every view.
struct AppFormat {
    var language: AppLanguage
    var calendar: CalendarSystem
    var today: CivilDate

    func money(_ money: Money) -> String { MoneyFormatter.format(money, language: language) }

    func date(_ date: CivilDate, _ style: DateText.Style = .medium) -> String {
        DateText.format(date, calendar: calendar, language: language, style: style)
    }

    func relative(_ date: CivilDate) -> String { DateText.relative(date, today: today, language: language) }

    func number(_ value: Int) -> String { Digits.localize(String(value), language: language) }

    /// Digits in the user's language for text that came from a localized format string.
    func digits(_ text: String) -> String { Digits.localize(text, language: language) }

    func unconverted(_ total: MoneyTotal) -> String {
        total.unconverted
            .sorted { $0.key.rawValue < $1.key.rawValue }
            .map { money(Money(amountMinor: $0.value, currency: $0.key)) }
            .joined(separator: language == .persian ? "، " : ", ")
    }

    func cycle(_ cycle: BillingCycle) -> String {
        switch cycle {
        case .weekly: String(localized: "Weekly")
        case .monthly: String(localized: "Monthly")
        case .yearly: String(localized: "Yearly")
        default: digits(String(localized: "Every \(cycle.interval) \(unit(cycle.unit, count: cycle.interval))"))
        }
    }

    func unit(_ unit: CycleUnit, count: Int) -> String {
        let one = count == 1
        return switch unit {
        case .day: one ? String(localized: "day") : String(localized: "days")
        case .week: one ? String(localized: "week") : String(localized: "weeks")
        case .month: one ? String(localized: "month") : String(localized: "months")
        case .year: one ? String(localized: "year") : String(localized: "years")
        }
    }

    func reminder(_ days: Int?) -> String {
        switch days {
        case nil: String(localized: "Off")
        case 0: String(localized: "On the day")
        case let days?: digits(String(localized: "\(days) days before"))
        }
    }

    static let preview = AppFormat(language: .english, calendar: .gregorian, today: CivilDate(year: 2026, month: 11, day: 20)!)
}

private struct AppFormatKey: EnvironmentKey {
    static let defaultValue = AppFormat.preview
}

extension EnvironmentValues {
    var appFormat: AppFormat {
        get { self[AppFormatKey.self] }
        set { self[AppFormatKey.self] = newValue }
    }
}

extension CivilDate {
    /// Noon on this day, so a `DatePicker` round trip never crosses midnight because of DST.
    var pickerDate: Date { date(hour: 12) }
}
