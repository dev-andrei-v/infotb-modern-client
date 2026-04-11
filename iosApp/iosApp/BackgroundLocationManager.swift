import CoreLocation

/// Dummy location manager that keeps the app alive in the background
/// so the NotificationEngine can continue polling and updating Dynamic Island.
/// Uses significant-change monitoring which is low power and doesn't require
/// actual GPS usage — it just prevents iOS from suspending the process.
class BackgroundLocationManager: NSObject, CLLocationManagerDelegate {

    private let manager = CLLocationManager()
    private var running = false

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyThreeKilometers
        manager.distanceFilter = CLLocationDistanceMax
        manager.allowsBackgroundLocationUpdates = true
        manager.pausesLocationUpdatesAutomatically = false
    }

    func start() {
        guard !running else { return }
        manager.requestAlwaysAuthorization()
        manager.startUpdatingLocation()
        running = true
    }

    func stop() {
        guard running else { return }
        manager.stopUpdatingLocation()
        running = false
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        // Intentionally empty — we don't need the location data,
        // we just need iOS to keep the process alive.
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        print("[BackgroundLocation] Error: \(error.localizedDescription)")
    }
}
