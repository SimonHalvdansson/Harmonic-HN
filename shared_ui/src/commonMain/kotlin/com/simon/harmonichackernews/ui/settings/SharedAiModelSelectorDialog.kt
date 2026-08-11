@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.simon.harmonichackernews.ui.common.HarmonicLoadingIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.network.AiModel
import com.simon.harmonichackernews.network.AiModelCatalogRepository
import com.simon.harmonichackernews.network.AiModelCatalogSort
import com.simon.harmonichackernews.network.AiSummaryProviders
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.resources.HarmonicDimens
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private enum class AiModelFilter { Popular, Free, Price }

private sealed interface AiModelCatalogState {
    data object Loading : AiModelCatalogState
    data class Loaded(val models: List<AiModel>) : AiModelCatalogState
    data class Error(val message: String) : AiModelCatalogState
}

private sealed interface AiModelPriceState {
    data object Empty : AiModelPriceState
    data object Loading : AiModelPriceState
    data class Resolved(val model: AiModel) : AiModelPriceState
    data class Error(val message: String) : AiModelPriceState
}

private val AiMonoFontFamily: FontFamily
    @Composable get() {
        val regular = Font(Res.font.jetbrains_mono_regular, FontWeight.Normal)
        val bold = Font(Res.font.jetbrains_mono_bold, FontWeight.Bold)
        return remember(regular, bold) { FontFamily(regular, bold) }
    }

@Composable
fun SharedAiModelSelectorDialog(
    initialModel: String,
    provider: AiSummaryProviders.Provider,
    catalogRepository: AiModelCatalogRepository,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    providerIcon: @Composable (providerSlug: String) -> Unit,
) {
    var modelInput by remember(initialModel) { mutableStateOf(initialModel) }
    var modelError by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(AiModelFilter.Popular) }
    var catalogState by remember {
        mutableStateOf<AiModelCatalogState>(AiModelCatalogState.Loading)
    }
    var catalogReload by remember { mutableIntStateOf(0) }
    var priceState by remember { mutableStateOf<AiModelPriceState>(AiModelPriceState.Empty) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val requiredModelMessage = stringResource(Res.string.ai_model_required)
    var dismissing by remember { mutableStateOf(false) }

    fun saveSelection(): Boolean {
        val selected = modelInput.trim()
        if (selected.isEmpty()) {
            modelError = requiredModelMessage
            return false
        }
        onSave(selected)
        return true
    }

    fun dismissWithAnimation() {
        if (dismissing) return
        dismissing = true
        keyboardController?.hide()
        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss() else dismissing = false
        }
    }

    fun saveAndDismiss() {
        if (saveSelection()) dismissWithAnimation()
    }

    LaunchedEffect(provider, filter, catalogReload) {
        catalogState = AiModelCatalogState.Loading
        val sort = if (filter == AiModelFilter.Price) {
            AiModelCatalogSort.PRICE_LOW_TO_HIGH
        } else {
            AiModelCatalogSort.POPULAR
        }
        catalogState = try {
            val models = catalogRepository.fetchModels(provider, sort)
            AiModelCatalogState.Loaded(
                if (filter == AiModelFilter.Free) models.filter(AiModel::isFree) else models,
            )
        } catch (error: Throwable) {
            AiModelCatalogState.Error(error.message ?: "Could not load models")
        }
    }

    LaunchedEffect(modelInput) {
        val requestedModel = modelInput.trim()
        if (requestedModel.isEmpty()) {
            priceState = AiModelPriceState.Empty
            return@LaunchedEffect
        }
        priceState = AiModelPriceState.Loading
        delay(400)
        priceState = try {
            AiModelPriceState.Resolved(catalogRepository.resolveModel(provider, requestedModel))
        } catch (error: Throwable) {
            AiModelPriceState.Error(error.message ?: "Price unavailable")
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
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Text(
                text = stringResource(Res.string.ai_model_choose_title),
                modifier = Modifier.padding(start = 24.dp, top = 18.dp, end = 24.dp),
                color = HarmonicTheme.colors.storyNormal,
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
            )
            Text(
                text = stringResource(Res.string.ai_model_catalog_subtitle),
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
                    .height(HarmonicDimens.compose_settings_dialog_single_line_field_height),
                label = { Text(stringResource(Res.string.ai_model_id_hint)) },
                isError = modelError != null,
                supportingText = modelError?.let { message -> { Text(message) } },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = AiMonoFontFamily,
                    fontSize = 14.sp,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { saveAndDismiss() }),
            )
            AiModelPrice(priceState)

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val count = (catalogState as? AiModelCatalogState.Loaded)?.models?.size
                Text(
                    text = count?.let {
                        pluralStringResource(Res.plurals.ai_model_count, it, it)
                    } ?: stringResource(Res.string.ai_model_suggestions),
                    modifier = Modifier.weight(1f),
                    color = HarmonicTheme.colors.storyNormal,
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                )
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(AiModelFilter.entries, key = { it.name }) { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = {
                            Text(
                                when (option) {
                                    AiModelFilter.Popular ->
                                        stringResource(Res.string.ai_model_sort_popular)
                                    AiModelFilter.Free ->
                                        stringResource(Res.string.ai_model_sort_free)
                                    AiModelFilter.Price ->
                                        stringResource(Res.string.ai_model_sort_price)
                                },
                            )
                        },
                    )
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(targetState = catalogState, label = "model catalog") { state ->
                    when (state) {
                        AiModelCatalogState.Loading -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            HarmonicLoadingIndicator()
                            Text("Loading models…", modifier = Modifier.padding(top = 12.dp))
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

                        is AiModelCatalogState.Loaded if state.models.isEmpty() ->
                            Text(stringResource(Res.string.ai_model_no_free))

                        is AiModelCatalogState.Loaded -> LazyColumn(
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                top = 4.dp,
                                end = 16.dp,
                                bottom = 12.dp,
                            ),
                        ) {
                            items(state.models, key = { it.openRouterId }) { model ->
                                AiModelRow(
                                    model = model,
                                    selected = model.requestId == modelInput.trim(),
                                    onClick = {
                                        modelInput = model.requestId
                                        modelError = null
                                        priceState = AiModelPriceState.Resolved(model)
                                    },
                                    providerIcon = providerIcon,
                                )
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
                    modifier = Modifier.height(56.dp).widthIn(min = 94.dp),
                ) {
                    Text(stringResource(Res.string.ai_model_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { saveAndDismiss() },
                    modifier = Modifier.height(56.dp).widthIn(min = 123.dp),
                ) {
                    Text(stringResource(Res.string.ai_model_save))
                }
            }
        }
    }
}

@Composable
private fun AiModelPrice(state: AiModelPriceState) {
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 28.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        AnimatedContent(targetState = state, label = "model price") { price ->
            when (price) {
                AiModelPriceState.Empty -> Text(
                    text = "Enter a model ID to see pricing",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                AiModelPriceState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    HarmonicLoadingIndicator(modifier = Modifier.size(22.dp))
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
    model: AiModel,
    selected: Boolean,
    onClick: () -> Unit,
    providerIcon: @Composable (String) -> Unit,
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
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                start = 16.dp,
                top = 13.dp,
                end = 8.dp,
                bottom = 13.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            providerIcon(model.providerSlug())
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    text = model.displayName(),
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
                            Res.string.ai_model_row_price_format,
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
