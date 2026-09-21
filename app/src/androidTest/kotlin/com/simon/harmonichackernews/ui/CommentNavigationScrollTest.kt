@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.ui.comments.animateToCommentNavigationTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommentNavigationScrollTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var state: LazyListState
    private lateinit var scope: CoroutineScope
    private val composedRows = mutableSetOf<Int>()

    @Test
    fun distantNavigationBoundsCompositionAndPreservesInsetsInBothDirections() {
        setList()
        scrollTo(900, -80)
        compose.runOnIdle {
            assertEquals(80, state.layoutInfo.visibleItemsInfo.single { it.index == 900 }.offset)
            assertTrue("Skipped rows must not be composed: ${composedRows.size}", composedRows.size < 40)
            composedRows.clear()
        }
        scrollTo(0, 0)
        compose.runOnIdle {
            assertEquals(0, state.firstVisibleItemIndex)
            assertEquals(0, state.firstVisibleItemScrollOffset)
            assertTrue("Return trip must also skip rows: ${composedRows.size}", composedRows.size < 40)
        }
    }

    @Test
    fun shortLastRowClampsToEndAndCancellationDoesNotSnapBack() {
        setList()
        scrollTo(999, -80)
        compose.runOnIdle { assertTrue(!state.canScrollForward) }
        compose.mainClock.autoAdvance = false
        lateinit var navigation: Job
        compose.runOnIdle {
            navigation = scope.launch { state.animateToCommentNavigationTarget(0, 0, true) }
        }
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle { navigation.cancel() }
        val index = state.firstVisibleItemIndex
        val offset = state.firstVisibleItemScrollOffset
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle {
            assertTrue(navigation.isCancelled)
            assertEquals(index, state.firstVisibleItemIndex)
            assertEquals(offset, state.firstVisibleItemScrollOffset)
        }
    }

    private fun setList() {
        compose.setContent {
            state = rememberLazyListState()
            scope = rememberCoroutineScope()
            LazyColumn(state = state, modifier = Modifier.height(600.dp)) {
                items(1_000) { index ->
                    SideEffect { composedRows.add(index) }
                    // Exercise multi-screen comments, varied heights, and a short final row.
                    Box(Modifier.height(if (index % 7 == 0) 1_400.dp else 80.dp))
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { composedRows.clear() }
    }

    private fun scrollTo(index: Int, offset: Int) {
        lateinit var navigation: Job
        compose.runOnIdle {
            navigation = scope.launch {
                state.animateToCommentNavigationTarget(index, offset, true)
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(navigation.isCompleted && !navigation.isCancelled) }
    }
}
