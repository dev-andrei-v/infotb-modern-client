import UIKit
import ComposeApp

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        MainViewControllerKt.doInitKoin()

        window = UIWindow(frame: UIScreen.main.bounds)
        let composeVC = MainViewControllerKt.MainViewController()
        let hostVC = StatusBarHostController(child: composeVC)
        window?.rootViewController = hostVC
        window?.makeKeyAndVisible()
        return true
    }
}

class StatusBarHostController: UIViewController {
    private let child: UIViewController
    private var isDark = false

    init(child: UIViewController) {
        self.child = child
        super.init(nibName: nil, bundle: nil)

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(themeDidChange(_:)),
            name: Notification.Name("InfoTBThemeDidChange"),
            object: nil
        )
    }

    required init?(coder: NSCoder) { fatalError() }

    override func viewDidLoad() {
        super.viewDidLoad()
        addChild(child)
        child.view.frame = view.bounds
        child.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(child.view)
        child.didMove(toParent: self)
    }

    @objc private func themeDidChange(_ notification: Notification) {
        if let userInfo = notification.userInfo, let dark = userInfo["isDark"] as? Bool {
            isDark = dark
            setNeedsStatusBarAppearanceUpdate()
        }
    }

    override var preferredStatusBarStyle: UIStatusBarStyle {
        isDark ? .lightContent : .darkContent
    }
}
