@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import com.simon.harmonichackernews.ui.content.FontMetrics
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource

@Composable
fun FontSelectionDialog(
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
                modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp).selectableGroup(),
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
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy((-2).dp),
                        ) {
                            Text(
                                text = label,
                                color = HarmonicTheme.colors.storyNormal,
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            )
                            Text(
                                text = "205 points · science.org · 8h",
                                color = HarmonicTheme.colors.storyDisabled,
                                fontFamily = fontFamily,
                                fontSize = FontMetrics.forFont(value.storedValue).storyMeta.sp,
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
    initialMode: WebViewPreloadMode,
    initialBattery: Int,
    onSave: (mode: WebViewPreloadMode, minimumBattery: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    PreloadPolicyDialog(
        title = "Preload websites",
        description = "When you open comments for a link, Harmonic can load the website " +
            "WebView in the background while you read. This makes switching to the website " +
            "faster, but uses network data, CPU, memory, and battery.",
        initialMode = initialMode,
        initialBattery = initialBattery,
        onSave = onSave,
        onDismiss = onDismiss,
    )
}

@Composable
fun PreloadCommentsDialog(
    initialMode: WebViewPreloadMode,
    initialBattery: Int,
    onSave: (mode: WebViewPreloadMode, minimumBattery: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    PreloadPolicyDialog(
        title = "Preload comments",
        description = "Harmonic can download and prepare comments for discussions visible " +
            "on the stories screen. This makes opening them faster, but uses network data, " +
            "CPU, memory, and battery—even for stories you never open. The official HN API " +
            "requires a separate request for every comment, so it can use substantially more " +
            "network data than Algolia.",
        initialMode = initialMode,
        initialBattery = initialBattery,
        onSave = onSave,
        onDismiss = onDismiss,
    )
}

@Composable
private fun PreloadPolicyDialog(
    title: String,
    description: String,
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
        title = { SettingsDialogTitle(title) },
        edgeToEdgeContent = true,
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = description,
                    color = HarmonicTheme.colors.textPrimary,
                    fontFamily = ProductSansFontFamily,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 24.dp),
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
                                )
                                .padding(horizontal = 24.dp),
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
                        .padding(start = 24.dp, top = 8.dp, end = 24.dp),
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
fun StringListEditorDialog(
    title: String,
    subtitle: String,
    inputLabel: String,
    initialItems: List<String>,
    emptyMessage: String,
    suggestedItems: List<String> = emptyList(),
    suggestionsLabel: String = "Suggestions",
    parseInput: (String) -> List<String>,
    emptyInputError: String = "",
    disableSuggestions: Boolean = false,
    onItemsChanged: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var items by remember(initialItems) { mutableStateOf(initialItems) }
    var input by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val suggestions = suggestedItems.filter { suggestion ->
        items.none { it.equals(suggestion, ignoreCase = true) }
    }

    val additions = remember(input, parseInput, items) {
        parseInput(input).filter { candidate ->
            items.none { it.equals(candidate, ignoreCase = true) }
        }
    }
    val canAdd = additions.isNotEmpty()

    fun updateItems(updated: List<String>) {
        items = updated
        onItemsChanged(updated)
    }

    fun addValues(rawValue: String = input) {
        val parsed = parseInput(rawValue)
        val toAdd = parsed.filter { candidate ->
            items.none { it.equals(candidate, ignoreCase = true) }
        }
        if (toAdd.isNotEmpty()) {
            updateItems(items + toAdd)
            if (rawValue == input) {
                input = ""
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = true),
        modifier = Modifier.animateContentSize(
            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        ),
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
                    verticalAlignment = Alignment.Top,
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                        label = { Text(inputLabel) },
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
                        enabled = canAdd,
                        modifier = Modifier
                            .padding(start = 10.dp, top = 12.dp)
                            .size(48.dp),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_add),
                            contentDescription = "Add",
                            tint = if (canAdd) {
                                HarmonicTheme.colors.drawable
                            } else {
                                HarmonicTheme.colors.drawable.copy(alpha = 0.38f)
                            },
                        )
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 224.dp)) {
                    if (items.isEmpty()) {
                        item(key = "empty-list-message") {
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
                        }
                    } else {
                        items(items, key = { it.lowercase() }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 44.dp)
                                    .animateItem(
                                        fadeInSpec = tween(160),
                                        placementSpec = tween(
                                            durationMillis = 220,
                                            easing = FastOutSlowInEasing,
                                        ),
                                        fadeOutSpec = tween(140),
                                    ),
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
fun UserTagDialog(
    currentTag: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var tag by remember(currentTag) { mutableStateOf(currentTag) }
    val focusRequester = remember { FocusRequester() }
    fun saveTag() = onSave(tag.trim())

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val textContent: @Composable () -> Unit = {
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
    }
    val dismissContent: @Composable () -> Unit = {
        SettingsDialogTextButton(onClick = onDismiss) { Text("Cancel") }
    }
    val confirmContent: @Composable () -> Unit = {
        SettingsDialogTextButton(onClick = { saveTag() }) { Text("Set") }
    }

    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        text = textContent,
        dismissButton = dismissContent,
        confirmButton = confirmContent,
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
