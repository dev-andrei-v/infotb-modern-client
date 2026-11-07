import CoreLocation
import MapKit
import Shared
import SwiftUI

private struct PreviewVehicle: Identifiable {
    let id: Int64
    let coordinate: CLLocationCoordinate2D
    let code: String?
    let lineName: String
    let color: String
    let type: String?
}

private struct NearbyRoute: Identifiable {
    let id: String
    let lineId: Int64
    let color: String
    let coordinates: [CLLocationCoordinate2D]

    init(detail: LineDetail, direction: Direction) {
        id = "\(detail.id)-\(direction.name)"
        lineId = detail.id
        color = detail.color
        if let path = detail.segmentPath, !path.isEmpty {
            coordinates = PolylineKt.decodePolyline(encoded: path).compactMap {
                guard let lat = $0.first?.doubleValue, let lng = $0.second?.doubleValue else { return nil }
                return CLLocationCoordinate2D(latitude: lat, longitude: lng)
            }
        } else {
            coordinates = detail.stops.map { .init(latitude: $0.lat, longitude: $0.lng) }
        }
    }
}

private struct MapViewport: Equatable {
    let latitude: Double
    let longitude: Double
    let latitudeDelta: Double
    let longitudeDelta: Double

    init(_ region: MKCoordinateRegion) {
        latitude = region.center.latitude
        longitude = region.center.longitude
        latitudeDelta = abs(region.span.latitudeDelta)
        longitudeDelta = abs(region.span.longitudeDelta)
    }

    var center: CLLocation { CLLocation(latitude: latitude, longitude: longitude) }

    /// Load a little beyond the visible map so a short pan stays within cache.
    var searchBounds: (south: Double, west: Double, north: Double, east: Double) {
        let latitudeHalf = min(latitudeDelta / 2, 0.035)
        let longitudeHalf = min(longitudeDelta / 2, 0.05)
        let latitudePad = min(latitudeDelta * 0.2, 0.014)
        let longitudePad = min(longitudeDelta * 0.2, 0.02)
        return (
            max(-90, latitude - latitudeHalf - latitudePad),
            max(-180, longitude - longitudeHalf - longitudePad),
            min(90, latitude + latitudeHalf + latitudePad),
            min(180, longitude + longitudeHalf + longitudePad)
        )
    }

    var stopSearchRadius: Double {
        let latitudeMeters = latitudeDelta * 111_000
        let longitudeMeters = longitudeDelta * 111_000 * max(0.1, cos(latitude * .pi / 180))
        return min(5_000, max(1_000, hypot(latitudeMeters, longitudeMeters) / 2))
    }

    var minimumSpanMeters: Double {
        min(latitudeDelta * 111_000,
            longitudeDelta * 111_000 * max(0.1, cos(latitude * .pi / 180)))
    }
}

private struct StopMapMarker: View {
    let name: String
    let isMetro: Bool
    let transportType: String?
    let selected: Bool

    private var signColor: Color { Color(hex: isMetro ? "#D21F32" : "#C92335") ?? .red }
    var body: some View {
        VStack(spacing: 0) {
            Group {
                if isMetro {
                    Text("M")
                        .font(.system(size: 21, weight: .heavy, design: .rounded))
                        .foregroundStyle(.white)
                        .frame(width: 30, height: 30)
                        .background(signColor, in: Circle())
                        .overlay(Circle().stroke(.white, lineWidth: 2))
                } else {
                    VStack(spacing: 0) {
                        Text("STB")
                            .font(.system(size: 6, weight: .black, design: .rounded))
                            .tracking(0.5)
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 9)
                            .background(signColor)
                        VehicleGlyph(type: transportType ?? TransportType.bus.name, size: 14)
                            .foregroundStyle(signColor)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .background(.white)
                    }
                    .frame(width: 27, height: 32)
                    .clipShape(STBStopPlaqueShape())
                    .overlay(STBStopPlaqueShape().stroke(signColor, lineWidth: 2))
                }
            }
            Rectangle().fill(signColor).frame(width: 3, height: 6)
            if selected {
                Text(name)
                    .font(.caption2.weight(.semibold))
                    .lineLimit(1)
                    .padding(.horizontal, 6).padding(.vertical, 3)
                    .background(.regularMaterial, in: Capsule())
                    .fixedSize()
            }
        }
        .shadow(color: .black.opacity(0.24), radius: 2, y: 1)
        .scaleEffect(selected ? 1.08 : 1)
        .animation(.easeOut(duration: 0.16), value: selected)
    }
}

