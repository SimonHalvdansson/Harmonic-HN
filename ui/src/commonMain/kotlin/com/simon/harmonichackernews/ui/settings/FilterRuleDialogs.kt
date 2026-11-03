@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.settings.DomainFilterHostPolicy
import com.simon.harmonichackernews.settings.DomainFilterRule
import com.simon.harmonichackernews.settings.TitleFilterMatchMode
import com.simon.harmonichackernews.settings.TitleFilterRule
import com.simon.harmonichackernews.ui.common.Button
import com.simon.harmonichackernews.ui.common.PlatformDisableDialogWindowAnimations
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import org.jetbrains.compose.resources.painterResource

@Composable
fun TitleFilterRulesDialog(
    initialRules: List<TitleFilterRule>,
    onRulesChanged: (List<TitleFilterRule>) -> Unit,
    onDismiss: () -> Unit,
    editable: Boolean = true,
) {
    var rules by remember(initialRules) { mutableStateOf(initialRules.distinctBy { it.mode to it.text.lowercase() }) }
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    fun update(updated: List<TitleFilterRule>) {
        rules = updated.distinctBy { it.mode to it.text.lowercase() }
        onRulesChanged(rules)
    }
    FilterRulesDialog(
        title = "Filter by story title",
        description = "Hide stories whose titles match any of these rules. Tap a rule to edit its matching mode.",
        rows = rules.map { FilterRuleRow("${it.mode}:${it.text.lowercase()}", it.text, it.mode.label) },
        emptyMessage = "No story title rules",
        editable = editable,
        onAdd = { adding = true },
        onEdit = { editing = it },
        onRemove = { index -> update(rules.filterIndexed { i, _ -> i != index }) },
        onDismiss = onDismiss,
        nestedDialog = {
            if (adding || editing != null) TitleFilterRuleEditor(
                initialRule = editing?.let(rules::getOrNull),
                onSave = { rule ->
                    val index = editing
                    update(if (index == null) rules + rule else rules.toMutableList().apply { set(index, rule) })
                    adding = false
                    editing = null
                },
                onDismiss = { adding = false; editing = null },
            )
        },
    )
}

@Composable
fun DomainFilterRulesDialog(
    initialRules: List<DomainFilterRule>,
    onRulesChanged: (List<DomainFilterRule>) -> Unit,
    onDismiss: () -> Unit,
    editable: Boolean = true,
) {
    var rules by remember(initialRules) { mutableStateOf(initialRules.distinct()) }
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    fun update(updated: List<DomainFilterRule>) {
        rules = updated.distinct()
        onRulesChanged(rules)
    }
    FilterRulesDialog(
        title = "Filter by domain",
        description = "Hide stories from matching hosts. Each rule can include subdomains.",
        rows = rules.map { rule ->
            FilterRuleRow(
                key = "${rule.legacyContainsText}:${rule.includeSubdomains}:${rule.domain}",
                title = rule.domain,
                mode = when {
                    rule.legacyContainsText -> "Contains text · previous behavior"
                    DomainFilterHostPolicy.isIpv4(rule.domain) -> "Exact IPv4 address"
                    rule.includeSubdomains -> "Hostname and subdomains"
                    else -> "Exact hostname"
                },
            )
        },
        emptyMessage = "No domain rules",
        editable = editable,
        onAdd = { adding = true },
        onEdit = { editing = it },
        onRemove = { index -> update(rules.filterIndexed { i, _ -> i != index }) },
        onDismiss = onDismiss,
        nestedDialog = {
            if (adding || editing != null) DomainFilterRuleEditor(
                initialRule = editing?.let(rules::getOrNull),
                onSave = { rule ->
                    val index = editing
                    update(if (index == null) rules + rule else rules.toMutableList().apply { set(index, rule) })
                    adding = false
                    editing = null
                },
                onDismiss = { adding = false; editing = null },
            )
        },
    )
}

internal data class FilterRuleRow(val key: String, val title: String, val mode: String)

private val TitleFilterMatchMode.label: String
    get() = when (this) {
        TitleFilterMatchMode.WORDS_PHRASE -> "Words / phrase"
        TitleFilterMatchMode.CONTAINS_TEXT -> "Contains text"
    }

