import Foundation
import Observation
import TamdidCore
import TamdidData

/// The single source of truth for the app: loads, mutates and saves `AppData`, and keeps
/// reminders in sync. Views read it from the environment and call its intent methods.
@Observable
@MainActor
final class AppModel {
    private(set) var data: AppData
    private(set) var isLoaded = false
    /// Set when saving fails, so the UI can tell the user instead of losing data silently.
    var lastError: String?

    let catalog: ServiceCatalog
    private let store: FileStore?
    private let reminders: ReminderScheduling
    private let clock: () -> CivilDate

    init(
        store: FileStore?,
        catalog: ServiceCatalog,
        reminders: ReminderScheduling,
        initialData: AppData = AppData(),
        clock: @escaping () -> CivilDate = { CivilDate.today() }
    ) {
        self.store = store
        self.catalog = catalog
        self.reminders = reminders
        self.data = initialData
        self.clock = clock
        if store == nil { isLoaded = true }
    }

    var today: CivilDate { clock() }
    var settings: AppSettings { data.settings }
    var subscriptions: [Subscription] { data.subscriptions }

    var insights: Insights {
        Insights(subscriptions: data.subscriptions, settings: data.settings, today: today)
    }

    func subscription(id: UUID) -> Subscription? {
        data.subscriptions.first { $0.id == id }
    }

    // MARK: - Loading and saving

    func load(defaultLanguage: AppLanguage) {
        guard !isLoaded, let store else { return }
        do {
            data = try store.load() ?? AppData(settings: .defaults(for: defaultLanguage))
        } catch {
            // Never brick the app over a bad file: keep it aside and start fresh.
            let backup = store.url.deletingLastPathComponent()
                .appendingPathComponent("data-unreadable-\(Int(Date().timeIntervalSince1970)).json")
            try? FileManager.default.moveItem(at: store.url, to: backup)
            data = AppData(settings: .defaults(for: defaultLanguage))
        }
        isLoaded = true
        Task { await reminders.reschedule(data) }
    }

    /// Applies a change, saves it, and refreshes reminders.
    func update(_ change: (inout AppData) -> Void) {
        var next = data
        change(&next)
        guard next != data else { return }
        data = next
        do {
            try store?.save(next)
        } catch {
            lastError = error.localizedDescription
        }
        let snapshot = next
        Task { await reminders.reschedule(snapshot) }
    }

    // MARK: - Intents

    func save(_ subscription: Subscription) {
        update { data in
            if let index = data.subscriptions.firstIndex(where: { $0.id == subscription.id }) {
                data.subscriptions[index] = subscription
            } else {
                data.subscriptions.append(subscription)
            }
        }
    }

    func delete(id: UUID) {
        update { $0.subscriptions.removeAll { $0.id == id } }
    }

    func modify(id: UUID, _ change: (inout Subscription) -> Void) {
        update { data in
            guard let index = data.subscriptions.firstIndex(where: { $0.id == id }) else { return }
            change(&data.subscriptions[index])
        }
    }

    func pause(id: UUID) { modify(id: id) { $0.pause(on: today) } }
    func resume(id: UUID) { modify(id: id) { $0.resume(on: today) } }
    func cancel(id: UUID) { modify(id: id) { $0.cancel(on: today) } }

    func updateSettings(_ change: (inout AppSettings) -> Void) {
        update { change(&$0.settings) }
    }

    func completeOnboarding(currency: CurrencyCode, calendar: CalendarSystem) {
        updateSettings {
            $0.primaryCurrency = currency
            $0.calendar = calendar
            $0.hasCompletedOnboarding = true
        }
    }

    /// Replaces everything with a backup. Onboarding stays finished.
    func restore(_ backup: AppData) {
        var restored = backup
        restored.settings.hasCompletedOnboarding = true
        update { $0 = restored }
    }
}
