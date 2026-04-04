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
    static let themeDidChange = Notification.Name("InfoTBThemeDidChange")

    private let child: UIViewController
    private var isDark = false

    init(child: UIViewController) {
        self.child = child
        super.init(nibName: nil, bundle: nil)

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(themeDidChange(_:)),
            name: Self.themeDidChange,
            object: nil
        )
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported. Use init(child:) instead.")
    }

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

    override var childForStatusBarStyle: UIViewController? {
        nil
    }

    override var preferredStatusBarStyle: UIStatusBarStyle {
        isDark ? .lightContent : .darkContent
    }
}
