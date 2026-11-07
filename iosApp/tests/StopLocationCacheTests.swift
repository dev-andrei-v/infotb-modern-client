// Compile with StopLocationCache.swift against the simulator Shared framework,
// then run the executable with `xcrun simctl spawn <device> <executable>`.
import CoreLocation
import Foundation
import Shared

@main
enum StopLocationCacheTests {
    static func main() throws {
        let suite = "ro.infotb.cache-tests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let cache = StopLocationCache(defaults: defaults)
        let fix = CLLocation(latitude: 44.435, longitude: 26.1)
        func stop(_ id: Int64) -> Stop {
            Stop(id: id, lat: 44.435, lng: 26.1, name: "Stop \(id)", description: nil,
                 type: "STATION", iconColor: nil, lines: nil)
        }
        cache.storeArea([stop(1)], south: 44.41, west: 26.07, north: 44.46, east: 26.13)
        assert(cache.hasFreshCoverage(near: fix), "Fresh coverage avoids refetching coordinates")
        assert(!cache.hasFreshCoverage(near: CLLocation(latitude: 44.5, longitude: 26.2)), "Different areas need discovery")
        cache.markUsed(1)
        assert(StopLocationCache(defaults: defaults).stops[1]?.useCount == 1, "Usage and coordinates survive relaunch")

        var areas = try JSONSerialization.jsonObject(with: defaults.data(forKey: "stopLocationAreas.v1")!) as! [[String: Any]]
        areas[0]["updated"] = Date().addingTimeInterval(-8 * 86_400).timeIntervalSinceReferenceDate
        defaults.set(try JSONSerialization.data(withJSONObject: areas), forKey: "stopLocationAreas.v1")
        let expired = StopLocationCache(defaults: defaults)
        assert(!expired.hasFreshCoverage(near: fix), "Expired coverage must refresh")
        assert(expired.nearest(to: fix).count == 1, "Expired stops remain usable offline")

        cache.store((2...1_100).map { stop(Int64($0)) })
        assert(cache.stops.count == 1_000, "Coordinate storage stays bounded")
        assert(cache.stops[1] != nil, "Frequently used stops survive eviction")
        assert(!cache.hasFreshCoverage(near: fix), "Evicted areas cannot claim complete coverage")
        cache.storeArea([], south: 44.41, west: 26.07, north: 44.46, east: 26.13)
        assert(cache.stops.isEmpty, "A refreshed area removes obsolete stops")
        assert(cache.hasFreshCoverage(near: fix), "Empty areas are cached too")

        var a = CachedStop(id: 1, name: "Universitate", latitude: 44.435, longitude: 26.1)
        var b = CachedStop(id: 2, name: "  UNIVERSITATE  ", latitude: 44.4351, longitude: 26.1)
        let c = CachedStop(id: 3, name: "Izvor", latitude: 44.434, longitude: 26.09)
        a.lines = [CachedStopLine(id: 1, name: "61", color: "#1565C0", type: "BUS")]
        b.lines = [CachedStopLine(id: 1, name: "61", color: "#1565C0", type: "BUS"),
                   CachedStopLine(id: 2, name: "85", color: "#1565C0", type: "BUS")]
        let groups = NearbyStopGroup.groups([a, b, c])
        assert(groups.count == 2, "Direction-specific stops appear as one station")
        assert(groups[0].stops.map(\.id) == [1, 2], "Platforms remain individually accessible")
        assert(groups[0].nearest.id == 1, "The nearest physical location represents the station")
        assert(groups[0].lines.map(\.name) == ["61", "85"], "Serving lines are combined without duplicates")
        print("Stop location cache checks passed")
    }
}
