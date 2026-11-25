package com.simon.harmonichackernews.ui.comments

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import com.simon.harmonichackernews.platform.TranslationLanguages
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.platform.CommentTranslationResult
import com.simon.harmonichackernews.platform.CommentTranslationService
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.presentation.PortableVisibleComment
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.translated_by_google_greyscale
import com.simon.harmonichackernews.resources.translated_by_google_white
import com.simon.harmonichackernews.resources.ic_translate
import com.simon.harmonichackernews.settings.CommentTranslationPreferences
import com.simon.harmonichackernews.settings.CommentTranslationMode
import com.simon.harmonichackernews.ui.common.HarmonicLoadingIndicator
import com.simon.harmonichackernews.ui.common.HarmonicTooltip
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

internal val LocalCommentTranslation = staticCompositionLocalOf<CommentTranslationService?> { null }

internal data class TranslationKey(val id: Int, val html: String, val language: String)

internal fun PortableCommentItem.translationKey(language: String?): TranslationKey? {
    val html = expandedAnchorText
    if (language == null || html.isNullOrBlank() || html in placeholders) return null
    return TranslationKey(id, html, language)
}

private val placeholders = setOf("[deleted]", "[dead]", "[flagged]", "[delayed]")

internal data class TranslationDisplay(
    val translated: String? = null,
    val showOriginal: Boolean = false,
    val loading: Boolean = false,
    val notice: String? = null,
    val retry: Boolean = false,
) {
    val visibleHtml get() = translated.takeUnless { showOriginal }
}

internal fun CommentTranslationPreferences.waitingToShowTranslation(state: TranslationDisplay?): Boolean =
    enabled && mode == CommentTranslationMode.Automatic && !showOriginalWhileTranslating &&
        (state == null || state.loading)

internal class CommentTranslations {
    private val entries = mutableStateMapOf<TranslationKey, TranslationDisplay>()
    operator fun get(key: TranslationKey?) = key?.let(entries::get)

    suspend fun toggle(key: TranslationKey, service: CommentTranslationService) {
        val prior = entries[key]
        if (prior?.translated != null) {
            entries[key] = prior.copy(showOriginal = !prior.showOriginal)
            return
        }
        if (prior != null && !prior.retry) return
        entries[key] = TranslationDisplay(loading = true)
        try {
            entries[key] = when (val result = service.translate(key.html, key.language)) {
                is CommentTranslationResult.Translated -> TranslationDisplay(translated = result.html)
                CommentTranslationResult.SameLanguage -> TranslationDisplay(notice = "Already in the selected language")
                CommentTranslationResult.Unsupported -> TranslationDisplay(notice = "Language unavailable")
            }
        } catch (cancelled: CancellationException) {
            entries.remove(key)
            throw cancelled
        } catch (_: Exception) {
            entries[key] = TranslationDisplay(notice = "Translation failed. Tap to retry.", retry = true)
        }
    }
}

@Composable
internal fun translationTarget(preferences: CommentTranslationPreferences): String? {
    val service = LocalCommentTranslation.current ?: return null
    if (!preferences.enabled) return null
    val options = service.rememberLanguageOptions() ?: return null
    return preferences.language.takeIf { it in options.names } ?: options.defaultLanguage.takeIf { it in options.names }
}

@Composable
internal fun CommentTranslationService.rememberLanguageOptions(): TranslationLanguages? {
    val options by produceState<TranslationLanguages?>(null, this) { value = languageOptions() }
    return options
}

@Composable
internal fun TranslateVisibleComments(
    translations: CommentTranslations,
    language: String,
    list: LazyListState,
    rows: List<PortableVisibleComment>,
    collapseParent: Boolean,
) {
    val service = LocalCommentTranslation.current ?: return
    val nextSibling = remember(rows) { nextVisibleSiblings(rows) }
    LaunchedEffect(translations, list, rows, language, service, collapseParent) {
        // Keep working on the same comment while it has priority. A scroll past it cancels
        // the wait, including when a different language model is still downloading.
        var active: TranslationKey? = null
        snapshotFlow {
            val visible = list.layoutInfo.visibleItemsInfo
            val first = ((visible.firstOrNull()?.index ?: return@snapshotFlow null) - 1).coerceAtLeast(0)
            val last = (visible.last().index - 1).coerceAtMost(rows.lastIndex)
            val ahead = if (list.lastScrolledBackward) first - 1 downTo maxOf(0, first - 3)
                else last + 1..minOf(rows.lastIndex, last + 3)
            val viewport = sequence {
                for (index in first..last) {
                    yield(index)
                    // Collapsing this branch brings its next sibling directly into view.
                    val sibling = nextSibling[index]
                    if (sibling > index + 1) yield(sibling)
                }
            }
            (viewport + ahead.asSequence()).mapNotNull { index ->
                rows.getOrNull(index)?.comment?.takeIf { it.expanded || !collapseParent }?.translationKey(language)
            }.firstOrNull { key ->
                val state = translations[key]
                state == null || (key == active && state.loading)
            }
        }.collectLatest { key ->
            active = key
            if (key != null && translations[key] == null) translations.toggle(key, service)
        }
    }
}

