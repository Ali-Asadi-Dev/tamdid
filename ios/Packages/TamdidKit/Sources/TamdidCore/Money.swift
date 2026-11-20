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

    /// Full name, for pickers.
    public func displayName(_ language: AppLanguage) -> String {
        let names: [String] = switch language {
        case .english: ["Toman", "US Dollar", "Euro", "British Pound", "UAE Dirham", "Turkish Lira", "Canadian Dollar"]
        case .persian: ["تومان", "دلار", "یورو", "پوند", "درهم", "لیر", "دلار کانادا"]
        case .arabic: ["تومان", "دولار أمريكي", "يورو", "جنيه إسترليني", "درهم إماراتي", "ليرة تركية", "دولار كندي"]
        case .turkish: ["Toman", "ABD Doları", "Euro", "İngiliz Sterlini", "BAE Dirhemi", "Türk Lirası", "Kanada Doları"]
        case .german: ["Toman", "US-Dollar", "Euro", "Britisches Pfund", "VAE-Dirham", "Türkische Lira", "Kanadischer Dollar"]
        }
        return names[Self.allCases.firstIndex(of: self)!]
    }

    /// The word written after an amount in Persian and Arabic ("۱۲۰٬۰۰۰ تومان").
    func amountWord(_ language: AppLanguage) -> String {
        guard language == .arabic else { return displayName(language) }
        let names = ["تومان", "دولار", "يورو", "جنيه", "درهم", "ليرة", "دولار كندي"]
        return names[Self.allCases.firstIndex(of: self)!]
    }

    /// Symbol used by languages that write it next to the number.
    var symbol: String {
        switch self {
        case .toman: "Toman"
        case .usDollar: "$"
        case .euro: "€"
        case .pound: "£"
        case .dirham: "AED"
        case .lira: "₺"
        case .canadianDollar: "CA$"
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
        let sign = money.amountMinor < 0 ? "-" : ""
        let currency = money.currency
        switch language {
        case .persian, .arabic:
            return "\(sign)\(number) \(currency.amountWord(language))"
        case .german:
            return "\(sign)\(number)\u{00A0}\(currency.symbol)"
        case .english, .turkish:
            switch currency {
            case .toman: return "\(sign)\(number) Toman"
            case .dirham: return "\(sign)AED \(number)"
            default: return "\(sign)\(currency.symbol)\(number)"
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

        let groupSeparator = language.groupingSeparator
        let decimalSeparator = language.decimalSeparator

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