/// The surface stop marker borrows the tall, red-edged STB stop plaque shape.
private struct STBStopPlaqueShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let topRadius = min(rect.width * 0.34, rect.height * 0.16)
        path.move(to: CGPoint(x: topRadius, y: 0))
        path.addLine(to: CGPoint(x: rect.width - topRadius, y: 0))
        path.addQuadCurve(to: CGPoint(x: rect.width, y: topRadius), control: CGPoint(x: rect.width, y: 0))
        path.addLine(to: CGPoint(x: rect.width, y: rect.height * 0.78))
        path.addLine(to: CGPoint(x: rect.width * 0.72, y: rect.height * 0.78))
        path.addLine(to: CGPoint(x: rect.width * 0.5, y: rect.height))
        path.addLine(to: CGPoint(x: rect.width * 0.28, y: rect.height * 0.78))
        path.addLine(to: CGPoint(x: 0, y: rect.height * 0.78))
        path.addLine(to: CGPoint(x: 0, y: topRadius))
        path.addQuadCurve(to: CGPoint(x: topRadius, y: 0), control: .zero)
        path.closeSubpath()
        return path
    }
}

private enum MapSheetLevel: CaseIterable {
    case collapsed, medium, expanded

    func height(in available: Double) -> Double {
        switch self {
        case .collapsed: min(132, available * 0.24)
        case .medium: min(max(270, available * 0.36), available * 0.62)
        case .expanded: available * 0.84
        }
    }
}

@Observable
@MainActor
private final class NearbyMapModel {
    var loading = false
    var failed = false
    var linesLoading = false
    var linesFailed = false
    var routesLoading = false
    var routesFailed = false
    var routes: [Int64: [NearbyRoute]] = [:]
    private var lastSearch: CLLocation?
    private var lastLoaded = Date.distantPast
    private var lastViewport: MapViewport?
    private var searchId = UUID()
    private var linesRequestId = UUID()
    private var routesRequestId = UUID()
    private var completedRoutes: Set<Int64> = []

    func load(near location: CLLocation, force: Bool = false) async {
        if !force, StopLocationCache.shared.hasFreshCoverage(near: location) {
            failed = false
            return
        }
        if !force, let lastSearch, location.distance(from: lastSearch) < 250,
           Date().timeIntervalSince(lastLoaded) < 300 { return }
        let requestId = UUID()
        searchId = requestId
        loading = true
        defer { if searchId == requestId { loading = false } }
        // A slightly larger square contains the full 1 km search circle.
        let lat = location.coordinate.latitude, lng = location.coordinate.longitude
        let dLat = 1_500.0 / 111_000
        let dLng = dLat / max(0.1, cos(lat * .pi / 180))
        let south = max(-90, lat - dLat), west = max(-180, lng - dLng)
        let north = min(90, lat + dLat), east = min(180, lng + dLng)
        let result = try? await IosServices.shared.stopsInBounds(
            south: south, west: west, north: north, east: east
        )
        guard !Task.isCancelled, searchId == requestId else { return }
        if let result {
            StopLocationCache.shared.storeArea(result, south: south, west: west, north: north, east: east)
            lastSearch = location
            lastLoaded = .now
            failed = false
        } else {
            failed = true
        }
    }

    func load(in viewport: MapViewport, force: Bool = false) async {
        // Very wide views are served from the local catalogue. Wait until the
        // rider zooms in before requesting a large, low-value stop rectangle.
        guard force || viewport.stopSearchRadius <= 4_500 else { return }
        let bounds = viewport.searchBounds
        if !force, StopLocationCache.shared.hasFreshCoverage(
            south: bounds.south, west: bounds.west, north: bounds.north, east: bounds.east
        ) {
            failed = false
            return
        }
        if !force, let lastViewport, Date().timeIntervalSince(lastLoaded) < 90 {
            let panThreshold = max(650, min(viewport.minimumSpanMeters, lastViewport.minimumSpanMeters) * 0.35)
            let panDistance = viewport.center.distance(from: lastViewport.center)
            let zoomRatio = viewport.stopSearchRadius / max(1, lastViewport.stopSearchRadius)
            let zoomChange = abs(zoomRatio - 1)
            if panDistance < panThreshold && zoomChange < 0.3 { return }
        }

        let requestId = UUID()
        searchId = requestId
        loading = true
        defer { if searchId == requestId { loading = false } }
        let result = try? await IosServices.shared.stopsInBounds(
            south: bounds.south, west: bounds.west, north: bounds.north, east: bounds.east
        )
        guard !Task.isCancelled, searchId == requestId else { return }
        if let result {
            StopLocationCache.shared.storeArea(result, south: bounds.south, west: bounds.west,
                                               north: bounds.north, east: bounds.east)
            lastSearch = viewport.center
            lastViewport = viewport
            lastLoaded = .now
            failed = false
        } else {
            failed = true
        }
    }

