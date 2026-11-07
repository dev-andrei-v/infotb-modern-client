package ro.infotb.modern.ui.widget

/** Publishes the dashboard's latest local snapshot to the platform home-screen widget. */
expect fun publishDashboardWidgetSnapshot(snapshot: String, selectedStopId: Long?)

/** Updates Android's launcher shortcuts from the locally saved planner trips. */
expect fun publishPlannerQuickShortcutSnapshot(savedTripsJson: String)
