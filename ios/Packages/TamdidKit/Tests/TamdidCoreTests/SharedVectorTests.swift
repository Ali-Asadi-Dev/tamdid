import Foundation
import Testing
@testable import TamdidCore

/// Loads `shared/test-vectors/business-rules.json`, the fixtures the Android suite also runs.
enum Vectors {
    static let repoRoot: URL = {
        var url = URL(fileURLWithPath: #filePath)
        for _ in 0..<6 { url.deleteLastPathComponent() }
        return url
    }()

    struct Cycle: Decodable, Sendable {
        let name: String
        let start: String
        let trialEnd: String?
        let unit: String
        let interval: Int
        let status: String
        let endDate: String?
        let onOrAfter: String?
        let from: String?
        let to: String?
        let expected: Expected

        enum Expected: Decodable, Sendable {
            case date(String?)
            case dates([String])

            init(from decoder: Decoder) throws {
                let c = try decoder.singleValueContainer()
                if c.decodeNil() { self = .date(nil) }
                else if let list = try? c.decode([String].self) { self = .dates(list) }
                else { self = .date(try c.decode(String.self)) }
            }
        }

        var subscription: Subscription {
            Subscription(
                name: name,
                price: Money(amountMinor: 100, currency: .usDollar),
                cycle: BillingCycle(unit: CycleUnit(rawValue: unit)!, interval: interval),
                startDate: CivilDate(iso: start)!,
                trialEndDate: trialEnd.flatMap(CivilDate.init(iso:)),
                status: SubscriptionStatus(rawValue: status)!,
                endDate: endDate.flatMap(CivilDate.init(iso:))
            )
        }
    }

    struct JalaliCase: Decodable, Sendable { let gregorian: String; let jalali: String }
    struct MonthLength: Decodable, Sendable { let year: Int; let month: Int; let days: Int }
    struct JalaliVectors: Decodable, Sendable {
        let conversions: [JalaliCase]
        let leapYears: [String: Bool]
        let monthLengths: [MonthLength]
    }
    struct MoneyCase: Decodable, Sendable { let amountMinor: Int64; let currency: String; let language: String; let expected: String }
    struct MonthlyCase: Decodable, Sendable { let amountMinor: Int64; let unit: String; let interval: Int; let expected: Int64 }

    struct DateTextCase: Decodable, Sendable { let date: String; let calendar: String; let language: String; let style: String; let expected: String }
    struct RelativeCase: Decodable, Sendable { let date: String; let today: String; let language: String; let expected: String }
    struct WeekdayCase: Decodable, Sendable { let firstWeekday: Int; let language: String; let expected: [String] }

    struct File: Decodable, Sendable {
        let nextRenewal: [Cycle]
        let charges: [Cycle]
        let jalali: JalaliVectors
        let money: [MoneyCase]
        let monthlyCost: [MonthlyCase]
        let dateText: [DateTextCase]
        let relative: [RelativeCase]
        let weekdays: [WeekdayCase]
    }

    static let file: File = {
        let url = repoRoot.appendingPathComponent("shared/test-vectors/business-rules.json")
        return try! JSONDecoder().decode(File.self, from: Data(contentsOf: url))
    }()
}

@Suite("Shared business-rule vectors")
struct SharedVectorTests {
    @Test("Next renewal", arguments: Vectors.file.nextRenewal)
    func nextRenewal(_ vector: Vectors.Cycle) {
        guard case .date(let expected) = vector.expected else { Issue.record("bad fixture"); return }
        let actual = vector.subscription.nextRenewal(onOrAfter: CivilDate(iso: vector.onOrAfter!)!)
        #expect(actual?.iso == expected, "\(vector.name)")
    }

    @Test("Charges in a window", arguments: Vectors.file.charges)
    func charges(_ vector: Vectors.Cycle) {
        guard case .dates(let expected) = vector.expected else { Issue.record("bad fixture"); return }
        let actual = vector.subscription.charges(from: CivilDate(iso: vector.from!)!, to: CivilDate(iso: vector.to!)!)
        #expect(actual.map(\.iso) == expected, "\(vector.name)")
    }

    @Test("Gregorian ↔ Jalali", arguments: Vectors.file.jalali.conversions)
    func jalaliConversion(_ vector: Vectors.JalaliCase) {
        let gregorian = CivilDate(iso: vector.gregorian)!
        let jalali = Jalali.fromGregorian(gregorian)
        #expect(jalali.description == vector.jalali)
        #expect(Jalali.toGregorian(jalali) == gregorian)
    }

    @Test func jalaliLeapYearsAndMonthLengths() {
        for (year, isLeap) in Vectors.file.jalali.leapYears {
            #expect(Jalali.isLeap(year: Int(year)!) == isLeap, "year \(year)")
        }
        for length in Vectors.file.jalali.monthLengths {
            #expect(Jalali.daysInMonth(year: length.year, month: length.month) == length.days)
        }
    }

    @Test("Money formatting", arguments: Vectors.file.money)
    func money(_ vector: Vectors.MoneyCase) {
        let money = Money(amountMinor: vector.amountMinor, currency: CurrencyCode(rawValue: vector.currency)!)
        #expect(MoneyFormatter.format(money, language: AppLanguage(rawValue: vector.language)!) == vector.expected)
    }

    @Test("Monthly cost normalisation", arguments: Vectors.file.monthlyCost)
    func monthlyCost(_ vector: Vectors.MonthlyCase) {
        let subscription = Subscription(
            name: "x",
            price: Money(amountMinor: vector.amountMinor, currency: .usDollar),
            cycle: BillingCycle(unit: CycleUnit(rawValue: vector.unit)!, interval: vector.interval),
            startDate: CivilDate(year: 2026, month: 1, day: 1)!
        )
        #expect(subscription.monthlyCostMinor == vector.expected)
    }

    @Test("Date text", arguments: Vectors.file.dateText)
    func dateText(_ vector: Vectors.DateTextCase) {
        let style: DateText.Style = switch vector.style {
        case "medium": .medium
        case "dayMonth": .dayMonth
        case "monthYear": .monthYear
        default: .shortMonth
        }
        let text = DateText.format(CivilDate(iso: vector.date)!, calendar: CalendarSystem(rawValue: vector.calendar)!,
                                   language: AppLanguage(rawValue: vector.language)!, style: style)
        #expect(text == vector.expected)
    }

    @Test("Relative days", arguments: Vectors.file.relative)
    func relative(_ vector: Vectors.RelativeCase) {
        let text = DateText.relative(CivilDate(iso: vector.date)!, today: CivilDate(iso: vector.today)!, language: AppLanguage(rawValue: vector.language)!)
        #expect(text == vector.expected)
    }

    @Test("Weekday symbols", arguments: Vectors.file.weekdays)
    func weekdays(_ vector: Vectors.WeekdayCase) {
        #expect(DateText.weekdaySymbols(firstWeekday: vector.firstWeekday, language: AppLanguage(rawValue: vector.language)!) == vector.expected)
    }
}
