import Foundation

/// A calendar date with no time or time zone, always in the proleptic Gregorian calendar.
///
/// Billing happens on *days*, not instants. Modelling dates this way keeps every rule
/// free of time-zone and daylight-saving bugs; conversion to `Date` happens only at the
/// edges (notifications, "today").
public struct CivilDate: Hashable, Comparable, Sendable, CustomStringConvertible {
    public let year: Int
    public let month: Int
    public let day: Int

    /// Creates a date, returning `nil` for impossible dates such as 2026-02-30.
    public init?(year: Int, month: Int, day: Int) {
        guard (1...12).contains(month), day >= 1, day <= CivilDate.daysInMonth(year: year, month: month) else {
            return nil
        }
        self.year = year
        self.month = month
        self.day = day
    }

    /// Creates a date from the number of days since 1970-01-01.
    public init(epochDay: Int) {
        // Howard Hinnant's civil_from_days.
        let z = epochDay + 719_468
        let era = (z >= 0 ? z : z - 146_096) / 146_097
        let doe = z - era * 146_097
        let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146_096) / 365
        let doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        let mp = (5 * doy + 2) / 153
        let d = doy - (153 * mp + 2) / 5 + 1
        let m = mp < 10 ? mp + 3 : mp - 9
        self.year = yoe + era * 400 + (m <= 2 ? 1 : 0)
        self.month = m
        self.day = d
    }

    /// Parses `yyyy-MM-dd`.
    public init?(iso: String) {
        let parts = iso.split(separator: "-")
        guard parts.count == 3,
              let y = Int(parts[0]), let m = Int(parts[1]), let d = Int(parts[2]) else { return nil }
        self.init(year: y, month: m, day: d)
    }

    /// Days since 1970-01-01 (Howard Hinnant's days_from_civil).
    public var epochDay: Int {
        let y = month <= 2 ? year - 1 : year
        let era = (y >= 0 ? y : y - 399) / 400
        let yoe = y - era * 400
        let doy = (153 * (month + (month > 2 ? -3 : 9)) + 2) / 5 + day - 1
        let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097 + doe - 719_468
    }

    /// ISO weekday: Monday = 1 … Sunday = 7.
    public var isoWeekday: Int {
        let r = (epochDay + 3) % 7
        return (r < 0 ? r + 7 : r) + 1
    }

    public var iso: String {
        String(format: "%04d-%02d-%02d", year, month, day)
    }

    public var description: String { iso }

    public func adding(days: Int) -> CivilDate {
        CivilDate(epochDay: epochDay + days)
    }

    /// Adds calendar months, clamping to the last day of the target month (Jan 31 + 1 month = Feb 28/29).
    public func adding(months: Int) -> CivilDate {
        let total = year * 12 + (month - 1) + months
        let y = total >= 0 ? total / 12 : (total - 11) / 12
        let m = total - y * 12 + 1
        let d = min(day, CivilDate.daysInMonth(year: y, month: m))
        return CivilDate(uncheckedYear: y, month: m, day: d)
    }

    public func days(until other: CivilDate) -> Int {
        other.epochDay - epochDay
    }

    public static func < (lhs: CivilDate, rhs: CivilDate) -> Bool {
        (lhs.year, lhs.month, lhs.day) < (rhs.year, rhs.month, rhs.day)
    }

    public static func isLeap(year: Int) -> Bool {
        (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
    }

    public static func daysInMonth(year: Int, month: Int) -> Int {
        switch month {
        case 1, 3, 5, 7, 8, 10, 12: return 31
        case 4, 6, 9, 11: return 30
        case 2: return isLeap(year: year) ? 29 : 28
        default: return 0
        }
    }

    init(uncheckedYear year: Int, month: Int, day: Int) {
        self.year = year
        self.month = month
        self.day = day
    }
}

// MARK: - Bridging to Foundation

extension CivilDate {
    /// The calendar day that `date` falls on in `timeZone`.
    public init(_ date: Date, timeZone: TimeZone = .current) {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let c = calendar.dateComponents([.year, .month, .day], from: date)
        self.init(uncheckedYear: c.year!, month: c.month!, day: c.day!)
    }

    public static func today(timeZone: TimeZone = .current, now: Date = Date()) -> CivilDate {
        CivilDate(now, timeZone: timeZone)
    }

    /// The instant at `hour:minute` on this day in `timeZone`.
    public func date(hour: Int = 0, minute: Int = 0, timeZone: TimeZone = .current) -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let components = DateComponents(year: year, month: month, day: day, hour: hour, minute: minute)
        return calendar.date(from: components)!
    }
}

// MARK: - Codable as "yyyy-MM-dd"

extension CivilDate: Codable {
    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let raw = try container.decode(String.self)
        guard let date = CivilDate(iso: raw) else {
            throw DecodingError.dataCorruptedError(in: container, debugDescription: "Invalid date: \(raw)")
        }
        self = date
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(iso)
    }
}
