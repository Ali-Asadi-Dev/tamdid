import SwiftUI
import TamdidCore

/// Design tokens shared with Android; see docs/DESIGN.md.
enum Palette {
    static let brand = Color("AccentColor")
    static let positive = Color(hex: "#12A594")
    static let warning = Color(hex: "#F76B15")
    static let danger = Color(hex: "#E5484D")

    /// Colors offered for subscriptions; also used for category charts.
    static let swatches = [
        "#5B4BDB", "#E5484D", "#12A594", "#F76B15", "#3E63DD", "#D6409F",
        "#30A46C", "#8E4EC6", "#0090FF", "#AD7F58", "#E54666", "#46A758",
    ]

    static func chart(_ index: Int) -> Color { Color(hex: swatches[index % swatches.count]) }
}

extension Color {
    /// `#RRGGBB`; falls back to the brand color for anything else.
    init(hex: String) {
        let digits = hex.trimmingCharacters(in: CharacterSet(charactersIn: "#"))
        guard digits.count == 6, let value = UInt32(digits, radix: 16) else {
            self = Color(red: 0x5B / 255, green: 0x4B / 255, blue: 0xDB / 255)
            return
        }
        self = Color(
            red: Double((value >> 16) & 0xFF) / 255,
            green: Double((value >> 8) & 0xFF) / 255,
            blue: Double(value & 0xFF) / 255
        )
    }
}

/// A colored rounded square with the service's initial. No logos: brand assets stay with their owners.
struct ServiceAvatar: View {
    let name: String
    let colorHex: String
    var size: CGFloat = 40

    var body: some View {
        RoundedRectangle(cornerRadius: size * 0.3, style: .continuous)
            .fill(Color(hex: colorHex))
            .frame(width: size, height: size)
            .overlay {
                Text(name.trimmingCharacters(in: .whitespaces).first.map { String($0).uppercased() } ?? "?")
                    .font(.system(size: size * 0.42, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
            }
            .accessibilityHidden(true)
    }
}

struct StatusBadge: View {
    let text: LocalizedStringKey
    let color: Color

    var body: some View {
        Text(text)
            .font(.caption2.weight(.semibold))
            .padding(.horizontal, 8)
            .padding(.vertical, 2)
            .foregroundStyle(color)
            .background(color.opacity(0.14), in: Capsule())
    }
}

/// The status badge to show for a subscription, if any.
struct SubscriptionBadge: View {
    let subscription: Subscription
    let today: CivilDate

    var body: some View {
        switch subscription.status {
        case .paused: StatusBadge(text: "Paused", color: Palette.warning)
        case .cancelled: StatusBadge(text: "Cancelled", color: .secondary)
        case .active:
            if subscription.isInTrial(on: today) {
                StatusBadge(text: "Free trial", color: Palette.positive)
            }
        }
    }
}

/// One subscription in a list: avatar, name, a detail line and the price.
struct SubscriptionRow: View {
    let subscription: Subscription
    let detail: String
    var amount: Money? = nil
    var amountColor: Color = .primary
    var showsBadge = false

    @Environment(\.appFormat) private var format

    var body: some View {
        HStack(spacing: 12) {
            ServiceAvatar(name: subscription.name, colorHex: subscription.colorHex)
            VStack(alignment: .leading, spacing: 2) {
                Text(subscription.name).font(.body).lineLimit(1)
                Text(detail).font(.footnote).foregroundStyle(.secondary).lineLimit(1)
            }
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 4) {
                Text(format.money(amount ?? subscription.price))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(amountColor)
                if showsBadge { SubscriptionBadge(subscription: subscription, today: format.today) }
            }
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
    }
}

/// A thin bar of colored segments, e.g. spend per category.
struct SegmentedBar: View {
    let values: [(Double, Color)]

    var body: some View {
        GeometryReader { proxy in
            let total = max(values.map(\.0).reduce(0, +), 1)
            HStack(spacing: 2) {
                ForEach(Array(values.enumerated()), id: \.offset) { _, item in
                    item.1.frame(width: max(0, (proxy.size.width - CGFloat(values.count - 1) * 2) * item.0 / total))
                }
            }
        }
        .frame(height: 12)
        .clipShape(Capsule())
        .accessibilityHidden(true)
    }
}

struct EmptyStateView: View {
    let systemImage: String
    let title: LocalizedStringKey
    let message: LocalizedStringKey
    var actionTitle: LocalizedStringKey? = nil
    var action: (() -> Void)? = nil

    var body: some View {
        ContentUnavailableView {
            Label(title, systemImage: systemImage)
        } description: {
            Text(message)
        } actions: {
            if let actionTitle, let action {
                Button(actionTitle, action: action).buttonStyle(.borderedProminent)
            }
        }
    }
}
