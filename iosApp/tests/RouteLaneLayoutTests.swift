// swiftc -module-cache-path /tmp/infotb-swift-module-cache iosApp/iosApp/App/RouteLaneLayout.swift iosApp/tests/RouteLaneLayoutTests.swift -o /tmp/route-lane-tests && /tmp/route-lane-tests
import CoreLocation
import Foundation

@main
enum RouteLaneLayoutTests {
    static func main() {
        let road = [CLLocationCoordinate2D(latitude: 44.43, longitude: 26.10),
                    CLLocationCoordinate2D(latitude: 44.44, longitude: 26.10)]
        func path(_ id: String, _ line: Int64, _ points: [CLLocationCoordinate2D] = road) -> RouteLanePath {
            RouteLanePath(id: id, lineId: line, coordinates: points)
        }
        let shared = RouteLaneLayout(paths: [path("a", 1), path("b", 2)]).coordinates(metersPerPoint: 1)
        assert(abs(shared["a"]![10].longitude - shared["b"]![10].longitude) > 0.00005,
               "Different lines must remain visible on the same street")
        let zoomed = RouteLaneLayout(paths: [path("a", 1), path("b", 2)]).coordinates(metersPerPoint: 2)
        let gap = abs(shared["a"]![10].longitude - shared["b"]![10].longitude)
        let zoomedGap = abs(zoomed["a"]![10].longitude - zoomed["b"]![10].longitude)
        assert(abs(zoomedGap / gap - 2) < 0.001, "Lane spacing must follow map zoom")

        let sameLine = RouteLaneLayout(paths: [path("a", 1), path("b", 1, road.reversed())]).coordinates(metersPerPoint: 1)
        assert(abs(sameLine["a"]![10].longitude - 26.10) < 0.000001, "Directions of one line do not create extra lanes")
        let cross = [CLLocationCoordinate2D(latitude: 44.435, longitude: 26.09),
                     CLLocationCoordinate2D(latitude: 44.435, longitude: 26.11)]
        let crossing = RouteLaneLayout(paths: [path("a", 1), path("b", 2, cross)]).coordinates(metersPerPoint: 1)
        assert(crossing["a"]!.allSatisfy { abs($0.longitude - 26.10) < 0.000001 }, "Crossing streets are not shared corridors")
        let single = RouteLaneLayout(paths: [path("a", 1)]).coordinates(metersPerPoint: 1)
        assert(abs(single["a"]!.last!.latitude - 44.44) < 0.000001, "Unshared routes retain their geometry")
        assert(RouteLaneLayout(paths: []).coordinates(metersPerPoint: 1).isEmpty)
        print("Route lane layout checks passed")
    }
}
