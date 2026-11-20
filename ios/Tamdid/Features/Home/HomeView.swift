import SwiftUI
import TamdidCore

struct HomeView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.appFormat) private var format
    @State private var sheet: Sheet?

    var body: some View {
        let insights = model.insights
        Group {
            if model.subscriptions.isEmpty {
                EmptyStateView(
                    systemImage: "arrow.triangle.2.circlepath",
                    title: "No subscriptions yet",
                    message: "Add the services you pay for to see your monthly total and get reminders.",
                    actionTitle: "Add subscription",
                    action: { sheet = .add }
                )
            } else {
                List {
                    Section { TotalCard(insights: insights) }
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)

                    let trials = insights.endingTrials(days: 14)
                    if !trials.isEmpty {
                        Section("Free trials ending") {
                            ForEach(trials) { charge in chargeRow(charge, color: Palette.warning) }
                        }
                    }

                    Section("Next 7 days") {
                        let upcoming = insights.upcoming(days: 7).filter { !$0.isTrialEnd }
                        if upcoming.isEmpty {
                            Text("Nothing renews in the next 7 days.").foregroundStyle(.secondary)
                        }
                        ForEach(upcoming) { charge in chargeRow(charge, color: .primary) }
                    }

                    let categories = insights.byCategory
                    if !categories.isEmpty {
                        Section("By category") {
                            CategoryBreakdown(categories: categories, currency: model.settings.primaryCurrency)
                        }
                    }
                }
                .listStyle(.insetGrouped)
            }
        }
        .navigationTitle("Tamdid")
        .addButton(sheet: $sheet)
        .subscriptionNavigation(sheet: $sheet)
    }

    private func chargeRow(_ charge: UpcomingCharge, color: Color) -> some View {
        let sub = charge.subscription
        return NavigationLink(value: Route.detail(sub.id)) {
            SubscriptionRow(
                subscription: sub,
                detail: "\(format.relative(charge.date)) · \(format.date(charge.date, .dayMonth))",
                amount: Money(amountMinor: sub.price(on: charge.date), currency: sub.price.currency),
                amountColor: color
            )
        }
    }
}

private struct TotalCard: View {
    let insights: Insights
    @Environment(\.appFormat) private var format

    var body: some View {
        let monthly = insights.monthlyTotal
        VStack(alignment: .leading, spacing: 6) {
            Text("Per month").font(.subheadline.weight(.medium))
            Text(format.money(monthly.primary))
                .font(.system(size: 40, weight: .semibold, design: .rounded))
                .minimumScaleFactor(0.5)
                .lineLimit(1)
                .accessibilityIdentifier("monthlyTotal")
            HStack(spacing: 16) {
                Text("\(format.money(insights.yearlyTotal.primary)) a year")
                Text("\(format.money(Money(amountMinor: insights.dailyAverageMinor, currency: monthly.primary.currency))) a day")
            }
            .font(.subheadline)
            Text(format.digits(String(localized: "\(insights.active.count) active subscriptions")))
                .font(.caption)
            if !monthly.isComplete {
                Text("Not included (no exchange rate): \(format.unconverted(monthly))")
                    .font(.caption)
                    .padding(.top, 4)
            }
        }
        .foregroundStyle(.white)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(20)
        .background(Palette.brand.gradient, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    }
}

struct CategoryBreakdown: View {
    let categories: [CategorySpend]
    let currency: CurrencyCode
    @Environment(\.appFormat) private var format

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            SegmentedBar(values: categories.enumerated().map { (Double($0.element.monthlyMinor), Palette.chart($0.offset)) })
            ForEach(Array(categories.enumerated()), id: \.element.id) { index, spend in
                HStack {
                    Circle().fill(Palette.chart(index)).frame(width: 10, height: 10)
                    Text(spend.category.displayName(format.language))
                    Spacer()
                    Text(format.money(Money(amountMinor: spend.monthlyMinor, currency: currency)))
                        .foregroundStyle(.secondary)
                }
                .font(.subheadline)
                .accessibilityElement(children: .combine)
            }
        }
        .padding(.vertical, 6)
    }
}
