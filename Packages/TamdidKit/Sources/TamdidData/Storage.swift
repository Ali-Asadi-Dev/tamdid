import Foundation
import TamdidCore

public enum StorageError: Error, Equatable {
    case unsupportedSchemaVersion(Int)
    case unreadableBackup
}

/// Saves `AppData` as a single JSON file with atomic writes.
///
/// A subscription tracker holds tens of records, not thousands, so one document is simpler
/// and safer than a database: writes are all-or-nothing, the file *is* the backup format,
/// and the same code runs (and is tested) on Linux, iOS and, mirrored, Android.
public struct FileStore: Sendable {
    public let url: URL

    public init(url: URL) {
        self.url = url
    }

    /// `Application Support/Tamdid/data.json`, created if missing.
    public static func applicationSupport() throws -> FileStore {
        let base = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
        let directory = base.appendingPathComponent("Tamdid", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return FileStore(url: directory.appendingPathComponent("data.json"))
    }

    /// Loads saved data, or `nil` on first launch.
    public func load() throws -> AppData? {
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        return try BackupCodec.decode(Data(contentsOf: url))
    }

    public func save(_ data: AppData) throws {
        try BackupCodec.encode(data).write(to: url, options: [.atomic])
    }
}

public enum BackupCodec {
    public static func encode(_ data: AppData) throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        return try encoder.encode(data)
    }

    public static func decode(_ json: Data) throws -> AppData {
        struct Header: Decodable { let schemaVersion: Int }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        guard let header = try? decoder.decode(Header.self, from: json) else {
            throw StorageError.unreadableBackup
        }
        guard header.schemaVersion <= AppData.currentSchemaVersion else {
            throw StorageError.unsupportedSchemaVersion(header.schemaVersion)
        }
        do {
            return try decoder.decode(AppData.self, from: json)
        } catch {
            throw StorageError.unreadableBackup
        }
    }

    /// A spreadsheet-friendly export. Amounts are in major units with a dot decimal separator.
    public static func csv(_ subscriptions: [Subscription], today: CivilDate) -> String {
        let header = ["name", "category", "price", "currency", "cycle_unit", "cycle_interval",
                      "start_date", "trial_end", "status", "end_date", "next_renewal",
                      "monthly_cost", "total_spent", "payment_method", "url", "notes"]
        var lines = [header.joined(separator: ",")]
        for s in subscriptions.sorted(by: { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }) {
            let currency = s.price.currency
            let row = [
                s.name,
                s.category.rawValue,
                MoneyFormatter.editableText(s.price.amountMinor, currency: currency),
                currency.rawValue,
                s.cycle.unit.rawValue,
                String(s.cycle.interval),
                s.startDate.iso,
                s.trialEndDate?.iso ?? "",
                s.status.rawValue,
                s.endDate?.iso ?? "",
                s.nextRenewal(onOrAfter: today)?.iso ?? "",
                MoneyFormatter.editableText(s.monthlyCostMinor, currency: currency),
                MoneyFormatter.editableText(s.totalSpent(asOf: today), currency: currency),
                s.paymentMethod ?? "",
                s.url ?? "",
                s.notes ?? "",
            ]
            lines.append(row.map(escape).joined(separator: ","))
        }
        return lines.joined(separator: "\r\n") + "\r\n"
    }

    /// RFC 4180 quoting. Also neutralises leading `= + - @` so spreadsheets don't run formulas.
    static func escape(_ field: String) -> String {
        var value = field
        if let first = value.first, "=+-@".contains(first) { value = "'" + value }
        if value.contains(where: { $0 == "," || $0 == "\"" || $0 == "\n" || $0 == "\r" }) {
            return "\"" + value.replacingOccurrences(of: "\"", with: "\"\"") + "\""
        }
        return value
    }
}
