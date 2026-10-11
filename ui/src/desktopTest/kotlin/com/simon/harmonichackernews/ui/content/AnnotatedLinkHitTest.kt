package com.simon.harmonichackernews.ui.content

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnnotatedLinkHitTest {
    @Test fun secondaryClickOnBlankSpaceMustNotOpenNearbyLink() = SwingUtilities.invokeAndWait {
        var layout: TextLayoutResult? = null
        var coordinates: LayoutCoordinates? = null
        var previews = 0
        val body = buildAnnotatedString {
            append("Link")
            addLink(LinkAnnotation.Url("https://example.com"), 0, 4)
        }
        val scene = ImageComposeScene(400, 120, Density(1f)) {
            val gesture = remember { AnnotatedLinkGestureState() }
            Text(body, fontSize = 16.sp,
                modifier = Modifier.fillMaxWidth()
                    .onGloballyPositioned { coordinates = it }
                    .detectAnnotatedLinkLongPress(body, { layout }, { coordinates }, gesture,
                        LocalHapticFeedback.current, { _, _, _ -> previews++ }),
                onTextLayout = { layout = it })
        }
        try {
            repeat(5) { scene.render(it * 16_000_000L).close() }
            assertTrue(layout!!.getLineRight(0) < 100f)
            fun secondaryClick(position: Offset) {
                scene.sendPointerEvent(PointerEventType.Press, position,
                    buttons = PointerButtons(isSecondaryPressed = true), button = PointerButton.Secondary)
                scene.sendPointerEvent(PointerEventType.Release, position,
                    buttons = PointerButtons(), button = PointerButton.Secondary)
            }
            val blank = Offset(350f, layout!!.size.height / 2f)
            scene.sendPointerEvent(PointerEventType.Press, blank,
                buttons = PointerButtons(isSecondaryPressed = true), button = PointerButton.Secondary)
            scene.sendPointerEvent(PointerEventType.Release, blank,
                buttons = PointerButtons(), button = PointerButton.Secondary)
            scene.render(96_000_000L).close()
            assertEquals(0, previews, "The empty part of a full-width text row should not open the final link")
            val glyph = layout!!.getBoundingBox(3)
            secondaryClick(Offset(glyph.left + glyph.width * 0.2f, glyph.center.y))
            secondaryClick(Offset(glyph.left + glyph.width * 0.8f, glyph.center.y))
            assertEquals(2, previews, "Both halves of the final linked character must remain interactive")
        } finally { scene.close() }
    }
}
