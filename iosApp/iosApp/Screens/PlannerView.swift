import Shared
import SwiftUI
import MapKit

private struct SavedTrip: Codable, Identifiable, Equatable {
    var id: UUID = UUID()
    var from: SavedPlace
    var to: SavedPlace
    var departure: Date
    var useCurrentTime: Bool
    var modes: [String]
    var accessible: Bool
    var maxWalkDistance: Double

    struct SavedPlace: Codable, Equatable {
        var name: String
        var lat: Double
        var lng: Double
        var type: String?
        var description: String?
        var stopId: Int64?

        init(_ place: PlannerPlace) {
            name = place.name
            lat = place.lat
            lng = place.lng
            type = place.type
            description = place.description_
            stopId = place.stop_id?.int64Value
        }

        var place: PlannerPlace {
            PlannerPlace(name: name, lat: lat, lng: lng, type: type, description: description, stop_id: stopId.map { KotlinLong(value: $0) })
        }
    }

    init(from: PlannerPlace, to: PlannerPlace, departure: Date, useCurrentTime: Bool, modes: [String], accessible: Bool, maxWalkDistance: Double) {
        self.from = SavedPlace(from)
        self.to = SavedPlace(to)
        self.departure = departure
        self.useCurrentTime = useCurrentTime
        self.modes = modes
        self.accessible = accessible
        self.maxWalkDistance = maxWalkDistance
    }
}

private struct SavedDestination: Codable, Identifiable, Equatable {
    var id: UUID = UUID()
    var label: String
    var place: SavedTrip.SavedPlace
}

private struct PlannerLegStop {
    let name: String
    let time: String?
    let coordinate: CLLocationCoordinate2D?
}

private struct PlannerRouteLeg: Identifiable {
    let id: Int
    let type: String?
    let lineName: String?
    let direction: String?
    let startTime: String?
    let stopTime: String?
    let durationSeconds: Int?
    let color: String?
    let vehicleCode: String?
    let hasLiveVehicle: Bool
    let vehicleCoordinate: CLLocationCoordinate2D?
    let stops: [PlannerLegStop]
    let coordinates: [CLLocationCoordinate2D]

    var isWalking: Bool {
        ["WALK", "WALKING", "FOOT", "PEDESTRIAN"].contains(type?.uppercased() ?? "")
    }

    var glyphType: String {
        let value = (type ?? "BUS").uppercased()
        if value == "TROLLEYBUS" { return "CABLE_CAR" }
        if value == "METRO" { return "SUBWAY" }
        return value
    }
}

private enum PlannerPlaceField: Equatable { case origin, destination }

struct PlannerView: View {
    @State private var app = AppModel.shared
    @State private var origin: PlannerPlace?
    @State private var destination: PlannerPlace?
    @State private var departure = Date()
    @State private var useCurrentTime = true
    @State private var modes = Set(TransportType.ordered.map(\.name))
    @State private var accessible = false
    @State private var maxWalkDistance = 1000.0
    @State private var query = ""
    @State private var field: PlannerPlaceField?
    @State private var suggestions: [PlannerPlace] = []
    @State private var searching = false
    @State private var placeSearchTask: Task<Void, Never>?
    @State private var loading = false
    @State private var error: String?
    @State private var response: String?
    @State private var savedDestinations: [SavedDestination] = []
    @State private var isSavingDestination = false
    @State private var destinationLabel = ""
    @State private var selectedRouteIndex: Int?
    @State private var showAdvanced = false
    @State private var plannerLocation = ForegroundLocation()
    @State private var mapPickingTarget: PlannerPlaceField?
    @State private var mapPickPosition: MapCameraPosition = .region(Self.defaultMapRegion)
    @State private var mapPickCoordinate = CLLocationCoordinate2D(latitude: 44.435, longitude: 26.10)
    @State private var placePickerMessage: String?

    private let destinationsStorageKey = "planner.savedDestinations.v1"
    private let legacyTripsStorageKey = "planner.savedTrips.v1"

