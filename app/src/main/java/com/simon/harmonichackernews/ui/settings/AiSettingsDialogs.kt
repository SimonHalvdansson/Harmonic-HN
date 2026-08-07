@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.network.AiModelCatalog
import com.simon.harmonichackernews.network.AiSummaryProviders
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.network.OpenRouterProviderIconLoader
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.AiSummaryApiKeyStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Call

private val AiMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

private val AiFreeTitleSuffix = Regex("\\s*\\(free\\)\\s*$", RegexOption.IGNORE_CASE)

@Composable
fun AiSummaryTextDialog(
    preferenceKey: String,
    title: String,
    hint: String,
    defaultValue: String,
    minLines: Int,
    maxLines: Int,
    textSizeSp: Int,
    trimValue: Boolean,
    allowEmpty: Boolean,
    showReset: Boolean,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        PreferenceManager.getDefaultSharedPreferences(context)
    }
    val initialValue = remember(preferenceKey, defaultValue) {
        if (preferenceKey == AiSummaryApiKeyStore.PREF_API_KEY) {
            AiSummaryApiKeyStore.getApiKey(context)
        } else {
            prefs.getString(preferenceKey, defaultValue) ?: defaultValue
        }
    }
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    var error by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    fun save() {
        val savedValue = if (trimValue) value.trim() else value
        if (!allowEmpty && savedValue.isEmpty()) {
            error = "Required"
            return
        }
        if (preferenceKey == AiSummaryApiKeyStore.PREF_API_KEY) {
            if (!AiSummaryApiKeyStore.setApiKey(context, savedValue)) {
                error = "Couldn't securely save API key"
                return
            }
        } else {
            prefs.edit().putString(preferenceKey, savedValue).apply()
        }
        onSaved()
        onDismiss()
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { SettingsDialogTitle(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it
                    error = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .then(
                        if (maxLines <= 1) {
                            Modifier.height(
                                androidx.compose.ui.res.dimensionResource(
                                    R.dimen.compose_settings_dialog_single_line_field_height,
                                ),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .focusRequester(focusRequester),
                label = { Text(hint) },
                isError = error != null,
                supportingText = error?.let { message ->
                    {
                        Text(message)
                    }
                },
                singleLine = maxLines <= 1,
                minLines = minLines,
                maxLines = maxLines,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = ProductSansFontFamily,
                    fontSize = textSizeSp.sp,
                    lineHeight = if (maxLines > 1) {
                        (textSizeSp + 3).sp
                    } else {
                        (textSizeSp + 4).sp
                    },
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (
                        preferenceKey == AiSummaryApiKeyStore.PREF_API_KEY
                    ) {
                        KeyboardType.Ascii
                    } else {
                        KeyboardType.Text
                    },
                    imeAction = if (maxLines <= 1) ImeAction.Done else ImeAction.Default,
                ),
                keyboardActions = KeyboardActions(onDone = { save() }),
            )
        },
        confirmButton = {
            SettingsDialogTextButton(onClick = { save() }) {
                Text("Save")
            }
        },
        dismissButton = {
            SettingsDialogTextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        neutralButton = if (showReset) {
            {
                SettingsDialogTextButton(
                    onClick = {
                        error = null
                        value = defaultValue
                    },
                ) {
                    Text("Reset")
                }
            }
        } else {
            null
        },
    )
}

@Composable
fun AiSummaryBaseUrlDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        PreferenceManager.getDefaultSharedPreferences(context)
    }
    var url by remember {
        mutableStateOf(
            prefs.getString(
                AiModelCatalog.PREF_BASE_URL,
                AiSummaryProviders.defaultBaseUrl,
            ) ?: AiSummaryProviders.defaultBaseUrl,
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val providers = remember { AiSummaryProviders.PROVIDERS.toList() }

    fun save() {
        val savedUrl = url.trim()
        if (savedUrl.isEmpty()) {
            error = "Enter a base URL"
            return
        }
        val oldProvider = AiSummaryProviders.getProviderForBaseUrl(
            prefs.getString(
                AiModelCatalog.PREF_BASE_URL,
                AiSummaryProviders.defaultBaseUrl,
            ),
        )
        val newProvider = AiSummaryProviders.getProviderForBaseUrl(savedUrl)
        val editor = prefs.edit().putString(AiModelCatalog.PREF_BASE_URL, savedUrl)
        if (newProvider != null && newProvider.id != oldProvider?.id) {
            val translated = if (oldProvider == null) {
                ""
            } else {
                AiSummaryProviders.translateModelId(
                    oldProvider,
                    newProvider,
                    prefs.getString(AiModelCatalog.PREF_MODEL, ""),
                )
            }
            if (translated.isNullOrEmpty()) {
                editor.remove(AiModelCatalog.PREF_MODEL)
            } else {
                editor.putString(AiModelCatalog.PREF_MODEL, translated)
            }
        }
        editor.apply()
        if (newProvider != null && !prefs.contains(AiModelCatalog.PREF_MODEL)) {
            AiModelCatalog.ensureProviderDefault(context, newProvider)
        }
        onDismiss()
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { SettingsDialogTitle("Base URL") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Presets",
                    modifier = Modifier.padding(top = 4.dp),
                    color = HarmonicTheme.colors.storyNormal,
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                LazyRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(providers, key = { it.id }) { provider ->
                        val selected =
                            AiSummaryProviders.normalizeUrl(provider.baseUrl) ==
                                AiSummaryProviders.normalizeUrl(url)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                url = provider.baseUrl
                                error = null
                            },
                            label = { Text(provider.label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        error = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp)
                        .focusRequester(focusRequester),
                    label = { Text("Base URL") },
                    isError = error != null,
                    supportingText = error?.let { message ->
                        {
                            Text(message)
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                )
            }
        },
        confirmButton = {
            SettingsDialogTextButton(onClick = { save() }) {
                Text("Save")
            }
        },
        dismissButton = {
            SettingsDialogTextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private enum class AiModelFilter {
    Popular,
    Free,
    Price,
}

private sealed interface AiModelCatalogState {
    data object Loading : AiModelCatalogState
    data class Loaded(val models: List<AiModelCatalog.Model>) : AiModelCatalogState
    data class Error(val message: String) : AiModelCatalogState
}

private sealed interface AiModelPriceState {
    data object Empty : AiModelPriceState
    data object Loading : AiModelPriceState
    data class Resolved(val model: AiModelCatalog.Model) : AiModelPriceState
    data class Error(val message: String) : AiModelPriceState
}

@Composable
fun AiModelSelectorDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        PreferenceManager.getDefaultSharedPreferences(context)
    }
    val baseUrl = prefs.getString(
        AiModelCatalog.PREF_BASE_URL,
        AiSummaryProviders.defaultBaseUrl,
    ) ?: AiSummaryProviders.defaultBaseUrl
    val provider = AiSummaryProviders.getProviderForBaseUrl(baseUrl)
        ?: AiSummaryProviders.defaultProvider
    val initialModel = AiSummaryProviders.getModelForRequest(
        baseUrl,
        prefs.getString(AiModelCatalog.PREF_MODEL, ""),
    )
    var modelInput by remember { mutableStateOf(initialModel) }
    var modelError by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(AiModelFilter.Popular) }
    var catalogState by remember {
        mutableStateOf<AiModelCatalogState>(AiModelCatalogState.Loading)
    }
    var catalogReload by remember { mutableIntStateOf(0) }
    var priceState by remember {
        mutableStateOf<AiModelPriceState>(AiModelPriceState.Empty)
    }
    val catalogCall = remember { arrayOfNulls<Call>(1) }
    val priceCall = remember { arrayOfNulls<Call>(1) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val requiredModelMessage = stringResource(R.string.ai_model_required)
    var dismissing by remember { mutableStateOf(false) }

    fun saveSelection(): Boolean {
        val selected = modelInput.trim()
        if (selected.isEmpty()) {
            modelError = requiredModelMessage
            return false
        }
        prefs.edit()
            .putString(
                AiModelCatalog.PREF_MODEL,
                AiSummaryProviders.toProviderModelId(provider, selected),
            )
            .apply()
        return true
    }

    fun dismissWithAnimation() {
        if (dismissing) return
        dismissing = true
        keyboardController?.hide()
        coroutineScope.launch {
            sheetState.hide()
        }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                onDismiss()
            } else {
                dismissing = false
            }
        }
    }

    fun saveAndDismiss() {
        if (saveSelection()) {
            dismissWithAnimation()
        }
    }

    DisposableEffect(provider, filter, catalogReload) {
        var disposed = false
        catalogState = AiModelCatalogState.Loading
        val sort = if (filter == AiModelFilter.Price) {
            AiModelCatalog.Sort.PRICE_LOW_TO_HIGH
        } else {
            AiModelCatalog.Sort.POPULAR
        }
        catalogCall[0]?.cancel()
        catalogCall[0] = AiModelCatalog.fetchModels(
            provider,
            sort,
            object : AiModelCatalog.ModelsCallback {
                override fun onSuccess(models: MutableList<AiModelCatalog.Model>) {
                    if (disposed) return
                    val safeModels = models.toList()
                    catalogState = AiModelCatalogState.Loaded(
                        if (filter == AiModelFilter.Free) {
                            safeModels.filter(AiModelCatalog.Model::isFree)
                        } else {
                            safeModels
                        },
                    )
                }

                override fun onError(message: String?) {
                    if (!disposed) {
                        catalogState = AiModelCatalogState.Error(
                            message ?: "Could not load models",
                        )
                    }
                }
            },
        )
        onDispose {
            disposed = true
            catalogCall[0]?.cancel()
            catalogCall[0] = null
        }
    }

    LaunchedEffect(modelInput) {
        priceCall[0]?.cancel()
        priceCall[0] = null
        val requestedModel = modelInput.trim()
        if (requestedModel.isEmpty()) {
            priceState = AiModelPriceState.Empty
            return@LaunchedEffect
        }
        priceState = AiModelPriceState.Loading
        delay(400)
        priceCall[0] = AiModelCatalog.resolveModel(
            provider,
            requestedModel,
            object : AiModelCatalog.ModelCallback {
                override fun onSuccess(model: AiModelCatalog.Model) {
                    if (requestedModel == modelInput.trim()) {
                        priceState = AiModelPriceState.Resolved(model)
                    }
                }

                override fun onError(message: String?) {
                    if (requestedModel == modelInput.trim()) {
                        priceState = AiModelPriceState.Error(
                            message ?: "Price unavailable",
                        )
                    }
                }
            },
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            catalogCall[0]?.cancel()
            priceCall[0]?.cancel()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(2.dp),
                    ),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
        ) {
            Text(
                text = stringResource(R.string.ai_model_choose_title),
                modifier = Modifier.padding(start = 24.dp, top = 18.dp, end = 24.dp),
                color = HarmonicTheme.colors.storyNormal,
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
            )
            Text(
                text = stringResource(R.string.ai_model_catalog_subtitle),
                modifier = Modifier.padding(start = 24.dp, top = 2.dp, end = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 17.sp,
            )
            OutlinedTextField(
                value = modelInput,
                onValueChange = {
                    modelInput = it
                    modelError = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 18.dp, end = 24.dp, bottom = 10.dp)
                    .height(
                        androidx.compose.ui.res.dimensionResource(
                            R.dimen.compose_settings_dialog_single_line_field_height,
                        ),
                    ),
                label = { Text(stringResource(R.string.ai_model_id_hint)) },
                isError = modelError != null,
                supportingText = modelError?.let { message ->
                    {
                        Text(message)
                    }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = AiMonoFontFamily,
                    fontSize = 14.sp,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { saveAndDismiss() },
                ),
            )
            AiModelPrice(priceState)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val count = (catalogState as? AiModelCatalogState.Loaded)?.models?.size
                Text(
                    text = count?.let {
                        pluralStringResource(R.plurals.ai_model_count, it, it)
                    } ?: stringResource(R.string.ai_model_suggestions),
                    modifier = Modifier.weight(1f),
                    color = HarmonicTheme.colors.storyNormal,
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                )
            }
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(AiModelFilter.entries, key = { it.name }) { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = {
                            Text(
                                when (option) {
                                    AiModelFilter.Popular -> stringResource(
                                        R.string.ai_model_sort_popular,
                                    )
                                    AiModelFilter.Free -> stringResource(R.string.ai_model_sort_free)
                                    AiModelFilter.Price -> stringResource(
                                        R.string.ai_model_sort_price,
                                    )
                                },
                            )
                        },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = catalogState,
                    label = "model catalog",
                ) { state ->
                    when (state) {
                        AiModelCatalogState.Loading -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Loading models…",
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }

                        is AiModelCatalogState.Error -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp),
                        ) {
                            Text(
                                text = state.message,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            OutlinedButton(
                                onClick = { catalogReload++ },
                                modifier = Modifier.padding(top = 12.dp),
                            ) {
                                Text("Retry")
                            }
                        }

                        is AiModelCatalogState.Loaded -> {
                            if (state.models.isEmpty()) {
                                Text(stringResource(R.string.ai_model_no_free))
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        start = 16.dp,
                                        top = 4.dp,
                                        end = 16.dp,
                                        bottom = 12.dp,
                                    ),
                                ) {
                                    items(
                                        state.models,
                                        key = AiModelCatalog.Model::openRouterId,
                                    ) { model ->
                                        AiModelRow(
                                            model = model,
                                            selected = model.requestId == modelInput.trim(),
                                            onClick = {
                                                modelInput = model.requestId
                                                modelError = null
                                                priceState =
                                                    AiModelPriceState.Resolved(model)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsDialogTextButton(
                    onClick = { dismissWithAnimation() },
                    modifier = Modifier
                        .height(56.dp)
                        .widthIn(min = 94.dp),
                ) {
                    Text(stringResource(R.string.ai_model_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { saveAndDismiss() },
                    modifier = Modifier
                        .height(56.dp)
                        .widthIn(min = 123.dp),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.ai_model_save))
                }
            }
        }
    }
}

@Composable
private fun AiModelPrice(state: AiModelPriceState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        AnimatedContent(
            targetState = state,
            label = "model price",
        ) { price ->
            when (price) {
                AiModelPriceState.Empty -> Text(
                    text = "Enter a model ID to see pricing",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                AiModelPriceState.Loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    Text(
                        text = "Resolving price…",
                        modifier = Modifier.padding(start = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
                is AiModelPriceState.Error -> Text(
                    text = price.message,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                )
                is AiModelPriceState.Resolved -> Column {
                    Text(
                        text = "INPUT / OUTPUT",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = ProductSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.08.sp,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${price.model.formattedInputPrice()} / " +
                                price.model.formattedOutputPrice(),
                            color = HarmonicTheme.colors.storyNormal,
                            fontFamily = ProductSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        )
                        Text(
                            text = " per million tokens",
                            modifier = Modifier.padding(start = 6.dp, bottom = 2.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiModelRow(
    model: AiModelCatalog.Model,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val rowShape = RoundedCornerShape(18.dp)
    val normalContainer = HarmonicTheme.colors.surfaceContainerHigh
    val containerColor = if (selected) {
        lerp(normalContainer, MaterialTheme.colorScheme.primaryContainer, 0.04f)
    } else {
        normalContainer
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .defaultMinSize(minHeight = 92.dp)
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, rowShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        shape = rowShape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 13.dp, end = 8.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AiModelProviderIcon(providerSlug = model.providerSlug())
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
            ) {
                Text(
                    text = if (model.isFree) {
                        model.name.replace(AiFreeTitleSuffix, "")
                    } else {
                        model.name
                    },
                    color = HarmonicTheme.colors.storyNormal,
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = model.requestId,
                    modifier = Modifier.padding(top = 2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = AiMonoFontFamily,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
                if (model.isFree) {
                    Text(
                        text = "FREE",
                        modifier = Modifier
                            .padding(top = 7.dp)
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontFamily = ProductSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                } else {
                    Text(
                        text = stringResource(
                            R.string.ai_model_row_price_format,
                            model.formattedInputPrice(),
                            model.formattedOutputPrice(),
                        ),
                        modifier = Modifier.padding(top = 7.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            SettingsRadioButton(selected = selected)
        }
    }
}

@Composable
private fun AiModelProviderIcon(providerSlug: String) {
    val context = LocalContext.current
    var iconData by remember(providerSlug) { mutableStateOf<Any?>(null) }

    DisposableEffect(providerSlug) {
        var active = true
        OpenRouterProviderIconLoader.resolve(providerSlug) { resolvedSlug, resolvedIcon ->
            if (active && resolvedSlug.equals(providerSlug, ignoreCase = true)) {
                iconData = resolvedIcon
            }
        }
        onDispose {
            active = false
        }
    }

    Box(
        modifier = Modifier
            .size(24.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = providerSlug.take(1).uppercase().ifEmpty { "?" },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = ProductSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
        )
        iconData?.let { resolvedIcon ->
            val request = remember(context, resolvedIcon) {
                ImageRequest.Builder(context)
                    .data(resolvedIcon)
                    .setHeader("User-Agent", NetworkComponent.USER_AGENT)
                    .crossfade(100)
                    .apply {
                        if (
                            resolvedIcon is ByteArray ||
                            resolvedIcon is String &&
                            resolvedIcon.contains(".svg", ignoreCase = true)
                        ) {
                            decoderFactory(SvgDecoder.Factory())
                        }
                    }
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape),
            )
        }
    }
}