    func loadLines(for stops: [CachedStop], force: Bool = false) async {
        let requestId = UUID()
        linesRequestId = requestId
        linesLoading = true
        linesFailed = false
        defer { if linesRequestId == requestId { linesLoading = false } }
        // Grouped stations can have several physical platforms. Limit their
        // membership requests to four at a time, reusing a day's cached data.
        let pending = stops.filter {
            guard !force, let updated = $0.linesUpdated else { return true }
            return Date().timeIntervalSince(updated) >= StopLocationCache.linesTTL
        }
        for start in stride(from: 0, to: pending.count, by: 4) {
            guard !Task.isCancelled else { return }
            var batch: [(stopId: Int64, lines: [Line])] = []
            await withTaskGroup(of: (Int64, [Line]?).self) { group in
                for stop in pending[start..<min(start + 4, pending.count)] {
                    group.addTask { @MainActor in
                        (stop.id, try? await IosServices.shared.stopLines(stopId: stop.id))
                    }
                }
                for await (id, lines) in group {
                    guard !Task.isCancelled, linesRequestId == requestId else { continue }
                    if let lines { batch.append((stopId: id, lines: lines)) }
                    else { linesFailed = true }
                }
            }
            if !batch.isEmpty { StopLocationCache.shared.storeLines(batch) }
        }
    }

    func loadRoutes(_ lineIds: [Int64]) async {
        let requestId = UUID()
        routesRequestId = requestId
        routesLoading = true
        routesFailed = false
        defer { if routesRequestId == requestId { routesLoading = false } }
        var routeStops: [Stop] = []
        for id in lineIds where !completedRoutes.contains(id) {
            guard !Task.isCancelled else { return }
            var paths: [NearbyRoute] = []
            for direction in [Direction.tur, .retur] {
                if let detail = try? await IosServices.shared.mapLine(lineId: id, direction: direction) {
                    guard !Task.isCancelled else { return }
                    routeStops.append(contentsOf: detail.stops)
                    paths.append(NearbyRoute(detail: detail, direction: direction))
                } else {
                    routesFailed = true
                }
            }
            routes[id] = paths
            if paths.count == 2 { completedRoutes.insert(id) }
        }
        if !routeStops.isEmpty { StopLocationCache.shared.store(routeStops) }
    }
}

struct NearbyMapView: View {
    @State private var location = ForegroundLocation()
    @State private var model = NearbyMapModel()
    @State private var cache = StopLocationCache.shared
    @State private var position: MapCameraPosition = .region(Self.bucharest)
    @State private var mapViewport: MapViewport? = MapViewport(Self.bucharest)
    @State private var centered = false
    @State private var selectedGroupId: String?
    @State private var selectedStop: CachedStop?
    @State private var stopPreview: StopInfo?
    @State private var previewLoading = false
    @State private var selectedLineId: Int64?
    @State private var selectedTypes = Set(TransportType.ordered.map(\.name))
    @State private var lanes = RouteLaneLayout(paths: [])
    @State private var metersPerPoint = 6.0
    @State private var mapWidth = 400.0
    @State private var sheetLevel: MapSheetLevel = .collapsed
    @State private var sheetDrag = 0.0
    @State private var routeColors: [Int64: String] = [:]
    @State private var clockNow = Date()

    private static let bucharest = MKCoordinateRegion(
        center: .init(latitude: 44.435, longitude: 26.10),
        latitudinalMeters: 3_000, longitudinalMeters: 3_000
    )

    private var mapCenter: CLLocation? { mapViewport?.center ?? location.location }

    private var nearestStops: [CachedStop] {
        guard let center = mapCenter else { return [] }
        let stops = cache.nearest(to: center, radius: mapViewport?.stopSearchRadius ?? 1_000)
        // Include nearby metro entrances even in dense clusters of surface
        // stops, so the metro filter can discover its nearest lines too.
        let groups = NearbyStopGroup.groups(stops)
        let radius = mapViewport?.stopSearchRadius ?? 1_000
        let groupLimit = radius < 1_700 ? 8 : radius < 3_200 ? 5 : 3
        let metroLimit = radius < 3_200 ? 2 : 1
        let first = Array(groups.prefix(groupLimit))
        let ids = Set(first.map(\.id))
        let metro = groups.filter { !ids.contains($0.id) && $0.stops.contains { $0.type == "SUBWAY_STATION" } }.prefix(metroLimit)
        return (first + metro).flatMap(\.stops).sorted { $0.distance(from: center) < $1.distance(from: center) }
    }