    private static let defaultMapRegion = MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 44.435, longitude: 26.10),
        latitudinalMeters: 3_000,
        longitudinalMeters: 3_000
    )

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                departureCard
                modeCard
                advancedCard
                savedSection
                if let error {
                    Label {
                        Text(error).textSelection(.enabled)
                    } icon: {
                        Image(systemName: "wifi.exclamationmark")
                    }
                    .font(.subheadline).foregroundStyle(.secondary)
                }
                if let response { routeResults(response) }
            }
            .padding(.horizontal, InfoTBDesign.pageInset)
            .padding(.top, 12)
            .padding(.bottom, 12)
        }
        .background(Color(.systemGroupedBackground))
        .safeAreaInset(edge: .bottom, spacing: 0) { tripControlsDock }
        .navigationTitle(tr("planner.title"))
        .navigationBarTitleDisplayMode(.inline)
        .foregroundLocation(plannerLocation)
        .task {
            loadSavedTrips()
            handlePendingPlannerDestination()
        }
        .onChange(of: app.pendingPlannerDestinationID) { _, destinationID in
            if destinationID != nil { handlePendingPlannerDestination() }
        }
        .sheet(item: Binding(get: { field.map(PlaceFieldSheet.init) }, set: { field = $0?.field })) { item in
            placePicker(item.field)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
        .alert(tr("planner.saveDestination"), isPresented: $isSavingDestination) {
            TextField(tr("planner.destinationLabel"), text: $destinationLabel)
                .textInputAutocapitalization(.words)
            Button(tr("planner.cancel"), role: .cancel) { }
            Button(tr("planner.saveAction")) { saveDestination() }
        } message: {
            Text(destination?.name ?? "")
        }
    }

    private var tripControlsDock: some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                placeDockButton(title: tr("planner.from"), place: origin, icon: "circle.fill", field: .origin)
                Button {
                    swap(&origin, &destination)
                } label: {
                    Image(systemName: "arrow.left.arrow.right")
                        .font(.body.weight(.semibold))
                        .frame(width: 40, height: 52)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .foregroundStyle(InfoTBDesign.accent)
                .disabled(origin == nil || destination == nil)
                .accessibilityLabel(tr("planner.swap"))
                placeDockButton(title: tr("planner.to"), place: destination, icon: "mappin.and.ellipse", field: .destination)
            }
            Button(action: findRoutes) {
                HStack(spacing: 8) {
                    if loading { ProgressView().tint(.white) }
                    Text(tr("planner.findRoutes"))
                }
                .frame(maxWidth: .infinity, minHeight: 48)
            }
            .buttonStyle(.borderedProminent)
            .disabled(loading || origin == nil || destination == nil || modes.isEmpty)
        }
        .padding(.horizontal, InfoTBDesign.pageInset)
        .padding(.top, 10)
        .padding(.bottom, 8)
        .background(.regularMaterial)
    }

    private func placeDockButton(title: String, place: PlannerPlace?, icon: String, field target: PlannerPlaceField) -> some View {
        Button {
            query = ""
            suggestions = []
            placePickerMessage = nil
            field = target
        } label: {
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(target == .origin ? InfoTBDesign.accent : Color.red)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title.uppercased()).font(.caption2.weight(.semibold)).foregroundStyle(.secondary)
                    Text(place?.name ?? tr(target == .origin ? "planner.chooseOrigin" : "planner.chooseDestination"))
                        .font(.footnote.weight(.medium))
                        .foregroundStyle(place == nil ? Color.secondary : Color.primary)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 10)
            .frame(maxWidth: .infinity, minHeight: 52, alignment: .leading)
            .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityHint(tr(target == .origin ? "planner.chooseOrigin" : "planner.chooseDestination"))
    }

    private var departureCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Label(tr("planner.departure"), systemImage: "clock").font(.subheadline.weight(.semibold))
                Spacer()
                Toggle(tr("planner.leaveNow"), isOn: $useCurrentTime)
                    .labelsHidden()
                    .tint(InfoTBDesign.accent)
                Text(tr(useCurrentTime ? "planner.leaveNow" : "planner.later"))
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(useCurrentTime ? InfoTBDesign.accent : .primary)
            }
            if !useCurrentTime {
                DatePicker(tr("planner.dateAndTime"), selection: $departure, in: Date()..., displayedComponents: [.date, .hourAndMinute])
                    .datePickerStyle(.compact)
            }
        }
        .padding(12).background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
    }

    private var modeCard: some View {
        VStack(alignment: .leading, spacing: 7) {
            Label(tr("planner.vehicles"), systemImage: "bus").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 7) {
                ForEach(TransportType.ordered, id: \.name) { type in
                    let isOn = modes.contains(type.name)
                    Button {
                        if isOn { modes.remove(type.name) } else { modes.insert(type.name) }
                    } label: {
                        HStack(spacing: 6) {
                            VehicleGlyph(type: type.name, size: 13).foregroundStyle(type.color)
                            Text(type.label).lineLimit(1)
                            if isOn { Image(systemName: "checkmark").font(.system(size: 9, weight: .bold)).foregroundStyle(type.color) }
                        }
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(isOn ? Color.primary : Color.secondary)
                        .padding(.horizontal, 10).frame(minHeight: 36)
                        .background(isOn ? type.color.opacity(0.10) : Color(.tertiarySystemGroupedBackground), in: Capsule())
                        .overlay(Capsule().stroke(isOn ? type.color.opacity(0.4) : Color.secondary.opacity(0.15), lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(type.label)
                    .accessibilityAddTraits(isOn ? .isSelected : [])
                }
                }
            }
        }
        .padding(12).background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
    }

    private var advancedCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Button { withAnimation(.snappy) { showAdvanced.toggle() } } label: {
                HStack { Label(tr("planner.advanced"), systemImage: "slider.horizontal.3").font(.headline); Spacer(); Image(systemName: showAdvanced ? "chevron.up" : "chevron.down").foregroundStyle(.secondary) }
            }.buttonStyle(.plain)
            if showAdvanced {
                Toggle(tr("planner.accessibility"), isOn: $accessible)
                VStack(alignment: .leading) {
                    HStack { Text(tr("planner.walkDistance")); Spacer(); Text("\(Int(maxWalkDistance)) m").foregroundStyle(.secondary) }
                    Slider(value: $maxWalkDistance, in: 0...2000, step: 250)
                }
            }
        }
        .padding(16).background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 20))
    }

    private var savedSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Label(tr("planner.savedDestinations"), systemImage: "bookmark.fill").font(.subheadline.weight(.semibold))
                Spacer()
                if !savedDestinations.isEmpty { Text("\(savedDestinations.count)").font(.caption.weight(.semibold)).foregroundStyle(.secondary) }
            }
            if savedDestinations.isEmpty {
                Text(tr("planner.noSavedDestinations")).font(.caption).foregroundStyle(.secondary)
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(savedDestinations) { saved in
                            Button { open(saved) } label: {
                                VStack(alignment: .leading, spacing: 3) {
                                    Label(saved.label, systemImage: "mappin.and.ellipse")
                                        .font(.caption.weight(.semibold)).lineLimit(1)
                                    Text(saved.place.name).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                                }
                                .padding(.horizontal, 11).padding(.vertical, 8)
                                .frame(minWidth: 110, alignment: .leading)
                                .background(Color(.tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
                            }
                            .buttonStyle(.plain)
                            .contextMenu {
                                Button(role: .destructive) { delete(saved) } label: {
                                    Label(tr("planner.removeSavedDestination"), systemImage: "trash")
                                }
                            }
                        }
                    }
                }
            }
        }
        .padding(12).background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
    }

    @ViewBuilder
    private func placePicker(_ target: PlannerPlaceField) -> some View {
        if mapPickingTarget == target {
            mapPlacePicker(target)
        } else {
            placeSearchPicker(target)
        }
    }

    private func placeSearchPicker(_ target: PlannerPlaceField) -> some View {
        NavigationStack {
            VStack(spacing: 0) {
                HStack {
                    Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                    TextField(tr("planner.searchPlaces"), text: $query)
                        .textInputAutocapitalization(.words).autocorrectionDisabled()
                        .onChange(of: query) { _, value in search(value) }
                    if searching { ProgressView() }
                }.padding(12).background(Color(.tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12)).padding()
                if suggestions.isEmpty && !query.isEmpty && !searching {
                    ContentUnavailableView.search(text: query)
                } else {
                    List(suggestions.indices, id: \.self) { index in
                        let place = suggestions[index]
                        Button { choose(place, for: target) } label: {
                            VStack(alignment: .leading, spacing: 3) {
                                Text(place.name).font(.body.weight(.medium)).foregroundStyle(.primary)
                                if let description = place.description_, !description.isEmpty { Text(description).font(.caption).foregroundStyle(.secondary).lineLimit(2) }
                            }
                        }
                    }.listStyle(.plain)
                }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                VStack(spacing: 8) {
                    if let placePickerMessage {
                        Text(placePickerMessage).font(.caption).foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    HStack(spacing: 10) {
                        Button { useCurrentLocation(for: target) } label: {
                            Label(tr("planner.currentLocation"), systemImage: "location.fill")
                                .frame(maxWidth: .infinity, minHeight: 48)
                        }
                        .buttonStyle(.bordered)
                        Button { beginMapPick(for: target) } label: {
                            Label(tr("planner.pickOnMap"), systemImage: "map")
                                .frame(maxWidth: .infinity, minHeight: 48)
                        }
                        .buttonStyle(.borderedProminent)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 10)
                .padding(.bottom, 8)
                .background(.regularMaterial)
            }
            .navigationTitle(tr(target == .origin ? "planner.chooseOrigin" : "planner.chooseDestination"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button(tr("planner.cancel")) { field = nil } } }
        }
    }

    private func mapPlacePicker(_ target: PlannerPlaceField) -> some View {
        NavigationStack {
            Map(position: $mapPickPosition, interactionModes: [.pan, .zoom]) {
                UserAnnotation()
            }
            .overlay {
                Image(systemName: "mappin.and.ellipse")
                    .font(.system(size: 34, weight: .semibold))
                    .foregroundStyle(Color.red)
                    .shadow(color: .black.opacity(0.2), radius: 2, y: 1)
                    .offset(y: -16)
                    .allowsHitTesting(false)
            }
            .onMapCameraChange(frequency: .onEnd) { context in
                mapPickCoordinate = context.region.center
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                VStack(spacing: 8) {
                    Text("\(mapPickCoordinate.latitude, format: .number.precision(.fractionLength(4))), \(mapPickCoordinate.longitude, format: .number.precision(.fractionLength(4)))")
                        .font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                    Button {
                        choose(PlannerPlace(name: tr("planner.mapPoint"), lat: mapPickCoordinate.latitude,
                                            lng: mapPickCoordinate.longitude, type: "LOCATION", description: nil, stop_id: nil), for: target)
                        mapPickingTarget = nil
                    } label: {
                        Text(tr("planner.useMapPoint")).frame(maxWidth: .infinity, minHeight: 48)
                    }
                    .buttonStyle(.borderedProminent)
                }
                .padding(.horizontal, 16).padding(.top, 12).padding(.bottom, 8)
                .background(.regularMaterial)
            }
            .navigationTitle(tr("planner.pickOnMap"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(tr("planner.cancel")) { mapPickingTarget = nil }
                }
            }
        }
    }

    private func beginMapPick(for target: PlannerPlaceField) {
        let center = plannerLocation.location?.coordinate ?? CLLocationCoordinate2D(latitude: 44.435, longitude: 26.10)
        mapPickCoordinate = center
        mapPickPosition = .region(MKCoordinateRegion(center: center, latitudinalMeters: 2_500, longitudinalMeters: 2_500))
        mapPickingTarget = target
    }

    private func useCurrentLocation(for target: PlannerPlaceField) {
        placePickerMessage = nil
        if let fix = plannerLocation.location {
            choose(PlannerPlace(name: tr("planner.currentLocation"), lat: fix.coordinate.latitude,
                                lng: fix.coordinate.longitude, type: "CURRENT_LOCATION", description: nil, stop_id: nil), for: target)
            return
        }
        plannerLocation.retry()
        placePickerMessage = tr("planner.waitingLocation")
        Task { @MainActor in
            for _ in 0..<48 {
                if let fix = plannerLocation.location {
                    choose(PlannerPlace(name: tr("planner.currentLocation"), lat: fix.coordinate.latitude,
                                        lng: fix.coordinate.longitude, type: "CURRENT_LOCATION", description: nil, stop_id: nil), for: target)
                    return
                }
                if plannerLocation.unavailable {
                    placePickerMessage = tr("planner.locationUnavailable")
                    return
                }
                try? await Task.sleep(for: .milliseconds(250))
            }
            placePickerMessage = tr("planner.locationUnavailable")
        }
    }

    private func routeResults(_ raw: String) -> some View {
        let rows = parsedRows(raw)
        return VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label(tr(selectedRouteIndex == nil ? "planner.routes" : "planner.routeDetails"), systemImage: "point.topleft.down.to.point.bottomright.curvepath").font(.headline)
                Spacer()
                if selectedRouteIndex == nil {
                    Button { beginSaveDestination() } label: { Label(tr("planner.saveDestination"), systemImage: "bookmark") }.font(.subheadline)
                }
            }
            if let selectedRouteIndex, rows.indices.contains(selectedRouteIndex) {
                routeDetail(rows[selectedRouteIndex], index: selectedRouteIndex)
            } else {
                if rows.isEmpty {
                    Text(tr("planner.noRoutes")).font(.subheadline).foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading).padding(14)
                        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
                } else {
                    ForEach(rows.indices, id: \.self) { index in
                        let row = rows[index]
                        Button { withAnimation(.snappy) { selectedRouteIndex = index } } label: {
                            VStack(alignment: .leading, spacing: 10) {
                                HStack(alignment: .firstTextBaseline) {
                                    Text(row.title ?? "\(tr("planner.option")) \(index + 1)")
                                        .font(.subheadline.weight(.semibold)).foregroundStyle(.primary)
                                    Spacer()
                                    if let duration = row.duration {
                                        Label(duration, systemImage: "clock")
                                            .font(.caption.weight(.semibold)).foregroundStyle(InfoTBDesign.accent)
                                    }
                                }
                                if !row.legs.isEmpty {
                                    FlowLayout(spacing: 6, lineSpacing: 6) {
                                        ForEach(Array(row.legs.enumerated()), id: \.offset) { index, leg in
                                            if index > 0 { Image(systemName: "arrow.right").font(.caption2).foregroundStyle(.tertiary) }
                                            routeLegBadge(leg)
                                        }
                                    }
                                }
                                HStack {
                                    if let arrival = row.arrival { Label("\(tr("planner.arrives")) \(arrival)", systemImage: "flag.checkered").font(.caption).foregroundStyle(.secondary) }
                                    Spacer()
                                    Label(tr("planner.seeDetails"), systemImage: "chevron.right")
                                        .font(.caption.weight(.semibold)).foregroundStyle(InfoTBDesign.accent)
                                }
                            }
                            .padding(14).frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
                            .contentShape(RoundedRectangle(cornerRadius: 16))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private struct RouteRow { var title: String?; var duration: String?; var detail: String?; var arrival: String?; var vehicleCoordinate: CLLocationCoordinate2D?; var legs: [PlannerRouteLeg] }

    private func routeLegBadge(_ leg: PlannerRouteLeg) -> some View {
        HStack(spacing: 5) {
            if leg.isWalking {
                Image(systemName: "figure.walk").font(.caption.weight(.semibold))
            } else {
                VehicleGlyph(type: leg.glyphType, size: 12)
            }
            Text(leg.isWalking ? tr("planner.walk") : (leg.lineName ?? transportLabel(leg.glyphType)))
                .font(.caption.weight(.semibold)).lineLimit(1)
        }
        .foregroundStyle(leg.isWalking ? Color.secondary : (Color(hex: leg.color ?? "") ?? InfoTBDesign.accent))
        .padding(.horizontal, 9).padding(.vertical, 6)
        .background((leg.isWalking ? Color.secondary : (Color(hex: leg.color ?? "") ?? InfoTBDesign.accent)).opacity(0.10), in: Capsule())
    }

    private func routeDetail(_ row: RouteRow, index: Int) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Button { withAnimation(.snappy) { selectedRouteIndex = nil } } label: {
                    Label(tr("planner.backToResults"), systemImage: "chevron.left")
                        .font(.subheadline.weight(.semibold))
                }
                .buttonStyle(.plain).foregroundStyle(InfoTBDesign.accent)
                Spacer()
                Button(action: findRoutes) {
                    Label(tr("planner.refresh"), systemImage: "arrow.clockwise").font(.caption.weight(.semibold))
                }
                .buttonStyle(.plain).foregroundStyle(.secondary).disabled(loading)
            }
            HStack {
                Text(row.title ?? "\(tr("planner.option")) \(index + 1)").font(.headline)
                Spacer()
                if let duration = row.duration { Label(duration, systemImage: "clock").font(.subheadline.weight(.semibold)).foregroundStyle(InfoTBDesign.accent) }
            }
            if let origin, let destination {
                PlannerRouteMapPreview(origin: origin, destination: destination, legs: row.legs, vehicleCoordinate: row.vehicleCoordinate)
            }
            if let arrival = row.arrival {
                Label("\(tr("planner.arrives")) \(arrival)", systemImage: "flag.checkered")
                    .font(.subheadline.weight(.medium)).foregroundStyle(.secondary)
            }
            ForEach(Array(row.legs.enumerated()), id: \.offset) { _, leg in
                routeLegDetail(leg)
            }
        }
        .padding(14).frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 18))
    }

    private func routeLegDetail(_ leg: PlannerRouteLeg) -> some View {
        VStack(alignment: .leading, spacing: 7) {
            HStack(spacing: 9) {
                if leg.isWalking {
                    Image(systemName: "figure.walk").font(.body.weight(.semibold)).foregroundStyle(.secondary).frame(width: 24)
                } else {
                    VehicleGlyph(type: leg.glyphType, size: 16)
                        .foregroundStyle(Color(hex: leg.color ?? "") ?? InfoTBDesign.accent).frame(width: 24)
                }
                VStack(alignment: .leading, spacing: 3) {
                    Text(leg.isWalking ? tr("planner.walk") : (leg.lineName ?? transportLabel(leg.glyphType)))
                        .font(.subheadline.weight(.semibold))
                    if let direction = leg.direction, !direction.isEmpty {
                        Text(direction).font(.caption).foregroundStyle(.secondary)
                    }
                }
                Spacer(minLength: 4)
                if leg.hasLiveVehicle {
                    Label(tr("planner.live"), systemImage: "dot.radiowaves.left.and.right")
                        .font(.caption2.weight(.semibold)).foregroundStyle(Color.realtime)
                }
            }
            if let first = leg.stops.first, let last = leg.stops.last {
                HStack(spacing: 6) {
                    Text(first.name)
                    if let time = plannerClockTime(first.time ?? leg.startTime) { Text(time).monospacedDigit() }
                    Image(systemName: "arrow.right").font(.caption2).foregroundStyle(.tertiary)
                    Text(last.name)
                    if let time = plannerClockTime(last.time ?? leg.stopTime) { Text(time).monospacedDigit() }
                }
                .font(.caption).foregroundStyle(.secondary).lineLimit(1)
            } else if let start = plannerClockTime(leg.startTime), let stop = plannerClockTime(leg.stopTime) {
                Text("\(start) – \(stop)").font(.caption).foregroundStyle(.secondary).monospacedDigit()
            }
        }
        .padding(11).frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 13))
    }

    private func transportLabel(_ type: String) -> String {
        TransportType.ordered.first(where: { $0.name == type })?.label ?? type.capitalized
    }

    private func parsedRows(_ raw: String) -> [RouteRow] {
        guard let data = raw.data(using: .utf8), let root = try? JSONSerialization.jsonObject(with: data) else { return [] }
        func routeArray(_ value: Any) -> [[String: Any]]? {
            if let array = value as? [[String: Any]] { return array }
            guard let dict = value as? [String: Any] else { return nil }
            for key in ["routes", "options", "itineraries", "results", "data"] {
                if let found = dict[key], let rows = routeArray(found), !rows.isEmpty { return rows }
            }
            return nil
        }
        return (routeArray(root) ?? []).map { item in
            let durationValue = item["duration"] ?? item["total_duration"]
            let duration: String? = {
                if let string = durationValue as? String { return string }
                if let minutes = durationValue as? NSNumber { return "\(minutes.intValue) min" }
                return nil
            }()
            let segments = item["segments"] as? [[String: Any]] ?? item["steps"] as? [[String: Any]] ?? []
            let legs = segments.enumerated().compactMap { offset, segment -> PlannerRouteLeg? in
                let mode = (segment["type"] ?? segment["mode"] ?? segment["vehicle_type"]) as? String
                let line = segment["line"] as? [String: Any]
                let name = (line?["name"] ?? segment["line_name"] ?? segment["route_name"]) as? String
                let stopItems = segment["stops"] as? [[String: Any]] ?? []
                let stops = stopItems.compactMap { stop -> PlannerLegStop? in
                    guard let stopName = stop["name"] as? String else { return nil }
                    let lat = (stop["lat"] as? NSNumber)?.doubleValue
                    let lng = (stop["lng"] as? NSNumber)?.doubleValue
                    let coordinate: CLLocationCoordinate2D?
                    if let lat, let lng { coordinate = CLLocationCoordinate2D(latitude: lat, longitude: lng) }
                    else { coordinate = nil }
                    return PlannerLegStop(name: stopName, time: stop["time"] as? String, coordinate: coordinate)
                }
                let geometry = (segment["segment_path"] as? String).map {
                    PolylineKt.decodePolyline(encoded: $0).compactMap { pair -> CLLocationCoordinate2D? in
                        guard let lat = pair.first?.doubleValue, let lng = pair.second?.doubleValue else { return nil }
                        return CLLocationCoordinate2D(latitude: lat, longitude: lng)
                    }
                } ?? []
                let vehicleId = (segment["transport_vehicle_id"] as? NSNumber)?.int64Value ?? 0
                let vehicleCode = segment["transport_vehicle_code"] as? String
                let timetable = (segment["is_timetable"] as? NSNumber)?.boolValue ?? false
                let vehicleLat = (segment["transport_vehicle_lat"] as? NSNumber)?.doubleValue
                let vehicleLng = (segment["transport_vehicle_lng"] as? NSNumber)?.doubleValue
                let vehicleCoordinate: CLLocationCoordinate2D?
                if let vehicleLat, let vehicleLng {
                    vehicleCoordinate = CLLocationCoordinate2D(latitude: vehicleLat, longitude: vehicleLng)
                } else {
                    vehicleCoordinate = nil
                }
                return PlannerRouteLeg(
                    id: offset,
                    type: mode,
                    lineName: name,
                    direction: segment["direction_name"] as? String,
                    startTime: segment["start_time"] as? String,
                    stopTime: segment["stop_time"] as? String,
                    durationSeconds: (segment["duration_seconds"] as? NSNumber)?.intValue,
                    color: segment["transport_color"] as? String,
                    vehicleCode: vehicleCode,
                    hasLiveVehicle: vehicleId > 0 || (!timetable && vehicleCode != nil),
                    vehicleCoordinate: vehicleCoordinate,
                    stops: stops,
                    coordinates: geometry.isEmpty ? stops.compactMap(\.coordinate) : geometry
                )
            }
            return RouteRow(
                title: item["title"] as? String ?? item["name"] as? String,
                duration: duration,
                detail: item["summary"] as? String ?? item["description"] as? String,
                arrival: plannerClockTime(item["arrival_time"] as? String ?? item["arrival"] as? String ?? item["stop_time"] as? String),
                vehicleCoordinate: {
                    guard let lat = (item["transport_vehicle_lat"] as? NSNumber)?.doubleValue,
                          let lng = (item["transport_vehicle_lng"] as? NSNumber)?.doubleValue else { return nil }
                    return CLLocationCoordinate2D(latitude: lat, longitude: lng)
                }(),
                legs: legs
            )
        }
    }

    private func plannerClockTime(_ value: String?) -> String? {
        guard let value, !value.isEmpty else { return nil }
        if let timestamp = Double(value) {
            let seconds = timestamp > 100_000_000_000 ? timestamp / 1_000 : timestamp
            return Date(timeIntervalSince1970: seconds).formatted(date: .omitted, time: .shortened)
        }
        let formatter = ISO8601DateFormatter()
        return formatter.date(from: value)?.formatted(date: .omitted, time: .shortened) ?? value
    }

    private func search(_ value: String) {
        placeSearchTask?.cancel()
        guard value.trimmingCharacters(in: .whitespacesAndNewlines).count >= 2 else {
            suggestions = []
            searching = false
            placePickerMessage = nil
            return
        }
        let requested = value
        searching = true
        placePickerMessage = nil
        placeSearchTask = Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(250))
            guard !Task.isCancelled, requested == query else { return }
            let results = try? await IosServices.shared.searchPlannerPlaces(query: requested)
            guard !Task.isCancelled, requested == query else { return }
            suggestions = results ?? []
            searching = false
            if results == nil { placePickerMessage = tr("planner.searchFailed") }
        }
    }

    private func choose(_ place: PlannerPlace, for target: PlannerPlaceField) {
        if target == .origin { origin = place } else { destination = place }
        response = nil
        selectedRouteIndex = nil
        error = nil
        field = nil
    }

    private func findRoutes() {
        guard let origin, let destination else { return }
        loading = true
        error = nil
        response = nil
        selectedRouteIndex = nil
        let time = useCurrentTime ? Date() : departure
        Task {
            let result = try? await IosServices.shared.planRoutes(
                start: origin,
                destination: destination,
                maxWalkDistance: Int32(maxWalkDistance),
                startTimeMs: Int64(time.timeIntervalSince1970 * 1000),
                vehicleTypes: TransportType.ordered.filter { modes.contains($0.name) }.map(\.name),
                accessible: accessible,
                language: AppModel.shared.localeCode
            )
            await MainActor.run {
                loading = false
                if let result { response = result }
                else { error = IosServices.shared.lastPlannerError ?? tr("planner.loadFailed") }
            }
        }
    }

    private func beginSaveDestination() {
        guard let destination else { return }
        destinationLabel = destination.name
        isSavingDestination = true
    }

    private func saveDestination() {
        guard let destination else { return }
        let label = destinationLabel.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !label.isEmpty else { return }
        let place = SavedTrip.SavedPlace(destination)
        var saved = SavedDestination(label: label, place: place)
        if let existing = savedDestinations.first(where: { samePlace($0.place, place) }) {
            saved.id = existing.id
            savedDestinations.removeAll { $0.id == existing.id }
        }
        savedDestinations.insert(saved, at: 0)
        persistSavedDestinations()
        AppQuickActions.refresh(localeCode: app.localeCode)
    }

    private func samePlace(_ lhs: SavedTrip.SavedPlace, _ rhs: SavedTrip.SavedPlace) -> Bool {
        if let left = lhs.stopId, let right = rhs.stopId { return left == right }
        return abs(lhs.lat - rhs.lat) < 0.00005 && abs(lhs.lng - rhs.lng) < 0.00005
    }

    private func open(_ saved: SavedDestination) {
        AppQuickActions.recordUse(destinationID: saved.id, localeCode: app.localeCode)
        destination = saved.place.place
        origin = nil
        response = nil
        selectedRouteIndex = nil
        error = nil
        if let fix = plannerLocation.location {
            setCurrentOrigin(fix.coordinate)
            findRoutes()
        } else {
            loading = true
            error = tr("planner.waitingLocation")
            plannerLocation.retry()
            Task { @MainActor in
                for _ in 0..<48 {
                    if let fix = plannerLocation.location {
                        setCurrentOrigin(fix.coordinate)
                        error = nil
                        findRoutes()
                        return
                    }
                    if plannerLocation.unavailable { break }
                    try? await Task.sleep(for: .milliseconds(250))
                }
                loading = false
                error = tr("planner.locationUnavailable")
            }
        }
    }

    private func setCurrentOrigin(_ coordinate: CLLocationCoordinate2D) {
        origin = PlannerPlace(name: tr("planner.currentLocation"), lat: coordinate.latitude,
                              lng: coordinate.longitude, type: "CURRENT_LOCATION", description: nil, stop_id: nil)
    }

    private func handlePendingPlannerDestination() {
        guard let id = app.pendingPlannerDestinationID,
              let uuid = UUID(uuidString: id),
              let saved = savedDestinations.first(where: { $0.id == uuid }) else { return }
        _ = app.consumePendingPlannerDestination()
        open(saved)
    }

    private func delete(_ saved: SavedDestination) {
        savedDestinations.removeAll { $0.id == saved.id }
        persistSavedDestinations()
        AppQuickActions.refresh(localeCode: app.localeCode)
    }

    private func loadSavedTrips() {
        if let data = UserDefaults.standard.data(forKey: destinationsStorageKey),
           let destinations = try? JSONDecoder().decode([SavedDestination].self, from: data) {
            savedDestinations = destinations
            AppQuickActions.refresh(localeCode: app.localeCode)
            return
        }
        guard let data = UserDefaults.standard.data(forKey: legacyTripsStorageKey),
              let trips = try? JSONDecoder().decode([SavedTrip].self, from: data) else { return }
        savedDestinations = trips.map { SavedDestination(label: $0.to.name, place: $0.to) }
            .reduce(into: [SavedDestination]()) { result, saved in
                if !result.contains(where: { samePlace($0.place, saved.place) }) { result.append(saved) }
            }
        persistSavedDestinations()
    }

    private func persistSavedDestinations() {
        guard let data = try? JSONEncoder().encode(savedDestinations) else { return }
        UserDefaults.standard.set(data, forKey: destinationsStorageKey)
    }

}

