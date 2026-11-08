package ro.infotb.modern.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ro.infotb.modern.data.model.*

class LineNewsTest {
    private val now = 1_794_056_000_000L
    private val line = LineDetail(100, "100", TransportType.BUS, "#123456")
    private fun news(id: Long, ageMs: Long, namedLine: AnnouncementLine = AnnouncementLine(null, "100", "#123456", TransportType.BUS)) =
        Announcement(id, "Service change", "Details", listOf(namedLine), now - ageMs)

    @Test fun matchesLineNamesWhenApiOmitsIdsAndKeepsAllRecentNotices() {
        val notices = listOf(news(1, 10_000), news(2, 1000), news(3, LINE_NEWS_WINDOW_MS))
        assertEquals(listOf(2L, 1L, 3L), recentLineNews(notices, line, emptySet(), now).map { it.id })
    }

    @Test fun excludesExpiredFutureDismissedAndOtherLines() {
        val notices = listOf(news(1, LINE_NEWS_WINDOW_MS + 1), news(2, -1000), news(3, 1000),
            news(4, 1000, AnnouncementLine(null, "101", "#123456", TransportType.BUS)),
            news(5, 1000, AnnouncementLine(null, "100", "#123456", TransportType.TRAM)))
        assertTrue(recentLineNews(notices, line, setOf(3), now).isEmpty())
    }

    @Test fun matchesStableIdsAndToleratesSecondTimestamps() {
        val notice = news(1, 1000, AnnouncementLine(100, "Old name", "#123456", TransportType.BUS))
        assertEquals(listOf(1L), recentLineNews(listOf(notice.copy(created_at = notice.created_at / 1000)), line, emptySet(), now).map { it.id })
    }

    @Test fun dismissalDoesNotHideOtherNoticesOnSameLine() {
        assertEquals(listOf(2L), recentLineNews(listOf(news(1, 1000), news(2, 1000)), line, setOf(1), now).map { it.id })
    }
}
