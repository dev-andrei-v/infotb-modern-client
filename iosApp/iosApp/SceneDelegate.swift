import SwiftUI
import UIKit

enum AppQuickActions {
    static let linesType = "ro.infotb.modern.quick.lines"
    static let mapType = "ro.infotb.modern.quick.map"
    static let savedDestinationType = "ro.infotb.modern.quick.savedDestination"
    static let destinationIDKey = "destinationID"

    private static let destinationsKey = "planner.savedDestinations.v1"
    private static let usageKey = "planner.savedDestinationUsage.v1"

    static func refresh(localeCode: String) {
        let lines = UIApplicationShortcutItem(
            type: linesType,
            localizedTitle: localeCode == "en" ? "Lines" : "Linii",
            localizedSubtitle: nil,
            icon: UIApplicationShortcutIcon(systemImageName: "point.topleft.down.to.point.bottomright.curvepath.fill"),
            userInfo: nil
        )
        let map = UIApplicationShortcutItem(
            type: mapType,
            localizedTitle: localeCode == "en" ? "Map" : "Hartă",
            localizedSubtitle: nil,
            icon: UIApplicationShortcutIcon(systemImageName: "map.fill"),
            userInfo: nil
        )
        var shortcuts = [lines, map]

        if let data = UserDefaults.standard.data(forKey: destinationsKey),
           let destinations = try? JSONDecoder().decode([SavedDestination].self, from: data),
           !destinations.isEmpty {
            let usage = UserDefaults.standard.dictionary(forKey: usageKey) as? [String: Int] ?? [:]
            let mostUsed = destinations.enumerated().sorted { lhs, rhs in
                let leftCount = usage[lhs.element.id.uuidString, default: 0]
                let rightCount = usage[rhs.element.id.uuidString, default: 0]
                return leftCount == rightCount ? lhs.offset < rhs.offset : leftCount > rightCount
            }.first?.element
            if let destination = mostUsed {
                let shortLabel = String(destination.label.prefix(18))
                let title = localeCode == "en" ? "Plan to \(shortLabel)" : "Ruta spre \(shortLabel)"
                let plan = UIApplicationShortcutItem(
                    type: savedDestinationType,
                    localizedTitle: title,
                    localizedSubtitle: destination.place.name,
                    icon: UIApplicationShortcutIcon(systemImageName: "arrow.triangle.swap"),
                    userInfo: [destinationIDKey: destination.id.uuidString as NSString]
                )
                shortcuts.append(plan)
            }
        }
        UIApplication.shared.shortcutItems = shortcuts
    }

    static func recordUse(destinationID: UUID, localeCode: String) {
        var usage = UserDefaults.standard.dictionary(forKey: usageKey) as? [String: Int] ?? [:]
        usage[destinationID.uuidString, default: 0] += 1
        UserDefaults.standard.set(usage, forKey: usageKey)
        refresh(localeCode: localeCode)
    }

    private struct SavedDestination: Decodable {
        let id: UUID
        let label: String
        let place: Place

        struct Place: Decodable { let name: String }
    }
}

/// Owns the app window. iOS 26+ SDKs require the UIScene life cycle, so the
/// window is created here instead of in AppDelegate.
class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        guard let windowScene = scene as? UIWindowScene else { return }

        let window = UIWindow(windowScene: windowScene)
        window.rootViewController = UIHostingController(rootView: RootView())
        window.makeKeyAndVisible()
        self.window = window

        // Deep link that launched the app from a cold start.
        if let url = connectionOptions.urlContexts.first?.url {
            _ = DeepLinkHandler.handle(url)
        }
        if let shortcut = connectionOptions.shortcutItem {
            _ = AppModel.shared.openQuickAction(shortcut)
        }
    }

    func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        guard let url = URLContexts.first?.url else { return }
        _ = DeepLinkHandler.handle(url)
    }

    func windowScene(_ windowScene: UIWindowScene, performActionFor shortcutItem: UIApplicationShortcutItem, completionHandler: @escaping (Bool) -> Void) {
        completionHandler(AppModel.shared.openQuickAction(shortcutItem))
    }
}

enum DeepLinkHandler {
    /// Handles Live Activity stop links and widget Dashboard group links.
    static func handle(_ url: URL) -> Bool {
        guard url.scheme == "infotb",
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return false }

        if url.host == "dashboard", url.path == "/refresh" {
            Task { @MainActor in AppModel.shared.refreshDashboardFromWidget() }
            return true
        }
        if url.host == "dashboard", url.path == "/group",
           let name = components.queryItems?.first(where: { $0.name == "name" })?.value {
            AppModel.shared.openDashboardGroup(name)
            return true
        }
        let items = components.queryItems ?? []
        if url.host == "line" {
            var lineId = items.first(where: { $0.name == "lineId" })?.value.flatMap(Int64.init)
            if lineId == nil, let pathId = url.pathComponents.dropFirst().first.flatMap(Int64.init) {
                lineId = pathId
            }
            if let lineId {
                AppModel.shared.push(.line(lineId))
                return true
            }
        }
        guard url.host == "stop" else { return false }

        var stopId = items.first(where: { $0.name == "stopId" })?.value.flatMap(Int64.init)
        if stopId == nil, let pathId = url.pathComponents.dropFirst().first.flatMap(Int64.init) {
            stopId = pathId
        }
        let lineId = items.first(where: { $0.name == "lineId" })?.value.flatMap(Int64.init) ?? -1
        let direction = items.first(where: { $0.name == "direction" })?.value.flatMap(Int32.init) ?? -1

        guard let resolvedStopId = stopId, resolvedStopId >= 0 else { return false }

        AppModel.shared.openStop(
            stopId: resolvedStopId,
            lineId: lineId >= 0 ? lineId : nil,
            direction: direction >= 0 ? direction : nil
        )
        return true
    }
}
