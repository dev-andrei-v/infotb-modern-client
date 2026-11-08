import CoreLocation
import Observation
import SwiftUI

/// Accurate foreground updates for nearby stops. Separate from the coarse
/// background manager used to keep arrival tracking alive.
@Observable
final class ForegroundLocation: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var running = false
    private var timeout: Task<Void, Never>?
    private(set) var location: CLLocation?
    private(set) var unavailable = false
    var permissionDenied: Bool {
        manager.authorizationStatus == .denied || manager.authorizationStatus == .restricted
    }

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        manager.distanceFilter = 25
    }

    func start() {
        guard !running else { return }
        running = true
        unavailable = false
        if let location, Date().timeIntervalSince(location.timestamp) > 120 {
            self.location = nil
        }
        updateAuthorization()
    }

    func requestPermission() {
        if manager.authorizationStatus == .notDetermined {
            manager.requestWhenInUseAuthorization()
        }
    }

    func stop() {
        running = false
        manager.stopUpdatingLocation()
        timeout?.cancel()
        timeout = nil
    }

    func retry() {
        stop()
        start()
    }

    private func updateAuthorization() {
        guard running else { return }
        switch manager.authorizationStatus {
        case .notDetermined:
            manager.requestWhenInUseAuthorization()
        case .authorizedAlways, .authorizedWhenInUse:
            manager.startUpdatingLocation()
            timeout?.cancel()
            timeout = Task { @MainActor [weak self] in
                do { try await Task.sleep(for: .seconds(12)) } catch { return }
                guard let self, self.location == nil else { return }
                self.unavailable = true
            }
        default:
            location = nil
            unavailable = true
            manager.stopUpdatingLocation()
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        updateAuthorization()
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard running, let fix = locations.last,
              fix.horizontalAccuracy >= 0,
              CLLocationCoordinate2DIsValid(fix.coordinate),
              abs(fix.timestamp.timeIntervalSinceNow) < 120 else { return }
        location = fix
        unavailable = false
        timeout?.cancel()
        timeout = nil
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        guard running else { return }
        if (error as? CLError)?.code == .denied {
            location = nil
            unavailable = true
        }
        // Transient failures can recover; the timeout provides the fallback UI.
    }

    deinit {
        timeout?.cancel()
        manager.stopUpdatingLocation()
    }
}

private struct ForegroundLocationTracking: ViewModifier {
    let location: ForegroundLocation
    let enabled: Bool
    @State private var visible = false

    func body(content: Content) -> some View {
        content
            .onAppear {
                visible = true
                if enabled && UIApplication.shared.applicationState != .background { location.start() }
            }
            .onChange(of: enabled) { _, enabled in
                if enabled && visible && UIApplication.shared.applicationState != .background { location.start() }
                else { location.stop() }
            }
            // This app embeds SwiftUI in a UIKit scene, so use its lifecycle
            // notifications rather than relying on SwiftUI's scenePhase.
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in
                if visible && enabled { location.start() }
            }
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.didEnterBackgroundNotification)) { _ in
                location.stop()
            }
            .onDisappear { visible = false; location.stop() }
    }
}

extension View {
    func foregroundLocation(_ location: ForegroundLocation, enabled: Bool = true) -> some View {
        modifier(ForegroundLocationTracking(location: location, enabled: enabled))
    }
}

struct LocationStatusView: View {
    let location: ForegroundLocation
    @Environment(\.openURL) private var openURL

    var body: some View {
        if location.unavailable {
            VStack(alignment: .leading, spacing: 8) {
                Label(tr("map.locationUnavailable"), systemImage: "location.slash")
                    .font(.subheadline).foregroundStyle(.secondary)
                if location.permissionDenied {
                    Button(tr("map.openSettings")) {
                        if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                    }
                } else {
                    Button(tr("common.retry")) { location.retry() }
                }
            }
        } else {
            HStack(spacing: 8) {
                ProgressView()
                Text(tr("dashboard.locationLoading")).font(.subheadline).foregroundStyle(.secondary)
            }
        }
    }
}
