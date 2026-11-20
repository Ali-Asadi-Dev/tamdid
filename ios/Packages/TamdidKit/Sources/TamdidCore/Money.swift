/// Languages the app ships in. Formatting rules depend on it (digits, separators, names).
public enum AppLanguage: String, Codable, CaseIterable, Sendable {
    case english = "en"
    case persian = "fa"

    public var isRightToLeft: Bool { self == .persian }
}

public enum CurrencyCode: String, Codable, CaseIterable, Sendable, Identifiable {
    /// Iranian toman (10 rials). Not an ISO code, but what people actually price things in.
    case toman = "IRT"
    case usDollar = "USD"
    case euro = "EUR"
    case pound = "GBP"
    case dirham = "AED"
    case lira = "TRY"
    case canadianDollar = "CAD"

    public var id: String { rawValue }

    /// Number of minor units (cents) per major unit, as a power of ten.
    public var fractionDigits: Int { self == .toman ? 0 : 2 }

    public var minorUnitsPerMajor: Int64 {
        var result: Int64 = 1
        for _ in 0..<fractionDigits { result *= 10 }
        return result
    }

    public func displayName(_ language: AppLanguage) -> String {
        switch (self, language) {
        case (.toman, .english): "Toman"
        case (.usDollar, .english): "US Dollar"
        case (.euro, .english): "Euro"
        case (.pound, .english): "British Pound"
        case (.dirham, .english): "UAE Dirham"
        case (.lira, .english): "Turkish Lira"
        case (.canadianDollar, .english): "Canadian Dollar"
        case (.toman, .persian): "تومان"
        case (.usDollar, .persian): "دلار"
        case (.euro, .persian): "یورو"
        case (.pound, .persian): "پوند"
        case (.dirham, .persian): "درهم"
        case (.lira, .persian): "لیر"
        case (.canadianDollar, .persian): "دلار کانادا"
        }
    }
}

/// An exact amount of money in minor units (cents, or whole tomans).
public struct Money: Hashable, Codable, Sendable {
    public var amountMinor: Int64
    public var currency: CurrencyCode

    public init(amountMinor: Int64, currency: CurrencyCode) {
        self.amountMinor = amountMinor
        self.currency = currency
    }

    public static func zero(_ currency: CurrencyCode) -> Money {
        Money(amountMinor: 0, currency: currency)
    }
}

/// Deterministic money formatting, identical on iOS and Android.
///
/// Platform number formatters differ between OS versions (and between ICU builds for
/// Persian), so the app formats amounts itself and verifies the result with shared fixtures.
public enum MoneyFormatter {
    public static func format(_ money: Money, language: AppLanguage) -> String {
        let number = formatNumber(money.amountMinor, fractionDigits: money.currency.fractionDigits, language: language)
        let negative = money.amountMinor < 0
        let sign = negative ? "-" : ""
        switch language {
        case .persian:
            return "\(sign)\(number) \(money.currency.displayName(.persian))"
        case .english:
            switch money.currency {
            case .usDollar: return "\(sign)$\(number)"
            case .euro: return "\(sign)€\(number)"
            case .pound: return "\(sign)£\(number)"
            case .lira: return "\(sign)₺\(number)"
            case .canadianDollar: return "\(sign)CA$\(number)"
            case .dirham: return "\(sign)AED \(number)"
            case .toman: return "\(sign)\(number) Toman"
            }
        }
    }

    /// Formats an absolute amount: grouping, decimal separator, and digits for `language`.
    public static func formatNumber(_ amountMinor: Int64, fractionDigits: Int, language: AppLanguage) -> String {
        let magnitude = amountMinor.magnitude
        var divisor: UInt64 = 1
        for _ in 0..<fractionDigits { divisor *= 10 }
        let whole = magnitude / divisor
        let fraction = magnitude % divisor

        let groupSeparator = language == .persian ? "\u{066C}" : ","
        let decimalSeparator = language == .persian ? "\u{066B}" : "."

        let digits = Array(String(whole))
        var grouped = ""
        for (index, character) in digits.enumerated() {
            if index > 0 && (digits.count - index) % 3 == 0 { grouped += groupSeparator }
            grouped.append(character)
        }
        var result = grouped
        if fractionDigits > 0 {
            let fractionText = String(fraction)
            result += decimalSeparator + String(repeating: "0", count: fractionDigits - fractionText.count) + fractionText
        }
        return Digits.localize(result, language: language)
    }

    /// Parses what a user typed into an amount field. Accepts Persian, Arabic, and Latin
    /// digits, grouping separators, and `.`, `٫` or `/` as the decimal point.
    /// Returns `nil` for empty or malformed input, or more decimals than the currency allows.
    public static func parse(_ input: String, currency: CurrencyCode) -> Int64? {
        let latin = Digits.latinize(input)
        var cleaned = ""
        for character in latin {
            switch character {
            case "0"..."9": cleaned.append(character)
            case ".", "\u{066B}", "/": cleaned.append(".")
            case ",", "\u{066C}", "\u{060C}", " ", "\u{00A0}", "\u{202F}", "'": continue
            default: return nil
            }
        }
        let parts = cleaned.split(separator: ".", omittingEmptySubsequences: false)
        guard !cleaned.isEmpty, parts.count <= 2 else { return nil }
        let wholeText = parts[0].isEmpty ? "0" : String(parts[0])
        let fractionText = parts.count == 2 ? String(parts[1]) : ""
        guard fractionText.count <= currency.fractionDigits,
              let whole = Int64(wholeText) else { return nil }
        let paddedFraction = fractionText + String(repeating: "0", count: currency.fractionDigits - fractionText.count)
        let fraction = paddedFraction.isEmpty ? 0 : (Int64(paddedFraction) ?? 0)
        let (scaled, overflow) = whole.multipliedReportingOverflow(by: currency.minorUnitsPerMajor)
        guard !overflow else { return nil }
        return scaled + fraction
    }

    /// The amount as a plain editable string ("9.99", "120000"), with Latin digits.
    public static func editableText(_ amountMinor: Int64, currency: CurrencyCode) -> String {
        let divisor = currency.minorUnitsPerMajor
        let whole = amountMinor / divisor
        guard currency.fractionDigits > 0 else { return String(whole) }
        let fraction = String(abs(amountMinor % divisor))
        return "\(whole).\(String(repeating: "0", count: currency.fractionDigits - fraction.count))\(fraction)"
    }
}

public enum Digits {
    private static let persian: [Character] = ["۰", "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹"]
    private static let arabic: [Character] = ["٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩"]

    /// Replaces Latin digits with Persian ones when `language` is Persian.
    public static func localize(_ text: String, language: AppLanguage) -> String {
        guard language == .persian else { return text }
        return String(text.map { character in
            if let value = character.wholeNumberValue, character.isASCII { return persian[value] }
            return character
        })
    }

    /// Replaces Persian and Arabic-Indic digits with Latin ones.
    public static func latinize(_ text: String) -> String {
        String(text.map { character in
            if let index = persian.firstIndex(of: character) { return Character(String(index)) }
            if let index = arabic.firstIndex(of: character) { return Character(String(index)) }
            return character
        })
    }
}
