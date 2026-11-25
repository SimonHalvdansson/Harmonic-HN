package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_translate
import com.simon.harmonichackernews.resources.ic_visibility
import kotlinx.coroutines.delay
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.CommentBooleanPreference
import com.simon.harmonichackernews.settings.CommentTranslationMode
import com.simon.harmonichackernews.ui.comments.*
import com.simon.harmonichackernews.ui.content.CommentRow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.Dp
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.data.CommentPresentationSnapshot
import com.simon.harmonichackernews.data.CommentSnapshot
import com.simon.harmonichackernews.data.StoryPresentationSnapshot
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.settings.forPureBlackDarkMode
import com.simon.harmonichackernews.ui.content.CommentRowStyleContext
import com.simon.harmonichackernews.ui.content.toCommentRowStyle
import com.simon.harmonichackernews.ui.common.PlatformDialogDimHost
import com.simon.harmonichackernews.ui.common.PlatformDisableDialogWindowAnimations
import com.simon.harmonichackernews.ui.common.PlatformDialogBackgroundDimAmount
import com.simon.harmonichackernews.ui.common.platformDialogProperties
import kotlin.time.Clock

@Composable
fun CommentTranslationSettingsRoute(repository: AppSettingsRepository, onBack: () -> Unit) {
    val service = LocalCommentTranslation.current
    val options = service?.rememberLanguageOptions()
    if (service == null || options == null || options.names.isEmpty()) {
        SettingsPage(title = "Comment translation", showNavigation = true, onBack = onBack) {
            item {
                SettingsDialogBodyText(when {
                    service == null -> "Translation is unavailable in this build."
                    options == null -> "Loading languages…"
                    else -> "Translation languages are unavailable on this device."
                })
            }
        }
        return
    }
    val settings by repository.updates.collectAsStateWithLifecycle(initialValue = repository.snapshot())
    val prefs = settings.comments.translation
    val defaultLanguage = options.defaultLanguage
    val languages = options.names
    val defaultLabel = "Device language (" + languages.getValue(defaultLanguage) + ")"
    var showDisclaimer by rememberSaveable { mutableStateOf(false) }
    var chooseLanguage by rememberSaveable { mutableStateOf(false) }
    val display = CommentDisplaySettings.from(
        preferences = settings.comments.forPureBlackDarkMode(settings.pureBlackDarkMode),
        showInvert = false, isTablet = false, hasAccountDetails = true, canProvideSummary = false,
    )
    SettingsPage(
        title = "Comment translation", showNavigation = true, onBack = onBack,
        headerContent = {
            SettingsMainToggle("Translate comments", checked = prefs.enabled, enabled = true,
                onCheckedChange = { repository.setCommentBoolean(CommentBooleanPreference.TRANSLATION, it) })
        },
        pinnedContent = {
            TranslationCommentPreview(
                display, language = prefs.language.takeIf { it in languages } ?: defaultLanguage,
                bookmarksEnabled = settings.general.bookmarksEnabled,
            )
        },
    ) {
        item {
            SettingsCategory("Translation") {
                SegmentedSetting(
                    title = "Translate comments", summary = prefs.mode.description,
                    options = CommentTranslationMode.entries.map { it to it.label },
                    selected = prefs.mode, onSelected = repository::setTranslationMode,
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Show original text while translating",
                    icon = Res.drawable.ic_visibility,
                    checked = prefs.showOriginalWhileTranslating,
                    enabled = prefs.enabled && prefs.mode == CommentTranslationMode.Automatic,
                    onCheckedChange = { repository.setCommentBoolean(CommentBooleanPreference.SHOW_ORIGINAL_WHILE_TRANSLATING, it) },
                )
                SettingsDivider()
                SettingRow("Translate to", languages[prefs.language] ?: defaultLabel, Res.drawable.ic_translate,
                    onClick = { chooseLanguage = true })
            }
        }
        item {
            Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)) {
                val explanation = if (service.requiresGoogleAttribution)
                    "Google Translate runs on this device. Language models download on first use."
                else "Comments are translated on this device. iOS may ask you to download a language."
                Text(explanation,
                    style = MaterialTheme.typography.bodySmall)
                if (service.requiresGoogleAttribution) TextButton(onClick = { showDisclaimer = true }, contentPadding = PaddingValues(vertical = 4.dp)) {
                    Text("Translation disclaimer", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
    if (showDisclaimer && service.requiresGoogleAttribution) MessageActionDialog(
        title = "Translation disclaimer",
        message = "THIS SERVICE MAY CONTAIN TRANSLATIONS POWERED BY GOOGLE. " +
            "GOOGLE DISCLAIMS ALL WARRANTIES RELATED TO THE TRANSLATIONS, EXPRESS OR IMPLIED, " +
            "INCLUDING ANY WARRANTIES OF ACCURACY, RELIABILITY, AND ANY IMPLIED WARRANTIES OF " +
            "MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.",
        positiveLabel = "OK",
        onPositive = { showDisclaimer = false },
        onDismiss = { showDisclaimer = false },
    )
    if (chooseLanguage) SingleChoiceDialog(
        title = "Translate to",
        options = listOf("" to defaultLabel) + languages.toList().sortedBy { it.second },
        selected = prefs.language,
        maxListHeight = Dp.Infinity,
        onDismiss = { chooseLanguage = false },
        onSelected = {
            repository.setTranslationLanguage(it)
            chooseLanguage = false
        },
    )
}

@Composable
private fun TranslationCommentPreview(display: CommentDisplaySettings, language: String, bookmarksEnabled: Boolean) {
    val service = LocalCommentTranslation.current ?: return
    val prefs = display.translation
    val actions = remember { HeaderPreviewActions() }
    val controller = remember(prefs, language) {
        CommentsScreenController.create(
            shouldSmoothScroll = { false },
            story = StoryListItemSnapshot(StorySnapshot(id = -1), StoryPresentationSnapshot()),
            showWebsite = false, accountUser = null, savedItemState = actions, listener = actions,
        )
    }
    val sample = remember(language) {
        val body = if (language.substringBefore('-') == "fr") "I love how much you can learn from a good discussion."
            else "J’aime tout ce que l’on peut apprendre en lisant une bonne discussion."
        PortableCommentItem(
            CommentSnapshot(id = 1, author = "alice", text = body, expandedAnchorText = body,
                createdAtEpochSeconds = Clock.System.now().epochSeconds.toInt() - 3600),
            CommentPresentationSnapshot(expanded = true),
        )
    }
    LaunchedEffect(controller, actions.version) {
        controller.updateContent(controller.screenState.copy(
            downvotedCommentIds = if (actions.downvoted) setOf(sample.id) else emptySet(),
        ))
    }
    val key = sample.translationKey(language.takeIf { prefs.enabled && prefs.mode != CommentTranslationMode.Details })
    val translations = controller.translations
    LaunchedEffect(controller, key) {
        if (key != null && prefs.mode == CommentTranslationMode.Automatic && translations[key] == null) {
            // Let the preview demonstrate the waiting state even with a cached model.
            delay(650)
            if (translations[key] == null) translations.toggle(key, service)
        }
    }
    var expanded by remember { mutableStateOf(true) }
    CommentRow(
        comment = sample.copy(presentation = sample.presentation.copy(expanded = expanded)),
        style = display.toCommentRowStyle(CommentRowStyleContext.Thread(animateChanges = true)).copy(depthIndicatorMode = "none"),
        storyAuthor = null, accountUser = null, userTag = null, subtreeReplyCount = 0,
        collapseParent = true, showTopLevelIndicator = false, isThreadRow = true, age = "1h",
        translationEnabled = true,
        waitingForTranslation = key != null && prefs.waitingToShowTranslation(translations[key]),
        translatedHtml = translations[key]?.visibleHtml, translationNotice = translations[key]?.notice,
        headerAction = {
            AnimatedContent(key, contentKey = { it != null }, label = "Preview translate button") { buttonKey ->
                if (buttonKey != null) TranslationButton(buttonKey, translations)
            }
        },
        onToggleExpanded = { expanded = !expanded },
        onShowActions = { controller.showCommentActions(sample, it) },
        captureActionSource = controller.isCommentActionOverlayShowing(),
        suppressActionSource = sample.id in controller.suppressedCommentIds && !controller.shouldKeepCommentActionSourceVisible(sample.id),
        onActionSourceGeometryChanged = { controller.updateCommentActionSourceGeometry(sample.id, it) },
        onLinkLongClick = { _, _, _ -> }, onReferenceLongClick = { _, _, _ -> },
    )
    if (LocalSettingsDialogsVisible.current && controller.isCommentActionOverlayShowing()) {
        PlatformDialogDimHost {
            Dialog(
                onDismissRequest = controller::requestDismissCommentActions,
                properties = platformDialogProperties(true, false, false, scrimEnabled = false),
            ) {
                PlatformDisableDialogWindowAnimations()
                PlatformDialogBackgroundDimAmount(0f)
                CommentActionOverlay(
                    controller, display, hasAccount = true, bookmarksEnabled = bookmarksEnabled,
                    textStyle = LocalSettingsPlatformStyle.current.textStyle, onOpenLink = {},
                )
            }
        }
    }
}
