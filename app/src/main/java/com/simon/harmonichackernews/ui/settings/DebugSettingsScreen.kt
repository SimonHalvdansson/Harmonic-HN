package com.simon.harmonichackernews.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.simon.harmonichackernews.BuildConfig
import com.simon.harmonichackernews.CoulombGasActivity
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.ui.editor.ComposeEditorPreference
import com.simon.harmonichackernews.ui.submissions.SubmissionsUiPreference
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.Utils

private const val OpenWithoutCacheStoryId = 49089500

private data class DebugLink(
    val title: String,
    val icon: Int,
    val url: String,
)

private val SampleContentLinks = listOf(
    DebugLink(
        "Link post",
        R.drawable.ic_ballot,
        "https://news.ycombinator.com/item?id=47938725",
    ),
    DebugLink(
        "Reference links post",
        R.drawable.ic_link,
        "https://news.ycombinator.com/item?id=48352939",
    ),
    DebugLink(
        "YouTube comment",
        R.drawable.ic_link,
        "https://news.ycombinator.com/item?id=34225887",
    ),
    DebugLink(
        "Very long comment",
        R.drawable.ic_comment,
        "https://news.ycombinator.com/item?id=49103136",
    ),
    DebugLink(
        "Poll",
        R.drawable.ic_comment,
        "https://news.ycombinator.com/item?id=39572682",
    ),
    DebugLink(
        "Internal HN link",
        R.drawable.ic_link,
        "https://news.ycombinator.com/item?id=30676384",
    ),
)

private val PreviewLinks = listOf(
    DebugLink(
        "arXiv",
        R.drawable.ic_link_preview_arxiv,
        "https://news.ycombinator.com/item?id=42788451",
    ),
    DebugLink(
        "GitHub",
        R.drawable.ic_link_preview_github,
        "https://news.ycombinator.com/item?id=49070029",
    ),
    DebugLink(
        "GitLab",
        R.drawable.ic_link_preview_gitlab,
        "https://news.ycombinator.com/item?id=18798209",
    ),
    DebugLink(
        "Stack Exchange",
        R.drawable.ic_link_preview_stack_exchange,
        "https://news.ycombinator.com/item?id=21113344",
    ),
    DebugLink(
        "Wikipedia",
        R.drawable.ic_link_preview_wikipedia,
        "https://news.ycombinator.com/item?id=21699011",
    ),
    DebugLink(
        "Twitter/X",
        R.drawable.ic_link_preview_x,
        "https://news.ycombinator.com/item?id=48012735",
    ),
)

