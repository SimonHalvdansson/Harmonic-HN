package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.navigation.MainNavigationStore
import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CaptchaRequestsTest {
    private fun challenge(key: String = "fixture") = HackerNewsCaptchaChallenge(
        actionUrl = "https://news.ycombinator.com/login",
        siteKey = key,
        formFields = emptyList(),
        useCookies = true,
    )

    @Test
    fun blankAndStaleCallbacksLeaveThePendingActionIntact() = runTest {
        val navigation = MainNavigationStore()
        val requests = CaptchaRequests(navigation)
        val pending = async { requests.awaitResponse(challenge()) }
        runCurrent()
        val request = assertNotNull(navigation.state.value.captchaRequest)
        assertTrue(requests.owns(request.serial))
        requests.complete(request.serial, " \n")
        requests.complete(request.serial + 1, "wrong challenge")
        requests.cancel(request.serial + 1)
        runCurrent()
        assertFalse(pending.isCompleted)
        assertEquals(request, navigation.state.value.captchaRequest)
        requests.complete(request.serial, " token ")
        assertEquals("token", pending.await())
        assertNull(navigation.state.value.captchaRequest)
        assertFalse(requests.owns(request.serial))
    }

    @Test
    fun serializedChallengesKeepTheirOwnResponsesAndIgnoreEarlierCallbacks() = runTest {
        val navigation = MainNavigationStore()
        val requests = CaptchaRequests(navigation)
        val first = async { requests.awaitResponse(challenge("first")) }
        runCurrent()
        val firstRequest = assertNotNull(navigation.state.value.captchaRequest)
        val second = async { requests.awaitResponse(challenge("second")) }
        runCurrent()
        assertEquals(firstRequest, navigation.state.value.captchaRequest)
        requests.complete(firstRequest.serial, "first response")
        runCurrent()
        assertEquals("first response", first.await())
        val secondRequest = assertNotNull(navigation.state.value.captchaRequest)
        assertTrue(secondRequest.serial > firstRequest.serial)
        assertEquals("second", secondRequest.challenge.siteKey)
        requests.cancel(firstRequest.serial)
        requests.complete(firstRequest.serial, "late first response")
        runCurrent()
        assertFalse(second.isCompleted)
        requests.complete(secondRequest.serial, "second response")
        assertEquals("second response", second.await())
        assertNull(navigation.state.value.captchaRequest)
    }

    @Test
    fun cancellingTheActiveCoroutineCleansItsDialogAndOpensTheNextWaiter() = runTest {
        val navigation = MainNavigationStore()
        val requests = CaptchaRequests(navigation)
        val first = async { requests.awaitResponse(challenge("cancelled")) }
        runCurrent()
        val firstRequest = assertNotNull(navigation.state.value.captchaRequest)
        val second = async { requests.awaitResponse(challenge("next")) }
        runCurrent()
        first.cancelAndJoin()
        runCurrent()
        val secondRequest = assertNotNull(navigation.state.value.captchaRequest)
        assertEquals("next", secondRequest.challenge.siteKey)
        assertFalse(requests.owns(firstRequest.serial))
        requests.complete(firstRequest.serial, "stale cancelled token")
        requests.complete(secondRequest.serial, "next token")
        assertEquals("next token", second.await())
        assertNull(navigation.state.value.captchaRequest)
    }

    @Test
    fun cancellingAQueuedCoroutineCannotDismissTheActiveChallengeOrStarveTheNext() = runTest {
        val navigation = MainNavigationStore()
        val requests = CaptchaRequests(navigation)
        val first = async { requests.awaitResponse(challenge("active")) }
        runCurrent()
        val firstRequest = assertNotNull(navigation.state.value.captchaRequest)
        val cancelled = async { requests.awaitResponse(challenge("never shown")) }
        val third = async { requests.awaitResponse(challenge("last")) }
        runCurrent()
        cancelled.cancelAndJoin()
        assertEquals(firstRequest, navigation.state.value.captchaRequest)
        requests.cancel(firstRequest.serial)
        assertNull(first.await())
        runCurrent()
        val thirdRequest = assertNotNull(navigation.state.value.captchaRequest)
        assertEquals("last", thirdRequest.challenge.siteKey)
        requests.complete(thirdRequest.serial, "last token")
        assertEquals("last token", third.await())
        assertNull(navigation.state.value.captchaRequest)
    }

    @Test
    fun completionAndCancellationResolveExactlyOnceInEitherOrder() = runTest {
        for (completionFirst in listOf(true, false)) {
            val navigation = MainNavigationStore()
            val requests = CaptchaRequests(navigation)
            val pending = async { requests.awaitResponse(challenge()) }
            runCurrent()
            val serial = assertNotNull(navigation.state.value.captchaRequest).serial
            if (completionFirst) {
                requests.complete(serial, "only token")
                requests.cancel(serial)
            } else {
                requests.cancel(serial)
                requests.complete(serial, "too late")
            }
            assertEquals(if (completionFirst) "only token" else null, pending.await())
            assertNull(navigation.state.value.captchaRequest)
        }
    }

    @Test
    fun closingASceneCancelsActiveAndQueuedRequestsAndPreventsNewDialogs() = runTest {
        val navigation = MainNavigationStore()
        val requests = CaptchaRequests(navigation)
        val active = async { requests.awaitResponse(challenge("active")) }
        val queued = async { requests.awaitResponse(challenge("queued")) }
        runCurrent()
        requests.close()
        assertNull(active.await())
        assertNull(queued.await())
        assertNull(navigation.state.value.captchaRequest)
        assertNull(requests.awaitResponse(challenge("closed scene")))
        assertNull(navigation.state.value.captchaRequest)
    }

    @Test
    fun cleanupCannotDismissADifferentRequestPublishedByTheHost() = runTest {
        val navigation = MainNavigationStore()
        val requests = CaptchaRequests(navigation)
        val pending = async { requests.awaitResponse(challenge("old")) }
        runCurrent()
        val replacement = navigation.showCaptchaDialog(challenge("replacement"))
        pending.cancelAndJoin()
        assertEquals(replacement, navigation.state.value.captchaRequest)
        requests.close()
        navigation.dismissCaptchaDialog()
    }
}
