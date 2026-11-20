import Foundation
import TamdidCore
import UserNotifications

/// Keeps scheduled reminders in sync with the data. A protocol so tests can count calls.
protocol ReminderScheduling: Sendable {
    func reschedule(_ data: AppData) async
}

/// Turns the shared `ReminderPlanner` plan into local notifications. iOS keeps at most 64
/// pending notifications per app, so the plan is capped at 60 and rebuilt on every change.
struct NotificationReminderScheduler: ReminderScheduling {
    static let identifierPrefix = "tamdid.reminder."

    func requestAuthorization() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    func reschedule(_ data: AppData) async {
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        guard settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional else { return }

        let pending = await center.pendingNotificationRequests()
        let ours = pending.map(\.identifier).filter { $0.hasPrefix(Self.identifierPrefix) }
        center.removePendingNotificationRequests(withIdentifiers: ours)

        let language = AppLanguage.current
        let now = Date()
        for reminder in ReminderPlanner.plan(data.subscriptions, today: CivilDate.today()) {
            let fireDate = reminder.fireDate.date(hour: data.settings.reminderHour)
            guard fireDate > now else { continue }
            let content = UNMutableNotificationContent()
            content.title = Self.title(for: reminder, language: language)
            content.body = Self.body(for: reminder, calendar: data.settings.calendar, language: language)
            content.sound = .default
            var components = Calendar(identifier: .gregorian).dateComponents([.year, .month, .day, .hour, .minute], from: fireDate)
            components.timeZone = nil // fire at this wall-clock time wherever the user is
            let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
            let request = UNNotificationRequest(identifier: Self.identifierPrefix + reminder.id, content: content, trigger: trigger)
            try? await center.add(request)
        }
    }

    static func title(for reminder: PlannedReminder, language: AppLanguage) -> String {
        let days = reminder.fireDate.days(until: reminder.chargeDate)
        let when: String = switch days {
        case 0: String(localized: "today")
        case 1: String(localized: "tomorrow")
        default: String(localized: "in \(days) days")
        }
        let text = reminder.kind == .trialEnding
            ? String(localized: "\(reminder.subscriptionName) free trial ends \(when)")
            : String(localized: "\(reminder.subscriptionName) renews \(when)")
        return Digits.localize(text, language: language)
    }

    static func body(for reminder: PlannedReminder, calendar: CalendarSystem, language: AppLanguage) -> String {
        let amount = MoneyFormatter.format(reminder.amount, language: language)
        let date = DateText.format(reminder.chargeDate, calendar: calendar, language: language, style: .medium)
        return String(localized: "\(amount) will be charged on \(date).")
    }
}

/// Does nothing: used by previews, tests and screenshot runs.
struct NoReminders: ReminderScheduling {
    func reschedule(_ data: AppData) async {}
}

extension AppLanguage {
    /// The language the app is running in (per-app language from iOS Settings).
    static var current: AppLanguage {
        AppLanguage(code: Bundle.main.preferredLocalizations.first)
    }
}
