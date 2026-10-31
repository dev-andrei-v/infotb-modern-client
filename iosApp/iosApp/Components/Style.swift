import Shared
import SwiftUI

extension Color {
    /// Parses "#RRGGBB" from the STB API; nil when it isn't a colour.
    init?(hex: String) {
        let cleaned = hex.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "#", with: "")
        guard cleaned.count == 6, let value = UInt32(cleaned, radix: 16) else { return nil }
        self.init(
            red: Double((value >> 16) & 0xFF) / 255,
            green: Double((value >> 8) & 0xFF) / 255,
            blue: Double(value & 0xFF) / 255
        )
    }

    static let realtime = Color(red: 0x43 / 255, green: 0xA0 / 255, blue: 0x47 / 255)
    static let scheduled = Color(red: 0x7E / 255, green: 0x8C / 255, blue: 0xA0 / 255)
    static let night = Color(red: 0x5C / 255, green: 0x6B / 255, blue: 0xC0 / 255)
    static let favorite = Color(red: 1, green: 0xC1 / 255, blue: 0x07 / 255)
    static let lineFallback = Color(red: 0x1E / 255, green: 0x88 / 255, blue: 0xE5 / 255)
}

extension TransportType {
    var color: Color {
        switch self {
        case .tram: Color(red: 0xE5 / 255, green: 0x39 / 255, blue: 0x35 / 255)
        case .cableCar: Color(red: 0x43 / 255, green: 0xA0 / 255, blue: 0x47 / 255)
        case .subway: Color(red: 0xF5 / 255, green: 0x7C / 255, blue: 0x00 / 255)
        default: Color.lineFallback
        }
    }

    var label: String { tr("transport.\(name)") }

    /// Display order in the lines list, matching Android.
    static let ordered: [TransportType] = [.bus, .tram, .cableCar, .subway]
}

extension LineArrivalDetail {
    /// "lineId_direction": a line in one direction, unique at a stop.
    var lineKey: String { "\(id)_\(direction.value)" }
}

/// Colour of the line arriving next, falling back to the stop's dominant transport type.
func cardAccent(for lines: [LineArrivalDetail], sorted: [LineArrivalDetail]) -> Color {
    if let next = sorted.first(where: { $0.arrivingTime >= 0 }), let color = Color(hex: next.color) {
        return color
    }
    let counts = Dictionary(grouping: lines, by: { $0.type.name }).mapValues(\.count)
    if let top = counts.max(by: { $0.value < $1.value })?.key {
        return TransportType.Companion.shared.fromString(value: top).color
    }
    return .accentColor
}

// MARK: - Liquid Glass

extension View {
    /// Liquid Glass on iOS 26+, a translucent material before that.
    @ViewBuilder
    func glassBackground<S: Shape>(in shape: S, interactive: Bool = false) -> some View {
        if #available(iOS 26.0, *) {
            self.glassEffect(interactive ? .regular.interactive() : .regular, in: shape)
        } else {
            self.background(.regularMaterial, in: shape)
        }
    }

    /// Tinted Liquid Glass for small status chips.
    @ViewBuilder
    func tintedGlass<S: Shape>(_ tint: Color, in shape: S) -> some View {
        if #available(iOS 26.0, *) {
            self.glassEffect(.regular.tint(tint.opacity(0.25)), in: shape)
        } else {
            self.background(tint.opacity(0.15), in: shape)
        }
    }
}

/// The background shared by every card: grouped-list colour with a continuous corner.
struct CardBackground: ViewModifier {
    var radius: CGFloat = 18

    func body(content: Content) -> some View {
        content
            .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: radius, style: .continuous))
    }
}

extension View {
    func card(radius: CGFloat = 18) -> some View { modifier(CardBackground(radius: radius)) }
}
