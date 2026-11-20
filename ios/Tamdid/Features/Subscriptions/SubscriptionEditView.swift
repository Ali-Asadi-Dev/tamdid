import SwiftUI
import TamdidCore

/// Form state for creating or editing a subscription. Mirrors Android's `EditViewModel`.
@Observable
@MainActor
final class SubscriptionForm {
    enum FormError: Hashable { case name, price, trial }

    let existing: Subscription?
    var name = ""
    var priceText = ""
    var currency = CurrencyCode.usDollar
    var unit = CycleUnit.month
    var interval = 1
    var startDate: CivilDate
    var hasTrial = false
    var trialEnd: CivilDate
    var reminderDays: Int? = 1
    var category = SubscriptionCategory.other
    var colorHex = Palette.swatches[0]
    var paymentMethod = ""
    var url = ""
    var notes = ""
    var catalogID: String?
    /// Errors appear only after the first save attempt, then update live.
    var showsErrors = false

    init(existing: Subscription?, service: CatalogService?, settings: AppSettings, today: CivilDate, language: AppLanguage) {
        self.existing = existing
        startDate = today
        trialEnd = today.adding(days: 7)
        if let existing {
            name = existing.name
            priceText = MoneyFormatter.editableText(existing.price.amountMinor, currency: existing.price.currency)
            currency = existing.price.currency
            unit = existing.cycle.unit
            interval = existing.cycle.interval
            startDate = existing.startDate
            hasTrial = existing.trialEndDate != nil
            trialEnd = existing.trialEndDate ?? existing.startDate.adding(days: 7)
            reminderDays = existing.reminderDaysBefore
            category = existing.category
            colorHex = existing.colorHex
            paymentMethod = existing.paymentMethod ?? ""
            url = existing.url ?? ""
            notes = existing.notes ?? ""
            catalogID = existing.catalogID
        } else {
            currency = settings.primaryCurrency
            reminderDays = settings.defaultReminderDays
            if let service {
                catalogID = service.id
                name = service.displayName(language)
                category = service.category
                colorHex = service.color
            }
        }
    }

    var isNew: Bool { existing == nil }
    var parsedPrice: Int64? { MoneyFormatter.parse(priceText, currency: currency) }
    var cycle: BillingCycle { BillingCycle(unit: unit, interval: interval) }

    var monthlyPreview: Money? {
        guard let price = parsedPrice, cycle != .monthly else { return nil }
        return Money(amountMinor: Int64((Double(price) * cycle.periodsPerMonth + 0.5).rounded(.down)), currency: currency)
    }

    var priceWillChange: Bool {
        guard let existing, let price = parsedPrice else { return false }
        return price != existing.price.amountMinor && currency == existing.price.currency
    }

    var errors: Set<FormError> {
        var result = Set<FormError>()
        if name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { result.insert(.name) }
        if parsedPrice == nil { result.insert(.price) }
        if hasTrial && trialEnd < startDate { result.insert(.trial) }
        return result
    }

    /// The subscription to save, or `nil` when the form has errors.
    func build(today: CivilDate) -> Subscription? {
        showsErrors = true
        guard errors.isEmpty, let amount = parsedPrice else { return nil }
        func clean(_ text: String) -> String? {
            let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.isEmpty ? nil : trimmed
        }
        var sub = existing ?? Subscription(name: name, price: Money(amountMinor: amount, currency: currency), startDate: startDate)
        sub.name = name.trimmingCharacters(in: .whitespacesAndNewlines)
        sub.catalogID = catalogID
        sub.category = category
        sub.colorHex = colorHex
        sub.cycle = cycle
        sub.startDate = startDate
        sub.trialEndDate = hasTrial ? trialEnd : nil
        sub.reminderDaysBefore = reminderDays
        sub.paymentMethod = clean(paymentMethod)
        sub.url = clean(url)
        sub.notes = clean(notes)
        if let existing {
            if currency != existing.price.currency {
                // A different currency is a correction, not a price change.
                sub.price = Money(amountMinor: amount, currency: currency)
                sub.priceHistory = []
                sub.currentPriceSince = nil
            } else {
                sub.changePrice(to: amount, effectiveFrom: max(today, startDate))
            }
        }
        return sub
    }
}

struct SubscriptionEditView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.appFormat) private var format
    @Environment(\.dismiss) private var dismiss
    @State private var form: SubscriptionForm?

    let editing: UUID?
    let catalogID: String?
    var onSaved: ((UUID) -> Void)? = nil

    var body: some View {
        Group {
            if let form {
                FormContent(form: form)
            } else {
                ProgressView()
            }
        }
        .navigationTitle(editing == nil ? "New subscription" : "Edit subscription")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            // When pushed from the catalog, the back button already cancels.
            if editing != nil {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button("Save") { save() }.accessibilityIdentifier("save")
            }
        }
        .onAppear {
            guard form == nil else { return }
            form = SubscriptionForm(
                existing: editing.flatMap(model.subscription(id:)),
                service: catalogID.flatMap(model.catalog.service(id:)),
                settings: model.settings,
                today: format.today,
                language: format.language
            )
        }
    }

    private func save() {
        guard let form, let sub = form.build(today: format.today) else { return }
        model.save(sub)
        onSaved?(sub.id)
        dismiss()
    }
}

