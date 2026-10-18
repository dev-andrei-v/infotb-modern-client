import ActivityKit
import Foundation

/// Manages Live Activities for transit arrival tracking.
/// Observes NSNotifications posted by Kotlin IosNotifier to bridge KMP → Swift.
@objc class LiveActivityManager: NSObject {

    @objc static let shared = LiveActivityManager()

    private var currentActivity: Any? = nil

    /// Extra per-update fields decoded from the Kotlin notification payload.
    private struct Extras {
        var hasArrived: Bool
        var stopsAway: Int?
        var distanceMeters: Int?
        var progress: Double?
        var nextMinutes: [Int]

        init(_ info: [AnyHashable: Any]) {
            hasArrived = (info["hasArrived"] as? Bool) ?? false
            let stops = (info["stopsAway"] as? NSNumber)?.intValue ?? -1
            stopsAway = stops >= 0 ? stops : nil
            let meters = (info["distanceMeters"] as? NSNumber)?.intValue ?? -1
            distanceMeters = meters >= 0 ? meters : nil
            let prog = (info["progress"] as? NSNumber)?.doubleValue ?? -1
            progress = prog >= 0 ? prog : nil
            nextMinutes = (info["nextMinutes"] as? [NSNumber])?.map { $0.intValue } ?? []
        }
    }

    private override init() {
        super.init()
        registerObservers()
    }

    // MARK: - NSNotification Bridge from Kotlin

    private func registerObservers() {
        let nc = NotificationCenter.default

        nc.addObserver(
            self,
            selector: #selector(handleStartTracking(_:)),
            name: NSNotification.Name("ro.infotb.modern.START_LIVE_TRACKING"),
            object: nil
        )
        nc.addObserver(
            self,
            selector: #selector(handleUpdateTracking(_:)),
            name: NSNotification.Name("ro.infotb.modern.UPDATE_LIVE_TRACKING"),
            object: nil
        )
        nc.addObserver(
            self,
            selector: #selector(handleStopTracking(_:)),
            name: NSNotification.Name("ro.infotb.modern.STOP_LIVE_TRACKING"),
            object: nil
        )
    }

    @objc private func handleStartTracking(_ notification: Notification) {
        guard let info = notification.userInfo else { return }
        startTracking(
            activityId: info["activityId"] as? String ?? "",
            lineName: info["lineName"] as? String ?? "",
            lineColor: info["lineColor"] as? String ?? "#007AFF",
            stopName: info["stopName"] as? String ?? "",
            stopId: (info["stopId"] as? NSNumber)?.int64Value ?? 0,
            lineId: (info["lineId"] as? NSNumber)?.int64Value ?? 0,
            direction: (info["direction"] as? NSNumber)?.intValue ?? 0,
            minutes: (info["minutes"] as? NSNumber)?.intValue ?? 0,
            isTimetable: (info["isTimetable"] as? Bool) ?? false,
            extras: Extras(info)
        )
    }

    @objc private func handleUpdateTracking(_ notification: Notification) {
        guard let info = notification.userInfo else { return }
        updateTracking(
            activityId: info["activityId"] as? String ?? "",
            lineName: info["lineName"] as? String ?? "",
            lineColor: info["lineColor"] as? String ?? "#007AFF",
            stopName: info["stopName"] as? String ?? "",
            stopId: (info["stopId"] as? NSNumber)?.int64Value ?? 0,
            lineId: (info["lineId"] as? NSNumber)?.int64Value ?? 0,
            direction: (info["direction"] as? NSNumber)?.intValue ?? 0,
            minutes: (info["minutes"] as? NSNumber)?.intValue ?? 0,
            isTimetable: (info["isTimetable"] as? Bool) ?? false,
            extras: Extras(info)
        )
    }

    @objc private func handleStopTracking(_ notification: Notification) {
        stopTracking()
    }

    /// End all live activities immediately. Called when the app is terminated.
    @objc func endAllActivities() {
        guard #available(iOS 16.2, *) else { return }
        Task {
            await endCurrentActivity()
            print("[LiveActivity] Ended all activities on app termination")
        }
    }

    // MARK: - ActivityKit Management

