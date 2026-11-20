import SwiftUI
import TamdidCore
import UserNotifications

struct OnboardingView: View {
    @Environment(AppModel.self) private var model
    @State private var page = 0

    private let pages: [(icon: String, title: LocalizedStringKey, body: LocalizedStringKey)] = [
        ("calendar", "Every subscription in one place",
         "Netflix, your phone plan, the gym. Add what you pay for and see it all together."),
        ("bell.badge", "Never get charged by surprise",
         "Get a reminder before each renewal, and before every free trial turns into a payment."),
        ("chart.bar", "Know your real monthly cost",
         "Weekly, monthly and yearly plans, in any currency, added up into one number."),
    ]

    var body: some View {
        VStack {
            HStack {
                Spacer()
                if page < pages.count {
                    Button("Skip") { withAnimation { page = pages.count } }
                }
            }
            .frame(height: 44)
            .padding(.horizontal)

            TabView(selection: $page) {
                ForEach(pages.indices, id: \.self) { index in
                    IntroPage(icon: pages[index].icon, title: pages[index].title, message: pages[index].body).tag(index)
                }
                SetupPage().tag(pages.count)
            }
            .tabViewStyle(.page(indexDisplayMode: .always))
            .indexViewStyle(.page(backgroundDisplayMode: .always))

            if page < pages.count {
                Button {
                    withAnimation { page += 1 }
                } label: {
                    Text("Next").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .padding()
            }
        }
        .background(Color(.systemGroupedBackground))
    }
}

private struct IntroPage: View {
    let icon: String
    let title: LocalizedStringKey
    let message: LocalizedStringKey

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            Image(systemName: icon)
                .font(.system(size: 60))
                .foregroundStyle(Palette.brand)
                .frame(width: 140, height: 140)
                .background(Palette.brand.opacity(0.12), in: Circle())
            Text(title).font(.title2.weight(.semibold)).multilineTextAlignment(.center)
            Text(message).font(.body).foregroundStyle(.secondary).multilineTextAlignment(.center)
            Spacer()
            Spacer()
        }
        .padding(32)
    }
}

private struct SetupPage: View {
    @Environment(AppModel.self) private var model
    @Environment(\.appFormat) private var format
    @State private var currency: CurrencyCode?
    @State private var calendar: CalendarSystem?
    @State private var notificationsAllowed = false

    var body: some View {
        Form {
            Section {
                Text("Make it yours").font(.title2.weight(.semibold))
                    .listRowBackground(Color.clear)
            }
            Section {
                Picker("Main currency", selection: Binding(get: { currency ?? defaultCurrency }, set: { currency = $0 })) {
                    ForEach(CurrencyCode.allCases) { Text(verbatim: "\($0.displayName(format.language)) (\($0.rawValue))").tag($0) }
                }
                Picker("Calendar", selection: Binding(get: { calendar ?? defaultCalendar }, set: { calendar = $0 })) {
                    Text("Gregorian").tag(CalendarSystem.gregorian)
                    Text("Solar Hijri").tag(CalendarSystem.jalali)
                }
                .pickerStyle(.segmented)
            }
            Section("Reminders") {
                if notificationsAllowed {
                    Label("Notifications are on", systemImage: "checkmark.circle.fill").foregroundStyle(Palette.positive)
                } else {
                    Button("Allow notifications") {
                        Task { notificationsAllowed = await NotificationReminderScheduler().requestAuthorization() }
                    }
                }
            }
            Section {
                Text("Your data stays on this phone. No account, no ads, no tracking.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Button {
                    model.completeOnboarding(currency: currency ?? defaultCurrency, calendar: calendar ?? defaultCalendar)
                } label: {
                    Text("Get started").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .listRowBackground(Color.clear)
                .accessibilityIdentifier("getStarted")
            }
        }
        .scrollContentBackground(.hidden)
        .task {
            let status = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
            notificationsAllowed = status == .authorized || status == .provisional
        }
    }

    // Defaults follow the app's language (e.g. Persian → toman and Solar Hijri).
    private var defaultCurrency: CurrencyCode { AppSettings.defaults(for: format.language).primaryCurrency }
    private var defaultCalendar: CalendarSystem { AppSettings.defaults(for: format.language).calendar }
}
