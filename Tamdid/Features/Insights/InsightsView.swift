import Charts
import SwiftUI
import TamdidCore

struct InsightsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.appFormat) private var format

    var body: some View {
        let insights = model.insights
        let currency = model.settings.primaryCurrency
        Group {
            if insights.active.isEmpty {
                EmptyStateView(systemImage: "chart.bar", title: "Insights",
                               message: "Add a few subscriptions to see where your money goes.")
            } else {
                List {
                    Section {
                        LabeledContent("Per month", value: format.money(insights.monthlyTotal.primary))
                        LabeledContent("Per year", value: format.money(insights.yearlyTotal.primary))
                        LabeledContent("Per day", value: format.money(Money(amountMinor: insights.dailyAverageMinor, currency: currency)))
                    }

                    ForEach(Array(insights.overlaps.enumerated()), id: \.offset) { _, overlap in
                        Section {
                            OverlapTip(category: overlap.category, subscriptions: overlap.subscriptions)
                        }
                    }

                    Section("Last 12 months") {
                        MonthlyChart(months: insights.spendByMonth(count: 12, calendar: model.settings.calendar))
                    }

                    Section("By category") {
                        let categories = insights.byCategory
                        let maximum = Double(max(categories.first?.monthlyMinor ?? 1, 1))
                        ForEach(Array(categories.enumerated()), id: \.element.id) { index, spend in
                            VStack(alignment: .leading, spacing: 6) {
                                HStack {
                                    Text(spend.category.displayName(format.language))
                                    Spacer()
                                    Text(format.money(Money(amountMinor: spend.monthlyMinor, currency: currency)))
                                }
                                .font(.subheadline)
                                GeometryReader { proxy in
                                    Capsule().fill(Palette.chart(index))
                                        .frame(width: proxy.size.width * Double(spend.monthlyMinor) / maximum)
                                }
                                .frame(height: 8)
                            }
                            .accessibilityElement(children: .combine)
                        }
                    }

                    Section("Most expensive") {
                        ForEach(Array(insights.mostExpensive(limit: 5).enumerated()), id: \.offset) { _, ranked in
                            NavigationLink(value: Route.detail(ranked.subscription.id)) {
                                SubscriptionRow(
                                    subscription: ranked.subscription,
                                    detail: String(localized: "\(format.money(Money(amountMinor: ranked.monthlyMinor, currency: currency))) per month")
                                )
                            }
                        }
                    }
                }
                .listStyle(.insetGrouped)
                .navigationDestination(for: Route.self) { route in
                    switch route {
                    case .detail(let id): SubscriptionDetailView(id: id)
                    }
                }
            }
        }
        .navigationTitle("Insights")
    }
}

private struct OverlapTip: View {
    let category: SubscriptionCategory
    let subscriptions: [Subscription]
    @Environment(\.appFormat) private var format

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "lightbulb.fill").foregroundStyle(Palette.brand)
            VStack(alignment: .leading, spacing: 4) {
                Text(format.digits(String(localized: "You pay for \(subscriptions.count) services in \(category.displayName(format.language))")))
                    .font(.subheadline.weight(.semibold))
                Text(subscriptions.map(\.name).joined(separator: " · ")).font(.footnote)
                if let currency = subscriptions.first?.price.currency, subscriptions.allSatisfy({ $0.price.currency == currency }) {
                    let total = subscriptions.reduce(Int64(0)) { $0 + $1.monthlyCostMinor }
                    Text("\(format.money(Money(amountMinor: total, currency: currency))) a month together. Could one of them go?")
                        .font(.footnote)
                }
            }
        }
        .listRowBackground(Palette.brand.opacity(0.12))
    }
}

private struct MonthlyChart: View {
    let months: [MonthSpend]
    @Environment(\.appFormat) private var format

    private func label(_ month: MonthSpend) -> String {
        // Jalali and Arabic-script month names are long and share prefixes, so label by month number.
        format.calendar == .jalali || format.language.isRightToLeft
            ? format.number(month.month)
            : String(DateText.monthName(month.month, calendar: .gregorian, language: format.language).prefix(1))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let current = months.last {
                Text(format.money(current.total.primary)).font(.title2.weight(.semibold))
                Text(DateText.monthName(current.month, calendar: format.calendar, language: format.language))
                    .font(.footnote).foregroundStyle(.secondary)
            }
            Chart(months) { month in
                BarMark(
                    x: .value("Month", month.id),
                    y: .value("Amount", Double(month.total.primary.amountMinor))
                )
                .foregroundStyle(month.id == months.last?.id ? Palette.brand : Palette.brand.opacity(0.35))
                .cornerRadius(4)
                .accessibilityLabel(DateText.monthName(month.month, calendar: format.calendar, language: format.language))
                .accessibilityValue(format.money(month.total.primary))
            }
            .chartXAxis {
                AxisMarks(values: months.map(\.id)) { value in
                    AxisValueLabel {
                        if let id = value.as(String.self), let month = months.first(where: { $0.id == id }) {
                            Text(label(month))
                        }
                    }
                }
            }
            .chartYAxis(.hidden)
            .frame(height: 140)
        }
        .padding(.vertical, 6)
    }
}
