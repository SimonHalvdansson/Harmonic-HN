@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.FontUtils
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.utils.Utils
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect

@Composable
fun FontSelectionDialog(
    readerMode: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val labels = remember(resources) {
        resources.getStringArray(R.array.font_entries).toList()
    }
    val values = remember(resources) {
        resources.getStringArray(R.array.font_values).toList()
    }
    val options = remember(labels, values) { labels.zip(values) }
    val selected = if (readerMode) {
        SettingsUtils.getPreferredReaderModeFont(context)
    } else {
        SettingsUtils.getPreferredFont(context)
    }

    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            SettingsDialogTitle(
                if (readerMode) "Reader mode font" else "Title and comment font",
            )
        },
        edgeToEdgeContent = true,
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 580.dp)
                    .selectableGroup(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 4.dp,
                    bottom = 8.dp,
                ),
            ) {
                items(options, key = { it.second }) { (label, value) ->
                    val fontFamily = fontFamilyForSetting(value)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 64.dp)
                            .selectable(
                                selected = value == selected,
                                role = Role.RadioButton,
                                onClick = {
                                    if (readerMode) {
                                        SettingsUtils.setPreferredReaderModeFont(context, value)
                                    } else {
                                        SettingsUtils.setPreferredFont(context, value)
                                        FontUtils.init(context)
                                    }
                                    onDismiss()
                                },
                            )
                            .padding(horizontal = 24.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsRadioButton(selected = value == selected)
                        Spacer(Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = label,
                                color = HarmonicTheme.colors.storyNormal,
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            )
                            Text(
                                text = "Example text",
                                color = HarmonicTheme.colors.storyDisabled,
                                fontFamily = fontFamily,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        showButtons = false,
    )
}

@Composable
fun PreloadWebViewDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        PreferenceManager.getDefaultSharedPreferences(context)
    }
    var mode by rememberSaveable {
        mutableStateOf(SettingsUtils.shouldPreloadWebView(context))
    }
    var battery by rememberSaveable {
        mutableFloatStateOf(
            (SettingsUtils.getPreloadWebViewMinimumBattery(context) / 5f)
                .toInt()
                .times(5)
                .toFloat()
                .coerceIn(0f, 100f),
        )
    }
    val enabled = mode != SettingsUtils.PRELOAD_WEBVIEW_NEVER

    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { SettingsDialogTitle("Preload websites") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "When you open comments for a link, Harmonic can load the " +
                        "website WebView in the background while you read. This makes " +
                        "switching to the website faster, but uses network, CPU, and battery.",
                    color = HarmonicTheme.colors.textPrimary,
                    fontFamily = ProductSansFontFamily,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Column(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .selectableGroup(),
                ) {
                    listOf(
                        SettingsUtils.PRELOAD_WEBVIEW_ALWAYS to "Always",
                        SettingsUtils.PRELOAD_WEBVIEW_ONLY_WIFI to "Only on WiFi",
                        SettingsUtils.PRELOAD_WEBVIEW_NEVER to "Never",
                    ).forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .selectable(
                                    selected = mode == option.first,
                                    role = Role.RadioButton,
                                    onClick = { mode = option.first },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SettingsRadioButton(selected = mode == option.first)
                            Text(
                                text = option.second,
                                modifier = Modifier.padding(start = 4.dp),
                                fontFamily = ProductSansFontFamily,
                                fontSize = 16.sp,
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (enabled) 1f else 0.5f)
                        .padding(top = 8.dp),
                ) {
                    Text(
                        text = "Minimum battery: " +
                            if (battery <= 0f) "Any" else "${battery.toInt()}%",
                        fontFamily = ProductSansFontFamily,
                        fontSize = 14.sp,
                    )
                    Slider(
                        value = battery,
                        onValueChange = { battery = (it / 5f).toInt() * 5f },
                        enabled = enabled,
                        valueRange = 0f..100f,
                        steps = 19,
                    )
                }
            }
        },
        confirmButton = {
            SettingsDialogTextButton(
                onClick = {
                    prefs.edit()
                        .putString(SettingsUtils.PREF_PRELOAD_WEBVIEW, mode)
                        .putInt(
                            SettingsUtils.PREF_PRELOAD_WEBVIEW_MINIMUM_BATTERY,
                            battery.toInt(),
                        )
                        .apply()
                    onDismiss()
                },
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            SettingsDialogTextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private val SuggestedArchiveDomains = listOf(
    "ft.com",
    "wsj.com",
    "bloomberg.com",
    "economist.com",
    "foreignpolicy.com",
    "nytimes.com",
    "washingtonpost.com",
    "theatlantic.com",
    "newyorker.com",
    "technologyreview.com",
)

@Composable
fun ArchiveRedirectDomainsDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        PreferenceManager.getDefaultSharedPreferences(context)
    }
    var domains by remember {
        mutableStateOf(SettingsUtils.getArchiveRedirectDomains(context).toList())
    }

    fun save(updated: List<String>) {
        domains = updated
        prefs.edit()
            .putString(SettingsUtils.PREF_ARCHIVE_REDIRECT_DOMAINS, updated.joinToString(","))
            .apply()
    }

    StringListEditorDialog(
        title = "Redirect to archive version",
        subtitle = "Choose domains where we should automatically redirect the webview " +
            "to the archive.is version. Useful for domains where paywalls are persistent.",
        inputLabel = "Domain",
        items = domains,
        emptyMessage = "No archive redirect domains",
        suggestions = SuggestedArchiveDomains.filter { suggestion ->
            domains.none { it.equals(suggestion, ignoreCase = true) }
        },
        suggestionsLabel = "Suggested domains",
        parseInput = { SettingsUtils.parseArchiveRedirectDomains(it).toList() },
        emptyInputError = "Enter a domain",
        onItemsChanged = ::save,
        onDismiss = onDismiss,
    )
}

