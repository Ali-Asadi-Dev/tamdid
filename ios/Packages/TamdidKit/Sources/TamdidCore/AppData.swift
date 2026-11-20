import Foundation

public enum CalendarSystem: String, Codable, CaseIterable, Sendable {
    case gregorian, jalali
}

public enum ThemePreference: String, Codable, CaseIterable, Sendable {
    case system, light, dark
}

public struct AppSettings: Hashable, Codable, Sendable {
    public var primaryCurrency: CurrencyCode
    public var calendar: CalendarSystem
    /// Reminder applied to new subscriptions; `nil` means off.
    public var defaultReminderDays: Int?
    public var reminderHour: Int
    public var theme: ThemePreference
    /// 1 unit of the key currency = value units of `primaryCurrency`.
    public var exchangeRates: [CurrencyCode: Double]
    public var hasCompletedOnboarding: Bool

    public init(
        primaryCurrency: CurrencyCode = .usDollar,
        calendar: CalendarSystem = .gregorian,
        defaultReminderDays: Int? = 1,
        reminderHour: Int = 9,
        theme: ThemePreference = .system,
        exchangeRates: [CurrencyCode: Double] = [:],
        hasCompletedOnboarding: Bool = false
    ) {
        self.primaryCurrency = primaryCurrency
        self.calendar = calendar
        self.defaultReminderDays = defaultReminderDays
        self.reminderHour = reminderHour
        self.theme = theme
        self.exchangeRates = exchangeRates
        self.hasCompletedOnboarding = hasCompletedOnboarding
    }

    /// Sensible defaults for someone using the app in `language`.
    public static func defaults(for language: AppLanguage) -> AppSettings {
        switch language {
        case .persian: AppSettings(primaryCurrency: .toman, calendar: .jalali)
        case .english: AppSettings(primaryCurrency: .usDollar, calendar: .gregorian)
        case .arabic: AppSettings(primaryCurrency: .dirham, calendar: .gregorian)
        case .turkish: AppSettings(primaryCurrency: .lira, calendar: .gregorian)
        case .german: AppSettings(primaryCurrency: .euro, calendar: .gregorian)
        }
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let base = AppSettings()
        primaryCurrency = (try? c.decode(CurrencyCode.self, forKey: .primaryCurrency)) ?? base.primaryCurrency
        calendar = (try? c.decode(CalendarSystem.self, forKey: .calendar)) ?? base.calendar
        defaultReminderDays = try c.decodeIfPresent(Int.self, forKey: .defaultReminderDays)
        reminderHour = try c.decodeIfPresent(Int.self, forKey: .reminderHour) ?? base.reminderHour
        theme = (try? c.decode(ThemePreference.self, forKey: .theme)) ?? base.theme
        // Stored as a plain string-keyed object so the JSON is readable and Android-compatible.
        let rawRates = try c.decodeIfPresent([String: Double].self, forKey: .exchangeRates) ?? [:]
        exchangeRates = Dictionary(uniqueKeysWithValues: rawRates.compactMap { key, value in
            CurrencyCode(rawValue: key).map { ($0, value) }
        })
        hasCompletedOnboarding = try c.decodeIfPresent(Bool.self, forKey: .hasCompletedOnboarding) ?? false
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(primaryCurrency, forKey: .primaryCurrency)
        try c.encode(calendar, forKey: .calendar)
        try c.encodeIfPresent(defaultReminderDays, forKey: .defaultReminderDays)
        try c.encode(reminderHour, forKey: .reminderHour)
        try c.encode(theme, forKey: .theme)
        try c.encode(Dictionary(uniqueKeysWithValues: exchangeRates.map { ($0.key.rawValue, $0.value) }), forKey: .exchangeRates)
        try c.encode(hasCompletedOnboarding, forKey: .hasCompletedOnboarding)
    }

    private enum CodingKeys: String, CodingKey {
        case primaryCurrency, calendar, defaultReminderDays, reminderHour, theme, exchangeRates, hasCompletedOnboarding
    }
}

/// Everything the app stores. Saved as one JSON document; also the backup format.
public struct AppData: Hashable, Codable, Sendable {
    public static let currentSchemaVersion = 1

    public var schemaVersion: Int
    public var subscriptions: [Subscription]
    public var settings: AppSettings

    public init(subscriptions: [Subscription] = [], settings: AppSettings = AppSettings()) {
        self.schemaVersion = AppData.currentSchemaVersion
        self.subscriptions = subscriptions
        self.settings = settings
    }
}

// MARK: - Currency conversion

public struct CurrencyConverter: Sendable {
    public let primary: CurrencyCode
    public let rates: [CurrencyCode: Double]

    public init(primary: CurrencyCode, rates: [CurrencyCode: Double]) {
        self.primary = primary
        self.rates = rates
    }

    public init(settings: AppSettings) {
        self.init(primary: settings.primaryCurrency, rates: settings.exchangeRates)
    }

    /// Converts minor units of `currency` into minor units of the primary currency,
    /// or `nil` when there is no rate. Never guesses.
    public func toPrimary(_ amountMinor: Int64, from currency: CurrencyCode) -> Int64? {
        if currency == primary { return amountMinor }
        guard let rate = rates[currency], rate > 0 else { return nil }
        let major = Double(amountMinor) / Double(currency.minorUnitsPerMajor)
        return Subscription.roundHalfUp(major * rate * Double(primary.minorUnitsPerMajor))
    }
}

/// A total in the primary currency, plus whatever could not be converted.
public struct MoneyTotal: Hashable, Sendable {
    public var primary: Money
    public var unconverted: [CurrencyCode: Int64]

    public init(primary: Money, unconverted: [CurrencyCode: Int64] = [:]) {
        self.primary = primary
        self.unconverted = unconverted
    }

    public var isComplete: Bool { unconverted.isEmpty }

    mutating func add(_ amountMinor: Int64, _ currency: CurrencyCode, using converter: CurrencyConverter) {
        if let converted = converter.toPrimary(amountMinor, from: currency) {
            primary.amountMinor += converted
        } else {
            unconverted[currency, default: 0] += amountMinor
        }
    }
}
