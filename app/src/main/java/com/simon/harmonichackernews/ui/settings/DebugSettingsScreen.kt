@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.simon.harmonichackernews.ui.settings

import org.jetbrains.compose.resources.DrawableResource


import com.simon.harmonichackernews.resources.*

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.BuildConfig
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.ui.debug.CoulombGasContract
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.Utils

private const val OpenWithoutCacheStoryId = 49089500

private data class DebugLink(
    val title: String,
    val icon: DrawableResource,
    val url: String,
)

private val SampleContentLinks = listOf(
    DebugLink(
        "Link post",
        Res.drawable.ic_ballot,
        "https://news.ycombinator.com/item?id=47938725",
    ),
    DebugLink(
        "Reference links post",
        Res.drawable.ic_link,
        "https://news.ycombinator.com/item?id=48352939",
    ),
    DebugLink(
        "YouTube comment",
        Res.drawable.ic_link,
        "https://news.ycombinator.com/item?id=34225887",
    ),
    DebugLink(
        "Very long comment",
        Res.drawable.ic_comment,
        "https://news.ycombinator.com/item?id=49103136",
    ),
    DebugLink(
        "Poll",
        Res.drawable.ic_comment,
        "https://news.ycombinator.com/item?id=39572682",
    ),
    DebugLink(
        "Internal HN link",
        Res.drawable.ic_link,
        "https://news.ycombinator.com/item?id=30676384",
    ),
)

private val PreviewLinks = listOf(
    DebugLink(
        "arXiv",
        Res.drawable.ic_link_preview_arxiv,
        "https://news.ycombinator.com/item?id=42788451",
    ),
    DebugLink(
        "GitHub",
        Res.drawable.ic_link_preview_github,
        "https://news.ycombinator.com/item?id=49070029",
    ),
    DebugLink(
        "GitLab",
        Res.drawable.ic_link_preview_gitlab,
        "https://news.ycombinator.com/item?id=18798209",
    ),
    DebugLink(
        "Stack Exchange",
        Res.drawable.ic_link_preview_stack_exchange,
        "https://news.ycombinator.com/item?id=21113344",
    ),
    DebugLink(
        "Wikipedia",
        Res.drawable.ic_link_preview_wikipedia,
        "https://news.ycombinator.com/item?id=21699011",
    ),
    DebugLink(
        "Twitter/X",
        Res.drawable.ic_link_preview_x,
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

    SettingsPage(
        title = "Debug",
        showNavigation = showNavigation,
        onBack = onBack,
        contentVersion = refresh,
    ) {
        item {
            SettingsCategory("Debug tools") {
                SwitchSettingRow(
                    title = "Always show tap to refresh",
                    icon = Res.drawable.ic_refresh,
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
                    icon = Res.drawable.ic_info,
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
                    icon = Res.drawable.ic_cached,
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
                    icon = Res.drawable.ic_explore,
                    onClick = { dialog = "welcome" },
                )
                SettingsDivider()
                SettingRow(
                    title = "Changelog",
                    icon = Res.drawable.ic_history,
                    onClick = { dialog = "changelog" },
                )
                SettingsDivider()
                SettingRow(
                    title = "Debug notifications",
                    icon = Res.drawable.ic_notifications,
                    onClick = { dialog = "notifications" },
                )
            }
        }

        item {
            SettingsCategory("Environment") {
                SettingRow(
                    title = "App version",
                    summary = BuildConfig.VERSION_NAME,
                    icon = Res.drawable.ic_deployed_code,
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
                                CoulombGasContract.createIntent(context),
                            )
                        }
                    },
                )
                SettingsDivider()
                SettingRow(
                    title = "App build",
                    summary = BuildConfig.VERSION_CODE.toString(),
                    icon = Res.drawable.ic_tag,
                    onClick = null,
                )
                SettingsDivider()
                SettingRow(
                    title = "Build version",
                    summary = BuildConfig.BUILD_TYPE,
                    icon = Res.drawable.ic_build,
                    onClick = null,
                )
                SettingsDivider()
                SettingRow(
                    title = "Android version",
                    summary = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    icon = Res.drawable.ic_android,
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

@Composable
private fun DebugHnIdSetting(
    onOpenId: (Int) -> Unit,
) {
    val currentOnOpenId by rememberUpdatedState(onOpenId)
    val keyboardController = LocalSoftwareKeyboardController.current
    var value by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun openId() {
        val trimmed = value.trim()
        val id = trimmed.toIntOrNull()
        when {
            trimmed.isEmpty() || trimmed.any { !it.isDigit() } -> {
                error = "Enter a numeric HN ID"
            }
            id == null -> {
                error = "HN ID is too large"
            }
            id <= 0 -> {
                error = "Enter a positive HN ID"
            }
            else -> {
                error = null
                keyboardController?.hide()
                currentOnOpenId(id)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HarmonicTheme.colors.settingsSegment)
            .padding(start = 24.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
    ) {
        Row {
            Icon(
                painterResource(Res.drawable.ic_open_in_new),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = HarmonicTheme.colors.drawable,
            )
            Spacer(Modifier.width(32.dp))
            Text(
                "Open HN item by ID",
                color = HarmonicTheme.colors.textPrimary,
                fontFamily = ProductSansFontFamily,
                fontSize = 16.sp,
                lineHeight = 20.sp,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 56.dp, top = 12.dp),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it.filter(Char::isDigit)
                    error = null
                },
                modifier = Modifier.weight(1f),
                label = { Text("HN ID", fontFamily = ProductSansFontFamily) },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { message ->
                    { Text(message, fontFamily = ProductSansFontFamily) }
                },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = ProductSansFontFamily,
                    fontSize = 16.sp,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { openId() }),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedIconButton(
                onClick = { openId() },
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    painterResource(Res.drawable.ic_chevron_right),
                    contentDescription = "Open HN ID",
                    tint = HarmonicTheme.colors.drawable,
                )
            }
        }
    }
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
