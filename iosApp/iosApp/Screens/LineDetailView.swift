import Shared
import SwiftUI

@Observable
final class LineDetailModel {
    let viewModel = IosServices.shared.lineDetailViewModel()
    private let scope = KotlinScope()

    private(set) var detail: LineDetail?
    private(set) var vehicles: [VehiclePosition] = []
    private(set) var direction: Direction = .tur
    private(set) var isLoading = false
    private(set) var announcements: [Announcement] = []

    init(lineId: Int64, direction: Direction = .tur) {
        scope.own(viewModel)
        scope.watch(viewModel.lineDetail) { [weak self] in self?.detail = $0 as? LineDetail }
        scope.watch(viewModel.vehicles) { [weak self] in self?.vehicles = Unbox.list($0) }
        scope.watch(viewModel.direction) { [weak self] in self?.direction = $0 as? Direction ?? .tur }
        scope.watch(viewModel.isLoading) { [weak self] in self?.isLoading = Unbox.bool($0) }
        scope.watch(viewModel.announcements) { [weak self] in self?.announcements = Unbox.list($0) }
        // Before loadLine, so the first fetch is already for the right direction.
        if direction != .tur { viewModel.setDirection(direction: direction) }
        viewModel.loadLine(lineId: lineId)
    }

    func setDirection(_ direction: Direction) { viewModel.setDirection(direction: direction) }
}

struct LineDetailView: View {
    let lineId: Int64

    var body: some View {
        ModelHost(make: { LineDetailModel(lineId: lineId) }) { model in
            LineDetailContent(model: model)
        }
    }
}

private struct LineDetailContent: View {
    let model: LineDetailModel

    var body: some View {
        Group {
            if let detail = model.detail {
                list(detail)
            } else if model.isLoading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ContentUnavailableView(tr("lineDetail.notFound"), systemImage: "point.topleft.down.to.point.bottomright.curvepath")
            }
        }
        .navigationTitle("\(tr("lineDetail.linePrefix")) \(model.detail?.name ?? "")")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func list(_ detail: LineDetail) -> some View {
        List {
            Section {
                HStack(spacing: 10) {
                    LineBadge(name: detail.name, color: detail.color, size: .large)
                    TransportBadge(type: detail.type)
                    Spacer()
                    VStack(alignment: .trailing, spacing: 4) {
                        Text("\(detail.stops.count) \(tr("lineDetail.stations"))")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        if !model.vehicles.isEmpty {
                            LiveVehiclesPill(count: model.vehicles.count)
                        }
                    }
                }

                Picker(tr("lineDetail.direction"), selection: Binding(get: { model.direction }, set: { model.setDirection($0) })) {
                    Text(tr("direction.tur")).tag(Direction.tur)
                    Text(tr("direction.retur")).tag(Direction.retur)
                }
                .pickerStyle(.segmented)

                if let towards = model.direction == .tur ? detail.directionNameTur : detail.directionNameRetur, !towards.isEmpty {
                    Label(towards, systemImage: "arrow.right")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }

                Button {
                    AppModel.shared.push(.lineMap(detail.id, direction: model.direction))
                } label: {
                    Label(tr("lineDetail.showMap"), systemImage: "map.fill")
                        .labelStyle(.titleAndIcon)
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
            }

            if let notice = model.announcements.first {
                Section {
                    Button {
                        AppModel.shared.selectedTab = .news
                    } label: {
                        Label {
                            Text(notice.title).font(.subheadline).lineLimit(2)
                        } icon: {
                            Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.orange)
                        }
                    }
                    .foregroundStyle(.primary)
                }
            }

            Section(tr("lineDetail.stops")) {
                ForEach(Array(detail.stops.enumerated()), id: \.element.id) { index, stop in
                    NavigationLink(value: Route.stop(stop.id, lineId: detail.id, direction: model.direction)) {
                        StopTimelineRow(
                            index: index,
                            stop: stop,
                            color: Color(hex: detail.color) ?? .lineFallback,
                            isFirst: index == 0,
                            isLast: index == detail.stops.count - 1,
                            vehicles: model.vehicles
                        )
                    }
                    .listRowSeparator(.hidden)
                    .listRowInsets(EdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16))
                }
            }
        }
        .listStyle(.insetGrouped)
    }
}

