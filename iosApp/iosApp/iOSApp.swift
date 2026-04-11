import UIKit
import ComposeApp
import BackgroundTasks
import CoreLocation

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?
    private var backgroundTaskID: UIBackgroundTaskIdentifier = .invalid
    private lazy var locationManager = BackgroundLocationManager()

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        // Initialize Live Activity manager (registers NSNotification observers)
        _ = LiveActivityManager.shared

        MainViewControllerKt.doInitKoin()

        window = UIWindow(frame: UIScreen.main.bounds)
        window?.rootViewController = MainViewControllerKt.MainViewController()
        window?.makeKeyAndVisible()

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

        return true
    }

    @objc private func appDidEnterBackground() {
        locationManager.start()
        print("[AppDelegate] Background location started to keep Dynamic Island alive")
    }

    @objc private func appWillEnterForeground() {
        locationManager.stop()
    }

    func applicationWillTerminate(_ application: UIApplication) {
        LiveActivityManager.shared.endAllActivities()
    }

    func application(
        _ app: UIApplication,
        open url: URL,
        options: [UIApplication.OpenURLOptionsKey: Any] = [:]
    ) -> Bool {
        handleDeepLink(url)
    }

    private func handleDeepLink(_ url: URL) -> Bool {
        guard url.scheme == "infotb",
              url.host == "stop",
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        else {
            return false
        }

        let items = components.queryItems ?? []
        let stopId = items.first(where: { $0.name == "stopId" })?.value.flatMap(Int64.init) ?? -1
        let lineId = items.first(where: { $0.name == "lineId" })?.value.flatMap(Int64.init) ?? -1
        let direction = items.first(where: { $0.name == "direction" })?.value.flatMap(Int32.init) ?? -1

        guard stopId >= 0 else { return false }

        MainViewControllerKt.handleIncomingNavigation(
            stopId: stopId,
            lineId: lineId,
            direction: direction
        )
        return true
    }
}
