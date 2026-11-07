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

private enum PlannerPlaceField: Equatable { case origin, destination }

struct PlannerView: View {
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
    @State private var savedTrips: [SavedTrip] = []
    @State private var showAdvanced = false
    @State private var plannerLocation = ForegroundLocation()
    @State private var mapPickingTarget: PlannerPlaceField?
    @State private var mapPickPosition: MapCameraPosition = .region(Self.defaultMapRegion)
    @State private var mapPickCoordinate = CLLocationCoordinate2D(latitude: 44.435, longitude: 26.10)
    @State private var placePickerMessage: String?

    private let storageKey = "planner.savedTrips.v1"

    private static let defaultMapRegion = MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 44.435, longitude: 26.10),
        latitudinalMeters: 3_000,
        longitudinalMeters: 3_000
    )

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                placesCard
                departureCard
                modeCard
                advancedCard
                Button(action: findRoutes) {
                    HStack { if loading { ProgressView().tint(.white) }; Text(tr("planner.findRoutes")) }
                        .frame(maxWidth: .infinity).padding(.vertical, 5)
                }
                .buttonStyle(.borderedProminent)
                .disabled(loading || origin == nil || destination == nil || modes.isEmpty)

                if let error { Label(error, systemImage: "wifi.exclamationmark").font(.subheadline).foregroundStyle(.secondary) }
                if let response { routeResults(response) }
                savedSection
            }
            .padding()
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle(tr("planner.title"))
        .navigationBarTitleDisplayMode(.inline)
        .foregroundLocation(plannerLocation)
        .task { loadSavedTrips() }
        .sheet(item: Binding(get: { field.map(PlaceFieldSheet.init) }, set: { field = $0?.field })) { item in
            placePicker(item.field)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
    }

    private var placesCard: some View {
        VStack(spacing: 0) {
            placeRow(title: tr("planner.from"), place: origin, icon: "circle.fill", field: .origin)
            Divider().padding(.leading, 46)
            placeRow(title: tr("planner.to"), place: destination, icon: "mappin.and.ellipse", field: .destination)
            if origin != nil && destination != nil {
                Button {
                    swap(&origin, &destination)
                } label: { Label(tr("planner.swap"), systemImage: "arrow.up.arrow.down") }
                .font(.caption.weight(.semibold)).frame(maxWidth: .infinity, alignment: .trailing).padding(.top, 10)
            }
        }
        .padding(16)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 20))
    }

    private func placeRow(title: String, place: PlannerPlace?, icon: String, field target: PlannerPlaceField) -> some View {
        Button {
            query = ""
            suggestions = []
            field = target
        } label: {
            HStack(spacing: 14) {
                Image(systemName: icon).foregroundStyle(target == .origin ? Color.accentColor : Color.red).frame(width: 20)
                VStack(alignment: .leading, spacing: 3) {
                    Text(title.uppercased()).font(.caption2.weight(.semibold)).foregroundStyle(.secondary)
                    Text(place?.name ?? tr(target == .origin ? "planner.chooseOrigin" : "planner.chooseDestination"))
                        .font(.body.weight(.medium)).foregroundStyle(place == nil ? Color.secondary : Color.primary).lineLimit(1)
                }
                Spacer()
                Image(systemName: "magnifyingglass").foregroundStyle(.tertiary)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var departureCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label(tr("planner.departure"), systemImage: "clock").font(.headline)
            Toggle(tr("planner.leaveNow"), isOn: $useCurrentTime)
            if !useCurrentTime {
                DatePicker(tr("planner.dateAndTime"), selection: $departure, in: Date()..., displayedComponents: [.date, .hourAndMinute])
                    .datePickerStyle(.compact)
            }
        }
        .padding(16).background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 20))
    }

    private var modeCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label(tr("planner.vehicles"), systemImage: "bus").font(.headline)
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                ForEach(TransportType.ordered, id: \.name) { type in
                    let isOn = modes.contains(type.name)
                    Button {
                        if isOn { modes.remove(type.name) } else { modes.insert(type.name) }
                    } label: {
                        Label(type.label, systemImage: modeIcon(type.name))
                            .font(.subheadline.weight(.medium)).frame(maxWidth: .infinity, minHeight: 40)
                            .foregroundStyle(isOn ? type.color : Color.secondary)
                            .background(isOn ? type.color.opacity(0.12) : Color(.tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(isOn ? type.color.opacity(0.35) : .clear, lineWidth: 1))
                    }.buttonStyle(.plain)
                }
            }
        }
        .padding(16).background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 20))
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
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Label(tr("planner.savedTrips"), systemImage: "bookmark.fill").font(.headline)
                Spacer()
                if !savedTrips.isEmpty { Text("\(savedTrips.count)").font(.caption.weight(.semibold)).foregroundStyle(.secondary) }
            }
            if savedTrips.isEmpty {
                Text(tr("planner.noSavedTrips")).font(.subheadline).foregroundStyle(.secondary)
            } else {
                ForEach(savedTrips) { trip in
                    HStack(spacing: 12) {
                        Button { open(trip) } label: {
                            HStack(spacing: 12) {
                            Image(systemName: "arrow.triangle.swap").foregroundStyle(Color.accentColor)
                            VStack(alignment: .leading, spacing: 3) {
                                Text("\(trip.from.name) → \(trip.to.name)").font(.subheadline.weight(.semibold)).foregroundStyle(.primary).lineLimit(1)
                                Text(trip.departure.formatted(date: .abbreviated, time: .shortened)).font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            }
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        Button(role: .destructive) { delete(trip) } label: { Image(systemName: "trash").foregroundStyle(.secondary) }
                            .buttonStyle(.plain)
                    }
                    .padding(12).background(Color(.tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14))
                }
            }
        }
        .padding(16).background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 20))
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
                HStack(spacing: 10) {
                    Button { useCurrentLocation(for: target) } label: {
                        Label(tr("planner.currentLocation"), systemImage: "location.fill")
                            .frame(maxWidth: .infinity, minHeight: 40)
                    }
                    .buttonStyle(.bordered)
                    Button { beginMapPick(for: target) } label: {
                        Label(tr("planner.pickOnMap"), systemImage: "map")
                            .frame(maxWidth: .infinity, minHeight: 40)
                    }
                    .buttonStyle(.bordered)
                }
                .padding(.horizontal)
                if let placePickerMessage {
                    Text(placePickerMessage).font(.caption).foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 16).padding(.top, 6)
                }
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
                Spacer(minLength: 0)
            }
            .navigationTitle(tr(target == .origin ? "planner.chooseOrigin" : "planner.chooseDestination"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button(tr("planner.cancel")) { field = nil } } }
        }
    }

    private func mapPlacePicker(_ target: PlannerPlaceField) -> some View {
        NavigationStack {
            VStack(spacing: 0) {
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
                Text("\(mapPickCoordinate.latitude, format: .number.precision(.fractionLength(4))), \(mapPickCoordinate.longitude, format: .number.precision(.fractionLength(4)))")
                    .font(.caption.monospacedDigit()).foregroundStyle(.secondary).padding(.vertical, 8)
                Button {
                    choose(PlannerPlace(name: tr("planner.mapPoint"), lat: mapPickCoordinate.latitude,
                                        lng: mapPickCoordinate.longitude, type: "LOCATION", description: nil, stop_id: nil), for: target)
                    mapPickingTarget = nil
                } label: {
                    Text(tr("planner.useMapPoint")).frame(maxWidth: .infinity).padding(.vertical, 5)
                }
                .buttonStyle(.borderedProminent).padding(.horizontal).padding(.bottom, 8)
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
                Label(tr("planner.routes"), systemImage: "point.topleft.down.to.point.bottomright.curvepath").font(.headline)
                Spacer()
                Button { saveCurrentTrip() } label: { Label(tr("planner.saveTrip"), systemImage: "bookmark") }.font(.subheadline)
            }
            if rows.isEmpty {
                Text(raw).font(.footnote.monospaced()).textSelection(.enabled).lineLimit(18)
                    .frame(maxWidth: .infinity, alignment: .leading).padding(12).background(Color(.tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
            } else {
                ForEach(rows.indices, id: \.self) { index in
                    let row = rows[index]
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text(row.title ?? "\(tr("planner.option")) \(index + 1)").font(.headline)
                            Spacer()
                            if let duration = row.duration { Label(duration, systemImage: "clock").font(.caption.weight(.semibold)).foregroundStyle(Color.accentColor) }
                        }
                        if let detail = row.detail { Text(detail).font(.subheadline).foregroundStyle(.secondary) }
                        if !row.lines.isEmpty { Text(row.lines.joined(separator: "  ·  ")).font(.subheadline.weight(.semibold)).foregroundStyle(.primary) }
                        if let arrival = row.arrival { Text("\(tr("planner.arrives")) \(arrival)").font(.caption).foregroundStyle(.secondary) }
                    }
                    .padding(14).frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
                }
            }
        }
    }

    private struct RouteRow { var title: String?; var duration: String?; var detail: String?; var arrival: String?; var lines: [String] }

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
            let legs = segments.compactMap { segment -> String? in
                let mode = (segment["type"] ?? segment["mode"] ?? segment["vehicle_type"]) as? String
                let line = segment["line"] as? [String: Any]
                let name = (line?["name"] ?? segment["line_name"] ?? segment["route_name"]) as? String
                guard mode != nil || name != nil else { return nil }
                return [mode?.capitalized, name].compactMap { $0 }.joined(separator: " ")
            }
            return RouteRow(
                title: item["title"] as? String ?? item["name"] as? String,
                duration: duration,
                detail: item["summary"] as? String ?? item["description"] as? String,
                arrival: item["arrival_time"] as? String ?? item["arrival"] as? String,
                lines: legs
            )
        }
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
        field = nil
    }

    private func findRoutes() {
        guard let origin, let destination else { return }
        loading = true
        error = nil
        response = nil
        let time = useCurrentTime ? Date() : departure
        Task {
            let result = try? await IosServices.shared.planRoutes(
                start: origin,
                destination: destination,
                maxWalkDistance: Int32(maxWalkDistance),
                startTimeMs: Int64(time.timeIntervalSince1970 * 1000),
                vehicleTypes: TransportType.ordered.filter { modes.contains($0.name) }.map(\.name),
                accessible: accessible
            )
            await MainActor.run {
                loading = false
                if let result { response = result } else { error = tr("planner.loadFailed") }
            }
        }
    }

    private func saveCurrentTrip() {
        guard let origin, let destination else { return }
        let trip = SavedTrip(from: origin, to: destination, departure: departure, useCurrentTime: useCurrentTime, modes: Array(modes), accessible: accessible, maxWalkDistance: maxWalkDistance)
        savedTrips.removeAll { $0.from.name == trip.from.name && $0.to.name == trip.to.name }
        savedTrips.insert(trip, at: 0)
        persistSavedTrips()
    }

    private func open(_ trip: SavedTrip) {
        origin = trip.from.place
        destination = trip.to.place
        departure = trip.departure
        useCurrentTime = trip.useCurrentTime
        modes = Set(trip.modes)
        accessible = trip.accessible
        maxWalkDistance = trip.maxWalkDistance
        findRoutes()
    }

    private func delete(_ trip: SavedTrip) { savedTrips.removeAll { $0.id == trip.id }; persistSavedTrips() }
    private func loadSavedTrips() {
        guard let data = UserDefaults.standard.data(forKey: storageKey), let trips = try? JSONDecoder().decode([SavedTrip].self, from: data) else { return }
        savedTrips = trips
    }
    private func persistSavedTrips() {
        guard let data = try? JSONEncoder().encode(savedTrips) else { return }
        UserDefaults.standard.set(data, forKey: storageKey)
    }

    private func modeIcon(_ type: String) -> String {
        switch type {
        case TransportType.bus.name: "bus.fill"
        case TransportType.tram.name: "tram.fill"
        case TransportType.cableCar.name: "trolleybus"
        default: "tram.tunnel.fill"
        }
    }
}

private struct PlaceFieldSheet: Identifiable {
    let field: PlannerPlaceField
    var id: Int { field == .origin ? 0 : 1 }
    init(_ field: PlannerPlaceField) { self.field = field }
}
