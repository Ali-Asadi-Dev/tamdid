import Foundation

/// A preset service the user can pick when adding a subscription.
public struct CatalogService: Hashable, Codable, Sendable, Identifiable {
    public struct Names: Hashable, Codable, Sendable {
        public var en: String
        public var fa: String
    }

    public var id: String
    public var name: Names
    public var category: SubscriptionCategory
    public var color: String

    public func displayName(_ language: AppLanguage) -> String {
        language == .persian ? name.fa : name.en
    }
}

public struct ServiceCatalog: Sendable {
    public let services: [CatalogService]

    public init(services: [CatalogService]) {
        self.services = services
    }

    /// Decodes the shared `catalog.json`.
    public init(jsonData: Data) throws {
        struct File: Decodable { let services: [CatalogService] }
        self.services = try JSONDecoder().decode(File.self, from: jsonData).services
    }

    /// Matches either language, ignoring case, diacritics, zero-width joiners, and
    /// the Arabic/Persian variants of ی and ک that keyboards mix up.
    public func search(_ query: String) -> [CatalogService] {
        let needle = Self.normalize(query)
        guard !needle.isEmpty else { return services }
        return services.filter {
            Self.normalize($0.name.en).contains(needle) || Self.normalize($0.name.fa).contains(needle)
        }
    }

    public func service(id: String) -> CatalogService? {
        services.first { $0.id == id }
    }

    static func normalize(_ text: String) -> String {
        var result = ""
        for scalar in text.lowercased().unicodeScalars {
            switch scalar {
            case "\u{064A}", "\u{0649}": result.unicodeScalars.append("\u{06CC}") // ي ى → ی
            case "\u{0643}": result.unicodeScalars.append("\u{06A9}") // ك → ک
            case "\u{200C}", "\u{200D}", " ", "-", "+", ".": continue // ZWNJ, ZWJ, spaces and punctuation
            default: result.unicodeScalars.append(scalar)
            }
        }
        return result.folding(options: [.diacriticInsensitive], locale: nil)
    }
}
