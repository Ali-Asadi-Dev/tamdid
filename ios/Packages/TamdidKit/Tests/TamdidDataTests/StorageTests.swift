import Foundation
import Testing
import TamdidCore
@testable import TamdidData

private func d(_ iso: String) -> CivilDate { CivilDate(iso: iso)! }

@Suite struct StorageTests {
    private func temporaryStore() throws -> FileStore {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return FileStore(url: directory.appendingPathComponent("data.json"))
    }

    private let sample: AppData = Self.makeSample()

    private static func makeSample() -> AppData {
        var spotify = Subscription(
            name: "Spotify", catalogID: "spotify", category: .music, colorHex: "#30A46C",
            price: Money(amountMinor: 1099, currency: .usDollar), startDate: d("2025-03-31"),
            paymentMethod: "Visa •• 4242", notes: "Family plan, \"shared\"")
        spotify.changePrice(to: 1199, effectiveFrom: d("2026-01-01"))
        let filimo = Subscription(
            name: "فیلیمو", category: .streaming, price: Money(amountMinor: 120_000, currency: .toman),
            cycle: .quarterly, startDate: d("2026-02-01"), trialEndDate: d("2026-02-08"), reminderDaysBefore: nil)
        var settings = AppSettings.defaults(for: .persian)
        settings.exchangeRates = [.usDollar: 90_000, .euro: 98_000]
        settings.hasCompletedOnboarding = true
        return AppData(subscriptions: [spotify, filimo], settings: settings)
    }

    @Test func firstLaunchHasNoData() throws {
        #expect(try temporaryStore().load() == nil)
    }

    @Test func saveAndLoadRoundTrip() throws {
        let store = try temporaryStore()
        try store.save(sample)
        #expect(try store.load() == sample)
        // Saving again replaces the file atomically.
        var changed = sample
        changed.subscriptions.removeFirst()
        try store.save(changed)
        #expect(try store.load() == changed)
    }

    @Test func jsonIsReadableAndStable() throws {
        let json = String(decoding: try BackupCodec.encode(sample), as: UTF8.self)
        #expect(json.contains("\"schemaVersion\" : 1"))
        #expect(json.contains("\"startDate\" : \"2025-03-31\""))
        #expect(json.contains("\"USD\" : 90000"))
        #expect(json.contains("فیلیمو"))
        #expect(try BackupCodec.encode(sample) == BackupCodec.encode(sample))
    }

    @Test func refusesBackupsFromTheFuture() {
        let json = Data(#"{"schemaVersion": 99, "subscriptions": [], "settings": {}}"#.utf8)
        #expect(throws: StorageError.unsupportedSchemaVersion(99)) { try BackupCodec.decode(json) }
    }

    @Test func refusesGarbage() {
        #expect(throws: StorageError.unreadableBackup) { try BackupCodec.decode(Data("hello".utf8)) }
        #expect(throws: StorageError.unreadableBackup) {
            try BackupCodec.decode(Data(#"{"schemaVersion": 1, "subscriptions": [{"name": 5}], "settings": {}}"#.utf8))
        }
    }

    @Test func minimalSettingsDecodeWithDefaults() throws {
        let data = try BackupCodec.decode(Data(#"{"schemaVersion": 1, "subscriptions": [], "settings": {"exchangeRates": {"USD": 2, "XXX": 3}}}"#.utf8))
        #expect(data.settings.exchangeRates == [.usDollar: 2])
        #expect(data.settings.reminderHour == 9)
        #expect(!data.settings.hasCompletedOnboarding)
    }

    @Test func csvExport() {
        let csv = BackupCodec.csv(sample.subscriptions, today: d("2026-11-20"))
        let lines = csv.components(separatedBy: "\r\n")
        #expect(lines[0].hasPrefix("name,category,price,currency,cycle_unit"))
        #expect(lines[1].hasPrefix("Spotify,music,11.99,USD,month,1,2025-03-31,,active,,2026-11-30,11.99,"))
        #expect(lines[1].hasSuffix("\"Family plan, \"\"shared\"\"\""))
        #expect(lines[2].hasPrefix("فیلیمو,streaming,120000,IRT,month,3,2026-02-01,2026-02-08"))
        #expect(lines.last == "")
    }

    @Test func csvNeutralisesFormulas() {
        #expect(BackupCodec.escape("=HYPERLINK(\"x\")") == "\"'=HYPERLINK(\"\"x\"\")\"")
        #expect(BackupCodec.escape("+1") == "'+1")
        #expect(BackupCodec.escape("plain") == "plain")
    }
}
