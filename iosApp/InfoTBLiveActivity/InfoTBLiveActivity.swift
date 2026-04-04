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
            DynamicIsland {
                // EXPANDED Dynamic Island
                DynamicIslandExpandedRegion(.leading) {
                    lineBadge(
                        name: context.attributes.lineName,
                        color: Color(hex: context.attributes.lineColor),
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
                // COMPACT pill — leading
                lineBadge(
                    name: context.attributes.lineName,
                    color: Color(hex: context.attributes.lineColor),
                    size: 26
                )
            } compactTrailing: {
                // COMPACT pill — trailing
                compactCountdown(minutes: context.state.minutes)
            } minimal: {
                // MINIMAL (when multiple islands active)
                ZStack {
                    Circle()
                        .fill(Color(hex: context.attributes.lineColor))
                        .frame(width: 22, height: 22)
                    Text("\(context.state.minutes)")
                        .font(.system(size: 11, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                }
            }
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
            Text("\(minutes)")
                .font(.system(size: large ? 32 : 20, weight: .bold, design: .rounded))
                .foregroundStyle(minutes <= 2 ? .red : .primary)
                .contentTransition(.numericText(value: Double(minutes)))
                .animation(.snappy, value: minutes)
            Text("min")
                .font(.system(size: large ? 11 : 9, weight: .medium))
                .foregroundStyle(.secondary)
                .textCase(.uppercase)
        }
    }

    @ViewBuilder
    private func compactCountdown(minutes: Int) -> some View {
        HStack(spacing: 2) {
            Text("\(minutes)")
                .font(.system(size: 14, weight: .bold, design: .rounded))
                .foregroundStyle(minutes <= 2 ? .red : .white)
                .contentTransition(.numericText(value: Double(minutes)))
            Text("min")
                .font(.system(size: 10, weight: .medium))
                .foregroundStyle(.white.opacity(0.7))
        }
    }

    @ViewBuilder
    private func liveIndicator() -> some View {
        HStack(spacing: 3) {
            Image(systemName: "antenna.radiowaves.left.and.right")
                .font(.system(size: 8))
                .foregroundStyle(.green)
                .symbolEffect(.variableColor.iterative, options: .repeating)
            Text("LIVE")
                .font(.system(size: 9, weight: .bold, design: .rounded))
                .foregroundStyle(.green)
        }
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
        default:
            r = 0; g = 0; b = 0
        }
        self.init(red: r, green: g, blue: b)
    }
}
