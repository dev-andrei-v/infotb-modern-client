package ro.infotb.modern.util

import ro.infotb.modern.data.model.Announcement
import ro.infotb.modern.data.model.LineDetail

const val LINE_NEWS_WINDOW_MS = 24 * 60 * 60 * 1000L

fun recentLineNews(notices: List<Announcement>, line: LineDetail?, dismissed: Set<Long>, nowMs: Long): List<Announcement> {
    if (line == null) return emptyList()
    return notices.filter { notice ->
        // STB uses milliseconds; tolerate older cached responses in seconds.
        val publishedMs = if (notice.created_at < 100_000_000_000L) notice.created_at * 1000 else notice.created_at
        notice.id !in dismissed && publishedMs > 0 && nowMs - publishedMs in 0..LINE_NEWS_WINDOW_MS &&
            notice.lines.any { it.id == line.id || (it.name.trim().equals(line.name.trim(), ignoreCase = true) && it.type == line.type) }
    }.sortedByDescending { it.created_at }
}
