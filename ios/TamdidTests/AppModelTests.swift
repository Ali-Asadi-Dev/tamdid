import Foundation
import TamdidCore
import TamdidData
import Testing
@testable import Tamdid

/// Counts reschedules so tests can check reminders follow every change.
final class SpyReminders: ReminderScheduling, @unchecked Sendable {
    private let lock = NSLock()
    private var _count = 0
    var count: Int { lock.withLock { _count } }
    func reschedule(_ data: AppData) async { lock.withLock { _count += 1 } }
}

@MainActor
struct AppModelTests {
    private func makeModel(_ data: AppData = SampleData.english, store: FileStore? = nil) -> AppModel {
        AppModel(store: store, catalog: ServiceCatalog(services: []), reminders: NoReminders(), initialData: data, clock: { SampleData.today })
    }

    @Test func totalsMatchTheAndroidTestData() {
        let model = makeModel()
        // 15.49 + 12.99 + 2.99 + 20.00 + 390.00/12 + (9.99 EUR × 1.08), same figure as Android's ViewModelTest.
        #expect(model.insights.monthlyTotal.primary.amountMinor == 1549 + 1299 + 299 + 2000 + 3250 + 1079)
        #expect(model.insights.monthlyTotal.isComplete)
    }

    @Test func lifecycleIntents() {
        let model = makeModel()
        let netflix = model.subscriptions.first { $0.name == "Netflix" }!
        model.pause(id: netflix.id)
        #expect(model.subscription(id: netflix.id)?.status == .paused)
        model.resume(id: netflix.id)
        #expect(model.subscription(id: netflix.id)?.nextRenewal(onOrAfter: SampleData.today) == SampleData.today)
        model.cancel(id: netflix.id)
        #expect(model.subscription(id: netflix.id)?.status == .cancelled)
        model.delete(id: netflix.id)
        #expect(model.subscription(id: netflix.id) == nil)
    }

    @Test func formValidatesAndBuilds() {
        let model = makeModel()
        let form = SubscriptionForm(existing: nil, service: nil, settings: model.settings, today: SampleData.today, language: .english)
        #expect(form.build(today: SampleData.today) == nil)
        #expect(form.errors == [.name, .price])
        form.name = "Gym locker"
        form.priceText = "۵۰"
        form.hasTrial = true
        form.trialEnd = SampleData.today.adding(days: -1)
        #expect(form.errors == [.trial])
        form.hasTrial = false
        let built = form.build(today: SampleData.today)
        #expect(built?.price.amountMinor == 5000)
        #expect(built?.reminderDaysBefore == model.settings.defaultReminderDays)
    }

    @Test func editingThePriceKeepsHistory() {
        let model = makeModel()
        let netflix = model.subscriptions.first { $0.name == "Netflix" }!
        let form = SubscriptionForm(existing: netflix, service: nil, settings: model.settings, today: SampleData.today, language: .english)
        #expect(form.priceText == "15.49")
        form.priceText = "17.99"
        #expect(form.priceWillChange)
        let edited = form.build(today: SampleData.today)!
        #expect(edited.price(on: CivilDate(iso: "2026-10-22")!) == 1549)
        #expect(edited.price(on: SampleData.today) == 1799)
    }

    @Test func savesToDiskAndReloads() throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("\(UUID().uuidString).json")
        let store = FileStore(url: url)
        let first = AppModel(store: store, catalog: ServiceCatalog(services: []), reminders: NoReminders(), clock: { SampleData.today })
        first.load(defaultLanguage: .persian)
        #expect(first.settings.calendar == .jalali)
        #expect(first.settings.primaryCurrency == .toman)
        first.save(SampleData.english.subscriptions[0])
        let second = AppModel(store: store, catalog: ServiceCatalog(services: []), reminders: NoReminders(), clock: { SampleData.today })
        second.load(defaultLanguage: .english)
        #expect(second.subscriptions.count == 1)
    }

    @Test func remindersFollowEveryChange() async throws {
        let spy = SpyReminders()
        let model = AppModel(store: nil, catalog: ServiceCatalog(services: []), reminders: spy, initialData: SampleData.english, clock: { SampleData.today })
        model.pause(id: model.subscriptions[0].id)
        model.updateSettings { $0.reminderHour = 8 }
        model.updateSettings { $0.reminderHour = 8 } // no change: no reschedule
        try await Task.sleep(for: .milliseconds(200))
        #expect(spy.count == 2)
    }
}
