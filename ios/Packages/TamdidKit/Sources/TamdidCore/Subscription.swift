import Foundation

public enum CycleUnit: String, Codable, CaseIterable, Sendable {
    case day, week, month, year
}

/// How often a subscription renews: every `interval` `unit`s.
public struct BillingCycle: Hashable, Codable, Sendable {
    public var unit: CycleUnit
    public var interval: Int

    public init(unit: CycleUnit, interval: Int = 1) {
        self.unit = unit
        self.interval = max(1, interval)
    }

    /// Rejects intervals below 1 from backups: they would make every date the same and never end.
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        unit = try c.decode(CycleUnit.self, forKey: .unit)
        interval = try c.decodeIfPresent(Int.self, forKey: .interval) ?? 1
        guard interval >= 1 else {
            throw DecodingError.dataCorruptedError(forKey: .interval, in: c, debugDescription: "interval must be at least 1")
        }
    }

    public static let weekly = BillingCycle(unit: .week)
    public static let monthly = BillingCycle(unit: .month)
    public static let quarterly = BillingCycle(unit: .month, interval: 3)
    public static let yearly = BillingCycle(unit: .year)

    /// Average number of billing periods per month (e.g. 4.348 for weekly, 1/12 for yearly).
    public var periodsPerMonth: Double {
        let perUnit: Double = switch unit {
        case .day: 365.2425 / 12
        case .week: 52.1775 / 12
        case .month: 1
        case .year: 1.0 / 12
        }
        return perUnit / Double(interval)
    }

    /// The `n`th date of a series that starts at `anchor` (n = 0 is the anchor itself).
    /// Always computed from the anchor, never by repeated addition, so month-end dates recover.
    public func date(_ n: Int, from anchor: CivilDate) -> CivilDate {
        switch unit {
        case .day: anchor.adding(days: n * interval)
        case .week: anchor.adding(days: 7 * n * interval)
        case .month: anchor.adding(months: n * interval)
        case .year: anchor.adding(months: 12 * n * interval)
        }
    }

    /// A safe lower bound for the period index whose date is on or after `date`.
    func approximateIndex(onOrAfter date: CivilDate, from anchor: CivilDate) -> Int {
        let days = anchor.days(until: date)
        guard days > 0 else { return 0 }
        let approxDaysPerPeriod: Double = switch unit {
        case .day: Double(interval)
        case .week: Double(7 * interval)
        case .month: 31.0 * Double(interval)
        case .year: 366.0 * Double(interval)
        }
        return max(0, Int(Double(days) / approxDaysPerPeriod) - 1)
    }
}

public enum SubscriptionCategory: String, Codable, CaseIterable, Sendable, Identifiable {
    case streaming, music, cloud, productivity, ai, education, reading, gaming
    case social, shopping, health, utilities, finance, other

    public var id: String { rawValue }

    public func displayName(_ language: AppLanguage) -> String {
        let index = Self.allCases.firstIndex(of: self)!
        let names: [String] = switch language {
        case .english: ["Video & TV", "Music", "Cloud storage", "Productivity", "AI tools", "Education", "Books & reading",
                        "Gaming", "Social", "Shopping", "Health & fitness", "Internet & phone", "Finance", "Other"]
        case .persian: ["فیلم و سریال", "موسیقی", "فضای ابری", "بهره‌وری", "هوش مصنوعی", "آموزش", "کتاب و مطالعه",
                        "بازی", "شبکه اجتماعی", "خرید", "سلامت و ورزش", "اینترنت و موبایل", "مالی", "سایر"]
        case .arabic: ["أفلام ومسلسلات", "موسيقى", "تخزين سحابي", "إنتاجية", "أدوات الذكاء الاصطناعي", "تعليم", "كتب وقراءة",
                       "ألعاب", "تواصل اجتماعي", "تسوق", "صحة ولياقة", "إنترنت وهاتف", "مالية", "أخرى"]
        case .turkish: ["Film ve dizi", "Müzik", "Bulut depolama", "Verimlilik", "Yapay zekâ araçları", "Eğitim", "Kitap ve okuma",
                        "Oyun", "Sosyal medya", "Alışveriş", "Sağlık ve spor", "İnternet ve telefon", "Finans", "Diğer"]
        case .german: ["Filme & Serien", "Musik", "Cloud-Speicher", "Produktivität", "KI-Tools", "Bildung", "Bücher & Lesen",
                       "Gaming", "Soziale Netzwerke", "Shopping", "Gesundheit & Fitness", "Internet & Telefon", "Finanzen", "Sonstiges"]
        }
        return names[index]
    }
}

public enum SubscriptionStatus: String, Codable, CaseIterable, Sendable {
    case active, paused, cancelled
}

/// A price that applied from `effectiveFrom` until the next change.
public struct PriceChange: Hashable, Codable, Sendable {
    public var effectiveFrom: CivilDate
    public var amountMinor: Int64

