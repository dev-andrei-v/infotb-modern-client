import ActivityKit
import SwiftUI
import WidgetKit

struct InfoTBLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: TransitActivityAttributes.self) { context in
            // LOCK SCREEN / STANDBY banner
            lockScreenView(context: context)
                .widgetURL(deepLinkURL(for: context.attributes))
        } dynamicIsland: { context in
            let lineColor = Color(hex: context.attributes.lineColor)
            DynamicIsland {
                // EXPANDED Dynamic Island
                DynamicIslandExpandedRegion(.leading) {
                    lineBadge(
                        name: context.attributes.lineName,
                        color: lineColor,
                        size: 44
                    )
                }
                DynamicIslandExpandedRegion(.trailing) {
                    countdownView(
                        minutes: context.state.minutes,
                        isTimetable: context.state.isTimetable,
                        large: true
                    )
                }
                DynamicIslandExpandedRegion(.center) {
                    VStack(spacing: 2) {
                        Text(context.attributes.stopName)
                            .font(.subheadline.weight(.semibold))
                            .lineLimit(1)
                            .foregroundStyle(.white)
                    }
                }
                DynamicIslandExpandedRegion(.bottom) {
                    HStack(spacing: 6) {
                        Circle()
                            .fill(context.state.isTimetable ? .orange : .green)
                            .frame(width: 6, height: 6)
                        Text(context.state.isTimetable ? "Scheduled" : "Real-time tracking")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                        Spacer()
                        if !context.state.isTimetable {
                            liveIndicator()
                        }
                    }
                    .padding(.horizontal, 4)
                }
            } compactLeading: {
                compactRouteBadge(
                    name: context.attributes.lineName,
                    color: lineColor
                )
            } compactTrailing: {
                compactCountdown(
                    minutes: context.state.minutes,
                    isTimetable: context.state.isTimetable,
                    color: lineColor
                )
            } minimal: {
                minimalBadge(
                    name: context.attributes.lineName,
                    color: lineColor
                )
            }
            .keylineTint(lineColor.opacity(0.55))
            .widgetURL(deepLinkURL(for: context.attributes))
        }
    }

    // MARK: - Lock Screen Banner

    @ViewBuilder
    private func lockScreenView(context: ActivityViewContext<TransitActivityAttributes>) -> some View {
        let color = Color(hex: context.attributes.lineColor)

        HStack(spacing: 14) {
            lineBadge(name: context.attributes.lineName, color: color, size: 48)

            VStack(alignment: .leading, spacing: 3) {
                Text(context.attributes.stopName)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                HStack(spacing: 4) {
                    Circle()
                        .fill(context.state.isTimetable ? .orange : .green)
                        .frame(width: 5, height: 5)
                    Text(context.state.isTimetable ? "Scheduled" : "Real-time")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }

            Spacer()

            countdownView(
                minutes: context.state.minutes,
                isTimetable: context.state.isTimetable,
                large: true
            )
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(color.opacity(0.12))
        )
        .activityBackgroundTint(Color(.systemBackground))
    }

    // MARK: - Shared Components

    @ViewBuilder
    private func lineBadge(name: String, color: Color, size: CGFloat) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.25, style: .continuous)
                .fill(color)
                .frame(width: size, height: size)
            Text(name)
                .font(.system(size: size * 0.4, weight: .bold, design: .rounded))
                .foregroundStyle(.white)
                .minimumScaleFactor(0.5)
                .lineLimit(1)
                .padding(.horizontal, 2)
        }
    }

    @ViewBuilder
    private func countdownView(minutes: Int, isTimetable: Bool, large: Bool) -> some View {
        VStack(spacing: 0) {
            countdownNumber(minutes: minutes, large: large)
            Text("min")
                .font(.system(size: large ? 11 : 9, weight: .medium))
                .foregroundStyle(.secondary)
                .textCase(.uppercase)
        }
    }

    @ViewBuilder
    private func compactCountdown(minutes: Int, isTimetable: Bool, color: Color) -> some View {
        HStack(spacing: 4) {
            Circle()
                .fill(isTimetable ? .orange : color)
                .frame(width: 6, height: 6)
            countdownNumber(minutes: minutes, large: false, foreground: .white)
            Text("min")
                .font(.system(size: 10, weight: .medium))
                .foregroundStyle(.white.opacity(0.7))
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(
            Capsule(style: .continuous)
                .fill(color.opacity(0.28))
        )
    }

    @ViewBuilder
    private func liveIndicator() -> some View {
        HStack(spacing: 3) {
            Image(systemName: "antenna.radiowaves.left.and.right")
                .font(.system(size: 8))
                .foregroundStyle(.green)
            Text("LIVE")
                .font(.system(size: 9, weight: .bold, design: .rounded))
                .foregroundStyle(.green)
        }
    }

    @ViewBuilder
    private func compactRouteBadge(name: String, color: Color) -> some View {
        HStack(spacing: 4) {
            Circle()
                .fill(color)
                .frame(width: 8, height: 8)
            Text(name)
                .font(.system(size: 13, weight: .bold, design: .rounded))
                .foregroundStyle(.white)
                .lineLimit(1)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(
            Capsule(style: .continuous)
                .fill(color.opacity(0.3))
        )
    }

    @ViewBuilder
    private func minimalBadge(name: String, color: Color) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 6, style: .continuous)
                .fill(color)
                .frame(width: 28, height: 28)
            Text(name)
                .font(.system(size: 11, weight: .bold, design: .rounded))
                .foregroundStyle(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                .padding(.horizontal, 2)
        }
    }

    @ViewBuilder
    private func countdownNumber(minutes: Int, large: Bool, foreground: Color? = nil) -> some View {
        Text("\(minutes)")
            .font(.system(size: large ? 32 : 14, weight: .bold, design: .rounded))
            .foregroundStyle(foreground ?? (minutes <= 2 ? .red : .primary))
    }

    private func deepLinkURL(for attributes: TransitActivityAttributes) -> URL? {
        var components = URLComponents()
        components.scheme = "infotb"
        components.host = "stop"
        components.queryItems = [
            URLQueryItem(name: "stopId", value: String(attributes.stopId)),
            URLQueryItem(name: "lineId", value: String(attributes.lineId)),
            URLQueryItem(name: "direction", value: String(attributes.direction))
        ]
        return components.url
    }
}

// MARK: - Hex Color Extension

extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet(charactersIn: "#"))
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)

        let r, g, b: Double
        switch hex.count {
        case 6:
            r = Double((int >> 16) & 0xFF) / 255.0
            g = Double((int >> 8) & 0xFF) / 255.0
            b = Double(int & 0xFF) / 255.0
        case 3:
            r = Double((int >> 8) & 0xF) / 15.0
            g = Double((int >> 4) & 0xF) / 15.0
            b = Double(int & 0xF) / 15.0
        default:
            r = 0.1; g = 0.46; b = 0.82
        }
        self.init(red: r, green: g, blue: b)
    }
}
