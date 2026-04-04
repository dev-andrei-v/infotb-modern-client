import ActivityKit
import Foundation

struct TransitActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var minutes: Int
        var isTimetable: Bool
    }

    var activityId: String
    var lineName: String
    var lineColor: String
    var stopName: String
    var stopId: Int64
    var lineId: Int64
    var direction: Int
}