@Composable
private fun FilterRulesDialog(
    title: String,
    description: String,
    rows: List<FilterRuleRow>,
    emptyMessage: String,
    editable: Boolean,
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onDismiss: () -> Unit,
    nestedDialog: @Composable () -> Unit,
) {
    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("filter-rules-dialog"),
        title = { SettingsDialogTitle(title) },
        scrollableContent = true,
        compactSpacing = true,
        text = {
            FilterRuleListContent(description, rows, emptyMessage, editable, onAdd, onEdit, onRemove)
        },
        confirmButton = { SettingsDialogTextButton(onClick = onDismiss) { Text("Done") } },
        foreground = { nestedDialog() },
    )
}

@Composable
internal fun FilterRuleListContent(
    description: String,
    rows: List<FilterRuleRow>,
    emptyMessage: String,
    editable: Boolean,
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("filter-rule-list-content")) {
        if (!editable) Text(
            "These saved rules use an unsupported or damaged format. They are inactive, and their data has been kept unchanged. A newer version of Harmonic may be needed to read them.",
            fontFamily = ProductSansFontFamily, fontSize = 14.sp, lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
        Text(description, fontFamily = ProductSansFontFamily, fontSize = 14.sp, lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        Button(onClick = onAdd, enabled = editable, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(painterResource(Res.drawable.ic_add), null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add rule", fontFamily = ProductSansFontFamily)
        }
        LazyColumn(
            // The shared card animates this natural height, including wrapped mode labels.
            Modifier.fillMaxWidth().testTag("filter-rules-list").padding(top = 8.dp).heightIn(max = 380.dp),
        ) {
            if (rows.isEmpty()) item {
                Box(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text(if (editable) emptyMessage else "Saved rules unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = ProductSansFontFamily, fontSize = 15.sp)
                }
            }
            itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        // A disappearing item's retained layer would keep the wrap-content
                        // viewport tall; the list's size animation owns the removal instead.
                        .animateItem(tween(160), tween(280, easing = FastOutSlowInEasing), fadeOutSpec = null)
                        .clickable(enabled = editable) { onEdit(index) }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(row.title, fontFamily = ProductSansFontFamily, fontSize = 16.sp, lineHeight = 20.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(row.mode, fontFamily = ProductSansFontFamily, fontSize = 13.sp, lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                    }
                    IconButton(onClick = { onEdit(index) }, enabled = editable, modifier = Modifier.size(48.dp)) {
                        Icon(painterResource(Res.drawable.ic_edit), "Edit ${row.title}")
                    }
                    IconButton(onClick = { onRemove(index) }, enabled = editable, modifier = Modifier.size(48.dp)) {
                        Icon(painterResource(Res.drawable.ic_close), "Remove ${row.title}")
                    }
                }
            }
        }
    }
}

@Composable
fun TitleFilterRuleEditor(
    initialRule: TitleFilterRule? = null,
    onSave: (TitleFilterRule) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable(initialRule) { mutableStateOf(initialRule?.text.orEmpty()) }
    var modeValue by rememberSaveable(initialRule) {
        mutableStateOf((initialRule?.mode ?: TitleFilterMatchMode.WORDS_PHRASE).storedValue)
    }
    val mode = TitleFilterMatchMode.entries.firstOrNull { it.storedValue == modeValue }
        ?: TitleFilterMatchMode.WORDS_PHRASE
    val focus = remember { FocusRequester() }
    val dismissal = remember { SettingsDialogDismissState() }
    val rule = remember(text, mode) { TitleFilterRule(text.trim(), mode) }
    fun dismiss() = dismissal.request(onDismiss)
    fun save() {
        if (rule.text.isNotBlank() && !dismissal.isClosing) dismissal.request { onSave(rule) }
    }
    SettingsAlertDialog(
        onDismissRequest = ::dismiss,
        modifier = Modifier.testTag("title-filter-rule-editor"),
        title = { SettingsDialogTitle("Title filter") },
        scrollableContent = true,
        compactSpacing = true,
        text = {
            PlatformDisableDialogWindowAnimations()
            CompleteSettingsDialogDismissal(dismissal)
            RequestSettingsDialogFocus(focus, enabled = !dismissal.isClosing)
            TitleFilterRuleEditorContent(
                text = text, mode = mode, onTextChanged = { text = it },
                onModeChanged = { modeValue = it.storedValue }, enabled = !dismissal.isClosing,
                focusRequester = focus, onSave = ::save,
            )
        },
        dismissButton = { SettingsDialogTextButton(onClick = ::dismiss, enabled = !dismissal.isClosing) { Text("Cancel") } },
        confirmButton = {
            SettingsDialogTextButton(onClick = ::save, enabled = rule.text.isNotBlank() && !dismissal.isClosing) { Text("Save rule") }
        },
    )
}

@Composable
fun DomainFilterRuleEditor(
    initialRule: DomainFilterRule? = null,
    onSave: (DomainFilterRule) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable(initialRule) { mutableStateOf(initialRule?.domain.orEmpty()) }
    var includeSubdomains by rememberSaveable(initialRule) { mutableStateOf(initialRule?.includeSubdomains ?: true) }
    val host = remember(text) { DomainFilterHostPolicy.normalize(text) }
    val isIpAddress = host?.let(DomainFilterHostPolicy::isIpv4) == true
    val rule = remember(host, includeSubdomains) { host?.let { DomainFilterRule(it, includeSubdomains && !isIpAddress) } }
    val focus = remember { FocusRequester() }
    val dismissal = remember { SettingsDialogDismissState() }
    fun dismiss() = dismissal.request(onDismiss)
    fun save() {
        if (rule != null && !dismissal.isClosing) dismissal.request { onSave(rule) }
    }
    SettingsAlertDialog(
        onDismissRequest = ::dismiss,
        modifier = Modifier.testTag("domain-filter-rule-editor"),
        title = { SettingsDialogTitle("Domain filter") },
        scrollableContent = true,
        compactSpacing = true,
        text = {
            PlatformDisableDialogWindowAnimations()
            CompleteSettingsDialogDismissal(dismissal)
            RequestSettingsDialogFocus(focus, enabled = !dismissal.isClosing)
            DomainFilterRuleEditorContent(
                text = text, host = host, rule = rule, includeSubdomains = includeSubdomains,
                legacyRule = initialRule?.legacyContainsText == true,
                onTextChanged = { text = it }, onIncludeSubdomainsChanged = { includeSubdomains = it },
                enabled = !dismissal.isClosing, focusRequester = focus, onSave = ::save,
            )
        },
        dismissButton = { SettingsDialogTextButton(onClick = ::dismiss, enabled = !dismissal.isClosing) { Text("Cancel") } },
        confirmButton = {
            SettingsDialogTextButton(onClick = ::save, enabled = rule != null && !dismissal.isClosing) { Text("Save rule") }
        },
    )
}

@Composable
internal fun TitleFilterRuleEditorContent(
    text: String,
    mode: TitleFilterMatchMode,
    onTextChanged: (String) -> Unit,
    onModeChanged: (TitleFilterMatchMode) -> Unit,
    enabled: Boolean,
    focusRequester: FocusRequester,
    onSave: () -> Unit,
) {
    val rule = remember(text, mode) { TitleFilterRule(text.trim(), mode) }
    val examples = remember(rule) { titleFilterPreviewExamples(rule) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("title-filter-rule-content"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = text, onValueChange = { if (enabled) onTextChanged(it) },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("title-filter-rule-input"),
            label = { Text("Word or phrase") }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave() }),
        )
        Column {
            Text("Match", fontFamily = ProductSansFontFamily, fontWeight = FontWeight.Medium)
            SegmentedSetting(
                options = TitleFilterMatchMode.entries.map { it to it.label },
                selected = mode,
                enabled = enabled,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentPadding = PaddingValues(top = 4.dp),
                onSelected = onModeChanged,
            )
        }
        RulePreview(examples, if (text.isBlank()) "Type a rule to see how it matches." else null)
    }
}

