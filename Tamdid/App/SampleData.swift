import Foundation
import TamdidCore

/// Fixed data for previews, UI tests and App Store screenshots. Mirrors the Android test data.
enum SampleData {
    static let today = CivilDate(year: 2026, month: 11, day: 20)!

    private static func d(_ iso: String) -> CivilDate { CivilDate(iso: iso)! }

    static func data(_ language: AppLanguage) -> AppData {
        language == .persian ? persian : english
    }

    static func empty(_ language: AppLanguage) -> AppData {
        var settings = AppSettings.defaults(for: language)
        settings.hasCompletedOnboarding = true
        return AppData(settings: settings)
    }

    static var english: AppData {
        var spotify = Subscription(name: "Spotify", category: .music, colorHex: "#30A46C",
                                   price: Money(amountMinor: 1199, currency: .usDollar), startDate: d("2024-09-25"))
        spotify.changePrice(to: 1299, effectiveFrom: d("2026-07-25"))
        var settings = AppSettings.defaults(for: .english)
        settings.hasCompletedOnboarding = true
        settings.exchangeRates = [.euro: 1.08]
        return AppData(subscriptions: [
            Subscription(name: "Netflix", catalogID: "netflix", category: .streaming, colorHex: "#E5484D",
                         price: Money(amountMinor: 1549, currency: .usDollar), startDate: d("2025-04-22")),
            spotify,
            Subscription(name: "iCloud+", category: .cloud, colorHex: "#0090FF",
                         price: Money(amountMinor: 299, currency: .usDollar), startDate: d("2023-01-03"), reminderDaysBefore: nil),
            Subscription(name: "Claude Pro", category: .ai, colorHex: "#AD7F58",
                         price: Money(amountMinor: 2000, currency: .usDollar), startDate: d("2026-11-10"),
                         trialEndDate: d("2026-11-24"), reminderDaysBefore: 2),
            Subscription(name: "City Gym", category: .health, colorHex: "#8E4EC6",
                         price: Money(amountMinor: 39000, currency: .usDollar), cycle: .yearly, startDate: d("2026-02-01"), reminderDaysBefore: nil),
            Subscription(name: "Disney+", category: .streaming, colorHex: "#3E63DD",
                         price: Money(amountMinor: 999, currency: .euro), startDate: d("2026-05-01"), reminderDaysBefore: nil),
        ], settings: settings)
    }

    static var persian: AppData {
        var settings = AppSettings.defaults(for: .persian)
        settings.hasCompletedOnboarding = true
        settings.exchangeRates = [.usDollar: 105_000]
        return AppData(subscriptions: [
            Subscription(name: "فیلیمو", category: .streaming, colorHex: "#E5484D",
                         price: Money(amountMinor: 159_000, currency: .toman), startDate: d("2026-03-25")),
            Subscription(name: "نماوا", category: .streaming, colorHex: "#F76B15",
                         price: Money(amountMinor: 139_000, currency: .toman), startDate: d("2026-06-23"), reminderDaysBefore: nil),
            Subscription(name: "اینترنت خانگی", category: .utilities, colorHex: "#0090FF",
                         price: Money(amountMinor: 480_000, currency: .toman), startDate: d("2025-12-01"), reminderDaysBefore: 3),
            Subscription(name: "طاقچه", category: .reading, colorHex: "#12A594",
                         price: Money(amountMinor: 89_000, currency: .toman), startDate: d("2026-11-15"),
                         trialEndDate: d("2026-11-22"), reminderDaysBefore: nil),
            Subscription(name: "Spotify", category: .music, colorHex: "#30A46C",
                         price: Money(amountMinor: 1199, currency: .usDollar), startDate: d("2025-01-27"), reminderDaysBefore: nil),
        ], settings: settings)
    }
}
