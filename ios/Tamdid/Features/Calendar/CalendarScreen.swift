import SwiftUI
import TamdidCore

struct CalendarScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.appFormat) private var format
    @State private var sheet: Sheet?
    @State private var month: CalendarMonth?
    @State private var selected: CivilDate?

    var body: some View {
        let current = month.flatMap { $0.system == format.calendar ? $0 : nil }
            ?? CalendarMonth.containing(selected ?? format.today, in: format.calendar)
        let day = selected ?? format.today
        let charges = model.insights.charges(from: current.firstDay, to: current.lastDay)
        let converter = CurrencyConverter(settings: model.settings)
        let total = charges.reduce(into: Int64(0)) { sum, entry in
            for sub in entry.value {
                sum += converter.toPrimary(sub.price(on: entry.key), from: sub.price.currency) ?? 0
            }
        }

        List {
            Section {
                MonthHeader(
                    month: current,
                    onPrevious: { show(current.adding(months: -1)) },
                    onNext: { show(current.adding(months: 1)) }
                )
                .buttonStyle(.borderless)
                MonthGridView(
                    month: current,
                    selected: day,
                    markers: { charges[$0, default: []].map { Color(hex: $0.colorHex) } },
                    onSelect: { selected = $0 }
                )
                Text("Due this month: \(format.money(Money(amountMinor: total, currency: model.settings.primaryCurrency)))")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            Section(format.date(day)) {
                let dayCharges = charges[day, default: []]
                if dayCharges.isEmpty {
                    Text("No renewals on this day.").foregroundStyle(.secondary)
                }
                ForEach(dayCharges) { sub in
                    NavigationLink(value: Route.detail(sub.id)) {
                        SubscriptionRow(subscription: sub, detail: format.cycle(sub.cycle),
                                        amount: Money(amountMinor: sub.price(on: day), currency: sub.price.currency))
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Calendar")
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button("Today") {
                    month = nil
                    selected = nil
                }
            }
        }
        .addButton(sheet: $sheet)
        .subscriptionNavigation(sheet: $sheet)
    }

    private func show(_ next: CalendarMonth) {
        month = next
        if let selected, CalendarMonth.containing(selected, in: next.system) == next { return }
        selected = next.firstDay
    }
}