@Composable
internal fun DomainFilterRuleEditorContent(
    text: String,
    host: String?,
    rule: DomainFilterRule?,
    includeSubdomains: Boolean,
    legacyRule: Boolean,
    onTextChanged: (String) -> Unit,
    onIncludeSubdomainsChanged: (Boolean) -> Unit,
    enabled: Boolean,
    focusRequester: FocusRequester,
    onSave: () -> Unit,
) {
    var customTest by rememberSaveable { mutableStateOf(false) }
    var testDomain by rememberSaveable { mutableStateOf("") }
    val isIpAddress = host?.let(DomainFilterHostPolicy::isIpv4) == true
    val examples = remember(rule) { domainFilterPreviewExamples(rule) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("domain-filter-rule-content"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = text, onValueChange = { if (enabled) onTextChanged(it) },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("domain-filter-rule-input"),
            label = { Text("Domain or URL") }, singleLine = true, isError = text.isNotBlank() && host == null,
            supportingText = if (text.isNotBlank()) {
                {
                    if (host == null) Text("Enter a valid domain or http(s) URL without login details.")
                    else Row {
                        Text("Hostname: ")
                        Text(host, modifier = Modifier.weight(1f).testTag("normalized-filter-host"))
                    }
                }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave() }),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Include subdomains", fontFamily = ProductSansFontFamily, fontSize = 16.sp, lineHeight = 21.sp,
                modifier = Modifier.weight(1f).padding(end = 8.dp))
            Switch(checked = includeSubdomains && !isIpAddress, onCheckedChange = onIncludeSubdomainsChanged,
                enabled = enabled && !isIpAddress,
                modifier = Modifier.sizeIn(minWidth = 52.dp, minHeight = 48.dp).testTag("include-filter-subdomains"))
        }
        if (legacyRule) Text(
            "This saved rule uses the previous contains-text matching. Saving replaces it with the hostname rule shown here.",
            fontFamily = ProductSansFontFamily, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.tertiary,
        )
        RulePreview(examples, if (rule == null) "Enter a valid hostname to see how it matches." else null)
        SettingsDialogTextButton(onClick = { customTest = !customTest }, enabled = enabled,
            modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (customTest) "Hide custom test" else "Test a domain or URL")
        }
        if (customTest) {
            OutlinedTextField(value = testDomain, onValueChange = { testDomain = it },
                modifier = Modifier.fillMaxWidth().testTag("domain-filter-custom-test"), label = { Text("Domain or URL to test") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false))
            if (testDomain.isNotBlank()) {
                val testedHost = DomainFilterHostPolicy.normalize(testDomain)
                if (testedHost == null) Text("Enter a valid domain or http(s) URL.", color = MaterialTheme.colorScheme.error)
                else RulePreviewResult(testedHost, rule?.matchesHost(testedHost) == true)
            }
        }
    }
}

