import Foundation

enum LineStopPreferences {
    static let countKey = "lineNearbyStopCount"
    static let defaultCount = 3
    static let maximumCount = 5
}

/// Indices retain the original route numbering, even when nearby stops move up.
struct NearbyStopSelection {
    let nearby: [Int]

    init(distances: [Double]?, stopCount: Int, count: Int) {
        let indices = Array(0..<stopCount)
        guard let distances, distances.count == stopCount, count > 0 else {
            nearby = []
            return
        }
        nearby = Array(indices.filter { distances[$0].isFinite && distances[$0] >= 0 }
            .sorted {
                distances[$0] == distances[$1] ? $0 < $1 : distances[$0] < distances[$1]
            }
            .prefix(min(count, LineStopPreferences.maximumCount)))
    }
}