    private var nearbyLines: [CachedStopLine] {
        var seen: Set<Int64> = []
        return nearestStops.flatMap(\.lines).filter {
            selectedTypes.contains($0.type) && (isNightTransitTime || !isNightBus($0)) && seen.insert($0.id).inserted
        }
    }

    private var visibleStops: [CachedStop] {
        nearestStops.filter {
            guard !$0.lines.isEmpty else { return true }
            return $0.lines.contains {
                selectedTypes.contains($0.type) && (isNightTransitTime || !isNightBus($0))
            }
        }
    }

    private var isNightTransitTime: Bool {
        let hour = Calendar.current.component(.hour, from: clockNow)
        return hour >= 22 || hour < 5
    }

    private func isNightBus(_ line: CachedStopLine) -> Bool {
        line.type == TransportType.bus.name && line.name.range(of: "^N\\d+$", options: .regularExpression) != nil
    }

    private var visibleGroups: [NearbyStopGroup] { NearbyStopGroup.groups(visibleStops) }

    private var routeIds: [Int64] {
        let routeLimit = (mapViewport?.stopSearchRadius ?? 1_000) < 1_700 ? 6 : 3
        var ids = Array(nearbyLines.prefix(routeLimit).map(\.id))
        if let selectedLineId, nearbyLines.contains(where: { $0.id == selectedLineId }), !ids.contains(selectedLineId) {
            ids.append(selectedLineId)
        }
        return ids
    }

    private var visibleRoutes: [NearbyRoute] {
        routeIds.flatMap { model.routes[$0] ?? [] }.sorted {
            // Paint the focused route last so junctions remain legible.
            if ($0.lineId == selectedLineId) != ($1.lineId == selectedLineId) { return $1.lineId == selectedLineId }
            return $0.id < $1.id
        }
    }

    private var previewVehicles: [PreviewVehicle] {
        guard let stopPreview else { return [] }
        var seen = Set<Int64>()
        return stopPreview.lines.flatMap { line in
            line.vehicles.compactMap { vehicle -> PreviewVehicle? in
                guard seen.insert(vehicle.id).inserted else { return nil }
                return PreviewVehicle(id: vehicle.id, coordinate: .init(latitude: vehicle.lat, longitude: vehicle.lng),
                                      code: vehicle.code, lineName: line.name, color: line.color,
                                      type: vehicle.transportType?.name)
            }
        }
    }

