import Foundation

enum Money {
    private static let formatter: NumberFormatter = {
        let f = NumberFormatter()
        f.locale = Locale(identifier: "en_US")
        f.numberStyle = .decimal
        f.maximumFractionDigits = 0
        return f
    }()

    /// ₫1,240,000 — shown without a sign, like the web app; the context says spent or received.
    static func format(_ amount: Decimal) -> String {
        let value = amount < 0 ? -amount : amount
        return "₫" + (formatter.string(from: NSDecimalNumber(decimal: value)) ?? "\(value)")
    }

    /// VND has no minor unit, so keep only the digits: "45,000", "45.000" and "45000" all mean 45000.
    static func parse(_ text: String) -> Decimal? {
        let digits = text.filter(\.isNumber)
        guard !digits.isEmpty, let value = Decimal(string: digits), value > 0 else { return nil }
        return value
    }
}

enum DateText {
    private static let wire: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    private static let month: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM"
        return f
    }()

    /// The backend's LocalDate format.
    static func wireString(_ date: Date) -> String { wire.string(from: date) }
    static func monthString(_ date: Date) -> String { month.string(from: date) }

    /// "2026-11-24" -> "24 Nov 2026"; falls back to the raw text.
    static func display(_ wireDate: String) -> String {
        guard let date = wire.date(from: wireDate) else { return wireDate }
        return date.formatted(.dateTime.day().month(.abbreviated).year())
    }
}
