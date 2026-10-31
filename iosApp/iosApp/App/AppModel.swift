import Observation
import Shared
import UIKit

enum AppTab: Hashable {
    case dashboard, lines, news, settings
}

enum Route: Hashable {
    case line(Int64)
    case stop(Int64, lineId: Int64? = nil, direction: Direction? = nil)
    /// A line's route and live vehicles on a full-screen map.
    case lineMap(Int64, direction: Direction)
}

/// App-wide state: preferences, navigation, live-tracking subscriptions and
/// the dashboard, which lives for the whole app so switching tabs keeps its data.
@Observable
final class AppModel {
    static let shared = AppModel()

    private let services = IosServices.shared
    private let scope = KotlinScope()

    // Preferences
    private(set) var theme = "system"
    private(set) var localeCode = "ro"
    private(set) var noticeAlerts = true

    private(set) var subscriptions: [NotificationSubscription] = []

    // Navigation
    var selectedTab: AppTab = .dashboard
    var dashboardPath: [Route] = []
    var linesPath: [Route] = []
    var newsPath: [Route] = []

    /// Short confirmation shown at the top of the screen.
    private(set) var toast: String?
    private var toastTask: Task<Void, Never>?

    let dashboard = DashboardModel()
    let lines = LinesModel()
    let news = AnnouncementsModel()

    var kotlinLocale: Shared.Locale { localeCode == "en" ? .en : .ro }

    private init() {
        let prefs = services.preferencesStore
        scope.watch(prefs.theme) { [weak self] in
            self?.theme = $0 as? String ?? "system"
            self?.applyTheme()
        }
        scope.watch(prefs.locale) { [weak self] in self?.localeCode = $0 as? String ?? "ro" }
        scope.watch(prefs.noticeAlerts) { [weak self] in self?.noticeAlerts = Unbox.bool($0) }
        scope.watch(services.notificationStore.subscriptions) { [weak self] in
            self?.subscriptions = Unbox.list($0)
        }
    }

    // MARK: Preferences

    func setTheme(_ value: String) { services.preferencesStore.setTheme(theme: value) }
    func setLocale(_ value: String) { services.preferencesStore.setLocale(locale: value) }
    func setNoticeAlerts(_ value: Bool) { services.preferencesStore.setNoticeAlerts(enabled: value) }

    /// The theme override applies to the whole window, sheets included.
    func applyTheme() {
        let style: UIUserInterfaceStyle = switch theme {
        case "dark": .dark
        case "light": .light
        default: .unspecified
        }
        for scene in UIApplication.shared.connectedScenes {
            (scene as? UIWindowScene)?.windows.forEach { $0.overrideUserInterfaceStyle = style }
        }
    }

    // MARK: Navigation

    /// Opens a stop on the dashboard tab; used by deep links from the Live Activity.
    func openStop(stopId: Int64, lineId: Int64?, direction: Int32?) {
        selectedTab = .dashboard
        dashboardPath = [.stop(stopId, lineId: lineId, direction: direction.map { Direction.Companion.shared.fromInt(v: $0) })]
    }

    /// Pushes onto the stack of whichever tab is showing.
    func push(_ route: Route) {
        switch selectedTab {
        case .dashboard: dashboardPath.append(route)
        case .lines: linesPath.append(route)
        case .news: newsPath.append(route)
        case .settings: break
        }
    }

    func addToDashboard(stopId: Int64, stopName: String) {
        _ = services.dashboardStore.addWidget(stopId: stopId, stopName: stopName, size: .medium, lineKey: "")
        selectedTab = .dashboard
        dashboardPath = []
    }

    // MARK: Live tracking

    func isTracking(stopId: Int64, line: LineArrivalDetail) -> Bool {
        trackingSubscription(stopId: stopId, line: line) != nil
    }

    func trackingSubscription(stopId: Int64, line: LineArrivalDetail) -> NotificationSubscription? {
        subscriptions.first {
            $0.enabled && $0.stopId == stopId && $0.lineId == line.id && $0.direction == line.direction
        }
    }

    /// The bell next to an arrival: shows it in the Dynamic Island.
    func track(stopId: Int64, stopName: String, line: LineArrivalDetail, minutes: Int) {
        let live = services.arrivalTracker.track(
            stopId: stopId,
            stopName: stopName,
            lineId: line.id,
            lineName: line.name,
            lineColor: line.color,
            direction: line.direction,
            minutes: Int32(max(0, minutes)),
            isTimetable: line.isTimetable,
            lineType: line.type.name
        )
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        if live {
            showToast("\(tr("stopDetail.trackingStarted")) \(line.name) → \(stopName)")
        }
    }

    func stopTracking(_ subscription: NotificationSubscription) {
        services.notificationStore.removeSubscription(id: subscription.id)
        showToast(tr("stopDetail.trackingStopped"))
    }

    func toggleSubscription(_ subscription: NotificationSubscription) {
        services.notificationStore.toggleSubscription(id: subscription.id)
        services.notificationEngine.refreshNow()
    }

    func removeSubscription(_ subscription: NotificationSubscription) {
        services.notificationStore.removeSubscription(id: subscription.id)
        services.notificationEngine.refreshNow()
    }

    func refreshTracking() {
        services.notificationEngine.refreshNow()
    }

    // MARK: Toast

    func showToast(_ message: String) {
        toast = message
        toastTask?.cancel()
        toastTask = Task { @MainActor [weak self] in
            try? await Task.sleep(for: .seconds(2.5))
            guard !Task.isCancelled else { return }
            self?.toast = nil
        }
    }
}

/// Translates [key] with the shared Kotlin strings in the user's chosen
/// language. Reading it inside a view body re-renders when the language changes.
func tr(_ key: String) -> String {
    I18nKt.t(key: key, locale: AppModel.shared.kotlinLocale)
}