    private func routeColor(_ lineId: Int64) -> String {
        routeColors[lineId] ?? "#1565C0"
    }

    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .bottom) {
                map
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .onAppear { mapWidth = max(1, geometry.size.width) }
                    .onChange(of: geometry.size.width) { _, width in mapWidth = max(1, width) }
                bottomSheet(in: geometry.size.height)
            }
        }
        .navigationTitle(tr("nav.map"))
        .navigationBarTitleDisplayMode(.inline)
        .foregroundLocation(location)
        .task(id: location.location) {
            guard let fix = location.location else { return }
            if !centered {
                position = .region(.init(center: fix.coordinate, latitudinalMeters: 2_500, longitudinalMeters: 2_500))
                centered = true
            }
        }
        .task {
            let calendar = Calendar.current
            while !Task.isCancelled {
                let hour = calendar.component(.hour, from: .now)
                let transitionHour = (hour >= 5 && hour < 22) ? 22 : 5
                var components = DateComponents()
                components.hour = transitionHour
                components.minute = 0
                let next = calendar.nextDate(after: .now, matching: components, matchingPolicy: .nextTime) ?? .now.addingTimeInterval(60)
                try? await Task.sleep(for: .seconds(max(1, next.timeIntervalSinceNow)))
                if !Task.isCancelled { clockNow = .now }
            }
        }
        .task(id: mapViewport) {
            guard let viewport = mapViewport else { return }
            try? await Task.sleep(for: .milliseconds(650))
            guard !Task.isCancelled else { return }
            await model.load(in: viewport)
        }
        .task(id: nearestStops.map(\.id)) {
            try? await Task.sleep(for: .milliseconds(800))
            guard !Task.isCancelled else { return }
            while model.loading {
                try? await Task.sleep(for: .milliseconds(100))
                guard !Task.isCancelled else { return }
            }
            await model.loadLines(for: nearestStops)
        }
        .task(id: routeIds) {
            try? await Task.sleep(for: .milliseconds(350))
            guard !Task.isCancelled else { return }
            await model.loadRoutes(routeIds)
        }
        .task(id: selectedStop?.id) {
            guard let stop = selectedStop else { stopPreview = nil; previewLoading = false; return }
            stopPreview = nil
            previewLoading = true
            while !Task.isCancelled {
                stopPreview = try? await IosServices.shared.stopPreview(stopId: stop.id)
                previewLoading = false
                try? await Task.sleep(for: .seconds(15))
            }
        }
        .task(id: nearbyLines.map(\.id)) {
            // Keep a line's map colour stable while memberships arrive and
            // filters change, without repeating spatial queries for each badge.
            let palette = ["#1565C0", "#8E24AA", "#D84315", "#00838F", "#2E7D32", "#C62828", "#5E35B1", "#AD5700"]
            for (index, line) in nearbyLines.enumerated() where routeColors[line.id] == nil {
                routeColors[line.id] = line.type == TransportType.subway.name ? line.color :
                    (palette.first { !routeColors.values.contains($0) } ?? palette[index % palette.count])
            }
        }
        .task(id: visibleRoutes.map(\.id)) {
            lanes = RouteLaneLayout(paths: visibleRoutes.map { RouteLanePath(id: $0.id, lineId: $0.lineId, coordinates: $0.coordinates) })
        }
        .onChange(of: selectedTypes) { _, _ in
            selectedLineId = nil
            selectedGroupId = nil
            selectedStop = nil
        }
    }

    private var map: some View {
        let laneCoordinates = lanes.coordinates(metersPerPoint: metersPerPoint)
        return Map(position: $position, interactionModes: [.pan, .zoom]) {
            UserAnnotation()
            ForEach(visibleRoutes) { route in
                let points = laneCoordinates[route.id] ?? route.coordinates
                let dimmed = selectedLineId != nil && selectedLineId != route.lineId
                if points.count >= 2 {
                    MapPolyline(coordinates: points)
                        .stroke(.white.opacity(dimmed ? 0.25 : 0.8), lineWidth: selectedLineId == route.lineId ? 9 : 7)
                    MapPolyline(coordinates: points)
                        .stroke((Color(hex: routeColor(route.lineId)) ?? .lineFallback).opacity(dimmed ? 0.25 : 0.95),
                                style: StrokeStyle(lineWidth: selectedLineId == route.lineId ? 6 : 4, lineCap: .round, lineJoin: .round))
                }
            }
            ForEach(visibleGroups) { group in
                let stop = group.nearest
                Annotation(group.name, coordinate: stop.coordinate) {
                    Button {
                        withAnimation(.snappy) {
                            selectedGroupId = selectedGroupId == group.id ? nil : group.id
                            if selectedGroupId != nil {
                                selectedStop = group.nearest
                                sheetLevel = .medium
                            } else {
                                selectedStop = nil
                            }
                        }
                    } label: {
                        StopMapMarker(
                            name: group.name,
                            isMetro: stop.type == "SUBWAY_STATION",
                            transportType: group.lines.first?.type,
                            selected: selectedGroupId == group.id
                        )
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(group.name)
                }
            }
            ForEach(previewVehicles) { vehicle in
                Annotation(vehicle.code.map { "\(vehicle.lineName) · \($0)" } ?? vehicle.lineName, coordinate: vehicle.coordinate) {
                    VehicleGlyph(type: vehicle.type ?? TransportType.bus.name, size: 11)
                        .foregroundStyle(.white)
                        .padding(6).background(Color(hex: vehicle.color) ?? .accentColor, in: Circle())
                        .overlay(Circle().stroke(.white, lineWidth: 1.5))
                }
            }
        }
        .mapStyle(.standard(pointsOfInterest: .excludingAll))
        .mapControls { MapUserLocationButton(); MapScaleView() }
        .onMapCameraChange(frequency: .onEnd) { context in
            let scale = context.region.span.longitudeDelta * 111_000 *
                cos(context.region.center.latitude * .pi / 180) / max(1, mapWidth)
            if scale.isFinite && scale > 0 && abs(scale - metersPerPoint) / metersPerPoint > 0.05 {
                metersPerPoint = scale
            }
            mapViewport = MapViewport(context.region)
        }
    }

    private func bottomSheet(in available: Double) -> some View {
        let base = sheetLevel.height(in: available)
        let height = min(MapSheetLevel.expanded.height(in: available), max(MapSheetLevel.collapsed.height(in: available), base - sheetDrag))
        return VStack(spacing: 0) {
            HStack(spacing: 12) {
                Capsule()
                    .fill(.secondary.opacity(0.45))
                    .frame(width: 36, height: 5)
                    .frame(maxWidth: .infinity).frame(height: 32)
                    .contentShape(Rectangle())
                    .gesture(DragGesture(minimumDistance: 4)
                    .onChanged { sheetDrag = $0.translation.height }
                    .onEnded { value in
                        let projected = base - value.predictedEndTranslation.height
                        let nearest = MapSheetLevel.allCases.min {
                            abs($0.height(in: available) - projected) < abs($1.height(in: available) - projected)
                        } ?? .medium
                        withAnimation(.snappy) { sheetLevel = nearest; sheetDrag = 0 }
                    })
                    .accessibilityElement()
                    .accessibilityLabel(tr("map.adjustSheet"))
                    .accessibilityValue(tr(sheetLevel == .collapsed ? "map.sheetCollapsed" : sheetLevel == .expanded ? "map.sheetExpanded" : "map.sheetMedium"))
                    .accessibilityAdjustableAction { direction in
                        withAnimation(.snappy) {
                            switch direction {
                            case .increment: sheetLevel = sheetLevel == .collapsed ? .medium : .expanded
                            case .decrement: sheetLevel = sheetLevel == .expanded ? .medium : .collapsed
                            @unknown default: break
                            }
                        }
                    }
                Button {
                    withAnimation(.snappy) {
                        sheetLevel = sheetLevel == .collapsed ? .medium : .collapsed
                        sheetDrag = 0
                    }
                } label: {
                    Image(systemName: sheetLevel == .collapsed ? "chevron.up" : "chevron.down")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.secondary)
                        .frame(width: 32, height: 32)
                        .background(Color(.tertiarySystemFill), in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(tr(sheetLevel == .collapsed ? "map.expandSheet" : "map.collapseSheet"))
            }
            .padding(.horizontal, 16)

            if sheetLevel != .collapsed { filters }
            if mapCenter != nil {
                lineStrip.fixedSize(horizontal: false, vertical: true)
            } else {
                LocationStatusView(location: location).padding(.horizontal)
            }
            if sheetLevel != .collapsed { results }
            else { Spacer(minLength: 0) }
        }
        .frame(maxWidth: .infinity).frame(height: height, alignment: .top)
        .background(.regularMaterial, in: UnevenRoundedRectangle(topLeadingRadius: 28, topTrailingRadius: 28))
        .clipShape(UnevenRoundedRectangle(topLeadingRadius: 28, topTrailingRadius: 28))
        .shadow(color: .black.opacity(0.15), radius: 12, y: -3)
    }

    private var filters: some View {
        VStack(alignment: .leading, spacing: 4) {
            Label(tr("map.filters"), systemImage: "slider.horizontal.3")
                .font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                .padding(.horizontal, 16)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(TransportType.ordered, id: \.name) { type in
                        let selected = selectedTypes.contains(type.name)
                        Button {
                            if selected { selectedTypes.remove(type.name) }
                            else { selectedTypes.insert(type.name) }
                        } label: {
                            HStack(spacing: 6) {
                                VehicleGlyph(type: type.name, size: 13)
                                Text(type.label).lineLimit(1)
                                if selected { Image(systemName: "checkmark").font(.system(size: 9, weight: .bold)) }
                            }
                            .font(.caption.weight(.semibold))
                            .padding(.horizontal, 10).frame(height: 34)
                            .foregroundStyle(selected ? Color.white : Color.primary)
                            .background(selected ? Color.accentColor : Color(.tertiarySystemFill), in: Capsule())
                            .overlay(Capsule().stroke(selected ? Color.clear : Color.secondary.opacity(0.2), lineWidth: 1))
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(type.label)
                        .accessibilityAddTraits(selected ? .isSelected : [])
                    }
                }
                .padding(.horizontal, 16).padding(.vertical, 3)
            }
        }
        .frame(height: 62)
    }

    private var results: some View {
        List {
            if mapCenter == nil {
                Section { LocationStatusView(location: location) }
            } else {
                if model.failed {
                    Section {
                        Label(tr("map.cachedResults"), systemImage: "wifi.exclamationmark")
                            .font(.subheadline).foregroundStyle(.secondary)
                        Button(tr("common.retry")) {
                            Task {
                                if let mapViewport { await model.load(in: mapViewport, force: true) }
                                else if let fix = location.location { await model.load(near: fix, force: true) }
                            }
                        }
                    }
                }
                if let selectedStop { stopPreviewSection(selectedStop) }
                Section(tr("lineDetail.nearbyStops")) {
                    ForEach(visibleGroups) { group in
                        if group.stops.count == 1 {
                            Button { select(group.nearest) } label: { groupLabel(group) }
                                .buttonStyle(.plain)
                        } else {
                            DisclosureGroup(isExpanded: Binding(
                                get: { selectedGroupId == group.id },
                                set: { selectedGroupId = $0 ? group.id : nil }
                            )) {
                                ForEach(group.stops) { stop in platformRow(stop) }
                            } label: {
                                groupLabel(group)
                            }
                        }
                    }
                    if visibleStops.isEmpty {
                        if model.loading || model.linesLoading {
                            ProgressView()
                        } else {
                            Text(tr("map.noStops")).foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
    }

    private var lineStrip: some View {
        let visibleLineLimit = sheetLevel == .collapsed ? 4 : 10
        let shownLines = Array(nearbyLines.prefix(visibleLineLimit))
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(tr("map.nearbyLines")).font(.subheadline.weight(.semibold))
                Group {
                    if model.linesLoading || model.routesLoading { ProgressView().controlSize(.mini) }
                    else { Color.clear }
                }
                .frame(width: 12, height: 12)
                Spacer()
                if let line = nearbyLines.first(where: { $0.id == selectedLineId }) {
                    Button {
                        AppModel.shared.push(.line(line.id))
                    } label: {
                        Text("\(tr("map.viewStops")) \(line.name)")
                            .font(.subheadline.weight(.medium))
                    }
                }
            }
            .padding(.horizontal)

            FlowLayout(spacing: 8, lineSpacing: 8) {
                Button {
                    selectedLineId = nil
                } label: {
                    Label(tr("map.nearestRoutes"), systemImage: "point.3.connected.trianglepath.dotted")
                        .font(.subheadline.weight(.medium))
                        .padding(.horizontal, 12).frame(height: 38)
                        .background(selectedLineId == nil ? Color.accentColor.opacity(0.12) : Color(.tertiarySystemFill), in: Capsule())
                }
                .buttonStyle(.plain)
                .foregroundStyle(selectedLineId == nil ? Color.accentColor : .secondary)
                .accessibilityAddTraits(selectedLineId == nil ? .isSelected : [])

                ForEach(shownLines) { line in
                    let selected = selectedLineId == line.id
                    Button {
                        selectedLineId = selected ? nil : line.id
                    } label: {
                        HStack(spacing: 6) {
                            LineBadge(name: line.name, color: routeColor(line.id))
                            if selected { Image(systemName: "checkmark").font(.caption.weight(.bold)).foregroundStyle(Color.accentColor) }
                        }
                        .padding(.horizontal, 9).frame(height: 38)
                        .background(selected ? Color.accentColor.opacity(0.1) : Color(.tertiarySystemFill), in: Capsule())
                        .overlay(Capsule().stroke(selected ? Color.accentColor : .clear, lineWidth: 2))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("\(tr("lineDetail.linePrefix")) \(line.name)")
                    .accessibilityAddTraits(selected ? .isSelected : [])
                }
                if nearbyLines.count > visibleLineLimit {
                    Text("+\(nearbyLines.count - visibleLineLimit)")
                        .font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                        .padding(.horizontal, 10).frame(height: 34)
                        .background(Color(.tertiarySystemFill), in: Capsule())
                        .accessibilityLabel("\(nearbyLines.count - visibleLineLimit) \(tr("map.moreLines"))")
                }
            }
            .padding(.horizontal, 16)
            if !isNightTransitTime {
                Text(tr("map.nightBusesHidden"))
                    .font(.caption2).foregroundStyle(.secondary).padding(.horizontal, 16)
            }
            if model.linesFailed || model.routesFailed {
                HStack {
                    Text(tr("map.routesUnavailable")).font(.caption).foregroundStyle(.secondary)
                    Spacer()
                    Button(tr("common.retry")) {
                        Task { await model.loadLines(for: nearestStops, force: true); await model.loadRoutes(routeIds) }
                    }
                    .font(.caption.weight(.semibold))
                }
                .padding(.horizontal)
            }
        }
        .padding(.vertical, 10)
        .background(Color(.systemBackground))
    }

    private func groupLabel(_ group: NearbyStopGroup) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 5) {
                Text(group.name).font(.body.weight(.medium)).foregroundStyle(.primary)
                FlowLayout(spacing: 4, lineSpacing: 4) {
                    ForEach(group.lines.filter { selectedTypes.contains($0.type) && (isNightTransitTime || !isNightBus($0)) }) { line in
                        LineBadge(name: line.name, color: routeColor(line.id), size: .medium)
                    }
                }
                if group.stops.count > 1 {
                    Text(tr("map.platformCount").replacingOccurrences(of: "{n}", with: "\(group.stops.count)"))
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            Spacer()
            if let center = mapCenter {
                Text(DashboardFormat.distance(group.nearest.distance(from: center)))
                    .font(.caption.monospacedDigit()).foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 3)
    }

    private func platformRow(_ stop: CachedStop) -> some View {
        Button { select(stop) } label: {
            VStack(alignment: .leading, spacing: 5) {
                HStack {
                    Text(stop.description.flatMap { $0.isEmpty ? nil : $0 } ?? tr(stop.type == "SUBWAY_STATION" ? "map.metroEntrance" : "map.surfaceStop"))
                        .font(.subheadline.weight(.medium)).foregroundStyle(.primary)
                    Spacer()
                    if let center = mapCenter {
                        Text(DashboardFormat.distance(stop.distance(from: center)))
                            .font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                    }
                    Image(systemName: "chevron.right").font(.caption).foregroundStyle(.secondary)
                }
                FlowLayout(spacing: 4, lineSpacing: 4) {
                    ForEach(stop.lines.filter { selectedTypes.contains($0.type) && (isNightTransitTime || !isNightBus($0)) }) { line in
                        LineBadge(name: line.name, color: routeColor(line.id), size: .medium)
                    }
                }
            }
            .padding(.vertical, 4)
        }
        .buttonStyle(.plain)
    }

    private func select(_ stop: CachedStop) {
        withAnimation(.snappy) {
            selectedStop = stop
            selectedGroupId = NearbyStopGroup.groups(nearestStops)
                .first(where: { $0.stops.contains(where: { $0.id == stop.id }) })?.id
            sheetLevel = .expanded
        }
    }

    @ViewBuilder
    private func stopPreviewSection(_ stop: CachedStop) -> some View {
        Section {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text(stop.name).font(.headline)
                    Text(stop.description ?? tr("map.surfaceStop"))
                        .font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                Button { selectedStop = nil; selectedGroupId = nil } label: {
                    Image(systemName: "xmark.circle.fill").font(.title3).foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
            }

            if previewLoading && stopPreview == nil {
                ProgressView().frame(maxWidth: .infinity)
            } else if let preview = stopPreview, !preview.lines.isEmpty {
                ForEach(Array(preview.lines.enumerated()), id: \.offset) { _, line in
                    HStack(spacing: 9) {
                        Button { AppModel.shared.push(.line(line.id)) } label: {
                            LineBadge(name: line.name, color: line.color)
                        }
                        .buttonStyle(.plain)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(line.directionName).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                            if !line.vehicles.isEmpty {
                                Text("\(line.vehicles.count) \(tr(line.vehicles.count == 1 ? "lineDetail.vehicleLive" : "lineDetail.vehiclesLive"))")
                                    .font(.caption2.weight(.medium)).foregroundStyle(.green)
                            }
                        }
                        Spacer(minLength: 4)
                        if line.arrivingTime >= 0 {
                            ArrivalBadge(seconds: line.arrivingTime, isTimetable: line.isTimetable, size: .small,
                                         imminent: line.arrivingTime < 120)
                        } else {
                            OutOfServicePill()
                        }
                    }
                    .contentShape(Rectangle())
                    .onTapGesture { AppModel.shared.push(.line(line.id)) }
                }
            } else if stopPreview != nil {
                Text(tr("widget.noData")).font(.subheadline).foregroundStyle(.secondary)
            } else {
                Label(tr("map.routesUnavailable"), systemImage: "wifi.exclamationmark")
                    .font(.caption).foregroundStyle(.secondary)
            }

            Button { AppModel.shared.push(.stop(stop.id)) } label: {
                Label(tr("lineDetail.moreAboutStop"), systemImage: "arrow.right.circle")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
        } header: {
            Label(tr("map.nearbyLines"), systemImage: "tram.fill")
        }
    }
}
