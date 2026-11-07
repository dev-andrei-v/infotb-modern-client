package ro.infotb.modern.location

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Race usable providers; cancel all remaining sensor requests after a fix or timeout. */
internal suspend fun <T : Any> firstAvailableLocation(
    requests: List<suspend () -> T?>,
    timeoutMillis: Long = 12_000
): T? = withTimeoutOrNull(timeoutMillis) {
    coroutineScope {
        val results = Channel<T?>(requests.size)
        val jobs = requests.map { request -> launch { results.send(request()) } }
        try {
            repeat(requests.size) {
                results.receive()?.let { return@coroutineScope it }
            }
            null
        } finally {
            jobs.forEach { it.cancel() }
            results.cancel()
        }
    }
}
