package ro.infotb.modern.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.koin.compose.koinInject
import ro.infotb.modern.getPlatformName
import ro.infotb.modern.data.model.Direction
import ro.infotb.modern.data.repository.TransitRepository
import ro.infotb.modern.data.store.DashboardStore
import ro.infotb.modern.data.store.FavoritesStore
import ro.infotb.modern.data.store.NotificationMode
import ro.infotb.modern.data.store.NotificationStore
import ro.infotb.modern.data.store.PreferencesStore
import ro.infotb.modern.notification.NotificationEngine
import ro.infotb.modern.data.store.WidgetSize
import ro.infotb.modern.notification.NotificationEvent
import ro.infotb.modern.ui.components.layout.AppShell
import ro.infotb.modern.ui.navigation.Screen
import ro.infotb.modern.ui.screens.AnnouncementsScreen
import ro.infotb.modern.ui.screens.DashboardScreen
import ro.infotb.modern.ui.screens.LineDetailScreen
import ro.infotb.modern.ui.screens.LinesScreen
import ro.infotb.modern.ui.screens.NotificationsScreen
import ro.infotb.modern.ui.screens.SettingsScreen
import ro.infotb.modern.ui.screens.StopDetailScreen
import ro.infotb.modern.ui.theme.StbTheme
import ro.infotb.modern.ui.viewmodel.AnnouncementsViewModel
import ro.infotb.modern.ui.viewmodel.DashboardViewModel
import ro.infotb.modern.ui.viewmodel.LineDetailViewModel
import ro.infotb.modern.ui.viewmodel.LinesViewModel
import ro.infotb.modern.ui.viewmodel.StopDetailViewModel
import kotlinx.coroutines.launch
import ro.infotb.modern.util.Locale
import ro.infotb.modern.util.t

data class PendingStopNav(
    val stopId: Long,
    val lineId: Long,
    val direction: Int
)