private struct FormContent: View {
    @Bindable var form: SubscriptionForm
    @Environment(\.appFormat) private var format

    static let reminderOptions: [Int?] = [nil, 0, 1, 2, 3, 7]

    var body: some View {
        let errors = form.showsErrors ? form.errors : []
        Form {
            Section {
                TextField("Name", text: $form.name)
                    .textInputAutocapitalization(.words)
                    .accessibilityIdentifier("name")
                if errors.contains(.name) {
                    Text("Enter a name").font(.footnote).foregroundStyle(.red)
                }
                HStack {
                    TextField("Price", text: $form.priceText)
                        .keyboardType(.decimalPad)
                        .accessibilityIdentifier("price")
                    Picker("Currency", selection: $form.currency) {
                        ForEach(CurrencyCode.allCases) { Text($0.rawValue).tag($0) }
                    }
                    .labelsHidden()
                }
                if errors.contains(.price) {
                    Text("Enter a valid price").font(.footnote).foregroundStyle(.red)
                } else if let preview = form.monthlyPreview {
                    Text("\(format.money(preview)) per month").font(.footnote).foregroundStyle(.secondary)
                }
                if form.priceWillChange {
                    Text("A new price applies from today. Earlier payments keep the old price.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
            }

            Section("Billing cycle") {
                Stepper(value: $form.interval, in: 1...12) {
                    Text(format.digits(String(localized: "Every \(form.interval) \(format.unit(form.unit, count: form.interval))")))
                }
                Picker("Unit", selection: $form.unit) {
                    ForEach(CycleUnit.allCases, id: \.self) { Text(format.unit($0, count: form.interval)).tag($0) }
                }
                .pickerStyle(.segmented)
                CalendarDatePicker(title: "First payment", date: $form.startDate)
                Toggle("Starts with a free trial", isOn: $form.hasTrial.animation())
                if form.hasTrial {
                    CalendarDatePicker(title: "Trial ends on", date: $form.trialEnd)
                    if errors.contains(.trial) {
                        Text("The trial must end on or after the start date").font(.footnote).foregroundStyle(.red)
                    }
                }
            }

            Section {
                Picker("Remind me", selection: $form.reminderDays) {
                    ForEach(Self.reminderOptions, id: \.self) { Text(format.reminder($0)).tag($0) }
                }
                Picker("Category", selection: $form.category) {
                    ForEach(SubscriptionCategory.allCases) { Text($0.displayName(format.language)).tag($0) }
                }
            }

            Section("Color") {
                LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 6), spacing: 12) {
                    ForEach(Palette.swatches, id: \.self) { hex in
                        let selected = hex.caseInsensitiveCompare(form.colorHex) == .orderedSame
                        Circle()
                            .fill(Color(hex: hex))
                            .frame(width: 34, height: 34)
                            .overlay { if selected { Circle().strokeBorder(.primary, lineWidth: 3) } }
                            .onTapGesture { form.colorHex = hex }
                            .accessibilityLabel(hex)
                            .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
                    }
                }
                .padding(.vertical, 4)
            }

            Section {
                TextField("Payment method (optional)", text: $form.paymentMethod)
                TextField("Manage link (optional)", text: $form.url)
                    .keyboardType(.URL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                TextField("Notes (optional)", text: $form.notes, axis: .vertical)
                    .lineLimit(2...5)
            }
        }
    }
}

/// A date picker in the user's calendar: iOS can show the Solar Hijri calendar natively.
struct CalendarDatePicker: View {
    let title: LocalizedStringKey
    @Binding var date: CivilDate
    @Environment(\.appFormat) private var format

    var body: some View {
        DatePicker(title, selection: Binding(
            get: { date.pickerDate },
            set: { date = CivilDate($0) }
        ), displayedComponents: .date)
        .environment(\.calendar, Calendar(identifier: format.calendar == .jalali ? .persian : .gregorian))
    }
}

/// Catalog first, then the form. Saving opens the new subscription.
struct AddSubscriptionFlow: View {
    @Environment(AppModel.self) private var model
    @Environment(\.appFormat) private var format
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""

    var body: some View {
        NavigationStack {
            List {
                Section {
                    NavigationLink {
                        SubscriptionEditView(editing: nil, catalogID: nil, onSaved: { _ in dismiss() })
                    } label: {
                        Label {
                            VStack(alignment: .leading) {
                                Text("Custom subscription")
                                Text("Anything that isn’t on the list").font(.footnote).foregroundStyle(.secondary)
                            }
                        } icon: {
                            Image(systemName: "plus.square.fill").foregroundStyle(Palette.brand).font(.title2)
                        }
                    }
                }
                Section {
                    ForEach(model.catalog.search(query)) { service in
                        NavigationLink {
                            SubscriptionEditView(editing: nil, catalogID: service.id, onSaved: { _ in dismiss() })
                        } label: {
                            HStack(spacing: 12) {
                                ServiceAvatar(name: service.displayName(format.language), colorHex: service.color)
                                VStack(alignment: .leading) {
                                    Text(service.displayName(format.language))
                                    Text(service.category.displayName(format.language)).font(.footnote).foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                }
            }
            .searchable(text: $query)
            .navigationTitle("Choose a service")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
            }
        }
    }
}
