package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.presentation.detectUserProfileLinks
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_link
import com.simon.harmonichackernews.ui.common.TextButton
import com.simon.harmonichackernews.ui.content.htmlAnnotatedString
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun UserProfileBio(userId: String, about: String, onOpenLink: (String) -> Unit) {
    var showLinks by rememberSaveable(userId) { mutableStateOf(false) }
    val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant
    val linkColor = MaterialTheme.colorScheme.primary
    val listener = remember(onOpenLink) {
        LinkInteractionListener { if (it is LinkAnnotation.Url) onOpenLink(it.url) }
    }
    val formatted = remember(about, linkColor, listener) { htmlAnnotatedString(about, linkColor, listener) }
    val detected = remember(formatted) {
        detectUserProfileLinks(formatted.text).filter { formatted.getLinkAnnotations(it.start, it.end).isEmpty() }
    }
    val progress by animateFloatAsState(if (showLinks) 1f else 0f, tween(180), label = "Profile link appearance")
    val animatedColor = lerp(bodyColor, linkColor, progress)
    val text = remember(formatted, detected, showLinks, animatedColor, listener) {
        buildAnnotatedString {
            append(formatted)
            detected.forEach { link ->
                addStyle(SpanStyle(color = animatedColor), link.start, link.end)
                if (showLinks) addLink(
                    LinkAnnotation.Url(link.url, TextLinkStyles(SpanStyle(color = animatedColor)), listener),
                    link.start, link.end,
                )
            }
        }
    }
    var layout by remember(formatted) { mutableStateOf<TextLayoutResult?>(null) }
    Column(Modifier.fillMaxWidth()) {
        if (formatted.isNotBlank()) SelectionContainer {
            Text(
                text,
                modifier = Modifier.testTag("user-profile-bio").drawWithContent {
                    drawContent()
                    // Keep one paragraph and selection owner. Only inferred links change color
                    // and gain underlines; unrelated glyphs are never faded or drawn twice.
                    val measured = layout
                    if (measured != null && progress > 0f) detected.forEach { link ->
                        clipPath(measured.getPathForRange(link.start, link.end)) {
                            for (line in measured.getLineForOffset(link.start)..measured.getLineForOffset(link.end - 1)) {
                                val y = measured.getLineBaseline(line) + 1.5.dp.toPx()
                                drawLine(linkColor.copy(alpha = progress), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                            }
                        }
                    }
                },
                onTextLayout = { layout = it },
                color = bodyColor,
                fontFamily = ProductSansFontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
        if (detected.isNotEmpty()) {
            TextButton(onClick = { showLinks = !showLinks },
                modifier = Modifier.align(Alignment.End).testTag("user-profile-links-toggle")) {
                Icon(painterResource(Res.drawable.ic_link), null, Modifier.size(18.dp))
                AnimatedContent(
                    targetState = showLinks,
                    modifier = Modifier.padding(start = 6.dp),
                    contentAlignment = Alignment.CenterEnd,
                    transitionSpec = {
                        (fadeIn(tween(140, delayMillis = 40)) togetherWith fadeOut(tween(100)))
                            .using(SizeTransform(clip = false) { _, _ -> tween(220, easing = FastOutSlowInEasing) })
                    },
                    label = "Profile links button label",
                ) { clickable -> Text(if (clickable) "Plain text" else "Clickable links") }
            }
        }
    }
}
