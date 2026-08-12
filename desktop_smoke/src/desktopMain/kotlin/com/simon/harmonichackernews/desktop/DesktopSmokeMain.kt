package com.simon.harmonichackernews.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.navigation.EditorDestination
import com.simon.harmonichackernews.navigation.EditorType
import com.simon.harmonichackernews.presentation.StoryFeedRefreshPolicy
import com.simon.harmonichackernews.settings.StoryBooleanPreference
import com.simon.harmonichackernews.settings.StoryStringPreference
import com.simon.harmonichackernews.ui.content.HarmonicDropdownMenu
import com.simon.harmonichackernews.ui.content.HarmonicMenuText
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.ui.content.StoryItem
import com.simon.harmonichackernews.ui.content.StoryItemStyle
import com.simon.harmonichackernews.ui.editor.SharedEditorScreen
import com.simon.harmonichackernews.ui.stories.SharedStoriesRoot
import com.simon.harmonichackernews.ui.theme.HarmonicColors
import com.simon.harmonichackernews.ui.theme.HarmonicTheme

fun main() {
    val bootstrap = DesktopHarmonicAppBootstrap.inMemory(
        userAgent = "Harmonic-HN-Desktop-Smoke",
    )
    try {
        application {
            Window(
                onCloseRequest = ::exitApplication,
                title = "Harmonic KMP smoke host",
            ) {
                HarmonicTheme(
                    colors = desktopColors,
                    colorScheme = lightColorScheme(
                        primary = desktopColors.accent,
                        background = desktopColors.background,
                        surface = desktopColors.background,
                        onSurface = desktopColors.onSurface,
                    ),
                ) {
                    Surface(Modifier.fillMaxSize()) {
                        DesktopSmokeContent(bootstrap.app)
                    }
                }
            }
        }
    } finally {
        bootstrap.close()
    }
}

@androidx.compose.runtime.Composable
private fun DesktopSmokeContent(app: HarmonicAppComposition) {
    val settings by app.settings.updates.collectAsState(initial = app.settings.snapshot())
    val navigation by app.navigation.state.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    var searchMode by remember { mutableStateOf(false) }
    val refreshSource = remember {
        StoryFeedRefreshPolicy.plan(
            searching = false,
            storyType = StoryType.TOP_STORIES,
            showSwipeRefreshIndicator = false,
            showMainLoadingIndicator = false,
            listIsEmpty = true,
        ).source
    }

    if (navigation.editorRequest != null) {
        SharedEditorScreen(
            type = EditorType.POST,
            parentText = null,
            postTitle = null,
            user = null,
            titleMaxLength = 80,
            submitting = false,
            onClose = app.navigation::closeEditor,
            onSubmit = {},
        )
        return
    }

    SharedStoriesRoot(
        searching = searchMode,
        suppressSearchAutoFocus = false,
        predictiveBackActive = false,
        predictiveBackProgress = 0f,
        backgroundColor = desktopColors.background,
        mainLayer = {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Shared stories smoke host", style = MaterialTheme.typography.headlineMedium)
                Text("Refresh policy selected: $refreshSource")
                Text("Real shared graph: CIO network, settings, sessions, and navigation are ready")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            app.settings.setStoryString(
                                StoryStringPreference.DISPLAY_STYLE,
                                if (settings.story.cardStyle) "standard" else "card",
                            )
                        },
                    ) {
                        Text(if (settings.story.cardStyle) "Use standard row" else "Use card row")
                    }
                    Button(
                        onClick = {
                            app.settings.setStoryBoolean(
                                StoryBooleanPreference.COMPACT_VIEW,
                                !settings.story.compactView,
                            )
                        },
                    ) {
                        Text(
                            if (settings.story.compactView) {
                                "Use comfortable spacing"
                            } else {
                                "Use compact spacing"
                            },
                        )
                    }
                    Button(onClick = { menuExpanded = true }) { Text("Shared menu") }
                    Button(
                        onClick = {
                            app.navigation.openEditor(EditorDestination(EditorType.POST))
                        },
                    ) { Text("Shared editor") }
                    Button(onClick = { searchMode = true }) { Text("Shared search root") }
                    HarmonicDropdownMenu(
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                    ) {
                        HarmonicMenuText(
                            text = "Desktop target compiled this menu",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
                StoryItem(
                    model = SettingsStoryPreviewModel,
                    style = StoryItemStyle(
                        previewImageMode = "large",
                        borderlessLargeImage = false,
                        compact = settings.story.compactView,
                        showSummary = true,
                        showFavicon = true,
                        showPoints = true,
                        compactPoints = false,
                        includeTopLevelDomain = true,
                        showCommentCount = true,
                        showIndex = true,
                        commentsOnLeft = false,
                        tintCard = true,
                        cardStyle = settings.story.cardStyle,
                        useHotnessIcon = false,
                        preferredFont = "googlesansflexrounded",
                        textSize = 17.5f,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        searchLayer = {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Shared search layer", style = MaterialTheme.typography.headlineMedium)
                Text("This verifies the portable stories root on the desktop target.")
                Button(onClick = { searchMode = false }) { Text("Back to stories") }
            }
        },
    )
}

private val desktopColors = HarmonicColors(
    background = Color(0xFFF8F9FC),
    accent = Color(0xFF3559C7),
    onSurface = Color(0xFF1A1B20),
    textPrimary = Color(0xFF1A1B20),
    textSecondary = Color(0xFF5D5E66),
    link = Color(0xFF3559C7),
    surfaceContainerHigh = Color(0xFFE8EAF2),
    surfaceContainerHighest = Color(0xFFDDE0EA),
    secondaryContainer = Color(0xFFDCE2FF),
    onSecondaryContainer = Color(0xFF17285F),
    storyNormal = Color(0xFF26272D),
    storyDisabled = Color(0xFF73747C),
    outlineVariant = Color(0xFFC5C6D0),
    commentDivider = Color(0xFFD5D6DF),
    drawable = Color(0xFF3F4048),
    popupMenuBackground = Color(0xFFFFFFFF),
    settingsSegment = Color(0xFFE8EAF2),
    settingsHeaderSelected = Color(0xFFDCE2FF),
    settingsMainToggle = Color(0xFF3559C7),
    settingsMainToggleText = Color.White,
    overlayButton = Color(0xFFEAECF4),
    submissionsCommentTimeBackground = Color(0xFFE8EAF2),
    submissionsCommentTimeOutline = Color(0xFFC5C6D0),
)