internal fun nextVisibleSiblings(rows: List<PortableVisibleComment>): IntArray {
    val nextByParent = mutableMapOf<Int, Int>()
    val siblings = IntArray(rows.size) { -1 }
    for (index in rows.indices.reversed()) {
        siblings[index] = nextByParent.put(rows[index].comment.parent, index) ?: -1
    }
    return siblings
}

@Composable
internal fun TranslationButton(key: TranslationKey, translations: CommentTranslations, inDialog: Boolean = false) {
    val service = LocalCommentTranslation.current ?: return
    val scope = rememberCoroutineScope()
    val state = translations[key]
    val label = when {
        state?.loading == true -> "Translating comment"
        state?.visibleHtml != null -> "Show original comment"
        state?.notice != null -> state.notice
        service.requiresGoogleAttribution -> "Translate with Google"
        else -> "Translate comment"
    }
    HarmonicTooltip(label) {
        IconButton(
            onClick = { scope.launch { translations.toggle(key, service) } },
            colors = if (inDialog) IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                else IconButtonDefaults.iconButtonColors(),
            enabled = state?.loading != true && (state?.notice == null || state.retry),
            modifier = if (inDialog) Modifier.size(48.dp) else Modifier.size(width = 32.dp, height = 22.dp),
        ) {
            val iconSize = if (inDialog) 20.dp else 16.dp
            if (state?.loading == true) {
                HarmonicLoadingIndicator(Modifier.size(iconSize).semantics { contentDescription = label })
            } else {
                Icon(
                    painterResource(Res.drawable.ic_translate), label, Modifier.size(iconSize),
                    tint = if (state?.visibleHtml != null) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                )
            }
        }
    }
}

private data class TranslationBody(val html: String, val translated: Boolean, val waiting: Boolean)

@Composable
internal fun TranslationContent(
    enabled: Boolean,
    originalHtml: String,
    translatedHtml: String?,
    waiting: Boolean = false,
    highlightQuotes: Boolean = false,
    content: @Composable (String) -> Unit,
) {
    if (!enabled) {
        content(originalHtml)
        return
    }
    // Highlighted quotes draw 5dp into the left margin. Include that margin inside both
    // crossfade layers so their rail is visible on every frame, including at partial alpha.
    val gutter = if (highlightQuotes) 5.dp else 0.dp
    AnimatedContent(
        targetState = TranslationBody(translatedHtml ?: originalHtml, translatedHtml != null, waiting),
        modifier = if (!highlightQuotes) Modifier else Modifier.layout { measurable, constraints ->
            val inset = gutter.roundToPx()
            val child = measurable.measure(constraints.offset(horizontal = inset))
            layout((child.width - inset).coerceAtLeast(0), child.height) { child.place(-inset, 0) }
        },
        transitionSpec = {
            (fadeIn(tween(200)) togetherWith fadeOut(tween(160))).using(SizeTransform(clip = false))
        },
        label = "Comment translation",
    ) { body ->
        Column(Modifier.absolutePadding(left = gutter)) {
            if (body.waiting) {
                Box {
                    // Measure rich text at its actual width, but leave links and text unplaced
                    // while the placeholder is visible.
                    Box(Modifier.clearAndSetSemantics { }.layout { measurable, constraints ->
                        val original = measurable.measure(constraints)
                        layout(original.width, original.height) { }
                    }) { content(body.html) }
                    Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                        HarmonicLoadingIndicator(Modifier.size(20.dp).semantics { contentDescription = "Translating comment" })
                    }
                }
            } else {
                content(body.html)
            }
            if (body.translated && LocalCommentTranslation.current?.requiresGoogleAttribution == true) {
                Image(
                    painterResource(if (HarmonicTheme.isDark) Res.drawable.translated_by_google_white else Res.drawable.translated_by_google_greyscale),
                    contentDescription = "Translated by Google",
                    modifier = Modifier.padding(top = 4.dp).width(122.dp),
                )
            }
        }
    }
}
