package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.SavedItemSource
import com.simon.harmonichackernews.data.SavedItemSnapshot
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.network.HackerNewsActionFailureReason
import com.simon.harmonichackernews.network.HackerNewsActionResult
import com.simon.harmonichackernews.settings.TestKeyValueStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SavedItemActionUseCaseTest {
    private val repository = SavedItemsRepository(TestKeyValueStore())
    private var voteResult: HackerNewsActionResult = HackerNewsActionResult.Success()
    private var favoriteResult: HackerNewsActionResult = HackerNewsActionResult.Success()
    private var requestedVote: Pair<Int, String>? = null
    private var requestedFavorite: Pair<Int, Boolean>? = null
    private val actions = SavedItemActionUseCase(
        repository = repository,
        nowMillis = { 1234L },
        voteRequest = { id, direction ->
            requestedVote = id to direction
            voteResult
        },
        favoriteRequest = { id, favorite ->
            requestedFavorite = id to favorite
            favoriteResult
        },
    )

    @Test
    fun toggleBookmarkPersistsPortableState() {
        assertTrue(actions.toggleBookmark(42))
        assertTrue(actions.isBookmarked(42))
        assertFalse(actions.toggleBookmark(42))
        assertFalse(actions.isBookmarked(42))
    }

    @Test
    fun successfulStoryVoteKeepsOptimisticState() = runTest {
        val pending = actions.beginVote(42, isComment = false, direction = "up")
        assertTrue(actions.isUpvoted(42, isComment = false))

        assertIs<SavedItemActionOutcome.Success>(actions.execute(pending))

        assertEquals(42 to "up", requestedVote)
        assertTrue(actions.isUpvoted(42, isComment = false))
    }

    @Test
    fun concurrentAtomicVoteTogglesDoNotLoseReadModifyWriteSteps() = runTest {
        coroutineScope {
            repeat(100) {
                launch(Dispatchers.Default) { actions.toggleVoteAtomic(42, isComment = false) }
            }
        }

        assertFalse(actions.isUpvoted(42, isComment = false))
    }

    @Test
    fun failedCommentVoteRestoresPreviousState() = runTest {
        repository.setCommentMembership(SavedItemSource.UPVOTED, 7, true)
        voteResult = HackerNewsActionResult.Failure("Nope")
        val pending = actions.beginVote(7, isComment = true, direction = "un")
        assertFalse(actions.isUpvoted(7, isComment = true))

        val outcome = assertIs<SavedItemActionOutcome.Failure>(actions.execute(pending))

        assertEquals(voteResult, outcome.result)
        assertTrue(actions.isUpvoted(7, isComment = true))
    }

    @Test
    fun failedFavoriteRestoresPreviousState() = runTest {
        favoriteResult = HackerNewsActionResult.Captcha(
            com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge(
                actionUrl = "https://news.ycombinator.com/favorite",
                siteKey = "site-key",
                formFields = emptyList(),
                useCookies = false,
            ),
        )
        val pending = actions.beginFavorite(99)
        assertTrue(actions.isFavorited(99))

        assertIs<SavedItemActionOutcome.Failure>(actions.execute(pending))

        assertEquals(99 to true, requestedFavorite)
        assertFalse(actions.isFavorited(99))
    }

    @Test
    fun cancellationAfterRequestDispatchLetsTheRemoteResultSettle() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val releaseResponse = CompletableDeferred<Unit>()
        val cancellableActions = SavedItemActionUseCase(
            repository = repository,
            nowMillis = { 1234L },
            voteRequest = { _, _ ->
                requestStarted.complete(Unit)
                releaseResponse.await()
                HackerNewsActionResult.Success()
            },
            favoriteRequest = { _, _ -> HackerNewsActionResult.Success() },
        )
        val pending = cancellableActions.beginVoteAtomic(404, false, "up")
        val request = launch { cancellableActions.execute(pending) }
        requestStarted.await()

        request.cancel()
        releaseResponse.complete(Unit)
        request.join()

        assertTrue(cancellableActions.isUpvoted(404, false))
    }

    @Test
    fun preDispatchFailureRollsBackTheOptimisticTarget() = runTest {
        val failingActions = SavedItemActionUseCase(
            repository = repository,
            nowMillis = { 1234L },
            voteRequest = { _, _ -> HackerNewsActionResult.Success() },
            favoriteRequest = { _, _ -> error("response connection lost") },
        )
        val pending = failingActions.beginFavoriteAtomic(405)

        assertIs<SavedItemActionOutcome.Failure>(failingActions.execute(pending))

        assertFalse(failingActions.isFavorited(405))
    }

    @Test
    fun indeterminatePostDispatchFailureRetainsTheOptimisticTargetForLaterSync() = runTest {
        val uncertainActions = SavedItemActionUseCase(
            repository = repository,
            nowMillis = { 1234L },
            voteRequest = { _, _ -> HackerNewsActionResult.Success() },
            favoriteRequest = { _, _ ->
                HackerNewsActionResult.Failure(
                    summary = "Response lost",
                    reason = HackerNewsActionFailureReason.INDETERMINATE,
                )
            },
        )
        val pending = uncertainActions.beginFavoriteAtomic(406)

        assertIs<SavedItemActionOutcome.Indeterminate>(uncertainActions.execute(pending))

        assertTrue(uncertainActions.isFavorited(406))
    }

    @Test
    fun staleFailureDoesNotOverwriteANewerMembershipRevision() = runTest {
        favoriteResult = HackerNewsActionResult.Failure("Nope")
        val pending = actions.beginFavoriteAtomic(505)
        assertTrue(actions.isFavorited(505))
        repository.setMembershipAtomic(
            SavedItemSource.FAVORITES,
            id = 505,
            present = true,
            createdAtMillis = 2000L,
        )

        assertIs<SavedItemActionOutcome.Failure>(actions.execute(pending))

        assertTrue(actions.isFavorited(505))
    }

    @Test
    fun commentFavoriteUpdatesItemAndClassificationAsOneMembership() = runTest {
        val pending = actions.beginFavoriteAtomic(606, isComment = true)
        val optimistic = repository.loadSnapshot(SavedItemSource.FAVORITES)
        assertTrue(606 in optimistic.itemIds)
        assertTrue(606 in optimistic.commentIds)

        favoriteResult = HackerNewsActionResult.Failure("Nope")
        assertIs<SavedItemActionOutcome.Failure>(actions.execute(pending))

        val restored = repository.loadSnapshot(SavedItemSource.FAVORITES)
        assertFalse(606 in restored.itemIds)
        assertFalse(606 in restored.commentIds)
    }

    @Test
    fun commentVoteUpdatesItemAndClassificationAsOneMembership() = runTest {
        val pending = actions.beginVoteAtomic(607, isComment = true, direction = "up")

        val optimistic = repository.loadSnapshot(SavedItemSource.UPVOTED)
        assertTrue(607 in optimistic.itemIds)
        assertTrue(607 in optimistic.commentIds)
        assertIs<SavedItemActionOutcome.Success>(actions.execute(pending))
    }

    @Test
    fun confirmedSuccessReappliesIntentOverInterveningStaleSnapshot() = runTest {
        val pending = actions.beginFavoriteAtomic(707)
        repository.saveSnapshotAtomic(
            SavedItemSource.FAVORITES,
            SavedItemSnapshot(emptyList(), emptySet()),
            createdAtMillis = 2000L,
        )
        assertFalse(actions.isFavorited(707))

        assertIs<SavedItemActionOutcome.Success>(actions.execute(pending))

        assertTrue(actions.isFavorited(707))
    }

    @Test
    fun confirmedOlderSuccessDoesNotOverwriteNewerUserMutation() = runTest {
        val older = actions.beginFavoriteAtomic(808)
        actions.beginFavoriteAtomic(808)
        assertFalse(actions.isFavorited(808))

        assertIs<SavedItemActionOutcome.Success>(actions.execute(older))

        assertFalse(actions.isFavorited(808))
    }

    @Test
    fun sameItemTransactionsAreSerializedBeforeTheirOptimisticMutation() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val firstResult = CompletableDeferred<HackerNewsActionResult>()
        val secondResult = CompletableDeferred<HackerNewsActionResult>()
        val requestedTargets = mutableListOf<Boolean>()
        var requestCount = 0
        val serializedActions = SavedItemActionUseCase(
            repository = repository,
            nowMillis = { 1234L },
            voteRequest = { _, _ -> HackerNewsActionResult.Success() },
            favoriteRequest = { _, favorite ->
                requestedTargets += favorite
                when (++requestCount) {
                    1 -> {
                        firstStarted.complete(Unit)
                        firstResult.await()
                    }
                    else -> {
                        secondStarted.complete(Unit)
                        secondResult.await()
                    }
                }
            },
        )

        val first = async { serializedActions.toggleFavoriteAndExecuteAtomic(909) }
        firstStarted.await()
        val second = async { serializedActions.toggleFavoriteAndExecuteAtomic(909) }
        runCurrent()
        assertFalse(secondStarted.isCompleted)

        firstResult.complete(HackerNewsActionResult.Failure("first failed"))
        assertIs<SavedItemActionOutcome.Failure>(first.await())
        secondStarted.await()
        secondResult.complete(HackerNewsActionResult.Failure("second failed"))
        assertIs<SavedItemActionOutcome.Failure>(second.await())

        assertEquals(listOf(true, true), requestedTargets)
        assertFalse(serializedActions.isFavorited(909))
    }

    @Test
    fun cancellingAQueuedTransactionDoesNotPoisonTheItemLock() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var requestCount = 0
        val serializedActions = SavedItemActionUseCase(
            repository = repository,
            nowMillis = { 1234L },
            voteRequest = { _, _ -> HackerNewsActionResult.Success() },
            favoriteRequest = { _, _ ->
                requestCount++
                if (requestCount == 1) {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                    HackerNewsActionResult.Success()
                } else {
                    HackerNewsActionResult.Success()
                }
            },
        )

        val holder = async { serializedActions.toggleFavoriteAndExecuteAtomic(910) }
        firstStarted.await()
        val cancelledWaiter = launch {
            serializedActions.toggleFavoriteAndExecuteAtomic(910)
        }
        runCurrent()
        cancelledWaiter.cancelAndJoin()
        releaseFirst.complete(Unit)
        assertIs<SavedItemActionOutcome.Success>(holder.await())

        assertIs<SavedItemActionOutcome.Success>(
            serializedActions.toggleFavoriteAndExecuteAtomic(910),
        )
        assertEquals(2, requestCount)
        assertFalse(serializedActions.isFavorited(910))
    }
}
