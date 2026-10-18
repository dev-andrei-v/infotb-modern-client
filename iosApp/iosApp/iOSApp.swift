import UIKit
import ComposeApp
import BackgroundTasks
import CoreLocation

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    private var backgroundTaskID: UIBackgroundTaskIdentifier = .invalid
    private lazy var locationManager = BackgroundLocationManager()

    /// True while the Kotlin engine has a live tracking session running.
    private var isTracking = false
    private var isInBackground = false

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        // Initialize Live Activity manager (registers NSNotification observers)
        _ = LiveActivityManager.shared

        MainViewControllerKt.doInitKoin()

        // The window itself is created by SceneDelegate.

        registerNoticeRefreshTask()

        // Register for app lifecycle notifications
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(appDidEnterBackground),
            name: UIApplication.didEnterBackgroundNotification,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(appWillEnterForeground),
            name: UIApplication.willEnterForegroundNotification,
            object: nil
        )

        // Mirror the engine's tracking state so background keep-alive only
        // runs while there is actually something to track.
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(trackingStarted),
            name: NSNotification.Name("ro.infotb.modern.START_LIVE_TRACKING"),
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(trackingStopped),
            name: NSNotification.Name("ro.infotb.modern.STOP_LIVE_TRACKING"),
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(trackingStopped),
            name: userStopTrackingNotification,
            object: nil
        )

        return true
    }

    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        let config = UISceneConfiguration(name: "Default", sessionRole: connectingSceneSession.role)
        config.delegateClass = SceneDelegate.self
        return config
    }

    @objc private func appDidEnterBackground() {
        isInBackground = true
        scheduleNoticeRefresh()
        guard isTracking else {
            print("[AppDelegate] Backgrounded with no tracking; location stays off")
            return
        }
        locationManager.start()
        print("[AppDelegate] Background location started to keep Dynamic Island alive")
    }

    @objc private func appWillEnterForeground() {
        isInBackground = false
        locationManager.stop()
    }

    @objc private func trackingStarted() {
        isTracking = true
        if isInBackground {
            locationManager.start()
        } else {
            // First time the user tracks something: ask while we are visible,
            // which is the only time iOS will show the prompt.
            locationManager.requestPermissionIfNeeded()
        }
    }

    @objc private func trackingStopped() {
        isTracking = false
        locationManager.stop()
    }

    // MARK: - Background notice refresh

    /// iOS wakes the app now and then (its own schedule, roughly every few
    /// hours) so new STB notices about followed lines can be announced even
    /// when the app has not been opened.
    private static let noticeTaskId = "ro.infotb.modern.notices"

    private func registerNoticeRefreshTask() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: Self.noticeTaskId, using: nil) { [weak self] task in
            guard let refresh = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            self?.scheduleNoticeRefresh()
            refresh.expirationHandler = {
                refresh.setTaskCompleted(success: false)
            }
            MainViewControllerKt.checkNoticesInBackground { _ in
                refresh.setTaskCompleted(success: true)
            }
        }
    }

    private func scheduleNoticeRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: Self.noticeTaskId)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 30 * 60)
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            print("[AppDelegate] Could not schedule notice refresh: \(error.localizedDescription)")
        }
    }

    func applicationWillTerminate(_ application: UIApplication) {
        // End the session so a relaunch hours later doesn't resurrect the
        // island and the background keep-alive.
        NotificationCenter.default.post(name: userStopTrackingNotification, object: nil)
        LiveActivityManager.shared.endAllActivities()
    }
}
