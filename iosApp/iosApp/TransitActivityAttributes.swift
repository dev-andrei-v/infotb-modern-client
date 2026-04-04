import ActivityKit
import Foundation

struct TransitActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var minutes: Int
        var isTimetable: Bool
    }

    var lineName: String
    var lineColor: String
    var stopName: String
}
