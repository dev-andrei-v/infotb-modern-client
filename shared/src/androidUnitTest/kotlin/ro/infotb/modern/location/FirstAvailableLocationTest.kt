package ro.infotb.modern.location

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class FirstAvailableLocationTest {
    @Test fun networkFixDoesNotWaitForHangingGpsAndCancelsIt() = runTest {
        var gpsCancelled = false
        val expected = GeoPoint(44.43, 26.10)
        val actual = firstAvailableLocation(listOf(
            { try { awaitCancellation() } finally { gpsCancelled = true } },
            { delay(20); expected }
        ))
        assertEquals(expected, actual)
        assertTrue(gpsCancelled)
    }

    @Test fun unavailableProviderDoesNotDiscardLaterFix() = runTest {
        val expected = GeoPoint(44.43, 26.10)
        assertEquals(expected, firstAvailableLocation(listOf(
            { null }, { delay(20); expected }
        )))
    }

    @Test fun timeoutCancelsSensorRequestsAndAllowsFallback() = runTest {
        var cancelled = false
        val fallback = GeoPoint(44.43, 26.10)
        val actual = firstAvailableLocation<GeoPoint>(listOf(
            { try { awaitCancellation() } finally { cancelled = true } }
        ), timeoutMillis = 100) ?: fallback
        assertEquals(fallback, actual)
        assertTrue(cancelled)
    }

    @Test fun allUnavailableProvidersReturnNull() = runTest {
        assertNull(firstAvailableLocation<GeoPoint>(listOf({ null }, { null })))
        assertNull(firstAvailableLocation<GeoPoint>(emptyList()))
    }

    @Test fun leavingScreenCancelsRequestsInsteadOfReturningFallback() = runTest {
        val started = CompletableDeferred<Unit>()
        var cancelled = false
        val request = async {
            firstAvailableLocation<GeoPoint>(listOf({
                try { started.complete(Unit); awaitCancellation() }
                finally { cancelled = true }
            }))
        }
        started.await()
        request.cancel()
        assertFailsWith<CancellationException> { request.await() }
        assertTrue(cancelled)
    }
}
