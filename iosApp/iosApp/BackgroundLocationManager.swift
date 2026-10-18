import CoreLocation

/// Keeps the process alive in the background while a live tracking session is
/// running, so the NotificationEngine can keep polling and updating the
/// Dynamic Island. It is only started while tracking is active; otherwise iOS
/// would show the location indicator for nothing and burn battery.
///
/// With "Always" permission we use significant-change monitoring, which is
/// cheap and keeps the location pill off most of the time. With only
/// "When In Use" we fall back to coarse continuous updates, which is the only
/// mode iOS allows to run in the background for that permission level.
class BackgroundLocationManager: NSObject, CLLocationManagerDelegate {

    private let manager = CLLocationManager()
    private(set) var running = false

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyThreeKilometers
        manager.distanceFilter = CLLocationDistanceMax
        manager.pausesLocationUpdatesAutomatically = false
    }

    private var status: CLAuthorizationStatus {
        manager.authorizationStatus
    }

    /// Ask for permission once, from the foreground, the first time the user
    /// starts tracking. Prompts never appear when called from the background.
    func requestPermissionIfNeeded() {
        guard status == .notDetermined else { return }
        manager.requestAlwaysAuthorization()
    }

    func start() {
        guard !running else { return }
        switch status {
        case .authorizedAlways:
            manager.allowsBackgroundLocationUpdates = true
            manager.startMonitoringSignificantLocationChanges()
            running = true
            print("[BackgroundLocation] Started significant-change monitoring")
        case .authorizedWhenInUse:
            manager.allowsBackgroundLocationUpdates = true
            manager.startUpdatingLocation()
            running = true
            print("[BackgroundLocation] Started coarse continuous updates (when-in-use)")
        default:
            print("[BackgroundLocation] Not authorized; background polling will be limited")
        }
    }

    func stop() {
        guard running else { return }
        manager.stopMonitoringSignificantLocationChanges()
        manager.stopUpdatingLocation()
        running = false
        print("[BackgroundLocation] Stopped")
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        // Intentionally empty — we don't need the location data,
        // we just need iOS to keep the process alive.
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        print("[BackgroundLocation] Error: \(error.localizedDescription)")
    }
}
