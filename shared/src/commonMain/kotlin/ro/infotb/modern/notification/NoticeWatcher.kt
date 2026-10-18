package ro.infotb.modern.notification

import com.russhwolf.settings.Settings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ro.infotb.modern.currentTimeMillis
import ro.infotb.modern.data.model.Announcement
import ro.infotb.modern.data.repository.TransitRepository
import ro.infotb.modern.data.store.DashboardStore
import ro.infotb.modern.data.store.FavoritesStore
import ro.infotb.modern.data.store.NotificationStore
import ro.infotb.modern.data.store.PreferencesStore
import ro.infotb.modern.util.Constants
import ro.infotb.modern.util.Locale
import ro.infotb.modern.util.t

/**
 * Raises a system notification when STB publishes a notice that mentions a
 * line the user follows: a line on a dashboard card, a favourite, or one with
 * an arrival alert. Every notice is announced at most once; the ids already
 * announced are kept in settings.
 *
 * Checks run whenever the app fetches notices anyway (dashboard, News tab),
 * every [CHECK_INTERVAL_MS] while the arrival engine polls, and from the iOS
 * background refresh task.
 */
class NoticeWatcher(
    private val repository: TransitRepository,
    private val dashboardStore: DashboardStore,
    private val favoritesStore: FavoritesStore,
    private val notificationStore: NotificationStore,
    private val preferencesStore: PreferencesStore,
    private val settings: Settings,
    private val notifier: PlatformNotifier
) {
    private val mutex = Mutex()
    private var lastCheckMs = 0L

    /** Fetches the notices and announces new relevant ones. Returns true if anything was announced. */
    suspend fun checkNow(): Boolean {
        val notices = try {
            repository.getAnnouncements()
        } catch (_: Exception) {
            return false
        }
        return process(notices)
    }

    /** Like [checkNow], but only if the last check is older than [CHECK_INTERVAL_MS]. */
    suspend fun checkIfDue(): Boolean {
        val now = currentTimeMillis()
        if (now - lastCheckMs < CHECK_INTERVAL_MS) return false
        return checkNow()
    }

    /**
     * Announces the notices in [notices] that are new, recent and mention a
     * followed line. Callers that already fetched the list use this directly.
     */
    suspend fun process(notices: List<Announcement>): Boolean = mutex.withLock {
        lastCheckMs = currentTimeMillis()
        if (notices.isEmpty()) return false

        val seen = loadSeen()
        // First run: remember what exists today so the user is not flooded with old notices
        if (seen == null) {
            saveSeen(notices.map { it.id })
            return false
        }

        val enabled = preferencesStore.noticeAlerts.value
        val followed = followedLineIds()
        val nowSeconds = currentTimeMillis() / 1000
        val fresh = notices.filter { it.id !in seen }

        var announced = false
        if (enabled && followed.isNotEmpty()) {
            fresh
                .filter { nowSeconds - it.created_at <= MAX_NOTICE_AGE_SECONDS }
                .filter { notice -> notice.lines.any { it.id != null && it.id in followed } }
                .forEach { notice ->
                    val lineNames = notice.lines
                        .filter { it.id != null && it.id in followed }
                        .joinToString(", ") { it.name }
                    val locale = if (preferencesStore.locale.value == "ro") Locale.RO else Locale.EN
                    notifier.showNoticeNotification(
                        noticeId = notice.id,
                        title = "${t("notice.alertTitle", locale)} $lineNames",
                        body = notice.title
                    )
                    announced = true
                }
        }

        if (fresh.isNotEmpty()) saveSeen(seen + fresh.map { it.id })
        announced
    }

    /** Lines shown on the dashboard, favourites and lines with arrival alerts. */
    private fun followedLineIds(): Set<Long> {
        val fromDashboard = dashboardStore.widgets.value
            .flatMap { it.visibleLines }
            .mapNotNull { key -> key.substringBefore('_').toLongOrNull() }
        val fromFavorites = favoritesStore.favoriteLineIds.value
        val fromAlerts = notificationStore.subscriptions.value.filter { it.enabled }.map { it.lineId }
        return (fromDashboard + fromFavorites + fromAlerts).toSet()
    }

    private fun loadSeen(): Set<Long>? {
        val raw = settings.getStringOrNull(Constants.SETTINGS_KEY_NOTICES_SEEN) ?: return null
        return raw.split(',').mapNotNull { it.toLongOrNull() }.toSet()
    }

    private fun saveSeen(ids: Collection<Long>) {
        // Keep the newest ids only; the API never returns more than a few dozen anyway
        val kept = ids.toSet().sortedDescending().take(MAX_SEEN_IDS)
        settings.putString(Constants.SETTINGS_KEY_NOTICES_SEEN, kept.joinToString(","))
    }

    companion object {
        const val CHECK_INTERVAL_MS = 15 * 60 * 1000L
        private const val MAX_NOTICE_AGE_SECONDS = 3 * 24 * 3600L
        private const val MAX_SEEN_IDS = 300
    }
}
