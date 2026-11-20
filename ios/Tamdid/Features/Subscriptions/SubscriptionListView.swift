import SwiftUI
import TamdidCore

struct SubscriptionListView: View {
    enum Filter: CaseIterable { case all, active, paused, cancelled }
    enum Sort: CaseIterable { case nextRenewal, monthlyCost, name }

    @Environment(AppModel.self) private var model
    @Environment(\.appFormat) private var format
    @State private var sheet: Sheet?
    @State private var query = ""
    @State private var filter = Filter.all
    @State private var sort = Sort.nextRenewal

    struct Row: Identifiable {
        let subscription: Subscription
        let next: CivilDate?
        var id: UUID { subscription.id }
    }

    private var items: [Row] {
        let today = format.today
        let trimmed = query.trimmingCharacters(in: .whitespaces)
        let rows = model.subscriptions
            .filter { sub in
                switch filter {
                case .all: true
                case .active: sub.status == .active
                case .paused: sub.status == .paused
                case .cancelled: sub.status == .cancelled
                }
            }
            .filter { trimmed.isEmpty || $0.name.localizedCaseInsensitiveContains(trimmed) || ($0.notes ?? "").localizedCaseInsensitiveContains(trimmed) }
            .map { Row(subscription: $0, next: $0.nextRenewal(onOrAfter: today)) }
        switch sort {
        case .nextRenewal:
            return rows.sorted { lhs, rhs in
                switch (lhs.next, rhs.next) {
                case let (l?, r?): l < r
                case (.some, nil): true
                default: false
                }
            }
        case .monthlyCost: return rows.sorted { $0.subscription.monthlyCostMinor > $1.subscription.monthlyCostMinor }
        case .name: return rows.sorted { $0.subscription.name.localizedCaseInsensitiveCompare($1.subscription.name) == .orderedAscending }
        }
    }

    var body: some View {
        Group {
            if model.subscriptions.isEmpty {
                EmptyStateView(
                    systemImage: "list.bullet.rectangle",
                    title: "No subscriptions yet",
                    message: "Add the services you pay for to see your monthly total and get reminders.",
                    actionTitle: "Add subscription",
                    action: { sheet = .add }
                )
            } else {
                List {
                    Picker("Filter", selection: $filter) {
                        Text("All").tag(Filter.all)
                        Text("Active").tag(Filter.active)
                        Text("Paused").tag(Filter.paused)
                        Text("Cancelled").tag(Filter.cancelled)
                    }
                    .pickerStyle(.segmented)
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())

                    let rows = items
                    if rows.isEmpty {
                        Text("No matches.").foregroundStyle(.secondary)
                    }
                    ForEach(rows) { row in
                        NavigationLink(value: Route.detail(row.subscription.id)) {
                            SubscriptionRow(
                                subscription: row.subscription,
                                detail: row.next.map { "\(format.cycle(row.subscription.cycle)) · \(format.relative($0))" }
                                    ?? format.cycle(row.subscription.cycle),
                                showsBadge: true
                            )
                        }
                    }
                }
                .listStyle(.insetGrouped)
                .searchable(text: $query)
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Menu {
                            Picker("Sort", selection: $sort) {
                                Text("Next renewal").tag(Sort.nextRenewal)
                                Text("Monthly cost").tag(Sort.monthlyCost)
                                Text("Name").tag(Sort.name)
                            }
                        } label: {
                            Label("Sort", systemImage: "arrow.up.arrow.down")
                        }
                    }
                }
            }
        }
        .navigationTitle("Subscriptions")
        .addButton(sheet: $sheet)
        .subscriptionNavigation(sheet: $sheet)
    }
}
