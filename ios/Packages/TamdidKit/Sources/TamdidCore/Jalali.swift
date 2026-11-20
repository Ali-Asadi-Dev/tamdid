/// A date in the Solar Hijri (Jalali) calendar, the official calendar in Iran.
public struct JalaliDate: Hashable, Sendable, CustomStringConvertible {
    public let year: Int
    public let month: Int
    public let day: Int

    public init(year: Int, month: Int, day: Int) {
        self.year = year
        self.month = month
        self.day = day
    }

    public var description: String {
        let pad = { (n: Int, w: Int) in String(repeating: "0", count: max(0, w - String(n).count)) + String(n) }
        return "\(pad(year, 4))-\(pad(month, 2))-\(pad(day, 2))"
    }
}

/// Gregorian ↔ Jalali conversion.
///
/// Port of the widely used `jalaali-js` algorithm (Borkowski's method with the published
/// leap-year break points). It matches the official Iranian calendar for 1–3177 AP.
/// Integer division truncates toward zero on purpose, exactly like the reference implementation.
public enum Jalali {
    private static let breaks = [
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
        1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178,
    ]
    private static let julianDayOfEpoch = 2_440_588 // JDN of 1970-01-01

    public static func fromGregorian(_ date: CivilDate) -> JalaliDate {
        let jdn = date.epochDay + julianDayOfEpoch
        let gy = gregorianFromJDN(jdn).year
        var jy = gy - 621
        let info = calendarInfo(jy)
        let jdn1f = gregorianToJDN(gy, 3, info.march)
        var k = jdn - jdn1f
        if k >= 0 {
            if k <= 185 {
                return JalaliDate(year: jy, month: 1 + k / 31, day: k % 31 + 1)
            }
            k -= 186
        } else {
            jy -= 1
            k += 179
            if info.leap == 1 { k += 1 }
        }
        return JalaliDate(year: jy, month: 7 + k / 30, day: k % 30 + 1)
    }

    public static func toGregorian(_ date: JalaliDate) -> CivilDate {
        let info = calendarInfo(date.year)
        let jdn = gregorianToJDN(info.gregorianYear, 3, info.march)
            + (date.month - 1) * 31 - (date.month / 7) * (date.month - 7) + date.day - 1
        return CivilDate(epochDay: jdn - julianDayOfEpoch)
    }

    public static func isLeap(year: Int) -> Bool {
        calendarInfo(year).leap == 0
    }

    public static func daysInMonth(year: Int, month: Int) -> Int {
        if month <= 6 { return 31 }
        if month <= 11 { return 30 }
        return isLeap(year: year) ? 30 : 29
    }

    // MARK: - Reference algorithm

    private struct Info {
        let leap: Int
        let gregorianYear: Int
        let march: Int
    }

    private static func calendarInfo(_ jy: Int) -> Info {
        let gy = jy + 621
        var leapJ = -14
        var jp = breaks[0]
        var jump = 0
        for i in 1..<breaks.count {
            let jm = breaks[i]
            jump = jm - jp
            if jy < jm { break }
            leapJ += (jump / 33) * 8 + (jump % 33) / 4
            jp = jm
        }
        var n = jy - jp
        leapJ += (n / 33) * 8 + ((n % 33) + 3) / 4
        if jump % 33 == 4 && jump - n == 4 { leapJ += 1 }
        let leapG = gy / 4 - ((gy / 100 + 1) * 3) / 4 - 150
        let march = 20 + leapJ - leapG
        if jump - n < 6 { n = n - jump + ((jump + 4) / 33) * 33 }
        var leap = (((n + 1) % 33) - 1) % 4
        if leap == -1 { leap = 4 }
        return Info(leap: leap, gregorianYear: gy, march: march)
    }

    private static func gregorianToJDN(_ gy: Int, _ gm: Int, _ gd: Int) -> Int {
        var d = ((gy + (gm - 8) / 6 + 100_100) * 1461) / 4
            + (153 * ((gm + 9) % 12) + 2) / 5 + gd - 34_840_408
        d = d - ((gy + 100_100 + (gm - 8) / 6) / 100 * 3) / 4 + 752
        return d
    }

    private static func gregorianFromJDN(_ jdn: Int) -> (year: Int, month: Int, day: Int) {
        var j = 4 * jdn + 139_361_631
        j = j + ((4 * jdn + 183_187_720) / 146_097 * 3) / 4 * 4 - 3908
        let i = ((j % 1461) / 4) * 5 + 308
        let gd = (i % 153) / 5 + 1
        let gm = ((i / 153) % 12) + 1
        let gy = j / 1461 - 100_100 + (8 - gm) / 6
        return (gy, gm, gd)
    }
}
