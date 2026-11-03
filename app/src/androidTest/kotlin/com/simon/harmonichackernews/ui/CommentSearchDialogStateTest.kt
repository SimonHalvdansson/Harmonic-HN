package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.ui.comments.CommentsSearchDialog
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommentSearchDialogStateTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aRestoredSearchDraftRepopulatesARecreatedResultStoreWithoutLosingEnteredText() {
        val query = mutableStateOf("")
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                CommentsSearchDialog(
                    searchTerm = query.value, visibleComments = emptyList(),
                    settings = settings, storyAuthor = null, accountUser = null,
                    maxDialogHeight = 800.dp,
                    onSearchTermChanged = { query.value = it },
                    onDismiss = {}, onCommentSelected = {}, onOpenLink = {},
                    onLinkLongClick = { _, _, _, _ -> },
                    onReferenceLongClick = { _, _, _, _ -> },
                )
            }
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("Keep this comment search")
        compose.onNode(hasSetTextAction()).performTextInputSelection(TextRange(5, 9))
        compose.runOnIdle { query.value = "" }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNode(hasSetTextAction()).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Keep this comment search")),
        )
        compose.onNode(hasSetTextAction()).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(5, 9)),
        )
        compose.runOnIdle { assertEquals("Keep this comment search", query.value) }
    }

    private val settings = CommentDisplaySettings(
        collapseParent = false, showFavicons = false, showHeaderPreviewImage = false,
        tintHeader = false, showUpButton = false, paletteTintMode = "default",
        preferredTextSize = 14f, commentDepthIndicatorMode = "threads", showNavigationBar = false,
        font = "default", showInvert = false, showTopLevelDepthIndicator = false, theme = null,
        isTablet = false, faviconProvider = "default", swapLongPressTap = false,
        displayStyle = DisplayStyle.RAISED, showDividers = false,
        highlightCommentMeta = false, collectReferenceLinks = false, hasAccountDetails = false,
        canProvideSummary = false, showAdditionalSummaryInfo = false, enableSummaryBoldFormatting = true,
    )
}