/// One stop on the line, drawn as a numbered dot on a vertical track.
private struct StopTimelineRow: View {
    let index: Int
    let stop: Stop
    let color: Color
    let isFirst: Bool
    let isLast: Bool
    let vehicles: [VehiclePosition]

    var body: some View {
        let nearest = vehicles.map { Self.distanceKm(stop, $0) }.min() ?? .infinity
        let veryClose = nearest < 0.05
        let approaching = nearest < 0.2

        HStack(spacing: 12) {
            VStack(spacing: 0) {
                Rectangle().fill(isFirst ? .clear : color.opacity(0.35)).frame(width: 3)
                Text("\(index + 1)")
                    .font(.caption2.weight(.bold).monospacedDigit())
                    .foregroundStyle(color.contrastingText)
                    .frame(width: 26, height: 26)
                    .background(color, in: Circle())
                Rectangle().fill(isLast ? .clear : color.opacity(0.35)).frame(width: 3)
            }
            .frame(width: 26)

            VStack(alignment: .leading, spacing: 2) {
                Text(stop.name)
                    .font(.body.weight(.medium))
                    .lineLimit(1)
                if let description = stop.description_, !description.isEmpty {
                    Text(description)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
            .padding(.vertical, 10)

            Spacer(minLength: 4)

            if approaching {
                Label(
                    veryClose ? tr("lineDetail.vehicleClose") : tr("lineDetail.vehicleApproaching"),
                    systemImage: "circle.fill"
                )
                .labelStyle(.titleAndIcon)
                .font(.caption2.weight(.semibold))
                .foregroundStyle(veryClose ? Color.realtime : .orange)
            }
        }
    }

    /// Flat-earth distance in km; plenty for "is a vehicle near this stop".
    static func distanceKm(_ stop: Stop, _ vehicle: VehiclePosition) -> Double {
        let dLat = (vehicle.lat - stop.lat) * 111
        let dLng = (vehicle.lng - stop.lng) * 111 * cos(stop.lat * .pi / 180)
        return (dLat * dLat + dLng * dLng).squareRoot()
    }
}

/// The route on a full-screen map, vehicles refreshing live. It is pushed
/// like any page, so coming back from a stop returns to the same view.
struct LiveMapView: View {
    let lineId: Int64
    let direction: Direction

    var body: some View {
        ModelHost(make: { LineDetailModel(lineId: lineId, direction: direction) }) { model in
            LiveMapScreen(model: model)
        }
        .toolbar(.hidden, for: .navigationBar)
        .toolbar(.hidden, for: .tabBar)
    }
}

private struct LiveMapScreen: View {
    let model: LineDetailModel
    @Environment(\.dismiss) private var dismiss
    @State private var selectedStopId: Int64?

    var body: some View {
        Group {
            if let detail = model.detail {
                RouteMap(
                    stops: detail.stops,
                    vehicles: model.vehicles,
                    lineColor: detail.color,
                    lineType: detail.type,
                    segmentPath: detail.segmentPath,
                    selectedStopId: $selectedStopId
                )
                .overlay(alignment: .bottom) {
                    if let index = detail.stops.firstIndex(where: { $0.id == selectedStopId }) {
                        MapStopCard(detail: detail, stop: detail.stops[index], index: index, direction: model.direction) {
                            selectedStopId = nil
                        }
                        .id(detail.stops[index].id)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                    }
                }
            } else {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .ignoresSafeArea(edges: .horizontal)
        .ignoresSafeArea(edges: .top)
        .overlay(alignment: .top) { header }
        .animation(.snappy, value: selectedStopId)
    }

    private var header: some View {
        HStack(spacing: 10) {
            Button {
                dismiss()
            } label: {
                Image(systemName: "chevron.left")
                    .font(.headline)
                    .frame(width: 44, height: 44)
                    .glassBackground(in: Circle(), interactive: true)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(tr("stopDetail.back"))

            if let detail = model.detail {
                HStack(spacing: 8) {
                    LineBadge(name: detail.name, color: detail.color)
                    if let towards = model.direction == .tur ? detail.directionNameTur : detail.directionNameRetur,
                       !towards.isEmpty {
                        Image(systemName: "arrow.right")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(.secondary)
                        Text(towards)
                            .font(.subheadline.weight(.semibold))
                            .lineLimit(1)
                    }
                }
                .padding(.horizontal, 14)
                .frame(height: 44)
                .glassBackground(in: Capsule())
            }

            Spacer(minLength: 0)
        }
        .padding(.horizontal)
    }
}

/// The tapped stop: this line's next arrivals there, kept fresh while the card is open.
private struct MapStopCard: View {
    let detail: LineDetail
    let stop: Stop
    let index: Int
    let direction: Direction
    let onClose: () -> Void

    @State private var arrival: LineArrivalDetail?
    @State private var loaded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(stop.name)
                        .font(.headline)
                    Text(tr("lineDetail.stopOf")
                        .replacingOccurrences(of: "{n}", with: "\(index + 1)")
                        .replacingOccurrences(of: "{total}", with: "\(detail.stops.count)"))
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Button(action: onClose) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.title2)
                        .symbolRenderingMode(.hierarchical)
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(tr("dashboard.close"))
            }

            arrivals

            Button {
                AppModel.shared.push(.stop(stop.id, lineId: detail.id, direction: direction))
            } label: {
                Label(tr("lineDetail.moreAboutStop"), systemImage: "arrow.right.circle")
                    .labelStyle(.titleAndIcon)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .controlSize(.large)
        }
        .padding(16)
        .glassBackground(in: RoundedRectangle(cornerRadius: 28, style: .continuous))
        .padding(.horizontal)
        .padding(.bottom, 8)
        .task(id: stop.id) {
            // Refresh like the stop page does, for as long as the card is shown
            while !Task.isCancelled {
                let result = try? await IosServices.shared.lineArrival(stopId: stop.id, lineId: detail.id, direction: direction)
                arrival = result ?? nil
                loaded = true
                try? await Task.sleep(for: .seconds(10))
            }
        }
    }

    @ViewBuilder
    private var arrivals: some View {
        if let arrival, arrival.arrivingTime >= 0 {
            HStack(spacing: 8) {
                LineBadge(name: detail.name, color: detail.color)
                ArrivalBadge(
                    seconds: arrival.arrivingTime,
                    isTimetable: arrival.isTimetable,
                    size: .large,
                    imminent: arrival.arrivingTime < 120
                )
                .fixedSize()
                ForEach(Array(arrival.arrivingTimes.dropFirst().prefix(2).enumerated()), id: \.offset) { _, next in
                    ArrivalBadge(seconds: next.arrivingTime, isTimetable: next.isTimetable, size: .small)
                        .fixedSize()
                }
                Spacer(minLength: 0)
            }
        } else if loaded {
            HStack(spacing: 8) {
                LineBadge(name: detail.name, color: detail.color)
                Text(arrival == nil ? tr("widget.noData") : tr("widget.outOfService"))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        } else {
            ProgressView().frame(maxWidth: .infinity, minHeight: 36)
        }
    }
}

/// "1 vehicle live" / "3 vehicles live".
private func liveVehiclesText(_ count: Int) -> String {
    "\(count) \(tr(count == 1 ? "lineDetail.vehicleLive" : "lineDetail.vehiclesLive"))"
}

/// Green status pill with a pulsing dot: how many vehicles are on the route right now.
private struct LiveVehiclesPill: View {
    let count: Int

    var body: some View {
        HStack(spacing: 5) {
            Image(systemName: "circle.fill")
                .font(.system(size: 7))
                .symbolEffect(.pulse, options: .repeating)
            Text(liveVehiclesText(count))
        }
        .font(.caption.weight(.semibold))
        .foregroundStyle(Color.realtime)
        .padding(.horizontal, 8)
        .padding(.vertical, 3)
        .tintedGlass(.realtime, in: Capsule())
        .fixedSize()
    }
}