@Composable
fun FilterListDialog(
    preferenceKey: String,
    title: String,
    subtitle: String,
    inputLabel: String,
    emptyMessage: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        PreferenceManager.getDefaultSharedPreferences(context)
    }
    var items by remember(preferenceKey) {
        mutableStateOf(parseCommaSeparatedItems(prefs.getString(preferenceKey, "").orEmpty()))
    }

    StringListEditorDialog(
        title = title,
        subtitle = subtitle,
        inputLabel = inputLabel,
        items = items,
        emptyMessage = emptyMessage,
        parseInput = ::parseCommaSeparatedItems,
        emptyInputError = "Enter a value",
        disableSuggestions = preferenceKey == "pref_filter_users",
        onItemsChanged = { updated ->
            items = updated
            prefs.edit().putString(preferenceKey, updated.joinToString(",")).apply()
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun StringListEditorDialog(
    title: String,
    subtitle: String,
    inputLabel: String,
    items: List<String>,
    emptyMessage: String,
    suggestions: List<String> = emptyList(),
    suggestionsLabel: String = "Suggestions",
    parseInput: (String) -> List<String>,
    emptyInputError: String,
    disableSuggestions: Boolean = false,
    onItemsChanged: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    fun addValues(rawValue: String) {
        error = null
        val parsed = parseInput(rawValue)
        if (parsed.isEmpty()) {
            error = emptyInputError
            return
        }
        val additions = parsed.filter { candidate ->
            items.none { it.equals(candidate, ignoreCase = true) }
        }
        if (additions.isEmpty()) {
            error = "Already added"
            return
        }
        onItemsChanged(items + additions)
        input = ""
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = true),
        title = { SettingsDialogTitle(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = subtitle,
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                    color = HarmonicTheme.colors.textPrimary,
                    fontFamily = ProductSansFontFamily,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = {
                            input = it
                            error = null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        label = { Text(inputLabel) },
                        isError = error != null,
                        supportingText = error?.let { message ->
                            {
                                Text(message)
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (disableSuggestions) {
                                KeyboardType.Ascii
                            } else {
                                KeyboardType.Text
                            },
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { addValues(input) },
                        ),
                    )
                    OutlinedIconButton(
                        onClick = { addValues(input) },
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .size(48.dp),
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = "Add",
                            tint = HarmonicTheme.colors.drawable,
                        )
                    }
                }

                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = emptyMessage,
                            color = HarmonicTheme.colors.storyDisabled,
                            fontFamily = ProductSansFontFamily,
                            fontSize = 15.sp,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 224.dp),
                    ) {
                        items(items, key = { it.lowercase() }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 44.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = item,
                                    modifier = Modifier.weight(1f),
                                    color = HarmonicTheme.colors.textPrimary,
                                    fontFamily = ProductSansFontFamily,
                                    fontSize = 16.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                IconButton(
                                    onClick = {
                                        onItemsChanged(items.filterNot { it == item })
                                    },
                                    modifier = Modifier.size(44.dp),
                                    shapes = IconButtonDefaults.shapes(),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_close),
                                        contentDescription = "Remove $item",
                                        tint = HarmonicTheme.colors.drawable,
                                    )
                                }
                            }
                        }
                    }
                }

                if (suggestions.isNotEmpty()) {
                    Text(
                        text = suggestionsLabel,
                        modifier = Modifier.padding(top = 18.dp),
                        color = HarmonicTheme.colors.storyNormal,
                        fontFamily = ProductSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement
                            .spacedBy(8.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement
                            .spacedBy(0.dp),
                    ) {
                        suggestions.forEach { suggestion ->
                            AssistChip(
                                onClick = { addValues(suggestion) },
                                label = { Text(suggestion) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        showButtons = false,
    )
}

@Composable
fun UserTagDialog(
    userName: String,
    currentTag: String,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val context = LocalContext.current
    var tag by remember(userName, currentTag) { mutableStateOf(currentTag) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        edgeToEdgeContent = true,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = androidx.compose.ui.res.dimensionResource(
                            R.dimen.compose_settings_dialog_content_padding,
                        ),
                    )
                    .padding(
                        bottom = androidx.compose.ui.res.dimensionResource(
                            R.dimen.compose_settings_dialog_content_padding,
                        ),
                    ),
            ) {
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(
                            androidx.compose.ui.res.dimensionResource(
                                R.dimen.compose_settings_dialog_single_line_field_height,
                            ),
                        )
                        .focusRequester(focusRequester),
                    label = { Text("Tag") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val saved = tag.trim()
                            Utils.setUserTag(context, userName, saved)
                            onSaved(saved)
                        },
                    ),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = androidx.compose.ui.res.dimensionResource(
                                R.dimen.compose_settings_tag_field_button_gap,
                            ),
                        ),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                ) {
                    SettingsDialogOutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .height(
                                androidx.compose.ui.res.dimensionResource(
                                    R.dimen.compose_settings_tag_button_height,
                                ),
                            )
                            .widthIn(
                                min = androidx.compose.ui.res.dimensionResource(
                                    R.dimen.compose_settings_tag_cancel_button_min_width,
                                ),
                            ),
                    ) {
                        Text("Cancel")
                    }
                    Spacer(
                        Modifier.width(
                            androidx.compose.ui.res.dimensionResource(
                                R.dimen.compose_settings_tag_button_gap,
                            ),
                        ),
                    )
                    SettingsDialogOutlinedButton(
                        onClick = {
                            val saved = tag.trim()
                            Utils.setUserTag(context, userName, saved)
                            onSaved(saved)
                        },
                        modifier = Modifier
                            .height(
                                androidx.compose.ui.res.dimensionResource(
                                    R.dimen.compose_settings_tag_button_height,
                                ),
                            )
                            .widthIn(
                                min = androidx.compose.ui.res.dimensionResource(
                                    R.dimen.compose_settings_tag_button_min_width,
                                ),
                            ),
                    ) {
                        Text("Set")
                    }
                }
            }
        },
        confirmButton = {},
        showButtons = false,
    )
}

