import Shared
import SwiftUI

enum BadgeSize {
    case small, medium, large

    var font: Font {
        switch self {
        case .small: .system(size: 11, weight: .bold)
        case .medium: .system(size: 14, weight: .bold)
        case .large: .system(size: 17, weight: .bold)
        }
    }

    var padding: EdgeInsets {
        switch self {
        case .small: EdgeInsets(top: 2, leading: 5, bottom: 2, trailing: 5)
        case .medium: EdgeInsets(top: 4, leading: 8, bottom: 4, trailing: 8)
        case .large: EdgeInsets(top: 6, leading: 10, bottom: 6, trailing: 10)
        }
    }

    var minWidth: CGFloat {
        switch self {
        case .small: 28
        case .medium: 40
        case .large: 52
        }
    }
}

/// A line number on its official colour.
struct LineBadge: View {
    let name: String
    let color: String
    var size: BadgeSize = .medium

    var body: some View {
        let background = Color(hex: color) ?? .lineFallback
        Text(name)
            .font(size.font.monospacedDigit())
            .foregroundStyle(background.contrastingText)
            .lineLimit(1)
            .padding(size.padding)
            .frame(minWidth: size.minWidth)
            .background(background, in: RoundedRectangle(cornerRadius: 7, style: .continuous))
    }
}

/// Minimum width of the big arrival badge so the time column lines up across rows.
let heroArrivalMinWidth: CGFloat = 92

/// Countdown to an arrival: green with a live signal for GPS, grey with a calendar for the timetable.
struct ArrivalBadge: View {
    let seconds: Int64
    var isTimetable = false
    var size: BadgeSize = .medium
    /// Outlined when the vehicle is about to arrive.
    var imminent = false

    @State private var showInfo = false

    private var color: Color { isTimetable ? .scheduled : .realtime }

    var body: some View {
        HStack(spacing: 3) {
            Image(systemName: isTimetable ? "calendar" : "dot.radiowaves.left.and.right")
                .font(.system(size: iconSize, weight: .semibold))
            Text(FormatTimeKt.formatArrivalBadge(seconds: seconds))
                .font(textFont)
                .lineLimit(1)
        }
        .foregroundStyle(color)
        .padding(size.padding)
        .frame(minWidth: size == .large ? heroArrivalMinWidth : nil)
        .background(color.opacity(0.15), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
        .overlay {
            if imminent {
                RoundedRectangle(cornerRadius: 8, style: .continuous).strokeBorder(Color.realtime, lineWidth: 1.5)
            }
        }
        .contentTransition(.numericText())
        .onTapGesture { showInfo = true }
        .popover(isPresented: $showInfo) {
            Label(
                isTimetable ? tr("arrival.scheduledInfo") : tr("arrival.liveInfo"),
                systemImage: isTimetable ? "calendar" : "dot.radiowaves.left.and.right"
            )
            .font(.callout)
            .padding()
            .frame(maxWidth: 280)
            .presentationCompactAdaptation(.popover)
        }
        .accessibilityElement(children: .combine)
    }

    private var iconSize: CGFloat {
        switch size {
        case .small: 9
        case .medium: 12
        case .large: 14
        }
    }

    private var textFont: Font {
        switch size {
        case .small: .system(size: 11, weight: .semibold).monospacedDigit()
        case .medium: .system(size: 13, weight: .semibold).monospacedDigit()
        case .large: .system(size: 16, weight: .bold).monospacedDigit()
        }
    }
}

/// Transport type chip: glyph plus "BUS", "TRAM", "TROLLEY" or "METRO".
struct TransportBadge: View {
    let type: TransportType

    var body: some View {
        Label {
            Text(type.label.uppercased()).font(.caption.weight(.semibold))
        } icon: {
            VehicleGlyph(type: type.name, size: 13)
        }
        .foregroundStyle(type.color)
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(type.color.opacity(0.15), in: Capsule())
    }
}

/// Grey "out of service" pill used where a line has no upcoming arrival.
struct OutOfServicePill: View {
    var body: some View {
        Text(tr("widget.outOfService"))
            .font(.caption2)
            .foregroundStyle(.secondary)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(Color(.tertiarySystemFill), in: Capsule())
    }
}
