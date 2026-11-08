import CoreLocation
import Foundation
import Observation
import Shared

struct CachedStopLine: Codable, Identifiable {
    let id: Int64
    let name: String
    let color: String
    let type: String
}

struct CachedStop: Codable, Identifiable {
    let id: Int64
    var name: String
    var latitude: Double
    var longitude: Double
    var type: String?
    var color: String?
    var description: String?
    var lines: [CachedStopLine] = []
    var linesUpdated: Date?
    var updated = Date()
    var lastUsed = Date.distantPast
    var useCount = 0

    var coordinate: CLLocationCoordinate2D { .init(latitude: latitude, longitude: longitude) }
    func distance(from location: CLLocation) -> Double {
        location.distance(from: CLLocation(latitude: latitude, longitude: longitude))
    }
}

private struct CachedStopArea: Codable {
    let south: Double
    let west: Double
    let north: Double
    let east: Double
    var updated = Date()

    func contains(latitude: Double, longitude: Double) -> Bool {
        (south...north).contains(latitude) && (west...east).contains(longitude)
    }

    func contains(south: Double, west: Double, north: Double, east: Double) -> Bool {
        south >= self.south && west >= self.west && north <= self.north && east <= self.east
    }
}

/// A bounded, on-device stop catalogue. Frequent and recently opened stops
/// survive eviction; map searches refresh coordinates, line memberships daily.
@Observable
final class StopLocationCache {
    static let shared = StopLocationCache()
    private static let key = "stopLocationCache.v1"
    private static let areasKey = "stopLocationAreas.v1"
    static let coordinateTTL: TimeInterval = 7 * 86_400
    static let linesTTL: TimeInterval = 86_400
    private(set) var stops: [Int64: CachedStop]
    private var areas: [CachedStopArea]

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        let records = defaults.data(forKey: Self.key)
            .flatMap { try? JSONDecoder().decode([CachedStop].self, from: $0) } ?? []
        stops = Dictionary(records.map { ($0.id, $0) }, uniquingKeysWith: { _, newer in newer })
        areas = defaults.data(forKey: Self.areasKey)
            .flatMap { try? JSONDecoder().decode([CachedStopArea].self, from: $0) } ?? []
    }

    private let defaults: UserDefaults

    func store(_ values: [Stop]) {
        var updatedStops = stops
        for stop in values {
            merge(stop, into: &updatedStops)
        }
        stops = updatedStops
        persist()
    }

    private func merge(_ stop: Stop, into records: inout [Int64: CachedStop]) {
        let coordinate = CLLocationCoordinate2D(latitude: stop.lat, longitude: stop.lng)
        guard CLLocationCoordinate2DIsValid(coordinate), stop.lat != 0 || stop.lng != 0 else { return }
        var record = records[stop.id] ?? CachedStop(id: stop.id, name: stop.name, latitude: stop.lat, longitude: stop.lng)
        record.name = stop.name
        record.latitude = stop.lat
        record.longitude = stop.lng
        record.type = stop.type ?? record.type
        record.color = stop.iconColor ?? record.color
        record.description = stop.description_ ?? record.description
        record.updated = .now
        if let lines = stop.lines {
            var seen: Set<Int64> = []
            record.lines = lines.map { CachedStopLine(id: $0.id, name: $0.name, color: $0.color, type: $0.type.name) }
                .filter { seen.insert($0.id).inserted }
                .sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
            record.linesUpdated = .now
        }
        records[stop.id] = record
    }

    func storeLines(_ lines: [LineArrival], for stopId: Int64, persist shouldPersist: Bool = true) {
        setLines(lines.map { CachedStopLine(id: $0.id, name: $0.name, color: $0.color, type: $0.type.name) }, for: stopId, persist: shouldPersist)
    }

    func storeLines(_ lines: [Line], for stopId: Int64) {
        setLines(lines.map { CachedStopLine(id: $0.id, name: $0.name, color: $0.color, type: $0.type.name) }, for: stopId)
    }

    func storeLines(_ updates: [(stopId: Int64, lines: [Line])]) {
        var updatedStops = stops
        for (stopId, lines) in updates {
            guard var record = updatedStops[stopId] else { continue }
            var seen: Set<Int64> = []
            record.lines = lines.map { CachedStopLine(id: $0.id, name: $0.name, color: $0.color, type: $0.type.name) }
                .filter { seen.insert($0.id).inserted }
                .sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
            record.linesUpdated = .now
            updatedStops[stopId] = record
        }
        stops = updatedStops
        persist()
    }

    private func setLines(_ lines: [CachedStopLine], for stopId: Int64, persist shouldPersist: Bool = true) {
        guard var record = stops[stopId] else { return }
        var seen: Set<Int64> = []
        record.lines = lines.filter { seen.insert($0.id).inserted }
            .sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
        record.linesUpdated = .now
        stops[stopId] = record
        if shouldPersist { persist() }
    }

    func hasFreshCoverage(near location: CLLocation) -> Bool {
        let lat = location.coordinate.latitude, lng = location.coordinate.longitude
        let dLat = 1_100.0 / 111_000
        let dLng = dLat / max(0.1, cos(lat * .pi / 180))
        return areas.contains {
            Date().timeIntervalSince($0.updated) < Self.coordinateTTL &&
                $0.contains(latitude: lat - dLat, longitude: lng - dLng) &&
                $0.contains(latitude: lat + dLat, longitude: lng + dLng)
        }
    }

    func hasFreshCoverage(south: Double, west: Double, north: Double, east: Double) -> Bool {
        areas.contains {
            Date().timeIntervalSince($0.updated) < Self.coordinateTTL &&
                $0.contains(south: south, west: west, north: north, east: east)
        }
    }

    func storeArea(_ values: [Stop], south: Double, west: Double, north: Double, east: Double) {
        let area = CachedStopArea(south: south, west: west, north: north, east: east)
        let valid = values.filter { $0.type == "STATION" || $0.type == "SUBWAY_STATION" }
        let ids = Set(valid.map(\.id))
        // A successful refresh also removes stops that no longer exist here.
        var updatedStops = stops.filter { ids.contains($0.key) || !area.contains(latitude: $0.value.latitude, longitude: $0.value.longitude) }
        for stop in valid { merge(stop, into: &updatedStops) }
        stops = updatedStops
        areas.removeAll {
            Date().timeIntervalSince($0.updated) >= Self.coordinateTTL ||
                (area.contains(latitude: $0.south, longitude: $0.west) && area.contains(latitude: $0.north, longitude: $0.east))
        }
        if valid.allSatisfy({ stops[$0.id] != nil }) { areas.append(area) }
        areas = Array(areas.suffix(32))
        persist()
    }

    func markUsed(_ id: Int64) {
        guard var record = stops[id] else { return }
        record.useCount += 1
        record.lastUsed = .now
        stops[id] = record
        persist()
    }

    func nearest(to location: CLLocation, radius: Double = 1_000) -> [CachedStop] {
        var candidates: [(stop: CachedStop, distance: Double)] = []
        for stop in stops.values {
            let distance = stop.distance(from: location)
            if distance <= radius { candidates.append((stop, distance)) }
        }
        candidates.sort { a, b in
            if a.distance == b.distance { return a.stop.id < b.stop.id }
            return a.distance < b.distance
        }
        return candidates.map { $0.stop }
    }

    private func persist() {
        // Prioritize actual usage, then recent discovery. The cache never grows
        // without bound when the rider travels through different areas.
        let retained = Array(stops.values.sorted {
            if $0.useCount != $1.useCount { return $0.useCount > $1.useCount }
            if $0.lastUsed != $1.lastUsed { return $0.lastUsed > $1.lastUsed }
            return $0.updated > $1.updated
        }.prefix(1_000))
        let retainedIds = Set(retained.map(\.id))
        let evicted = stops.values.filter { !retainedIds.contains($0.id) }
        if !evicted.isEmpty {
            stops = Dictionary(uniqueKeysWithValues: retained.map { ($0.id, $0) })
            // Eviction can make a saved area's catalogue incomplete; refetch it
            // next time instead of treating missing stops as a valid empty area.
            areas.removeAll { area in evicted.contains { area.contains(latitude: $0.latitude, longitude: $0.longitude) } }
        }
        if let data = try? JSONEncoder().encode(retained) { defaults.set(data, forKey: Self.key) }
        if let data = try? JSONEncoder().encode(areas) { defaults.set(data, forKey: Self.areasKey) }
    }
}
