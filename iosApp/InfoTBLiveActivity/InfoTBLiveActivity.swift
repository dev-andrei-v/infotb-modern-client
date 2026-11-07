import ActivityKit
import AppIntents
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
                        lineType: context.attributes.lineType,
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
                    VStack(spacing: 6) {
                        if state.progress != nil && !state.hasArrived {
                            routeTrack(state: state, color: lineColor, lineType: context.attributes.lineType, onDark: true)
                        }
                        HStack(spacing: 6) {
                            statusLine(state: state, isStale: isStale, textColor: .white.opacity(0.72))
                            Spacer()
                            nextArrivals(state: state, textColor: .white.opacity(0.72))
                            if !state.isTimetable && !state.hasArrived && !isStale {
                                liveIndicator()
                            }
                            stopButton(onDark: true)
                        }
                    }
                    .padding(.horizontal, 8)
                }
            } compactLeading: {
                compactRouteBadge(
                    name: lineName,
                    color: lineColor,
                    lineType: context.attributes.lineType
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

        VStack(spacing: 10) {
            HStack(spacing: 14) {
                lineBadge(name: lineName, color: color, lineType: context.attributes.lineType, size: 48)

                VStack(alignment: .leading, spacing: 3) {
                    Text(stopName)
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
                    statusLine(state: state, isStale: isStale, textColor: .secondary)
                    nextArrivals(state: state, textColor: .secondary)
                }

                Spacer()

                countdownView(
                    state: state,
                    isStale: isStale,
                    large: true
                )

                stopButton(onDark: false)
            }
            if state.progress != nil && !state.hasArrived {
                routeTrack(state: state, color: color, lineType: context.attributes.lineType, onDark: false)
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(color.opacity(0.12))
        )
        .activityBackgroundTint(Color(.systemBackground))
    }

    // MARK: - Route track ("how close is the bus")

    /// A horizontal line with a dot per stop in the window, your stop as the
    /// big dot at the end, and the vehicle sliding along as `progress` grows.
    /// Below it: "4 stops · 1.2 km".
    @ViewBuilder
    private func routeTrack(
        state: TransitActivityAttributes.ContentState,
        color: Color,
        lineType: String,
        onDark: Bool
    ) -> some View {
        let progress = min(max(state.progress ?? 0, 0), 1)
        let windowStops = 5
        let lineColor: Color = onDark ? .white.opacity(0.3) : .secondary.opacity(0.3)
        let dotColor: Color = onDark ? .white.opacity(0.6) : .secondary
        VStack(spacing: 3) {
            GeometryReader { geo in
                let width = geo.size.width
                let trackHeight: CGFloat = 3
                let busSize: CGFloat = 16
                ZStack(alignment: .leading) {
                    // Track
                    Capsule().fill(lineColor)
                        .frame(height: trackHeight)
                    // Travelled part
                    Capsule().fill(color)
                        .frame(width: max(width * progress, trackHeight), height: trackHeight)
                    // Intermediate stop dots
                    ForEach(1..<windowStops, id: \.self) { i in
                        Circle()
                            .fill(dotColor)
                            .frame(width: 6, height: 6)
                            .position(x: width * CGFloat(i) / CGFloat(windowStops), y: geo.size.height / 2)
                    }
                    // Your stop
                    Circle()
                        .strokeBorder(color, lineWidth: 2)
                        .background(Circle().fill(onDark ? Color.black : Color(.systemBackground)))
                        .frame(width: 12, height: 12)
                        .position(x: width - 6, y: geo.size.height / 2)
                    // Vehicle
                    VehicleGlyph(type: lineType, size: 11)
                        .foregroundStyle(color.contrastingText)
                        .frame(width: busSize, height: busSize)
                        .background(Circle().fill(color))
                        .position(x: min(max(width * progress, busSize / 2), width - busSize / 2), y: geo.size.height / 2)
                        .animation(.easeInOut(duration: 0.6), value: progress)
                }
            }
            .frame(height: 18)
            if let stops = state.stopsAway {
                HStack(spacing: 4) {
                    Text(stopsAwayText(stops))
                    if let meters = state.distanceMeters {
                        Text("·")
                        Text(distanceText(meters))
                    }
                    Spacer()
                }
                .font(.caption2)
                .foregroundStyle(onDark ? .white.opacity(0.72) : .secondary)
            }
        }
    }

    @ViewBuilder
    private func nextArrivals(state: TransitActivityAttributes.ContentState, textColor: Color) -> some View {
        if !state.nextMinutes.isEmpty && !state.hasArrived {
            Text(L("apoi ", "then ") + state.nextMinutes.map { "\($0)" }.joined(separator: " · ") + " min")
                .font(.caption2)
                .foregroundStyle(textColor)
                .lineLimit(1)
        }
    }

    private func stopsAwayText(_ stops: Int) -> String {
        if stops == 1 { return L("Urmatoarea statie", "Next stop") }
        return "\(stops) " + L("statii", "stops")
    }

    private func distanceText(_ meters: Int) -> String {
        if meters >= 1000 {
            return String(format: "%.1f km", Double(meters) / 1000)
        }
        return "\(meters) m"
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

    /// A labelled "Stop" pill instead of a bare X, so it reads as "stop tracking".
    @ViewBuilder
    private func stopButton(onDark: Bool) -> some View {
        if #available(iOS 17.0, *) {
            Button(intent: StopTrackingIntent()) {
                HStack(spacing: 4) {
                    Image(systemName: "stop.fill")
                        .font(.system(size: 9, weight: .bold))
                    Text(L("Stop", "Stop"))
                        .font(.caption2.weight(.semibold))
                }
                .foregroundStyle(onDark ? Color.white : Color.primary)
                .padding(.horizontal, 9)
                .padding(.vertical, 5)
                .background(
                    Capsule(style: .continuous)
                        .fill(onDark ? Color.white.opacity(0.18) : Color.primary.opacity(0.08))
                )
            }
            .buttonStyle(.plain)
            .accessibilityLabel(L("Oprește urmărirea", "Stop tracking"))
        }
    }

    @ViewBuilder
    /// Line number on its colour, with the vehicle type glyph above the number inside the badge.
    private func lineBadge(name: String, color: Color, lineType: String, size: CGFloat) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.25, style: .continuous)
                .fill(color)
                .frame(width: size, height: size)
            VStack(spacing: size * 0.04) {
                VehicleGlyph(type: lineType, size: size * 0.3)
                    .foregroundStyle(color.contrastingText.opacity(0.9))
                Text(name)
                    .font(.system(size: size * 0.36, weight: .bold, design: .rounded))
                    .foregroundStyle(color.contrastingText)
                    .minimumScaleFactor(0.5)
                    .lineLimit(1)
                    .padding(.horizontal, 2)
            }
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
    private func compactRouteBadge(name: String, color: Color, lineType: String) -> some View {
        HStack(spacing: 4) {
            VehicleGlyph(type: lineType, size: 11)
                .foregroundStyle(color)
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

// MARK: - Group stop Home Screen widget

private struct HomeWidgetArrival: Codable, Hashable, Identifiable {
    var id: String { "\(lineName)-\(seconds)" }
    let lineName: String
    let color: String
    let transportType: String
    let seconds: Int64
    let isTimetable: Bool
}

private struct HomeWidgetStop: Codable, Hashable, Identifiable {
    var id: Int64
    let name: String
    let observedAtMs: Int64
    let arrivals: [HomeWidgetArrival]
}

private struct HomeWidgetGroup: Codable, Hashable, Identifiable {
    var id: String { name }
    let name: String
    let stops: [HomeWidgetStop]
}

private enum HomeWidgetStore {
    static let kind = "TransitGroupWidget"
    private static let suiteName = "group.ro.infotb.modern"
    private static let snapshotKey = "dashboard.homeWidgetGroups.v1"

    static func groups() -> [HomeWidgetGroup] {
        guard let data = UserDefaults(suiteName: suiteName)?.data(forKey: snapshotKey) else { return [] }
        return (try? JSONDecoder().decode([HomeWidgetGroup].self, from: data)) ?? []
    }

    static func selectedIndex(for groupID: String, count: Int) -> Int {
        guard count > 0 else { return 0 }
        let key = "dashboard.homeWidgetIndex.\(groupID)"
        let stored = UserDefaults(suiteName: suiteName)?.integer(forKey: key) ?? 0
        return ((stored % count) + count) % count
    }

    static func advance(groupID: String) {
        let group = groups().first { $0.name == groupID }
        guard let count = group?.stops.count, count > 1,
              let defaults = UserDefaults(suiteName: suiteName) else { return }
        defaults.set((selectedIndex(for: groupID, count: count) + 1) % count,
                     forKey: "dashboard.homeWidgetIndex.\(groupID)")
    }
}

private struct DashboardGroupEntity: AppEntity, Identifiable {
    static var typeDisplayRepresentation = TypeDisplayRepresentation(name: "Dashboard group")
    static var defaultQuery = DashboardGroupQuery()

    let id: String
    let name: String

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(title: LocalizedStringResource(stringLiteral: name.isEmpty ? "Ungrouped" : name))
    }

    init(id: String, name: String) {
        self.id = id
        self.name = name
    }
}

private struct DashboardGroupQuery: EntityQuery {
    func entities(for identifiers: [String]) async throws -> [DashboardGroupEntity] {
        HomeWidgetStore.groups()
            .filter { identifiers.contains($0.name) }
            .map { DashboardGroupEntity(id: $0.name, name: $0.name) }
    }

    func suggestedEntities() async throws -> [DashboardGroupEntity] {
        HomeWidgetStore.groups()
            .filter { !$0.stops.isEmpty }
            .map { DashboardGroupEntity(id: $0.name, name: $0.name) }
    }
}

private struct DashboardGroupWidgetConfiguration: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Transit group"
    static var description = IntentDescription("Show and rotate through stops in a Dashboard group.")

    @Parameter(title: "Dashboard group") var group: DashboardGroupEntity?
}

private struct RotateDashboardGroupStopIntent: AppIntent {
    static var title: LocalizedStringResource = "Next stop"

    @Parameter(title: "Group") var groupID: String

    init() { groupID = "" }
    init(groupID: String) { self.groupID = groupID }

    func perform() async throws -> some IntentResult {
        HomeWidgetStore.advance(groupID: groupID)
        WidgetCenter.shared.reloadTimelines(ofKind: HomeWidgetStore.kind)
        return .result()
    }
}

private struct GroupStopWidgetEntry: TimelineEntry {
    let date: Date
    let groupID: String
    let groupName: String
    let stop: HomeWidgetStop?
    let arrivals: [(name: String, color: String, seconds: Int, isTimetable: Bool)]
    let stopCount: Int
    let localeCode: String
}

private struct GroupStopWidgetProvider: AppIntentTimelineProvider {
    typealias Intent = DashboardGroupWidgetConfiguration
    typealias Entry = GroupStopWidgetEntry

    func placeholder(in context: Context) -> Entry {
        Entry(date: .now, groupID: "", groupName: "To work", stop: .init(id: 1, name: "Universitate", observedAtMs: 0, arrivals: []),
              arrivals: [("61", "#008F4C", 240, false), ("N105", "#5162A8", 540, false)], stopCount: 3, localeCode: "en")
    }

    func snapshot(for configuration: Intent, in context: Context) async -> Entry {
        makeEntry(configuration, at: .now)
    }

    func timeline(for configuration: Intent, in context: Context) async -> Timeline<Entry> {
        let now = Date.now
        let entries = (0...4).map { minute in
            makeEntry(configuration, at: now.addingTimeInterval(Double(minute) * 60))
        }
        return Timeline(entries: entries, policy: .after(now.addingTimeInterval(300)))
    }

    private func makeEntry(_ configuration: Intent, at date: Date) -> Entry {
        let groups = HomeWidgetStore.groups()
        let requestedGroup = configuration.group?.id
        let group = groups.first { $0.name == requestedGroup } ?? groups.first
        guard let group, !group.stops.isEmpty else {
            let locale = UserDefaults(suiteName: "group.ro.infotb.modern")?.string(forKey: "dashboard.homeWidgetLocale.v1") ?? "ro"
            return Entry(date: date, groupID: "", groupName: locale == "en" ? "Choose a group" : "Alege un grup",
                         stop: nil, arrivals: [], stopCount: 0, localeCode: locale)
        }
        let index = HomeWidgetStore.selectedIndex(for: group.name, count: group.stops.count)
        let stop = group.stops[index]
        let elapsed = max(0, Int64(date.timeIntervalSince1970 * 1000 - Double(stop.observedAtMs)) / 1000)
        let arrivals = stop.arrivals.compactMap { arrival -> (name: String, color: String, seconds: Int, isTimetable: Bool)? in
            let remaining = max(0, arrival.seconds - elapsed)
            guard arrival.seconds >= 0 else { return nil }
            return (arrival.lineName, arrival.color, Int(remaining), arrival.isTimetable)
        }
        let locale = UserDefaults(suiteName: "group.ro.infotb.modern")?.string(forKey: "dashboard.homeWidgetLocale.v1") ?? "ro"
        return Entry(date: date, groupID: group.name, groupName: group.name, stop: stop,
                     arrivals: arrivals, stopCount: group.stops.count, localeCode: locale)
    }
}

struct TransitGroupHomeWidget: Widget {
    static let kind = HomeWidgetStore.kind

    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: Self.kind, intent: DashboardGroupWidgetConfiguration.self,
                               provider: GroupStopWidgetProvider()) { entry in
            TransitGroupWidgetView(entry: entry)
        }
        .configurationDisplayName("Group stop")
        .description("See arrivals at a stop from one of your Dashboard groups.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

private struct TransitGroupWidgetView: View {
    let entry: GroupStopWidgetEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            HStack(spacing: 5) {
                Image(systemName: "tram.fill").foregroundStyle(Color(hex: "#C92335"))
                Text(entry.groupName.isEmpty ? (entry.localeCode == "en" ? "Ungrouped" : "Fără grup") : entry.groupName)
                    .font(.caption.weight(.semibold))
                    .lineLimit(1)
                Spacer(minLength: 0)
                if entry.stopCount > 1 {
                    Button(intent: RotateDashboardGroupStopIntent(groupID: entry.groupID)) {
                        Image(systemName: "arrow.trianglehead.2.clockwise.rotate.90")
                            .font(.caption.weight(.semibold))
                            .frame(width: 28, height: 28)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(entry.localeCode == "en" ? "Next stop" : "Următoarea stație")
                }
            }

            if let stop = entry.stop {
                Text(stop.name)
                    .font(.headline.weight(.bold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)

                if entry.arrivals.isEmpty {
                    Text(entry.localeCode == "en" ? "No arrival data" : "Fără date de sosire")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                } else {
                    VStack(alignment: .leading, spacing: 3) {
                        ForEach(Array(entry.arrivals.prefix(2).enumerated()), id: \.offset) { _, arrival in
                            HStack(spacing: 6) {
                                Text(arrival.name)
                                    .font(.caption.weight(.bold))
                                    .foregroundStyle(Color(hex: arrival.color))
                                    .frame(minWidth: 34, alignment: .leading)
                                Text(arrival.seconds < 60 ? "<1 min" : "\(max(1, arrival.seconds / 60)) min")
                                    .font(.caption.monospacedDigit().weight(.semibold))
                                    .foregroundStyle(arrival.isTimetable ? .secondary : .primary)
                                if arrival.isTimetable {
                                    Image(systemName: "calendar").font(.system(size: 9)).foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                }

                Spacer(minLength: 0)
                Link(destination: groupURL(entry.groupName)) {
                    Label(entry.localeCode == "en" ? "Other stops" : "Alte stații", systemImage: "arrow.up.right")
                        .font(.caption2.weight(.semibold))
                }
                .accessibilityLabel(entry.localeCode == "en" ? "Open this group in InfoTB" : "Deschide grupul în InfoTB")
            } else {
                Spacer(minLength: 0)
                Text(entry.localeCode == "en" ? "Add stops to a Dashboard group first." : "Adaugă mai întâi stații într-un grup din Panou.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 0)
            }
        }
        .padding()
        .containerBackground(for: .widget) { Color(.systemBackground) }
    }

    private func groupURL(_ group: String) -> URL {
        var components = URLComponents()
        components.scheme = "infotb"
        components.host = "dashboard"
        components.path = "/group"
        components.queryItems = [URLQueryItem(name: "name", value: group)]
        return components.url ?? URL(string: "infotb://dashboard/group")!
    }
}
