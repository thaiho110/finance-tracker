import SwiftUI

extension Color {
    /// "#8B5E3C" -> Color. Returns nil for anything that isn't a 6-digit hex colour.
    init?(hex: String?) {
        guard var text = hex?.trimmingCharacters(in: .whitespaces), !text.isEmpty else { return nil }
        if text.hasPrefix("#") { text.removeFirst() }
        guard text.count == 6, let value = UInt32(text, radix: 16) else { return nil }
        self.init(
            red: Double((value >> 16) & 0xFF) / 255,
            green: Double((value >> 8) & 0xFF) / 255,
            blue: Double(value & 0xFF) / 255
        )
    }
}

extension Decimal {
    var double: Double { NSDecimalNumber(decimal: self).doubleValue }
}

/// SF Symbols and colours for categories (the backend stores a colour, but no icon for the defaults).
enum CategoryStyle {
    static func symbol(name: String, kind: CategoryKind) -> String {
        switch name.lowercased() {
        case "coffee shop": return "cup.and.saucer.fill"
        case "fast food": return "takeoutbag.and.cup.and.straw.fill"
        case "dining": return "fork.knife"
        case "groceries": return "basket.fill"
        case "transportation": return "car.fill"
        case "travel": return "airplane"
        case "online shopping": return "shippingbox.fill"
        case "shopping": return "bag.fill"
        case "entertainment": return "film.fill"
        case "subscriptions": return "arrow.triangle.2.circlepath"
        case "utilities": return "bolt.fill"
        case "health": return "cross.case.fill"
        case "housing": return "house.fill"
        case "education": return "graduationcap.fill"
        default:
            switch kind {
            case .income: return "arrow.down.circle.fill"
            case .transfer: return "arrow.left.arrow.right"
            default: return "tag.fill"
            }
        }
    }

    static func color(_ hex: String?) -> Color { Color(hex: hex) ?? .gray }
}

/// A category's symbol on a soft tinted rounded square, like the icons in Wallet and Health.
struct CategoryIcon: View {
    let name: String
    let kind: CategoryKind
    let colorHex: String?
    var size: CGFloat = 38

    var body: some View {
        let tint = CategoryStyle.color(colorHex)
        Image(systemName: CategoryStyle.symbol(name: name, kind: kind))
            .font(.system(size: size * 0.45, weight: .semibold))
            .foregroundStyle(tint)
            .frame(width: size, height: size)
            .background(tint.opacity(0.15), in: RoundedRectangle(cornerRadius: size * 0.3, style: .continuous))
            .accessibilityHidden(true)
    }
}
