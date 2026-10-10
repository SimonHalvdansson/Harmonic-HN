package com.simon.harmonichackernews.ui.comments

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberTransition
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.simon.harmonichackernews.ui.navigation.ActivityNavigationTransitionDurationMillis
import com.simon.harmonichackernews.ui.navigation.LocalActivityNavigationTransition
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CommentsInitialRevealTest {
    @Test
    fun earlyCommentsWaitOnlyUntilOpeningFinishes() = runTest {
        withReveal {
            frames(6)
            hasComments.value = true
            frames(18)
            assertEquals(0f, reveal.value, "Comments must stay hidden during the opening")
            frames(10)
            assertTrue(reveal.value > 0f, "Fade must start as soon as the opening finishes")
            frames(20)
            assertEquals(1f, reveal.value)
            hasComments.value = false
            frames(2)
            hasComments.value = true
            frames(2)
            assertEquals(1f, reveal.value, "Later list updates must not replay the reveal")
        }
    }

    @Test
    fun slowRequestStartsFadingWithoutAnotherDelay() = runTest {
        withReveal {
            frames(63) // More than a second after opening.
            hasComments.value = true
            frames(4)
            assertTrue(reveal.value > 0f, "Ready comments must not wait another 320 ms")
            frames(16)
            assertEquals(1f, reveal.value)
        }
    }

    @Test
    fun screenWithoutNavigationAnimationDoesNotWait() = runTest {
        withReveal(withOpening = false) {
            hasComments.value = true
            frames(4)
            assertTrue(reveal.value > 0f)
        }
    }

    @Test
    fun cachedContentAndDisabledAnimationsStayVisibleDuringOpening() = runTest {
        withReveal(initiallyVisible = true) {
            assertEquals(1f, reveal.value)
        }
        withReveal(animateComments = false) {
            assertEquals(1f, reveal.value)
        }
    }

    private class RevealFixture(val frame: () -> Unit) {
        val hasComments = mutableStateOf(false)
        lateinit var reveal: Animatable<Float, AnimationVector1D>
        fun frames(count: Int) = repeat(count) { frame() }
    }

    private suspend fun TestScope.withReveal(
        withOpening: Boolean = true,
        initiallyVisible: Boolean = false,
        animateComments: Boolean = true,
        block: RevealFixture.() -> Unit,
    ) {
        val clock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + clock)
        val composition = Composition(EmptyApplier(), recomposer)
        val runner = launch(clock) { recomposer.runRecomposeAndApplyChanges() }
        val opening = MutableTransitionState(EnterExitState.PreEnter).apply {
            targetState = EnterExitState.Visible
        }
        var time = 0L
        val fixture = RevealFixture {
            Snapshot.sendApplyNotifications()
            runCurrent()
            time += 16_000_000L
            clock.sendFrame(time)
            runCurrent()
            Snapshot.sendApplyNotifications()
            runCurrent()
        }
        try {
            composition.setContent {
                val transition = rememberTransition(opening, label = "page opening")
                transition.animateFloat(
                    transitionSpec = { tween(ActivityNavigationTransitionDurationMillis) },
                    label = "page offset",
                ) { if (it == EnterExitState.Visible) 0f else 96f }
                CompositionLocalProvider(
                    LocalActivityNavigationTransition provides transition.takeIf { withOpening },
                ) {
                    fixture.reveal = rememberInitialCommentsReveal(
                        storyId = 42,
                        initiallyVisible = initiallyVisible,
                        hasComments = fixture.hasComments.value,
                        animateComments = animateComments,
                    )
                }
            }
            fixture.frames(1)
            fixture.block()
        } finally {
            composition.dispose()
            recomposer.cancel()
            runner.join()
        }
    }

    private class EmptyApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun onClear() = Unit
    }
}
