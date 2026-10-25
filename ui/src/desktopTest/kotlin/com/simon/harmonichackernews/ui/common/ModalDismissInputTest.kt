package com.simon.harmonichackernews.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.ui.comments.CommentNavigationButtons
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModalDismissInputTest {
    @Test
    fun dimmedBackButtonDismissesInsteadOfLeavingTheScreen() = SwingUtilities.invokeAndWait {
        val active = mutableStateOf(true)
        var dismissed = 0
        var navigated = 0
        val scene = ImageComposeScene(48, 48, Density(1f)) {
            MaterialTheme {
                TranslucentBackButton(
                    onClick = { navigated++ },
                    modalScrimAlpha = if (active.value) 0.32f else 0f,
                    modalScrimActive = active.value,
                    onModalDismiss = { dismissed++ },
                )
            }
        }
        var time = 0L
        fun frames() { repeat(5) { scene.render(time.also { time += 16_000_000 }).close() } }
        fun tap() {
            scene.sendPointerEvent(PointerEventType.Press, Offset(24f, 24f), type = PointerType.Touch)
            scene.sendPointerEvent(PointerEventType.Release, Offset(24f, 24f), type = PointerType.Touch)
            frames()
        }
        try {
            frames()
            tap()
            assertEquals(1, dismissed)
            assertEquals(0, navigated)
            active.value = false
            frames()
            tap()
            assertEquals(1, dismissed)
            assertEquals(1, navigated)
        } finally { scene.close() }
    }

    @Test
    fun roundedCardCornersDismissWhileBodyButtonsAndScrollingRemainInteractive() = SwingUtilities.invokeAndWait {
        var dismissed = 0
        var clicked = 0
        val scroll = ScrollState(0)
        val scene = ImageComposeScene(300, 300, Density(1f)) {
            Box(Modifier.fillMaxSize().clickable { dismissed++ }) {
                Box(Modifier.size(300.dp).modalCardTapBoundary(28.dp) { dismissed++ }) {
                    Column(Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).verticalScroll(scroll)) {
                        Box(Modifier.size(100.dp).clickable { clicked++ })
                        Box(Modifier.height(600.dp).background(Color.White))
                    }
                }
            }
        }
        var time = 0L
        fun frame() { scene.render(time.also { time += 16_000_000 }).close() }
        fun pointer(event: PointerEventType, point: Offset) {
            scene.sendPointerEvent(event, point, timeMillis = time / 1_000_000, type = PointerType.Touch)
            frame()
        }
        fun tap(point: Offset) {
            pointer(PointerEventType.Press, point)
            pointer(PointerEventType.Release, point)
        }
        try {
            repeat(5) { frame() }
            tap(Offset(150f, 150f))
            assertEquals(0, dismissed, "Blank card content must not dismiss")
            tap(Offset(50f, 50f))
            assertEquals(1, clicked, "Child actions must still receive taps")
            assertEquals(0, dismissed)
            for (corner in listOf(Offset(1f, 1f), Offset(299f, 1f), Offset(1f, 299f), Offset(299f, 299f))) {
                tap(corner)
            }
            assertEquals(4, dismissed, "All transparent corners must dismiss exactly once")
            pointer(PointerEventType.Press, Offset(150f, 250f))
            repeat(8) { pointer(PointerEventType.Move, Offset(150f, 250f - (it + 1) * 20f)) }
            pointer(PointerEventType.Release, Offset(150f, 90f))
            assertTrue(scroll.value > 80, "The card must still scroll with touch")
            assertEquals(4, dismissed, "A drag must not dismiss")
        } finally { scene.close() }
    }

    @Test
    fun dimmedNavigationButtonsDismissWithoutNavigatingAndRecoverAfterModalCloses() = SwingUtilities.invokeAndWait {
        val active = mutableStateOf(true)
        var dismissed = 0
        var navigated = 0
        val scene = ImageComposeScene(160, 56, Density(1f)) {
            MaterialTheme {
                CommentNavigationButtons(
                    onPrevious = { navigated++ }, onNext = { navigated++ },
                    onFirst = { navigated++ }, onLast = { navigated++ },
                    modalScrimAlpha = if (active.value) 0.32f else 0f,
                    modalScrimActive = active.value,
                    onModalDismiss = { dismissed++ },
                )
            }
        }
        var time = 0L
        fun frames() { repeat(5) { scene.render(time.also { time += 16_000_000 }).close() } }
        fun tap(x: Float) {
            scene.sendPointerEvent(PointerEventType.Press, Offset(x, 28f), type = PointerType.Touch)
            scene.sendPointerEvent(PointerEventType.Release, Offset(x, 28f), type = PointerType.Touch)
            frames()
        }
        try {
            frames()
            tap(28f)
            tap(80f)
            tap(132f)
            assertEquals(3, dismissed)
            assertEquals(0, navigated)
            active.value = false
            frames()
            tap(28f)
            tap(132f)
            assertEquals(2, navigated)
            assertEquals(3, dismissed)
        } finally { scene.close() }
    }
}