private fun parseCommaSeparatedItems(value: String): List<String> {
    return value
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinctBy(String::lowercase)
}

private fun fontFamilyForSetting(value: String): FontFamily {
    return when (value) {
        "googlesansflexrounded" -> FontFamily(
            Font(R.font.google_sans_flex_rounded_regular, FontWeight.Normal),
            Font(R.font.google_sans_flex_rounded_bold, FontWeight.Bold),
        )
        "googlesans" -> FontFamily(
            Font(R.font.google_sans_regular, FontWeight.Normal),
            Font(R.font.google_sans_bold, FontWeight.Bold),
        )
        "productsans" -> FontFamily(
            Font(R.font.product_sans_regular, FontWeight.Normal),
            Font(R.font.product_sans_bold, FontWeight.Bold),
        )
        "verdana" -> FontFamily(
            Font(R.font.verdana_regular, FontWeight.Normal),
            Font(R.font.verdana_bold, FontWeight.Bold),
        )
        "jetbrainsmono" -> FontFamily(
            Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
            Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
        )
        "googlesanscode" -> FontFamily(
            Font(R.font.google_sans_code_regular, FontWeight.Normal),
            Font(R.font.google_sans_code_bold, FontWeight.Bold),
        )
        "georgia" -> FontFamily(
            Font(R.font.georgia_regular, FontWeight.Normal),
            Font(R.font.georgia_bold, FontWeight.Bold),
        )
        "robotoslab" -> FontFamily(
            Font(R.font.roboto_slab_regular, FontWeight.Normal),
            Font(R.font.roboto_slab_bold, FontWeight.Bold),
        )
        else -> FontFamily.SansSerif
    }
}