@Composable
fun App(notificationNav: MutableState<PendingStopNav?> = remember { mutableStateOf(null) }) {
    val preferencesStore: PreferencesStore = koinInject()
    val repository: TransitRepository = koinInject()
    val dashboardStore: DashboardStore = koinInject()
    val favoritesStore: FavoritesStore = koinInject()
    val notificationStore: NotificationStore = koinInject()
    val notificationEngine: NotificationEngine = koinInject()

    // Start the notification engine
    DisposableEffect(Unit) {
        notificationEngine.start()
        onDispose { notificationEngine.stop() }
    }

    val themeValue by preferencesStore.theme.collectAsState()
    val localeValue by preferencesStore.locale.collectAsState()
    val locale = if (localeValue == "en") Locale.EN else Locale.RO
    val isIos = remember { getPlatformName() == "iOS" }
    val activeSubscriptions by notificationStore.subscriptions.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    fun handleTrackedStationSelection(
        stopId: Long,
        stopName: String,
        lineId: Long,
        lineName: String,
        lineColor: String,
        direction: Direction,
        minutes: Int,
        isTimetable: Boolean
    ) {
        if (isIos) {
            notificationEngine.startLiveTrackingNow(
                NotificationEvent(
                    lineName = lineName,
                    lineColor = lineColor,
                    stopName = stopName,
                    stopId = stopId,
                    lineId = lineId,
                    direction = direction.value,
                    minutes = minutes,
                    isTimetable = isTimetable
                )
            )
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = "${t("stopDetail.trackingStarted", locale)} $lineName → $stopName"
                )
            }
            notificationStore.replaceWithSingleSubscription(
                stopId = stopId,
                stopName = stopName,
                lineId = lineId,
                lineName = lineName,
                lineColor = lineColor,
                direction = direction,
                mode = NotificationMode.ONCE,
                thresholdMinutes = 3,
                onComplete = {
                    notificationEngine.refreshNow()
                }
            )
        } else {
            notificationStore.addSubscription(
                stopId = stopId,
                stopName = stopName,
                lineId = lineId,
                lineName = lineName,
                lineColor = lineColor,
                direction = direction,
                mode = NotificationMode.ONCE,
                thresholdMinutes = 3
            )
        }
    }

    StbTheme(themeMode = themeValue) {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        val navTarget = notificationNav.value
        LaunchedEffect(navTarget) {
            if (navTarget != null) {
                navController.navigate(
                    Screen.StopDetail(
                        stopId = navTarget.stopId,
                        lineId = navTarget.lineId.takeIf { it >= 0 },
                        direction = navTarget.direction.takeIf { it >= 0 }
                    ).route
                ) {
                    launchSingleTop = true
                }
                notificationNav.value = null
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
        AppShell(
            navController = navController,
            currentRoute = currentRoute,
            locale = locale
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route
            ) {
                composable(Screen.Dashboard.route) {
                    val viewModel = remember { DashboardViewModel(repository, dashboardStore) }
                    DashboardScreen(
                        viewModel = viewModel,
                        notificationStore = notificationStore,
                        useDirectNotificationAction = isIos,
                        locale = locale,
                        onNavigateToStop = { stopId ->
                            navController.navigate(Screen.StopDetail(stopId).route)
                        },
                        onNavigateToLine = { lineId ->
                            navController.navigate(Screen.LineDetail(lineId).route)
                        },
                        onNavigateToLines = {
                            navController.navigate(Screen.Lines.route)
                        },
                        onNavigateToSettings = {
                            navController.navigate(Screen.Settings.route)
                        },
                        onSetNotification = { sid, stopName, lid, lineName, lineColor, dir, minutes, isTimetable ->
                            handleTrackedStationSelection(
                                stopId = sid,
                                stopName = stopName,
                                lineId = lid,
                                lineName = lineName,
                                lineColor = lineColor,
                                direction = dir,
                                minutes = minutes,
                                isTimetable = isTimetable
                            )
                        }
                    )
                }

                composable(Screen.Lines.route) {
                    val viewModel = remember { LinesViewModel(repository, favoritesStore) }
                    LinesScreen(
                        viewModel = viewModel,
                        locale = locale,
                        onNavigateToLine = { lineId ->
                            navController.navigate(Screen.LineDetail(lineId).route)
                        }
                    )
                }

                composable(
                    route = Screen.LineDetail.ROUTE,
                    arguments = listOf(
                        navArgument("lineId") { type = NavType.LongType }
                    )
                ) { backStackEntry ->
                    val lineId = backStackEntry.arguments?.getLong("lineId") ?: return@composable
                    val viewModel = remember(lineId) { LineDetailViewModel(repository) }
                    LineDetailScreen(
                        lineId = lineId,
                        viewModel = viewModel,
                        locale = locale,
                        onBack = { navController.popBackStack() },
                        onNavigateToStop = { stopId, lid, direction ->
                            navController.navigate(
                                Screen.StopDetail(
                                    stopId = stopId,
                                    lineId = lid,
                                    direction = direction.value
                                ).route
                            )
                        }
                    )
                }

                composable(
                    route = Screen.StopDetail.ROUTE,
                    arguments = listOf(
                        navArgument("stopId") { type = NavType.LongType },
                        navArgument("lineId") {
                            type = NavType.LongType
                            defaultValue = -1L
                        },
                        navArgument("direction") {
                            type = NavType.IntType
                            defaultValue = -1
                        }
                    )
                ) { backStackEntry ->
                    val stopId = backStackEntry.arguments?.getLong("stopId") ?: return@composable
                    val lineId = backStackEntry.arguments?.getLong("lineId")?.takeIf { it >= 0 }
                    val dirInt = backStackEntry.arguments?.getInt("direction")?.takeIf { it >= 0 }
                    val direction = dirInt?.let { Direction.fromInt(it) }
                    val viewModel = remember(stopId) { StopDetailViewModel(repository) }

                    // Update dynamic island when visiting a stop with active subscription
                    LaunchedEffect(stopId) {
                        notificationEngine.refreshNow()
                    }

                    StopDetailScreen(
                        stopId = stopId,
                        lineId = lineId,
                        direction = direction,
                        viewModel = viewModel,
                        locale = locale,
                        activeSubscriptions = activeSubscriptions,
                        onBack = { navController.popBackStack() },
                        onNavigateToLine = { lid ->
                            navController.navigate(Screen.LineDetail(lid).route)
                        },
                        onAddToDashboard = { sid, stopName ->
                            dashboardStore.addWidget(
                                stopId = sid,
                                stopName = stopName,
                                size = WidgetSize.MEDIUM,
                                lineKey = ""
                            )
                            navController.navigate(Screen.Dashboard.route) {
                                popUpTo(Screen.Dashboard.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onSetNotification = { sid, stopName, lid, lineName, lineColor, dir, minutes, isTimetable ->
                            handleTrackedStationSelection(
                                stopId = sid,
                                stopName = stopName,
                                lineId = lid,
                                lineName = lineName,
                                lineColor = lineColor,
                                direction = dir,
                                minutes = minutes,
                                isTimetable = isTimetable
                            )
                        },
                        onStopNotification = { subscriptionId ->
                            notificationStore.removeSubscription(subscriptionId)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    message = t("stopDetail.trackingStopped", locale)
                                )
                            }
                        },
                        onRefresh = { notificationEngine.refreshNow() }
                    )
                }

                composable(Screen.Announcements.route) {
                    val viewModel = remember { AnnouncementsViewModel(repository) }
                    AnnouncementsScreen(
                        viewModel = viewModel,
                        locale = locale
                    )
                }

                composable(Screen.Notifications.route) {
                    NotificationsScreen(
                        notificationStore = notificationStore,
                        locale = locale
                    )
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(
                        preferencesStore = preferencesStore,
                        notificationStore = notificationStore,
                        locale = locale,
                        onNotificationChanged = { notificationEngine.refreshNow() }
                    )
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
        )
        }
    }
}
