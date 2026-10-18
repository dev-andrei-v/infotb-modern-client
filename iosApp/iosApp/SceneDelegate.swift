import UIKit
import ComposeApp

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
        window.rootViewController = MainViewControllerKt.MainViewController()
        window.makeKeyAndVisible()
        self.window = window

        // Deep link that launched the app from a cold start.
        if let url = connectionOptions.urlContexts.first?.url {
            _ = DeepLinkHandler.handle(url)
        }
    }

    func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        guard let url = URLContexts.first?.url else { return }
        _ = DeepLinkHandler.handle(url)
    }
}

enum DeepLinkHandler {
    /// Handles `infotb://stop?stopId=…&lineId=…&direction=…`.
    static func handle(_ url: URL) -> Bool {
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
