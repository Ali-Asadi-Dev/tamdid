import SwiftUI
import TamdidCore

/// A month of days in either calendar: Jalali weeks start on Saturday, Gregorian on Monday.
/// SwiftUI mirrors the grid automatically in right-to-left languages.
struct MonthGridView: View {
    let month: CalendarMonth
    let selected: CivilDate?
    var markers: (CivilDate) -> [Color] = { _ in [] }
    let onSelect: (CivilDate) -> Void

    @Environment(\.appFormat) private var format

    private var firstWeekday: Int { DateText.defaultFirstWeekday(for: month.system, language: format.language) }

    var body: some View {
        VStack(spacing: 4) {
            HStack(spacing: 0) {
                ForEach(Array(DateText.weekdaySymbols(firstWeekday: firstWeekday, language: format.language).enumerated()), id: \.offset) { _, symbol in
                    Text(symbol)
                        .font(.caption.weight(.medium))
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity)
                }
            }
            ForEach(Array(month.weeks(firstWeekday: firstWeekday).enumerated()), id: \.offset) { _, week in
                HStack(spacing: 0) {
                    ForEach(0..<7, id: \.self) { index in
                        if let date = week[index] {
                            dayCell(date)
                        } else {
                            Color.clear.frame(maxWidth: .infinity, minHeight: 44)
                        }
                    }
                }
            }
        }
    }

    private func dayNumber(_ date: CivilDate) -> Int {
        month.system == .gregorian ? date.day : Jalali.fromGregorian(date).day
    }

    private func dayCell(_ date: CivilDate) -> some View {
        let isSelected = date == selected
        let isToday = date == format.today
        return Button {
            onSelect(date)
        } label: {
            VStack(spacing: 3) {
                Text(format.number(dayNumber(date)))
                    .font(.callout.weight(isToday ? .bold : .regular))
                    .foregroundStyle(isSelected ? Color.white : Color.primary)
                HStack(spacing: 2) {
                    ForEach(Array(markers(date).prefix(3).enumerated()), id: \.offset) { _, color in
                        Circle().fill(isSelected ? Color.white : color).frame(width: 5, height: 5)
                    }
                }
                .frame(height: 5)
            }
            .frame(width: 40, height: 44)
            .background {
                if isSelected {
                    Circle().fill(Palette.brand)
                } else if isToday {
                    Circle().strokeBorder(Palette.brand, lineWidth: 1)
                }
            }
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(format.date(date))
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

/// "‹ Aban 1405 ›"
struct MonthHeader: View {
    let month: CalendarMonth
    let onPrevious: () -> Void
    let onNext: () -> Void

    @Environment(\.appFormat) private var format

    var body: some View {
        HStack {
            Button(action: onPrevious) {
                Image(systemName: "chevron.backward")
            }
            .accessibilityLabel("Previous month")
            Spacer()
            Text(DateText.format(month, language: format.language)).font(.headline)
            Spacer()
            Button(action: onNext) {
                Image(systemName: "chevron.forward")
            }
            .accessibilityLabel("Next month")
        }
        .padding(.horizontal, 8)
    }
}