    public init(effectiveFrom: CivilDate, amountMinor: Int64) {
        self.effectiveFrom = effectiveFrom
        self.amountMinor = amountMinor
    }
}

public struct Subscription: Identifiable, Hashable, Codable, Sendable {
    public var id: UUID
    public var name: String
    /// The catalog preset this was created from, if any.
    public var catalogID: String?
    public var category: SubscriptionCategory
    /// `#RRGGBB`.
    public var colorHex: String
    /// The current price. Earlier prices live in `priceHistory`.
    public var price: Money
    public var cycle: BillingCycle
    /// The first payment date (or the sign-up date when there is a free trial).
    public var startDate: CivilDate
    /// When set, the first charge happens on this date instead of `startDate`.
    public var trialEndDate: CivilDate?
    public var status: SubscriptionStatus
    /// The day the subscription was paused or cancelled. Charges on this day still count.
    public var endDate: CivilDate?
    /// `nil` turns reminders off.
    public var reminderDaysBefore: Int?
    public var paymentMethod: String?
    public var url: String?
    public var notes: String?
    /// Earlier prices, oldest first, each with the day it started. Empty when the price never changed.
    public var priceHistory: [PriceChange]
    /// The day the current `price` took effect; `nil` means since the start.
    public var currentPriceSince: CivilDate?
    /// Spending carried over from before the last resume, so pauses are not billed.
    public var carriedSpendMinor: Int64
    public var carriedPaymentCount: Int
    public var createdAt: Date

    public init(
        id: UUID = UUID(),
        name: String,
        catalogID: String? = nil,
        category: SubscriptionCategory = .other,
        colorHex: String = "#5B4BDB",
        price: Money,
        cycle: BillingCycle = .monthly,
        startDate: CivilDate,
        trialEndDate: CivilDate? = nil,
        status: SubscriptionStatus = .active,
        endDate: CivilDate? = nil,
        reminderDaysBefore: Int? = 1,
        paymentMethod: String? = nil,
        url: String? = nil,
        notes: String? = nil,
        priceHistory: [PriceChange] = [],
        currentPriceSince: CivilDate? = nil,
        carriedSpendMinor: Int64 = 0,
        carriedPaymentCount: Int = 0,
        createdAt: Date = Date()
    ) {
        self.id = id
        self.name = name
        self.catalogID = catalogID
        self.category = category
        self.colorHex = colorHex
        self.price = price
        self.cycle = cycle
        self.startDate = startDate
        self.trialEndDate = trialEndDate
        self.status = status
        self.endDate = endDate
        self.reminderDaysBefore = reminderDaysBefore
        self.paymentMethod = paymentMethod
        self.url = url
        self.notes = notes
        self.priceHistory = priceHistory
        self.currentPriceSince = currentPriceSince
        self.carriedSpendMinor = carriedSpendMinor
        self.carriedPaymentCount = carriedPaymentCount
        // Whole seconds, so the value survives an ISO-8601 round trip unchanged.
        self.createdAt = Date(timeIntervalSince1970: createdAt.timeIntervalSince1970.rounded(.down))
    }

    // Tolerant decoding: fields added in later versions get defaults, so old backups still import.
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(UUID.self, forKey: .id)
        name = try c.decode(String.self, forKey: .name)
        catalogID = try c.decodeIfPresent(String.self, forKey: .catalogID)
        category = (try? c.decode(SubscriptionCategory.self, forKey: .category)) ?? .other
        colorHex = try c.decodeIfPresent(String.self, forKey: .colorHex) ?? "#5B4BDB"
        price = try c.decode(Money.self, forKey: .price)
        cycle = try c.decodeIfPresent(BillingCycle.self, forKey: .cycle) ?? .monthly
        startDate = try c.decode(CivilDate.self, forKey: .startDate)
        trialEndDate = try c.decodeIfPresent(CivilDate.self, forKey: .trialEndDate)
        status = (try? c.decode(SubscriptionStatus.self, forKey: .status)) ?? .active
        endDate = try c.decodeIfPresent(CivilDate.self, forKey: .endDate)
        reminderDaysBefore = try c.decodeIfPresent(Int.self, forKey: .reminderDaysBefore)
        paymentMethod = try c.decodeIfPresent(String.self, forKey: .paymentMethod)
        url = try c.decodeIfPresent(String.self, forKey: .url)
        notes = try c.decodeIfPresent(String.self, forKey: .notes)
        priceHistory = try c.decodeIfPresent([PriceChange].self, forKey: .priceHistory) ?? []
        currentPriceSince = try c.decodeIfPresent(CivilDate.self, forKey: .currentPriceSince)
        carriedSpendMinor = try c.decodeIfPresent(Int64.self, forKey: .carriedSpendMinor) ?? 0
        carriedPaymentCount = try c.decodeIfPresent(Int.self, forKey: .carriedPaymentCount) ?? 0
        createdAt = try c.decodeIfPresent(Date.self, forKey: .createdAt) ?? Date(timeIntervalSince1970: 0)
    }
}
