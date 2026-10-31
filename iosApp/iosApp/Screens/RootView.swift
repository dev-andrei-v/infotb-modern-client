import Shared
import SwiftUI

struct RootView: View {
    @Bindable var app = AppModel.shared

    var body: some View {
        TabView(selection: $app.selectedTab) {
            NavigationStack(path: $app.dashboardPath) {
                DashboardView(model: app.dashboard)
                    .routeDestinations()
            }
            .tabItem { Label(tr("nav.dashboard"), systemImage: "square.grid.2x2.fill") }
            .tag(AppTab.dashboard)

            NavigationStack(path: $app.linesPath) {
                LinesView(model: app.lines)
                    .routeDestinations()
            }
            .tabItem { Label(tr("nav.lines"), systemImage: "point.topleft.down.to.point.bottomright.curvepath.fill") }
            .tag(AppTab.lines)

            NavigationStack(path: $app.newsPath) {
                AnnouncementsView(model: app.news)
                    .routeDestinations()
            }
            .tabItem { Label(tr("nav.announcements"), systemImage: "exclamationmark.bubble.fill") }
            .tag(AppTab.news)

            NavigationStack {
                SettingsView()
            }
            .tabItem { Label(tr("nav.settings"), systemImage: "gearshape.fill") }
            .tag(AppTab.settings)
        }
        .overlay(alignment: .top) { ToastView(message: app.toast) }
        .onAppear { app.applyTheme() }
    }
}

extension View {
    /// The detail screens every tab can push.
    func routeDestinations() -> some View {
        navigationDestination(for: Route.self) { route in
            switch route {
            case .line(let lineId):
                LineDetailView(lineId: lineId)
            case .stop(let stopId, let lineId, let direction):
                StopDetailView(stopId: stopId, lineId: lineId, direction: direction)
            case .lineMap(let lineId, let direction):
                LiveMapView(lineId: lineId, direction: direction)
            }
        }
    }
}

/// Creates a screen's model once, the first time the screen appears, and
/// keeps it for as long as the screen stays in the navigation stack.
struct ModelHost<Model: AnyObject, Content: View>: View {
    let make: () -> Model
    @ViewBuilder let content: (Model) -> Content

    @State private var model: Model?

    var body: some View {
        Group {
            if let model {
                content(model)
            } else {
                Color(.systemGroupedBackground)
            }
        }
        .onAppear {
            if model == nil { model = make() }
        }
    }
}

/// A short confirmation floating under the Dynamic Island.
private struct ToastView: View {
    let message: String?

    var body: some View {
        Group {
            if let message {
                Label(message, systemImage: "checkmark.circle.fill")
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(2)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 12)
                    .glassBackground(in: Capsule())
                    .shadow(color: .black.opacity(0.12), radius: 12, y: 4)
                    .padding(.horizontal, 24)
                    .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .animation(.spring(duration: 0.35), value: message)
    }
}
