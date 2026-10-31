import Shared
import SwiftUI

/// A titled run of cards on the dashboard; "" is the ungrouped section.
struct DashboardSection: Identifiable {
    let name: String
    let widgets: [WidgetConfig]
    let schedule: GroupSchedule?
    let isHidden: Bool
    /// Scheduled group outside its active hours.
    let isInactive: Bool
    var id: String { name }
}

@Observable
final class DashboardModel {
    let viewModel = IosServices.shared.dashboardViewModel()
    private let scope = KotlinScope()

    private(set) var widgets: [WidgetConfig] = []
    private(set) var groups: [String] = []
    private(set) var hiddenGroups: Set<String> = []
    private(set) var schedules: [String: GroupSchedule] = [:]
    private(set) var cardStates: [Int64: StopCardState] = [:]
    private(set) var announcements: [Announcement] = []
    private(set) var isRefreshing = false
    private(set) var isEditing = false
    private(set) var lastRefreshMs: Int64 = 0
    private(set) var sortMode: DashboardSort = .groups
    private(set) var hasUserLocation = false
    private(set) var locationLoading = false
    private(set) var locationFailed = false
    private(set) var distances: [Int64: Double] = [:]
    private(set) var timetables: [String: [TimetableHour]] = [:]
    private(set) var timetableLoading: Set<String> = []

    /// Scheduled groups the user opened while they are outside their hours.
    var expandedInactive: Set<String> = []
    /// Dashboard timetables the user unfolded, keyed like the view model's cache.
    var expandedTimetables: Set<String> = []

    var locationSupported: Bool { viewModel.locationSupported }
    var nearestMode: Bool { sortMode == .nearest && locationSupported }
    var visibleCount: Int { widgets.filter { !$0.hidden && !hiddenGroups.contains($0.group) }.count }

    init() {
        scope.own(viewModel)
        let vm = viewModel
        scope.watch(vm.widgets) { [weak self] in self?.widgets = Unbox.list($0); self?.updateDistances() }
        scope.watch(vm.groups) { [weak self] in self?.groups = Unbox.list($0) }
        scope.watch(vm.hiddenGroups) { [weak self] in self?.hiddenGroups = Set(Unbox.list($0) as [String]) }
        scope.watch(vm.groupSchedules) { [weak self] in self?.schedules = Unbox.stringKeyed($0) }
        scope.watch(vm.cardStates) { [weak self] in self?.cardStates = Unbox.longKeyed($0) }
        scope.watch(vm.announcements) { [weak self] in self?.announcements = Unbox.list($0) }
        scope.watch(vm.isRefreshing) { [weak self] in self?.isRefreshing = Unbox.bool($0) }
        scope.watch(vm.isEditing) { [weak self] in self?.isEditing = Unbox.bool($0) }
        scope.watch(vm.lastRefreshMs) { [weak self] in self?.lastRefreshMs = Unbox.int64($0) }
        scope.watch(vm.sortMode) { [weak self] in self?.sortMode = $0 as? DashboardSort ?? .groups }
        scope.watch(vm.userLocation) { [weak self] in self?.hasUserLocation = $0 != nil; self?.updateDistances() }
        scope.watch(vm.stopCoordinates) { [weak self] _ in self?.updateDistances() }
        scope.watch(vm.locationLoading) { [weak self] in self?.locationLoading = Unbox.bool($0) }
        scope.watch(vm.locationFailed) { [weak self] in self?.locationFailed = Unbox.bool($0) }
        scope.watch(vm.lineTimetables) { [weak self] in self?.timetables = Unbox.stringKeyed($0) }
        scope.watch(vm.timetableLoading) { [weak self] in self?.timetableLoading = Unbox.strings($0) }
    }

    private func updateDistances() {
        var result: [Int64: Double] = [:]
        for widget in widgets {
            if let meters = viewModel.distanceTo(stopId: widget.stopId)?.doubleValue {
                result[widget.stopId] = meters
            }
        }
        distances = result
    }

    // MARK: Layout

    /// Cards in display order. Outside edit mode, groups active right now float to
    /// the top and scheduled groups outside their hours sink to the bottom.
    func sections(at date: Date) -> [DashboardSection] {
        if nearestMode {
            let ordered = widgets
                .filter { !$0.hidden || isEditing }
                .sorted { (distances[$0.stopId] ?? .infinity) < (distances[$1.stopId] ?? .infinity) }
            return [DashboardSection(name: "", widgets: ordered, schedule: nil, isHidden: false, isInactive: false)]
        }

        let parts = Calendar.current.dateComponents([.hour, .minute], from: date)
        let minuteOfDay = Int32((parts.hour ?? 0) * 60 + (parts.minute ?? 0))
        let inactive = Set(schedules.filter { !$0.value.isActiveAt(minuteOfDay: minuteOfDay) }.keys)

        var byGroup: [String: [WidgetConfig]] = [:]
        var seen: [String] = []
        for widget in widgets {
            if byGroup[widget.group] == nil { seen.append(widget.group) }
            byGroup[widget.group, default: []].append(widget)
        }
        let order = groups + [""]
        func userIndex(_ name: String) -> Int { order.firstIndex(of: name) ?? order.count }
        func tier(_ name: String) -> Int {
            if isEditing { return 0 }
            if inactive.contains(name) { return 2 }
            return schedules[name] != nil ? 0 : 1
        }

        return seen
            .sorted { (tier($0), userIndex($0)) < (tier($1), userIndex($1)) }
            .map { name in
                DashboardSection(
                    name: name,
                    widgets: byGroup[name] ?? [],
                    schedule: schedules[name],
                    isHidden: hiddenGroups.contains(name),
                    isInactive: schedules[name] != nil && inactive.contains(name)
                )
            }
    }

