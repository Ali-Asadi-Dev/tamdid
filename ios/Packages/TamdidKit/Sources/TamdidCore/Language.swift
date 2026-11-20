/// Languages the app ships in. Formatting rules depend on it (digits, separators, names).
public enum AppLanguage: String, Codable, CaseIterable, Sendable, Identifiable {
    case english = "en"
    case persian = "fa"
    case arabic = "ar"
    case turkish = "tr"
    case german = "de"

    public var id: String { rawValue }

    /// Maps a BCP-47 code such as "fa-IR" or "de"; unknown languages fall back to English.
    public init(code: String?) {
        let prefix = code.map { String($0.prefix(2)).lowercased() } ?? "en"
        self = AppLanguage(rawValue: prefix) ?? .english
    }

    public var isRightToLeft: Bool { self == .persian || self == .arabic }

    /// The language's name in itself, for the language picker.
    public var nativeName: String {
        switch self {
        case .english: "English"
        case .persian: "فارسی"
        case .arabic: "العربية"
        case .turkish: "Türkçe"
        case .german: "Deutsch"
        }
    }

    var groupingSeparator: String {
        switch self {
        case .english: ","
        case .persian, .arabic: "\u{066C}"
        case .turkish, .german: "."
        }
    }

    var decimalSeparator: String {
        switch self {
        case .english: "."
        case .persian, .arabic: "\u{066B}"
        case .turkish, .german: ","
        }
    }
}

public enum Digits {
    private static let persian: [Character] = ["۰", "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹"]
    private static let arabic: [Character] = ["٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩"]

    /// Replaces Latin digits with the language's own digits (Persian or Arabic-Indic).
    public static func localize(_ text: String, language: AppLanguage) -> String {
        let table: [Character]
        switch language {
        case .persian: table = persian
        case .arabic: table = arabic
        default: return text
        }
        return String(text.map { character in
            if character.isASCII, let value = character.wholeNumberValue { return table[value] }
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
