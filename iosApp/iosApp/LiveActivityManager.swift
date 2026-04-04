import ActivityKit
import Foundation

/// Manages Live Activities for transit arrival tracking.
/// Observes NSNotifications posted by Kotlin IosNotifier to bridge KMP → Swift.
@objc class LiveActivityManager: NSObject {

    @objc static let shared = LiveActivityManager()

    private var currentActivity: Any? = nil

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
            lineName: info["lineName"] as? String ?? "",
            lineColor: info["lineColor"] as? String ?? "#007AFF",
            stopName: info["stopName"] as? String ?? "",
            minutes: (info["minutes"] as? Int) ?? 0,
            isTimetable: (info["isTimetable"] as? Bool) ?? false
        )
    }

    @objc private func handleUpdateTracking(_ notification: Notification) {
        guard let info = notification.userInfo else { return }
        updateTracking(
            lineName: info["lineName"] as? String ?? "",
            lineColor: info["lineColor"] as? String ?? "#007AFF",
            stopName: info["stopName"] as? String ?? "",
            minutes: (info["minutes"] as? Int) ?? 0,
            isTimetable: (info["isTimetable"] as? Bool) ?? false
        )
    }

    @objc private func handleStopTracking(_ notification: Notification) {
        stopTracking()
    }

    // MARK: - ActivityKit Management

    private func startTracking(
        lineName: String,
        lineColor: String,
        stopName: String,
        minutes: Int,
        isTimetable: Bool
    ) {
        guard #available(iOS 16.2, *) else { return }
        guard ActivityAuthorizationInfo().areActivitiesEnabled else {
            print("[LiveActivity] Activities not enabled by user")
            return
        }

        stopTrackingInternal()

        let attributes = TransitActivityAttributes(
            lineName: lineName,
            lineColor: lineColor,
            stopName: stopName
        )
        let state = TransitActivityAttributes.ContentState(
            minutes: minutes,
            isTimetable: isTimetable
        )

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
        }
    }

    private func updateTracking(
        lineName: String,
        lineColor: String,
        stopName: String,
        minutes: Int,
        isTimetable: Bool
    ) {
        guard #available(iOS 16.2, *) else { return }

        let state = TransitActivityAttributes.ContentState(
            minutes: minutes,
            isTimetable: isTimetable
        )

        Task {
            guard let activity = currentActivity as? Activity<TransitActivityAttributes> else {
                startTracking(
                    lineName: lineName,
                    lineColor: lineColor,
                    stopName: stopName,
                    minutes: minutes,
                    isTimetable: isTimetable
                )
                return
            }

            let content = ActivityContent(state: state, staleDate: Date().addingTimeInterval(120))
            await activity.update(content)
            print("[LiveActivity] Updated: \(minutes) min")
        }
    }

    private func stopTracking() {
        guard #available(iOS 16.2, *) else { return }
        stopTrackingInternal()
    }

    @available(iOS 16.2, *)
    private func stopTrackingInternal() {
        Task {
            for activity in Activity<TransitActivityAttributes>.activities {
                await activity.end(nil, dismissalPolicy: .immediate)
            }
            currentActivity = nil
            print("[LiveActivity] Stopped all activities")
        }
    }
}
