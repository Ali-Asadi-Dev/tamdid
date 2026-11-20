/// Billing rules. Everything here is a pure function of the subscription and a date,
/// which is what makes the rules cheap to test and identical across platforms.
extension Subscription {
    /// The first charge date: the trial end if there is one, otherwise the start date.
    public var anchorDate: CivilDate { trialEndDate ?? startDate }

    public var isActive: Bool { status == .active }

    /// Whether `date` is still inside the free trial.
    public func isInTrial(on date: CivilDate) -> Bool {
        guard let trialEndDate else { return false }
        return date < trialEndDate
    }

    /// The last day a charge can happen, or `nil` for no limit.
    /// A paused or cancelled subscription without an end date never charges.
    private var chargeLimit: CivilDate? {
        if status == .active { return endDate }
        return endDate ?? anchorDate.adding(days: -1)
    }

    /// All charge dates in `from...to`, oldest first.
    public func charges(from: CivilDate, to: CivilDate) -> [CivilDate] {
        var upper = to
        if let limit = chargeLimit, limit < upper { upper = limit }
        guard from <= upper else { return [] }

        var result: [CivilDate] = []
        var n = cycle.approximateIndex(onOrAfter: from, from: anchorDate)
        // The approximation may undershoot; walk forward to the first date in range.
        while true {
            let date = cycle.date(n, from: anchorDate)
            if date > upper { break }
            if date >= from { result.append(date) }
            n += 1
        }
        return result
    }

    /// The first charge on or after `date`, or `nil` if the subscription will not charge again.
    public func nextRenewal(onOrAfter date: CivilDate) -> CivilDate? {
        if let limit = chargeLimit, limit < date { return nil }
        var n = cycle.approximateIndex(onOrAfter: date, from: anchorDate)
        while true {
            let candidate = cycle.date(n, from: anchorDate)
            if candidate >= date {
                if let limit = chargeLimit, candidate > limit { return nil }
                return candidate
            }
            n += 1
        }
    }

    /// The price that applied on `date`.
    public func price(on date: CivilDate) -> Int64 {
        if let since = currentPriceSince, date < since {
            return priceHistory.last(where: { $0.effectiveFrom <= date })?.amountMinor
                ?? priceHistory.first?.amountMinor
                ?? price.amountMinor
        }
        return price.amountMinor
    }

    /// Records a new price effective from `date`. Charges before `date` keep the old price.
    public mutating func changePrice(to amountMinor: Int64, effectiveFrom date: CivilDate) {
        guard amountMinor != price.amountMinor else { return }
        let previousSince = currentPriceSince ?? startDate
        if date > previousSince {
            priceHistory.append(PriceChange(effectiveFrom: previousSince, amountMinor: price.amountMinor))
            currentPriceSince = date
        }
        // A change dated on or before the current price's start simply corrects it.
        price.amountMinor = amountMinor
    }

    /// Total paid up to and including `date`, using the price that applied at each charge.
    public func totalSpent(asOf date: CivilDate) -> Int64 {
        carriedSpendMinor + charges(from: anchorDate, to: date).reduce(0) { $0 + price(on: $1) }
    }

    /// Number of payments up to and including `date`.
    public func paymentCount(asOf date: CivilDate) -> Int {
        carriedPaymentCount + charges(from: anchorDate, to: date).count
    }

    /// The current price spread over an average month, in minor units, rounded half up.
    public var monthlyCostMinor: Int64 {
        Self.roundHalfUp(Double(price.amountMinor) * cycle.periodsPerMonth)
    }

    public var yearlyCostMinor: Int64 {
        Self.roundHalfUp(Double(price.amountMinor) * cycle.periodsPerMonth * 12)
    }

    // MARK: - Lifecycle

    public mutating func pause(on date: CivilDate) {
        guard status == .active else { return }
        status = .paused
        endDate = date
    }

    /// Cancels the subscription. A paused one keeps its pause date as the last charge day.
    public mutating func cancel(on date: CivilDate) {
        if status == .active || endDate == nil { endDate = date }
        status = .cancelled
    }

    /// Resumes a paused or cancelled subscription with the next charge on `date`.
    /// Spending so far is carried over so the paused period is not billed.
    public mutating func resume(on date: CivilDate) {
        guard status != .active else { return }
        let lastDay = endDate ?? date.adding(days: -1)
        carriedSpendMinor = totalSpent(asOf: lastDay)
        carriedPaymentCount = paymentCount(asOf: lastDay)
        status = .active
        endDate = nil
        trialEndDate = nil
        startDate = date
        priceHistory = []
        currentPriceSince = nil
    }

    static func roundHalfUp(_ value: Double) -> Int64 {
        Int64((value + 0.5).rounded(.down))
    }
}
