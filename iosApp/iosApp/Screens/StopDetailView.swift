import Shared
import SwiftUI

@Observable
final class StopDetailModel {
    let viewModel = IosServices.shared.stopDetailViewModel()
    private let scope = KotlinScope()

    let stopId: Int64
    let primaryLineId: Int64?

    private(set) var info: StopInfo?
    private(set) var isLoading = false
    private(set) var timetables: [String: [TimetableHour]] = [:]
    private(set) var timetableLoading: Set<String> = []
    private(set) var coordinates: Stop?
    var expandedTimetables: Set<String> = []

    init(stopId: Int64, lineId: Int64?, direction: Direction?) {
        self.stopId = stopId
        self.primaryLineId = lineId
        scope.own(viewModel)
        scope.watch(viewModel.stopInfo) { [weak self] in self?.info = $0 as? StopInfo }
        scope.watch(viewModel.isLoading) { [weak self] in self?.isLoading = Unbox.bool($0) }
        scope.watch(viewModel.lineTimetables) { [weak self] in self?.timetables = Unbox.stringKeyed($0) }
        scope.watch(viewModel.timetableLoading) { [weak self] in self?.timetableLoading = Unbox.strings($0) }
        scope.watch(viewModel.stopCoordinates) { [weak self] in self?.coordinates = $0 as? Stop }
        viewModel.loadStop(stopId: stopId, lineId: lineId.kotlin, direction: direction)
        // Brings the Dynamic Island up to date when visiting a tracked stop.
        AppModel.shared.refreshTracking()
    }

    /// The line this screen was opened for first, then by arrival, out of service last.
    var sortedLines: [LineArrivalDetail] {
        (info?.lines ?? []).sorted { a, b in
            let pa = a.id == primaryLineId ? 0 : 1, pb = b.id == primaryLineId ? 0 : 1
            if pa != pb { return pa < pb }
            let sa = a.arrivingTime >= 0 ? 0 : 1, sb = b.arrivingTime >= 0 ? 0 : 1
            if sa != sb { return sa < sb }
            return a.arrivingTime < b.arrivingTime
        }
    }

    func timetable(for line: LineArrivalDetail) -> [TimetableHour]? {
        line.timetable.isEmpty ? timetables[line.lineKey] : line.timetable
    }

    func toggleTimetable(_ line: LineArrivalDetail) {
        let key = line.lineKey
        if expandedTimetables.remove(key) == nil {
            expandedTimetables.insert(key)
            if line.timetable.isEmpty, timetables[key] == nil {
                viewModel.loadLineTimetable(lineId: line.id, direction: line.direction)
            }
        }
    }

    func refresh() async {
        viewModel.refreshNow()
        AppModel.shared.refreshTracking()
        try? await Task.sleep(for: .milliseconds(600))
    }
}

struct StopDetailView: View {
    let stopId: Int64
    let lineId: Int64?
    let direction: Direction?

    var body: some View {
        ModelHost(make: { StopDetailModel(stopId: stopId, lineId: lineId, direction: direction) }) { model in
            StopDetailContent(model: model)
        }
    }
}

private struct StopDetailContent: View {
    @Bindable var model: StopDetailModel
    private let app = AppModel.shared

    var body: some View {
        Group {
            if let info = model.info {
                list(info)
            } else if model.isLoading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ContentUnavailableView {
                    Label(tr("stopDetail.notFound"), systemImage: "mappin.slash")
                } actions: {
                    // The first fetch can fail on a cold start (e.g. opened from the Live Activity)
                    Button(tr("common.retry")) { Task { await model.refresh() } }
                        .buttonStyle(.borderedProminent)
                }
            }
        }
        .navigationTitle(model.info?.name ?? tr("stopDetail.arrivals"))
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            if let info = model.info {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        app.addToDashboard(stopId: model.stopId, stopName: info.name)
                    } label: {
                        Label(tr("stopDetail.addToDashboardLong"), systemImage: "plus.square.on.square")
                    }
                }
            }
        }
    }

    private func list(_ info: StopInfo) -> some View {
        List {
            if model.coordinates != nil || !(info.address ?? "").isEmpty {
                Section {
                    if let stop = model.coordinates {
                        StopMap(latitude: stop.lat, longitude: stop.lng, label: info.name)
                            .frame(height: 180)
                            .listRowInsets(EdgeInsets())
                    }
                    if let address = info.address, !address.isEmpty {
                        Label(address, systemImage: "mappin.and.ellipse")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }
            }

            Section {
                if info.lines.isEmpty {
                    Text(tr("stopDetail.noLines")).foregroundStyle(.secondary)
                }
                ForEach(model.sortedLines, id: \.lineKey) { line in
                    row(line, info: info)
                        .listRowBackground(
                            line.id == model.primaryLineId
                                ? (Color(hex: line.color) ?? .clear).opacity(0.08)
                                : Color(.secondarySystemGroupedBackground)
                        )
                }
            } header: {
                Text("\(info.lines.count) \(tr("stopDetail.lines"))")
            } footer: {
                Button {
                    app.addToDashboard(stopId: model.stopId, stopName: info.name)
                } label: {
                    Label(tr("stopDetail.addToDashboardLong"), systemImage: "plus")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .padding(.top, 8)
            }
        }
        .listStyle(.insetGrouped)
        .refreshable { await model.refresh() }
    }

    private func row(_ line: LineArrivalDetail, info: StopInfo) -> some View {
        let key = line.lineKey
        let subscription = app.trackingSubscription(stopId: model.stopId, line: line)
        return ArrivalRow(
            line: line,
            followUpCount: 3,
            isTracking: subscription != nil,
            timetableExpanded: model.expandedTimetables.contains(key),
            timetableLoading: model.timetableLoading.contains(key),
            onLineTap: { app.push(.line(line.id)) },
            onTimetable: { model.toggleTimetable(line) },
            onBell: {
                if let subscription {
                    app.stopTracking(subscription)
                } else {
                    app.track(stopId: model.stopId, stopName: info.name, line: line, minutes: Int(line.arrivingTime / 60))
                }
            }
        ) {
            if model.timetableLoading.contains(key) {
                ProgressView().frame(maxWidth: .infinity).padding(.vertical, 12)
            } else if let timetable = model.timetable(for: line), !timetable.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text("\(tr("stopDetail.timetableTitle")) \(line.name)")
                        .font(.subheadline.weight(.semibold))
                    TimetableGrid(timetable: timetable)
                }
            } else {
                Text(tr("widget.noData")).font(.caption).foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
}
