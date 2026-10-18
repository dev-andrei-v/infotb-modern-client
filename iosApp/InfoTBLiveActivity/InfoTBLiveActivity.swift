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
            let lineName = displayLineName(context.attributes.lineName)
            let stopName = displayStopName(context.attributes.stopName)
            let state = context.state
            let isStale = context.isStale
            return DynamicIsland {
                // EXPANDED Dynamic Island
                DynamicIslandExpandedRegion(.leading) {
                    lineBadge(
                        name: lineName,
                        color: lineColor,
                        size: 44
                    )
                    .padding(.leading, 4)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    countdownView(
                        state: state,
                        isStale: isStale,
                        large: true,
                        numberColor: .white,
                        unitColor: .white.opacity(0.72)
                    )
                    .padding(.trailing, 4)
                }
                DynamicIslandExpandedRegion(.center) {
                    VStack(spacing: 2) {
                        Text(stopName)
                            .font(.subheadline.weight(.semibold))
                            .lineLimit(1)
                            .foregroundStyle(.white)
                    }
                }
                DynamicIslandExpandedRegion(.bottom) {
                    HStack(spacing: 6) {
                        statusLine(state: state, isStale: isStale, textColor: .white.opacity(0.72))
                        Spacer()
                        if !state.isTimetable && !state.hasArrived && !isStale {
                            liveIndicator()
                        }
                        stopButton()
                    }
                    .padding(.horizontal, 8)
                }
            } compactLeading: {
                compactRouteBadge(
                    name: lineName,
                    color: lineColor
                )
            } compactTrailing: {
                compactCountdown(
                    state: state,
                    isStale: isStale,
                    color: .green
                )
            } minimal: {
                minimalCountdown(
                    state: state,
                    color: isStale ? .gray : (state.isTimetable ? .gray : .green)
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
        let lineName = displayLineName(context.attributes.lineName)
        let stopName = displayStopName(context.attributes.stopName)
        let state = context.state
        let isStale = context.isStale

        HStack(spacing: 14) {
            lineBadge(name: lineName, color: color, size: 48)

            VStack(alignment: .leading, spacing: 3) {
                Text(stopName)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                statusLine(state: state, isStale: isStale, textColor: .secondary)
            }

            Spacer()

            countdownView(
                state: state,
                isStale: isStale,
                large: true
            )

            stopButton()
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(color.opacity(0.12))
        )
        .activityBackgroundTint(Color(.systemBackground))
    }

    // MARK: - Shared Components

    /// "Real-time tracking" / "Scheduled" / "Arrived" / "Updating…" footer.
    @ViewBuilder
    private func statusLine(
        state: TransitActivityAttributes.ContentState,
        isStale: Bool,
        textColor: Color
    ) -> some View {
        HStack(spacing: 4) {
            Circle()
                .fill(statusDotColor(state: state, isStale: isStale))
                .frame(width: 6, height: 6)
            Text(statusText(state: state, isStale: isStale))
                .font(.caption2)
                .foregroundStyle(textColor)
        }
    }

    private func statusDotColor(state: TransitActivityAttributes.ContentState, isStale: Bool) -> Color {
        if isStale { return .gray }
        if state.hasArrived { return .blue }
        return state.isTimetable ? .orange : .green
    }

    private func statusText(state: TransitActivityAttributes.ContentState, isStale: Bool) -> String {
        if isStale { return L("Se actualizează…", "Updating…") }
        if state.hasArrived { return L("A ajuns în stație", "Arrived at the stop") }
        return state.isTimetable ? L("Orar", "Scheduled") : L("Urmărire în timp real", "Real-time tracking")
    }

    @ViewBuilder
    private func stopButton() -> some View {
        if #available(iOS 17.0, *) {
            Button(intent: StopTrackingIntent()) {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 18))
                    .foregroundStyle(.white.opacity(0.85))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(L("Oprește urmărirea", "Stop tracking"))
        }
    }

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
    private func countdownView(
        state: TransitActivityAttributes.ContentState,
        isStale: Bool,
        large: Bool,
        numberColor: Color? = nil,
        unitColor: Color? = nil
    ) -> some View {
        let arrivedNow = state.hasArrived || state.minutes <= 0
        VStack(spacing: 0) {
            if arrivedNow {
                Text(L("Acum", "Now"))
                    .font(.system(size: large ? 24 : 13, weight: .bold, design: .rounded))
                    .foregroundStyle(isStale ? .gray : (numberColor ?? .blue))
                    .minimumScaleFactor(0.6)
                    .lineLimit(1)
            } else {
                countdownNumber(
                    minutes: state.minutes,
                    large: large,
                    foreground: isStale ? .gray : (numberColor ?? (state.minutes <= 2 ? .red : .primary))
                )
                Text("min")
                    .font(.system(size: large ? 11 : 9, weight: .medium))
                    .foregroundStyle(isStale ? .gray : (unitColor ?? .secondary))
                    .textCase(.uppercase)
            }
        }
    }

    @ViewBuilder
    private func compactCountdown(
        state: TransitActivityAttributes.ContentState,
        isStale: Bool,
        color: Color
    ) -> some View {
        let arrivedNow = state.hasArrived || state.minutes <= 0
        let bgColor: Color = isStale ? .gray : (arrivedNow ? .blue : (state.isTimetable ? .gray : color))
        HStack(spacing: 4) {
            Circle()
                .fill(statusDotColor(state: state, isStale: isStale))
                .frame(width: 6, height: 6)
            if arrivedNow {
                Text(L("Acum", "Now"))
                    .font(.system(size: 13, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
            } else {
                countdownNumber(minutes: state.minutes, large: false, foreground: .white)
                Text("min")
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(.white.opacity(0.7))
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(
            Capsule(style: .continuous)
                .fill(bgColor.opacity(0.28))
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
    private func minimalCountdown(state: TransitActivityAttributes.ContentState, color: Color) -> some View {
        let arrivedNow = state.hasArrived || state.minutes <= 0
        Text(arrivedNow ? L("Acum", "Now") : (state.minutes > 99 ? "99" : "\(state.minutes)"))
            .font(.system(size: arrivedNow ? 11 : 14, weight: .bold, design: .rounded))
            .foregroundStyle((arrivedNow ? Color.blue : color).opacity(0.9))
            .minimumScaleFactor(0.5)
            .lineLimit(1)
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

    private func displayLineName(_ value: String) -> String {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? "STB" : trimmed
    }

    private func displayStopName(_ value: String) -> String {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? L("Stație urmărită", "Tracked station") : trimmed
    }

    /// Picks Romanian or English from the device language. The widget
    /// extension can't read the app's own locale setting without an App Group.
    private func L(_ ro: String, _ en: String) -> String {
        let lang = Locale.preferredLanguages.first ?? "en"
        return lang.hasPrefix("ro") ? ro : en
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