    private func startTracking(
        activityId: String,
        lineName: String,
        lineColor: String,
        stopName: String,
        stopId: Int64,
        lineId: Int64,
        direction: Int,
        minutes: Int,
        isTimetable: Bool,
        extras: Extras
    ) {
        guard #available(iOS 16.2, *) else { return }
        guard ActivityAuthorizationInfo().areActivitiesEnabled else {
            print("[LiveActivity] Activities not enabled by user")
            postFallback(info: [
                "lineName": lineName,
                "stopName": stopName,
                "minutes": minutes,
                "isTimetable": isTimetable
            ])
            return
        }

        Task {
            await endCurrentActivity()

            let attributes = TransitActivityAttributes(
                activityId: activityId,
                lineName: lineName,
                lineColor: lineColor,
                stopName: stopName,
                stopId: stopId,
                lineId: lineId,
                direction: direction
            )
            let state = makeState(minutes: minutes, isTimetable: isTimetable, extras: extras)

            do {
                let content = ActivityContent(state: state, staleDate: Date().addingTimeInterval(120))
                let activity = try Activity<TransitActivityAttributes>.request(
                    attributes: attributes,
                    content: content,
                    pushType: nil
                )
                currentActivity = activity
                print("[LiveActivity] Started: \(lineName) → \(stopName) in \(minutes) min")
            } catch {
                print("[LiveActivity] Failed to start: \(error.localizedDescription)")
                postFallback(info: [
                    "lineName": lineName,
                    "stopName": stopName,
                    "minutes": minutes,
                    "isTimetable": isTimetable
                ])
            }
        }
    }

    private func updateTracking(
        activityId: String,
        lineName: String,
        lineColor: String,
        stopName: String,
        stopId: Int64,
        lineId: Int64,
        direction: Int,
        minutes: Int,
        isTimetable: Bool,
        extras: Extras
    ) {
        guard #available(iOS 16.2, *) else { return }

        Task {
            guard let activity = currentActivity as? Activity<TransitActivityAttributes> else {
                startTracking(
                    activityId: activityId,
                    lineName: lineName,
                    lineColor: lineColor,
                    stopName: stopName,
                    stopId: stopId,
                    lineId: lineId,
                    direction: direction,
                    minutes: minutes,
                    isTimetable: isTimetable,
                    extras: extras
                )
                return
            }

            guard activity.attributes.activityId == activityId else {
                startTracking(
                    activityId: activityId,
                    lineName: lineName,
                    lineColor: lineColor,
                    stopName: stopName,
                    stopId: stopId,
                    lineId: lineId,
                    direction: direction,
                    minutes: minutes,
                    isTimetable: isTimetable,
                    extras: extras
                )
                return
            }

            let state = makeState(minutes: minutes, isTimetable: isTimetable, extras: extras)
            let content = ActivityContent(state: state, staleDate: Date().addingTimeInterval(120))
            await activity.update(content)
            print("[LiveActivity] Updated: \(minutes) min")
        }
    }

    private func makeState(minutes: Int, isTimetable: Bool, extras: Extras) -> TransitActivityAttributes.ContentState {
        TransitActivityAttributes.ContentState(
            minutes: minutes,
            isTimetable: isTimetable,
            hasArrived: extras.hasArrived,
            stopsAway: extras.stopsAway,
            distanceMeters: extras.distanceMeters,
            progress: extras.progress,
            nextMinutes: extras.nextMinutes
        )
    }

    private func stopTracking() {
        guard #available(iOS 16.2, *) else { return }
        stopTrackingInternal()
    }

    @available(iOS 16.2, *)
    private func stopTrackingInternal() {
        Task {
            await endCurrentActivity()
            print("[LiveActivity] Stopped all activities")
        }
    }

    private func postFallback(info: [String: Any]) {
        NotificationCenter.default.post(
            name: NSNotification.Name("ro.infotb.modern.LIVE_ACTIVITY_FAILED"),
            object: nil,
            userInfo: info
        )
    }

    @available(iOS 16.2, *)
    private func endCurrentActivity() async {
        if let activity = currentActivity as? Activity<TransitActivityAttributes> {
            await activity.end(nil, dismissalPolicy: .immediate)
            currentActivity = nil
            return
        }

        for activity in Activity<TransitActivityAttributes>.activities {
            await activity.end(nil, dismissalPolicy: .immediate)
        }
        currentActivity = nil
    }
}
