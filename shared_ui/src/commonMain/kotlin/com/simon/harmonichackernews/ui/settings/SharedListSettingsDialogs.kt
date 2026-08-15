@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.resources.HarmonicDimens
import com.simon.harmonichackernews.settings.AppFont
import com.simon.harmonichackernews.settings.WebViewPreloadMode
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource

@Composable
fun SharedFontSelectionDialog(
    readerMode: Boolean,
    selected: AppFont,
    options: List<Pair<String, AppFont>>,
    onSelected: (AppFont) -> Unit,
    onDismiss: () -> Unit,
) {
    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            SettingsDialogTitle(if (readerMode) "Reader mode font" else "Title and comment font")
        },
        edgeToEdgeContent = true,
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 580.dp).selectableGroup(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
            ) {
                items(options, key = { it.second }) { (label, value) ->
                    val fontFamily = fontFamilyForSetting(value.storedValue)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 64.dp)
                            .selectable(
                                selected = value == selected,
                                role = Role.RadioButton,
                                onClick = {
                                    onSelected(value)
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
fun SharedPreloadWebViewDialog(
    initialMode: WebViewPreloadMode,
    initialBattery: Int,
    onSave: (mode: WebViewPreloadMode, minimumBattery: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var modeValue by rememberSaveable { mutableStateOf(initialMode.storedValue) }
    val mode = WebViewPreloadMode.fromStored(modeValue)
    var battery by rememberSaveable {
        mutableFloatStateOf(((initialBattery / 5) * 5).toFloat().coerceIn(0f, 100f))
    }
    val enabled = mode != WebViewPreloadMode.NEVER
    val options = WebViewPreloadMode.entries

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
                Column(modifier = Modifier.padding(top = 12.dp).selectableGroup()) {
                    options.forEach { value ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .selectable(
                                    selected = mode == value,
                                    role = Role.RadioButton,
                                    onClick = { modeValue = value.storedValue },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SettingsRadioButton(selected = mode == value)
                            Text(
                                text = value.label,
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
                    onSave(mode, battery.toInt())
                    onDismiss()
                },
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            SettingsDialogTextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun SharedStringListEditorDialog(
    title: String,
    subtitle: String,
    inputLabel: String,
    initialItems: List<String>,
    emptyMessage: String,
    suggestedItems: List<String> = emptyList(),
    suggestionsLabel: String = "Suggestions",
    parseInput: (String) -> List<String>,
    emptyInputError: String,
    disableSuggestions: Boolean = false,
    onItemsChanged: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var items by remember(initialItems) { mutableStateOf(initialItems) }
    var input by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val suggestions = suggestedItems.filter { suggestion ->
        items.none { it.equals(suggestion, ignoreCase = true) }
    }

    fun updateItems(updated: List<String>) {
        items = updated
        onItemsChanged(updated)
    }

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
        updateItems(items + additions)
        input = ""
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

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
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = {
                            input = it
                            error = null
                        },
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                        label = { Text(inputLabel) },
                        isError = error != null,
                        supportingText = error?.let { message -> { Text(message) } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (disableSuggestions) {
                                KeyboardType.Ascii
                            } else {
                                KeyboardType.Text
                            },
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { addValues(input) }),
                    )
                    OutlinedIconButton(
                        onClick = { addValues(input) },
                        modifier = Modifier.padding(start = 10.dp).size(48.dp),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_add),
                            contentDescription = "Add",
                            tint = HarmonicTheme.colors.drawable,
                        )
                    }
                }

                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(72.dp),
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
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 224.dp)) {
                        items(items, key = { it.lowercase() }) { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp),
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
                                    onClick = { updateItems(items.filterNot { it == item }) },
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_close),
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
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
fun SharedUserTagDialog(
    currentTag: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var tag by remember(currentTag) { mutableStateOf(currentTag) }
    val focusRequester = remember { FocusRequester() }
    fun saveTag() = onSave(tag.trim())

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        edgeToEdgeContent = true,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = HarmonicDimens.compose_settings_dialog_content_padding)
                    .padding(horizontal = HarmonicDimens.compose_settings_dialog_content_padding)
                    .padding(bottom = HarmonicDimens.compose_settings_dialog_content_padding),
            ) {
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HarmonicDimens.compose_settings_dialog_single_line_field_height)
                        .focusRequester(focusRequester),
                    label = { Text("Tag") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { saveTag() }),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = HarmonicDimens.compose_settings_tag_field_button_gap),
                    horizontalArrangement = Arrangement.End,
                ) {
                    SettingsDialogOutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .height(HarmonicDimens.compose_settings_tag_button_height)
                            .widthIn(
                                min = HarmonicDimens.compose_settings_tag_cancel_button_min_width,
                            ),
                    ) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(HarmonicDimens.compose_settings_tag_button_gap))
                    SettingsDialogOutlinedButton(
                        onClick = { saveTag() },
                        modifier = Modifier
                            .height(HarmonicDimens.compose_settings_tag_button_height)
                            .widthIn(min = HarmonicDimens.compose_settings_tag_button_min_width),
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

@Composable
private fun fontFamilyForSetting(value: String): FontFamily = when (value) {
    "googlesansflexrounded" -> FontFamily(
        Font(Res.font.google_sans_flex_rounded_regular, FontWeight.Normal),
        Font(Res.font.google_sans_flex_rounded_bold, FontWeight.Bold),
    )
    "googlesans" -> FontFamily(
        Font(Res.font.google_sans_regular, FontWeight.Normal),
        Font(Res.font.google_sans_bold, FontWeight.Bold),
    )
    "productsans" -> FontFamily(
        Font(Res.font.product_sans_regular, FontWeight.Normal),
        Font(Res.font.product_sans_bold, FontWeight.Bold),
    )
    "verdana" -> FontFamily(
        Font(Res.font.verdana_regular, FontWeight.Normal),
        Font(Res.font.verdana_bold, FontWeight.Bold),
    )
    "jetbrainsmono" -> FontFamily(
        Font(Res.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(Res.font.jetbrains_mono_bold, FontWeight.Bold),
    )
    "googlesanscode" -> FontFamily(
        Font(Res.font.google_sans_code_regular, FontWeight.Normal),
        Font(Res.font.google_sans_code_regular, FontWeight.Bold),
    )
    "georgia" -> FontFamily(
        Font(Res.font.georgia_regular, FontWeight.Normal),
        Font(Res.font.georgia_bold, FontWeight.Bold),
    )
    "robotoslab" -> FontFamily(
        Font(Res.font.roboto_slab_regular, FontWeight.Normal),
        Font(Res.font.roboto_slab_bold, FontWeight.Bold),
    )
    else -> FontFamily.SansSerif
}