private struct PlannerRouteMapPreview: View {
    let origin: PlannerPlace
    let destination: PlannerPlace
    let legs: [PlannerRouteLeg]
    let vehicleCoordinate: CLLocationCoordinate2D?

    private var start: CLLocationCoordinate2D { CLLocationCoordinate2D(latitude: origin.lat, longitude: origin.lng) }
    private var finish: CLLocationCoordinate2D { CLLocationCoordinate2D(latitude: destination.lat, longitude: destination.lng) }
    private var coordinates: [CLLocationCoordinate2D] { [start, finish] + legs.flatMap(\.coordinates) + (vehicleCoordinate.map { [$0] } ?? []) }
    private var region: MKCoordinateRegion {
        let latitudes = coordinates.map(\.latitude)
        let longitudes = coordinates.map(\.longitude)
        let minLat = latitudes.min() ?? start.latitude
        let maxLat = latitudes.max() ?? finish.latitude
        let minLng = longitudes.min() ?? start.longitude
        let maxLng = longitudes.max() ?? finish.longitude
        return MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: (minLat + maxLat) / 2, longitude: (minLng + maxLng) / 2),
            latitudinalMeters: max(1_000, (maxLat - minLat) * 111_000 * 1.25),
            longitudinalMeters: max(1_000, (maxLng - minLng) * 111_000 * 1.25)
        )
    }

    var body: some View {
        Map(position: .constant(.region(region)), interactionModes: [.pan, .zoom]) {
            ForEach(Array(legs.enumerated()), id: \.offset) { _, leg in
                if leg.coordinates.count > 1 {
                    MapPolyline(coordinates: leg.coordinates)
                        .stroke(Color(hex: leg.color ?? "") ?? InfoTBDesign.accent,
                                style: StrokeStyle(lineWidth: 5, lineCap: .round, lineJoin: .round))
                }
                if let vehicle = leg.vehicleCoordinate {
                    Annotation(leg.vehicleCode ?? leg.lineName ?? "Vehicle", coordinate: vehicle) {
                        VehicleGlyph(type: leg.glyphType, size: 12)
                            .foregroundStyle(.white).padding(6)
                            .background(Color(hex: leg.color ?? "") ?? InfoTBDesign.accent, in: Circle())
                            .overlay(Circle().stroke(.white, lineWidth: 1.5))
                    }
                }
            }
            Annotation("Start", coordinate: start) { endpointPin("A", color: InfoTBDesign.accent) }
            Annotation("Destination", coordinate: finish) { endpointPin("B", color: .red) }
            if let vehicleCoordinate {
                Annotation("Vehicle", coordinate: vehicleCoordinate) {
                    Image(systemName: "bus.fill").font(.caption.weight(.bold)).foregroundStyle(.white)
                        .padding(7).background(InfoTBDesign.accent, in: Circle()).overlay(Circle().stroke(.white, lineWidth: 2))
                }
            }
        }
        .mapStyle(.standard(pointsOfInterest: .excludingAll))
        .frame(height: 220)
        .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
    }

    private func endpointPin(_ label: String, color: Color) -> some View {
        Text(label).font(.caption.weight(.heavy)).foregroundStyle(.white)
            .frame(width: 28, height: 28).background(color, in: Circle())
            .overlay(Circle().stroke(.white, lineWidth: 2)).shadow(color: .black.opacity(0.2), radius: 2, y: 1)
    }
}

private struct PlaceFieldSheet: Identifiable {
    let field: PlannerPlaceField
    var id: Int { field == .origin ? 0 : 1 }
    init(_ field: PlannerPlaceField) { self.field = field }
}