internal data class FilterPreviewExample(val text: String, val hidden: Boolean)

internal fun titleFilterPreviewExamples(rule: TitleFilterRule): List<FilterPreviewExample> {
    if (rule.text.isBlank()) return emptyList()
    val text = rule.text.trim()
    val examples = if (text.equals("AI", ignoreCase = true)) {
        if (rule.mode == TitleFilterMatchMode.WORDS_PHRASE) {
            listOf("AI tools for researchers", "How ai changes search", "A better office chair")
        } else {
            listOf("AI tools for researchers", "A better office chair", "A quiet room")
        }
    } else {
        val nonMatch = listOf("A different topic", "12345").first { !rule.matches(it) }
        listOf("A story about $text", "Before${text}after", nonMatch)
    }
    return examples.map { FilterPreviewExample(it, rule.matches(it)) }
}

internal fun domainFilterPreviewExamples(rule: DomainFilterRule?): List<FilterPreviewExample> {
    if (rule == null) return emptyList()
    return listOf(rule.domain, "news.${rule.domain}", "not${rule.domain}")
        .map { FilterPreviewExample(it, rule.matchesHost(it)) }
}

@Composable
private fun RulePreview(examples: List<FilterPreviewExample>, emptyMessage: String?) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Preview", fontFamily = ProductSansFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                if (emptyMessage != null) Text(emptyMessage, Modifier.padding(vertical = 14.dp),
                    fontFamily = ProductSansFontFamily, fontSize = 13.sp, lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                examples.forEachIndexed { index, example ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    RulePreviewResult(example.text, example.hidden)
                }
            }
        }
    }
}

@Composable
private fun RulePreviewResult(text: String, hidden: Boolean) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 8.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically) {
        val tint = if (hidden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        Icon(painterResource(if (hidden) Res.drawable.ic_check else Res.drawable.ic_horizontal_rule), null,
            Modifier.padding(end = 10.dp).size(20.dp), tint = tint)
        Text(text, fontFamily = ProductSansFontFamily, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
        Text(if (hidden) "Hidden" else "Visible", fontFamily = ProductSansFontFamily, fontSize = 12.sp, lineHeight = 17.sp,
            fontWeight = FontWeight.Medium, color = tint, modifier = Modifier.padding(start = 8.dp))
    }
}