    /// Whether section headers are worth showing (there is more than the ungrouped section).
    func showsHeaders(_ sections: [DashboardSection]) -> Bool {
        !nearestMode && (sections.count > 1 || sections.first?.name.isEmpty == false)
    }

    func isCollapsed(_ section: DashboardSection) -> Bool {
        section.isInactive && !isEditing && !expandedInactive.contains(section.name)
    }

    func toggleCollapsed(_ section: DashboardSection) {
        if expandedInactive.remove(section.name) == nil { expandedInactive.insert(section.name) }
    }

    // MARK: Card data

    /// Lines a card shows: the ones the user kept, soonest first, out of service last.
    func lines(for widget: WidgetConfig) -> [LineArrivalDetail] {
        let all = cardStates[widget.stopId]?.info?.lines ?? []
        return all
            .filter { widget.visibleLines.isEmpty || widget.visibleLines.contains($0.lineKey) }
            .sorted {
                let a = $0.arrivingTime >= 0 ? 0 : 1, b = $1.arrivingTime >= 0 ? 0 : 1
                return a != b ? a < b : $0.arrivingTime < $1.arrivingTime
            }
    }

    func news(for lines: [LineArrivalDetail]) -> [Announcement] {
        let ids = Set(lines.map(\.id))
        return announcements.filter { notice in
            notice.lines.contains { line in line.id.map { ids.contains($0.int64Value) } ?? false }
        }
    }

    static func timetableKey(stopId: Int64, line: LineArrivalDetail) -> String {
        "\(stopId)_\(line.id)_\(line.direction.value)"
    }

    func toggleTimetable(stopId: Int64, line: LineArrivalDetail) {
        let key = Self.timetableKey(stopId: stopId, line: line)
        if expandedTimetables.remove(key) == nil {
            expandedTimetables.insert(key)
            viewModel.loadLineTimetable(stopId: stopId, lineId: line.id, direction: line.direction)
        }
    }

    // MARK: Actions

    func setActive(_ active: Bool) { viewModel.setScreenActive(active: active) }
    func setSort(_ mode: DashboardSort) { viewModel.setSortMode(mode: mode) }
    func refreshLocation() { viewModel.refreshLocation() }
    func toggleEditing() { viewModel.toggleEditing() }
    func clearAll() { viewModel.clearAll() }

    func refresh() async {
        viewModel.refreshNow()
        // Let the spinner track the actual fetch, within reason.
        for _ in 0..<40 {
            try? await Task.sleep(for: .milliseconds(150))
            if !isRefreshing { break }
        }
    }

    func remove(_ widget: WidgetConfig) { viewModel.removeWidget(id: widget.id) }
    func toggleHidden(_ widget: WidgetConfig) { viewModel.toggleWidgetHidden(id: widget.id) }
    func resize(_ widget: WidgetConfig, to size: WidgetSize) { viewModel.resizeWidget(id: widget.id, size: size) }
    func toggleLine(_ widget: WidgetConfig, key: String) { viewModel.toggleLineVisibility(id: widget.id, lineKey: key) }
    func setGroup(_ widget: WidgetConfig, to group: String) { viewModel.setWidgetGroup(id: widget.id, group: group) }

    /// SwiftUI's onMove destination counts the moved row itself, so it is off by one when moving down.
    func move(in section: DashboardSection, from source: IndexSet, to destination: Int) {
        guard let from = source.first, section.widgets.indices.contains(from) else { return }
        let to = destination > from ? destination - 1 : destination
        viewModel.moveWidgetTo(id: section.widgets[from].id, index: Int32(to))
    }

    func addGroup(_ name: String) { viewModel.addGroup(name: name) }
    func renameGroup(_ old: String, to new: String) { viewModel.renameGroup(oldName: old, newName: new) }
    func removeGroup(_ name: String) { viewModel.removeGroup(name: name) }
    func toggleGroupHidden(_ name: String) { viewModel.toggleGroupHidden(name: name) }
    func setSchedule(_ name: String, _ schedule: GroupSchedule?) { viewModel.setGroupSchedule(name: name, schedule: schedule) }

    func moveGroups(from source: IndexSet, to destination: Int) {
        guard let from = source.first, groups.indices.contains(from) else { return }
        let to = destination > from ? destination - 1 : destination
        viewModel.moveGroupTo(name: groups[from], index: Int32(to))
    }
}

// MARK: - Formatting

enum DashboardFormat {
    /// "07:30" from minutes after midnight.
    static func time(_ minutes: Int32) -> String {
        String(format: "%02d:%02d", minutes / 60, minutes % 60)
    }

    static func schedule(_ schedule: GroupSchedule) -> String {
        "\(time(schedule.fromMinutes))–\(time(schedule.toMinutes))"
    }

    /// "just now", "12s ago", "3 min ago".
    static func ago(_ elapsedMs: Int64) -> String {
        let seconds = max(0, elapsedMs / 1000)
        if seconds < 5 { return tr("time.justNow") }
        if seconds < 60 { return tr("time.secondsAgo").replacingOccurrences(of: "{n}", with: "\(seconds)") }
        return tr("time.minutesAgo").replacingOccurrences(of: "{n}", with: "\(seconds / 60)")
    }

    /// "350 m" or "1.2 km".
    static func distance(_ meters: Double) -> String {
        meters >= 1000 ? String(format: "%.1f km", meters / 1000) : "\(Int(meters)) m"
    }
}
