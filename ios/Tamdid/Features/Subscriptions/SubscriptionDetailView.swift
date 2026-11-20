import SwiftUI
import TamdidCore

struct SubscriptionDetailView: View {
    let id: UUID

    @Environment(AppModel.self) private var model
    @Environment(\.appFormat) private var format
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @State private var editing = false
    @State private var confirmingDelete = false

    var body: some View {
        if let sub = model.subscription(id: id) {
            content(sub)
        } else {
            // Deleted while open.
            Color.clear.onAppear { dismiss() }
        }
    }

    private func content(_ sub: Subscription) -> some View {
        let today = format.today
        let next = sub.nextRenewal(onOrAfter: today)
        return List {
            Section {
                HStack(spacing: 16) {
                    ServiceAvatar(name: sub.name, colorHex: sub.colorHex, size: 64)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(sub.name).font(.title2.weight(.semibold))
                        Text(sub.category.displayName(format.language)).foregroundStyle(.secondary)
                        SubscriptionBadge(subscription: sub, today: today)
                    }
                }
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets())
            }

            Section {
                VStack(alignment: .leading, spacing: 2) {
                    Text(format.money(sub.price)).font(.title.weight(.semibold))
                    Text(format.cycle(sub.cycle)).foregroundStyle(.secondary)
                    Text("\(format.money(Money(amountMinor: sub.monthlyCostMinor, currency: sub.price.currency))) per month")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                LabeledContent("Next renewal") {
                    if let next {
                        Text(verbatim: "\(format.date(next)) (\(format.relative(next)))")
                    } else {
                        Text("No upcoming renewal")
                    }
                }
                if let trial = sub.trialEndDate {
                    LabeledContent("Trial ends", value: format.date(trial))
                }
                LabeledContent("Reminder", value: format.reminder(sub.reminderDaysBefore))
            }

            Section {
                LabeledContent("Spent so far", value: format.money(Money(amountMinor: sub.totalSpent(asOf: today), currency: sub.price.currency)))
                LabeledContent("Payments", value: format.number(sub.paymentCount(asOf: today)))
                LabeledContent("Started", value: format.date(sub.startDate))
                if let end = sub.endDate {
                    LabeledContent("Ended", value: format.date(end))
                }
                if let method = sub.paymentMethod {
                    LabeledContent("Payment method", value: method)
                }
            }

            if !sub.priceHistory.isEmpty {
                Section("Price history") {
                    if let since = sub.currentPriceSince {
                        LabeledContent("since \(format.date(since))", value: format.money(sub.price))
                    }
                    ForEach(sub.priceHistory.reversed(), id: \.self) { change in
                        LabeledContent("since \(format.date(change.effectiveFrom))",
                                       value: format.money(Money(amountMinor: change.amountMinor, currency: sub.price.currency)))
                    }
                }
            }

            if let notes = sub.notes {
                Section("Notes") { Text(notes) }
            }

            Section {
                if let link = sub.url, let url = URL(string: link.hasPrefix("http") ? link : "https://\(link)") {
                    Button {
                        openURL(url)
                    } label: {
                        Label("Manage subscription", systemImage: "arrow.up.forward.app")
                    }
                }
                switch sub.status {
                case .active:
                    Button("Pause") { model.pause(id: id) }
                    Button("Mark as cancelled") { model.cancel(id: id) }
                case .paused, .cancelled:
                    Button("Resume") { model.resume(id: id) }
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .primaryAction) {
                Button {
                    editing = true
                } label: {
                    Label("Edit", systemImage: "pencil")
                }
                Button(role: .destructive) {
                    confirmingDelete = true
                } label: {
                    Label("Delete", systemImage: "trash")
                }
                .accessibilityIdentifier("delete")
            }
        }
        .sheet(isPresented: $editing) {
            NavigationStack { SubscriptionEditView(editing: id, catalogID: nil) }
        }
        .confirmationDialog("Delete \(sub.name)?", isPresented: $confirmingDelete, titleVisibility: .visible) {
            Button("Delete", role: .destructive) {
                model.delete(id: id)
                dismiss()
            }
        } message: {
            Text("Its payment history will be removed too. This can't be undone.")
        }
    }
}
