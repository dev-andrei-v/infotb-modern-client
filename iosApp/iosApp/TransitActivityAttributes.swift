import ActivityKit
import AppIntents
import Foundation
import SwiftUI

struct TransitActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var minutes: Int
        var isTimetable: Bool
        /// True once the tracked vehicle reached the stop; the island shows "Arrived".
        var hasArrived: Bool
        /// Stops the vehicle still has to reach (1 = yours is next). nil when unknown.
        var stopsAway: Int?
        /// Distance along the route from the vehicle to the stop. nil when unknown.
        var distanceMeters: Int?
        /// 0...1 position of the vehicle within the last few stops before yours. nil when unknown.
        var progress: Double?
        /// Minutes until the arrivals after this one.
        var nextMinutes: [Int]

        init(
            minutes: Int,
            isTimetable: Bool,
            hasArrived: Bool = false,
            stopsAway: Int? = nil,
            distanceMeters: Int? = nil,
            progress: Double? = nil,
            nextMinutes: [Int] = []
        ) {
            self.minutes = minutes
            self.isTimetable = isTimetable
            self.hasArrived = hasArrived
            self.stopsAway = stopsAway
            self.distanceMeters = distanceMeters
            self.progress = progress
            self.nextMinutes = nextMinutes
        }

        // Tolerate payloads written by older app versions.
        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            minutes = try c.decode(Int.self, forKey: .minutes)
            isTimetable = try c.decode(Bool.self, forKey: .isTimetable)
            hasArrived = try c.decodeIfPresent(Bool.self, forKey: .hasArrived) ?? false
            stopsAway = try c.decodeIfPresent(Int.self, forKey: .stopsAway)
            distanceMeters = try c.decodeIfPresent(Int.self, forKey: .distanceMeters)
            progress = try c.decodeIfPresent(Double.self, forKey: .progress)
            nextMinutes = try c.decodeIfPresent([Int].self, forKey: .nextMinutes) ?? []
        }
    }

    var activityId: String
    var lineName: String
    var lineColor: String
    var stopName: String
    var stopId: Int64
    var lineId: Int64
    var direction: Int
    /// BUS, TRAM, CABLE_CAR (trolleybus) or SUBWAY; drives the vehicle glyph.
    var lineType: String

    init(
        activityId: String,
        lineName: String,
        lineColor: String,
        stopName: String,
        stopId: Int64,
        lineId: Int64,
        direction: Int,
        lineType: String = "BUS"
    ) {
        self.activityId = activityId
        self.lineName = lineName
        self.lineColor = lineColor
        self.stopName = stopName
        self.stopId = stopId
        self.lineId = lineId
        self.direction = direction
        self.lineType = lineType
    }

    // Tolerate activities started by older app versions.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        activityId = try c.decode(String.self, forKey: .activityId)
        lineName = try c.decode(String.self, forKey: .lineName)
        lineColor = try c.decode(String.self, forKey: .lineColor)
        stopName = try c.decode(String.self, forKey: .stopName)
        stopId = try c.decode(Int64.self, forKey: .stopId)
        lineId = try c.decode(Int64.self, forKey: .lineId)
        direction = try c.decode(Int.self, forKey: .direction)
        lineType = try c.decodeIfPresent(String.self, forKey: .lineType) ?? "BUS"
    }
}

/// The vehicle marker used in the Live Activity: a bus, tram, trolleybus or
/// metro glyph depending on the tracked line's type.
struct VehicleGlyph: View {
    let type: String
    let size: CGFloat

    var body: some View {
        switch type.uppercased() {
        case "TRAM":
            Image(systemName: "tram.fill")
                .font(.system(size: size, weight: .bold))
        case "SUBWAY", "METRO":
            Image(systemName: "tram.tunnel.fill")
                .font(.system(size: size, weight: .bold))
        case "CABLE_CAR", "TROLLEYBUS":
            // No SF Symbol exists for a trolleybus: bus body plus two poles to the wire.
            ZStack {
                Image(systemName: "bus.fill")
                    .font(.system(size: size * 0.9, weight: .bold))
                    .offset(y: size * 0.12)
                Path { p in
                    p.move(to: CGPoint(x: size * 0.42, y: size * 0.02))
                    p.addLine(to: CGPoint(x: size * 0.30, y: size * 0.30))
                    p.move(to: CGPoint(x: size * 0.78, y: size * 0.02))
                    p.addLine(to: CGPoint(x: size * 0.66, y: size * 0.30))
                }
                .stroke(style: StrokeStyle(lineWidth: max(1, size * 0.11), lineCap: .round))
                .frame(width: size, height: size)
            }
            .frame(width: size, height: size)
        default:
            Image(systemName: "bus.fill")
                .font(.system(size: size, weight: .bold))
        }
    }
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
