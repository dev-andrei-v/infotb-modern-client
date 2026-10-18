import ActivityKit
import AppIntents
import Foundation

struct TransitActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var minutes: Int
        var isTimetable: Bool
        /// True once the tracked vehicle reached the stop; the island shows "Arrived".
        var hasArrived: Bool

        init(minutes: Int, isTimetable: Bool, hasArrived: Bool = false) {
            self.minutes = minutes
            self.isTimetable = isTimetable
            self.hasArrived = hasArrived
        }

        // Tolerate payloads written before `hasArrived` existed.
        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            minutes = try c.decode(Int.self, forKey: .minutes)
            isTimetable = try c.decode(Bool.self, forKey: .isTimetable)
            hasArrived = try c.decodeIfPresent(Bool.self, forKey: .hasArrived) ?? false
        }
    }

    var activityId: String
    var lineName: String
    var lineColor: String
    var stopName: String
    var stopId: Int64
    var lineId: Int64
    var direction: Int
}

/// Name of the NotificationCenter message posted (inside the app process) when
/// the user asks to stop tracking from the Dynamic Island or lock screen.
/// The Kotlin `IosNotifier` observes it and ends the session.
let userStopTrackingNotification = NSNotification.Name("ro.infotb.modern.USER_STOP_TRACKING")

/// "Stop" button for the Live Activity. A `LiveActivityIntent` runs in the
/// app's own process, so posting to NotificationCenter reaches the engine.
@available(iOS 17.0, *)
struct StopTrackingIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "Stop tracking"
    static var description = IntentDescription("Stops tracking the current arrival.")
    static var openAppWhenRun: Bool = false

    func perform() async throws -> some IntentResult {
        NotificationCenter.default.post(name: userStopTrackingNotification, object: nil)
        return .result()
    }
}
