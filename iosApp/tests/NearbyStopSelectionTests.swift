// Run from the repository root:
// swiftc iosApp/iosApp/App/NearbyStopSelection.swift iosApp/tests/NearbyStopSelectionTests.swift -o /tmp/nearby-stop-tests && /tmp/nearby-stop-tests
import Foundation

@main
enum NearbyStopSelectionTests {
    static func main() {
        let nearest = NearbyStopSelection(distances: [500, 100, 300, 100, 900], stopCount: 5, count: 2)
        assert(nearest.nearby == [1, 3], "Equal distances keep route order")

        for distances: [Double]? in [nil, [100], [100, 200, 300]] {
            let off = NearbyStopSelection(distances: distances, stopCount: 3, count: 0)
            assert(off.nearby.isEmpty, "Off always preserves the full route")
        }
        let missing = NearbyStopSelection(distances: nil, stopCount: 3, count: 3)
        assert(missing.nearby.isEmpty, "No location preserves all stops")
        let invalid = NearbyStopSelection(distances: [.nan, .infinity, -1, 50], stopCount: 4, count: 3)
        assert(invalid.nearby == [3], "Invalid coordinates cannot become nearby stops")
        let short = NearbyStopSelection(distances: [20, 10], stopCount: 2, count: 5)
        assert(short.nearby == [1, 0], "Short routes do not duplicate stops")
        let capped = NearbyStopSelection(distances: Array(repeating: 1, count: 8), stopCount: 8, count: 100)
        assert(capped.nearby == [0, 1, 2, 3, 4])
        let empty = NearbyStopSelection(distances: [], stopCount: 0, count: 3)
        assert(empty.nearby.isEmpty)
        print("Nearby stop selection checks passed")
    }
}