@Composable
fun DebugSettingsScreen(
    showNavigation: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val refresh = rememberPreferenceRefresh()
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var versionTapCount by remember { mutableIntStateOf(0) }
    var lastVersionTapTime by remember { mutableLongStateOf(0L) }

    @Suppress("UNUSED_VARIABLE")
    val observedRefresh = refresh

    SettingsPage(
        title = "Debug",
        showNavigation = showNavigation,
        onBack = onBack,
        contentVersion = refresh,
    ) {
        item {
            SettingsCategory("Debug tools") {
                ImplementationSetting(
                    title = "Settings",
                    selected = SettingsUiPreference.selected(context),
                    onSelected = { implementation ->
                        prefs.edit()
                            .putString(SettingsUiPreference.KEY, implementation)
                            .apply()
                    },
                )
                SettingsDivider()
                ImplementationSetting(
                    title = "Post and comment editor",
                    selected = ComposeEditorPreference.selected(context),
                    onSelected = { implementation ->
                        prefs.edit()
                            .putString(ComposeEditorPreference.KEY, implementation)
                            .apply()
                    },
                )
                SettingsDivider()
                ImplementationSetting(
                    title = "Submissions",
                    selected = SubmissionsUiPreference.selected(context),
                    onSelected = { implementation ->
                        prefs.edit()
                            .putString(SubmissionsUiPreference.KEY, implementation)
                            .apply()
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Always show tap to refresh",
                    icon = R.drawable.ic_refresh,
                    checked = prefs.getBoolean("pref_always_show_tap_to_refresh", false),
                    onCheckedChange = {
                        prefs.edit()
                            .putBoolean("pref_always_show_tap_to_refresh", it)
                            .apply()
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Additional AI summary debug info",
                    icon = R.drawable.ic_info,
                    checked = prefs.getBoolean("pref_debug_show_llm_summary_info", false),
                    onCheckedChange = {
                        prefs.edit()
                            .putBoolean("pref_debug_show_llm_summary_info", it)
                            .apply()
                    },
                )
                SettingsDivider()
                DebugHnIdSetting(
                    onOpenId = {
                        Utils.openCommentsActivity(it, -1, context)
                    },
                )
                SettingsDivider()
                SettingRow(
                    title = "Open without cache",
                    icon = R.drawable.ic_cached,
                    onClick = {
                        Utils.removeStoryFromCaches(context, OpenWithoutCacheStoryId)
                        NetworkComponent.removeCachedStoryResponses(
                            context,
                            OpenWithoutCacheStoryId,
                        )
                        Utils.openCommentsActivity(
                            OpenWithoutCacheStoryId,
                            -1,
                            context,
                        )
                    },
                )
            }
        }

        item {
            SettingsCategory("Sample content") {
                DebugLinkRows(SampleContentLinks)
            }
        }

        item {
            SettingsCategory("Link previews") {
                DebugLinkRows(PreviewLinks)
            }
        }

        item {
            SettingsCategory("Dialogs") {
                SettingRow(
                    title = "Welcome dialog",
                    icon = R.drawable.ic_explore,
                    onClick = { dialog = "welcome" },
                )
                SettingsDivider()
                SettingRow(
                    title = "Changelog",
                    icon = R.drawable.ic_history,
                    onClick = { dialog = "changelog" },
                )
                SettingsDivider()
                SettingRow(
                    title = "Debug notifications",
                    icon = R.drawable.ic_notifications,
                    onClick = { dialog = "notifications" },
                )
            }
        }

        item {
            SettingsCategory("Environment") {
                SettingRow(
                    title = "App version",
                    summary = BuildConfig.VERSION_NAME,
                    icon = R.drawable.ic_deployed_code,
                    onClick = {
                        val now = SystemClock.elapsedRealtime()
                        versionTapCount = if (now - lastVersionTapTime < 800L) {
                            versionTapCount + 1
                        } else {
                            1
                        }
                        lastVersionTapTime = now
                        if (versionTapCount == 5) {
                            versionTapCount = 0
                            lastVersionTapTime = 0L
                            context.startActivity(
                                Intent(context, CoulombGasActivity::class.java),
                            )
                        }
                    },
                )
                SettingsDivider()
                SettingRow(
                    title = "App build",
                    summary = BuildConfig.VERSION_CODE.toString(),
                    icon = R.drawable.ic_tag,
                    onClick = null,
                )
                SettingsDivider()
                SettingRow(
                    title = "Build version",
                    summary = BuildConfig.BUILD_TYPE,
                    icon = R.drawable.ic_build,
                    onClick = null,
                )
                SettingsDivider()
                SettingRow(
                    title = "Android version",
                    summary = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    icon = R.drawable.ic_android,
                    onClick = null,
                )
            }
        }
    }

    when (dialog) {
        "changelog" -> ChangelogDialog(
            onDismiss = { dialog = null },
        )
        "welcome" -> WelcomeSettingsDialog(
            styleChooser = false,
            onDismiss = { dialog = null },
        )
        "notifications" -> DebugNotificationsDialog(
            onDismiss = { dialog = null },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ImplementationSetting(
    title: String,
    selected: String,
    onSelected: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HarmonicTheme.colors.settingsSegment)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            color = HarmonicTheme.colors.textPrimary,
            fontFamily = ProductSansFontFamily,
        )
        ButtonGroup(
            overflowIndicator = { menuState ->
                ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            toggleableItem(
                checked = selected == ComposeEditorPreference.VIEWS,
                label = "Views",
                onCheckedChange = { checked ->
                    if (checked) onSelected(ComposeEditorPreference.VIEWS)
                },
                weight = 1f,
            )
            toggleableItem(
                checked = selected == ComposeEditorPreference.COMPOSE,
                label = "Compose",
                onCheckedChange = { checked ->
                    if (checked) onSelected(ComposeEditorPreference.COMPOSE)
                },
                weight = 1f,
            )
        }
    }
}

@Composable
private fun DebugHnIdSetting(
    onOpenId: (Int) -> Unit,
) {
    val currentOnOpenId by rememberUpdatedState(onOpenId)
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .background(HarmonicTheme.colors.settingsSegment),
        factory = { context ->
            val root = LayoutInflater.from(context)
                .inflate(R.layout.preference_debug_hn_id, null, false)
            root.findViewById<TextView>(android.R.id.title).text = "Open HN item by ID"
            root.findViewById<ImageView>(android.R.id.icon)
                .setImageResource(R.drawable.ic_open_in_new)

            val inputLayout =
                root.findViewById<TextInputLayout>(R.id.debug_hn_id_input_layout)
            val input = root.findViewById<TextInputEditText>(R.id.debug_hn_id_input)
            val openButton = root.findViewById<MaterialButton>(R.id.debug_open_hn_id)

            fun openId() {
                val value = input.text?.toString()?.trim().orEmpty()
                val id = value.toIntOrNull()
                inputLayout.error = when {
                    value.isEmpty() || value.any { !it.isDigit() } ->
                        "Enter a numeric HN ID"

                    id == null -> "HN ID is too large"
                    id <= 0 -> "Enter a positive HN ID"
                    else -> null
                }
                if (inputLayout.error == null && id != null) {
                    currentOnOpenId(id)
                }
            }

            input.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    openId()
                    true
                } else {
                    false
                }
            }
            openButton.setOnClickListener { openId() }
            root
        },
    )
}

@Composable
private fun ChangelogDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    SettingsChangelogDialog(
        onDismiss = onDismiss,
        onOpenGithub = {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/SimonHalvdansson/Harmonic-HN"),
                ),
            )
            onDismiss()
        },
    )
}

@Composable
private fun DebugLinkRows(links: List<DebugLink>) {
    val context = LocalContext.current
    links.forEachIndexed { index, link ->
        SettingRow(
            title = link.title,
            icon = link.icon,
            onClick = { Utils.openLinkMaybeHN(context, link.url) },
        )
        if (index != links.lastIndex) {
            SettingsDivider()
        }
    }
}
