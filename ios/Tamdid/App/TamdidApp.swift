import SwiftUI
import TamdidCore
import TamdidData

@main
struct TamdidApp: App {
    @State private var model: AppModel

    init() {
        _model = State(initialValue: AppModel.makeForLaunch())
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                .onAppear { model.load(defaultLanguage: .current) }
        }
    }
}

extension AppModel {
    /// The real app, or seeded sample data when UI tests launch with `-sample-data`.
    static func makeForLaunch() -> AppModel {
        let catalog = (Bundle.main.url(forResource: "catalog", withExtension: "json"))
            .flatMap { try? Data(contentsOf: $0) }
            .flatMap { try? ServiceCatalog(jsonData: $0) } ?? ServiceCatalog(services: [])
        let arguments = ProcessInfo.processInfo.arguments
        if let index = arguments.firstIndex(of: "-sample-data"), index + 1 < arguments.count {
            let language = AppLanguage(code: arguments[index + 1])
            let today = SampleData.today
            return AppModel(store: nil, catalog: catalog, reminders: NoReminders(),
                            initialData: arguments.contains("-empty") ? SampleData.empty(language) : SampleData.data(language),
                            clock: { today })
        }
        let store = try? FileStore.applicationSupport()
        return AppModel(store: store, catalog: catalog, reminders: NotificationReminderScheduler())
    }
}

/// Picks onboarding or the main tabs, and provides formatting for the user's language and calendar.
struct RootView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        let format = AppFormat(language: .current, calendar: model.settings.calendar, today: model.today)
        Group {
            if !model.isLoaded {
                Color(.systemGroupedBackground).ignoresSafeArea()
            } else if !model.settings.hasCompletedOnboarding {
                OnboardingView()
            } else {
                MainTabView()
            }
        }
        .environment(\.appFormat, format)
        .preferredColorScheme(model.settings.theme.colorScheme)
        .tint(Palette.brand)
    }
}

extension ThemePreference {
    var colorScheme: ColorScheme? {
        switch self {
        case .system: nil
        case .light: .light
        case .dark: .dark
        }
    }
}

enum Route: Hashable {
    case detail(UUID)
}

enum Sheet: Identifiable {
    case add
    case edit(UUID)

    var id: String {
        switch self {
        case .add: "add"
        case .edit(let id): "edit-\(id)"
        }
    }
}

struct MainTabView: View {
    enum Tab: Hashable { case home, subscriptions, calendar, insights, settings }

    @State private var tab = Tab.home

    var body: some View {
        TabView(selection: $tab) {
            NavigationStack { HomeView() }
                .tabItem { Label("Home", systemImage: "house") }
                .tag(Tab.home)
            NavigationStack { SubscriptionListView() }
                .tabItem { Label("Plans", systemImage: "list.bullet.rectangle") }
                .tag(Tab.subscriptions)
            NavigationStack { CalendarScreen() }
                .tabItem { Label("Calendar", systemImage: "calendar") }
                .tag(Tab.calendar)
            NavigationStack { InsightsView() }
                .tabItem { Label("Insights", systemImage: "chart.bar") }
                .tag(Tab.insights)
            NavigationStack { SettingsView() }
                .tabItem { Label("Settings", systemImage: "gearshape") }
                .tag(Tab.settings)
        }
    }
}

/// Shared by every tab: opens a subscription's detail page and the add/edit sheets.
struct SubscriptionNavigation: ViewModifier {
    @Binding var sheet: Sheet?

    func body(content: Content) -> some View {
        content
            .navigationDestination(for: Route.self) { route in
                switch route {
                case .detail(let id): SubscriptionDetailView(id: id)
                }
            }
            .sheet(item: $sheet) { sheet in
                switch sheet {
                case .add: AddSubscriptionFlow()
                case .edit(let id): NavigationStack { SubscriptionEditView(editing: id, catalogID: nil) }
                }
            }
    }
}

extension View {
    func subscriptionNavigation(sheet: Binding<Sheet?>) -> some View {
        modifier(SubscriptionNavigation(sheet: sheet))
    }

    /// The "+" toolbar button that starts adding a subscription.
    func addButton(sheet: Binding<Sheet?>) -> some View {
        toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    sheet.wrappedValue = .add
                } label: {
                    Label("Add subscription", systemImage: "plus")
                }
                .accessibilityIdentifier("add")
            }
        }
    }
}
