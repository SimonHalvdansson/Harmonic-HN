package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Icon
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.StoryTypeMenuPolicy
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.ui.stories.menuIcon
import com.simon.harmonichackernews.ui.common.sharedHazeBackground
import com.simon.harmonichackernews.ui.common.sharedHazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.simon.harmonichackernews.ui.navigation.ActivityNavigationTransitionDurationMillis

@Composable
fun ManageFrontpagesSettingsRoute(
    repository: AppSettingsRepository,
    onBack: () -> Unit,
    focusFrontpage: StoryType? = if (LocalSettingsParentSection.current == SettingsSection.FiltersTags) StoryType.UNSLOP else null,
) {
    val settings by repository.updates.collectAsState(initial = repository.snapshot())
    val story = settings.story
    val frontpages = StoryTypeMenuPolicy.frontpages(story.additionalFrontpages, story.frontpageOrder)
    val available = StoryType.additionalFrontpages.filterNot { it in frontpages }

    ManageFrontpagesSettingsScreen(
        frontpages = frontpages,
        focusFrontpage = focusFrontpage,
        defaultLabel = story.preferredStoryType,
        available = available,
        onBack = onBack,
        onDefaultSelected = { repository.setPreferredStoryType(it.label) },
        onOrderChanged = { repository.setFrontpageOrder(it.map(StoryType::name)) },
        onReset = {
            repository.setFrontpageOrder(emptyList())
            repository.setPreferredStoryType(StoryType.TOP_STORIES.label)
        },
        onRemove = { type ->
            repository.setAdditionalFrontpages(story.additionalFrontpages - type.label)
            repository.setFrontpageOrder(frontpages.filterNot { it == type }.map(StoryType::name))
        },
        onAdd = { type ->
            repository.setFrontpageOrder((frontpages + type).map(StoryType::name))
            repository.setAdditionalFrontpages(story.additionalFrontpages + type.label)
        },
    )
}

@Composable
fun ManageFrontpagesSettingsScreen(
    frontpages: List<StoryType>,
    defaultLabel: String,
    available: List<StoryType>,
    onBack: () -> Unit,
    onDefaultSelected: (StoryType) -> Unit,
    onOrderChanged: (List<StoryType>) -> Unit,
    onReset: () -> Unit,
    onRemove: (StoryType) -> Unit,
    onAdd: (StoryType) -> Unit,
    focusFrontpage: StoryType? = null,
) {
    var infoFrontpage by rememberSaveable { mutableStateOf<StoryType?>(null) }
    val listState = rememberLazyListState()
    val hazeState = rememberHazeState()
    val resetButtonShape = RoundedCornerShape(16.dp)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var resetButtonHeight by remember { mutableStateOf(56.dp) }
    val selectedBackground = frontpageSelectionColor(
        pageBackground = settingsPageBackgroundColor(),
        cardBackground = settingsItemBackgroundColor(),
        accent = MaterialTheme.colorScheme.primary,
    )
    // Persisting a drop must not replace the state that is still animating that drop.
    val reorder = remember(listState) { FrontpageReorderState(frontpages, listState) }
    LaunchedEffect(frontpages) { reorder.updateItems(frontpages) }
    val focusPulse = remember(focusFrontpage) { Animatable(0f) }
    var focusHandled by rememberSaveable(focusFrontpage) { mutableStateOf(false) }
    LaunchedEffect(focusFrontpage) {
        val target = focusFrontpage ?: return@LaunchedEffect
        if (focusHandled) return@LaunchedEffect
        // Let the destination finish opening before drawing attention to the target row.
        delay(ActivityNavigationTransitionDurationMillis.toLong())
        val activeIndex = reorder.items.indexOf(target)
        val availableIndex = available.indexOf(target)
        val index = when {
            activeIndex >= 0 -> activeIndex + 1
            availableIndex >= 0 -> reorder.items.size + 2 + availableIndex
            else -> return@LaunchedEffect
        }
        val durationScale = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
        withContext(object : MotionDurationScale {
            override val scaleFactor = durationScale * 1.5f
        }) {
            listState.animateScrollToItem(index, scrollOffset = with(density) { -16.dp.roundToPx() })
        }
        focusHandled = true
        focusPulse.animateTo(1f, tween(150, easing = FastOutSlowInEasing))
        delay(1_000)
        focusPulse.animateTo(0f, tween(350, easing = FastOutSlowInEasing))
    }
    val focusAccent = MaterialTheme.colorScheme.primary
    fun focusedBackground(type: StoryType, background: Color): Color =
        if (type == focusFrontpage) {
            focusAccent.copy(alpha = 0.12f * focusPulse.value).compositeOver(background)
        } else background

    val drop = reorder.drop
    LaunchedEffect(drop) {
        if (drop != null) {
            drop.progress.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
            reorder.completeDrop(drop)
        }
    }
    val edgeSize = with(LocalDensity.current) { 56.dp.toPx() }
    LaunchedEffect(reorder.draggedType) {
        // Keep scrolling when the handle is held near an edge, even without pointer movement.
        while (reorder.draggedType != null) {
            withFrameMillis { }
            val info = listState.layoutInfo
            val scroll = when {
                reorder.draggedTop < info.viewportStartOffset + edgeSize ->
                    (reorder.draggedTop - info.viewportStartOffset - edgeSize).coerceAtLeast(-edgeSize)
                reorder.draggedBottom > info.viewportEndOffset - edgeSize ->
                    (reorder.draggedBottom - info.viewportEndOffset + edgeSize).coerceAtMost(edgeSize)
                else -> 0f
            }
            if (scroll != 0f) {
                listState.scrollBy(scroll * 0.15f)
                reorder.move(0f)
            }
        }
    }
    CompositionLocalProvider(
        LocalRippleConfiguration provides RippleConfiguration(
            color = if (HarmonicTheme.isDark) Color.White else Color.Unspecified,
            rippleAlpha = RippleAlpha(draggedAlpha = 0.08f, focusedAlpha = 0.06f, hoveredAlpha = 0.03f, pressedAlpha = 0.05f),
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            SettingsPage(
                modifier = Modifier.sharedHazeSource(hazeState),
                title = stringResource(Res.string.settings_section_frontpages),
                showNavigation = true,
                onBack = onBack,
                listState = listState,
                extraBottomPadding = resetButtonHeight + 16.dp,
                contentVersion = reorder.items.hashCode() + defaultLabel.hashCode(),
            ) {
                item(key = "frontpage-help") {
                    Text(
                        text = "Drag to reorder",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        color = HarmonicTheme.colors.textSecondary,
                        fontFamily = ProductSansFontFamily,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
                itemsIndexed(reorder.items, key = { _, type -> type.name }) { index, type ->
                    val lifted = reorder.draggedType == type || drop?.type == type
                    val selected = type.label == defaultLabel
                    val accent = MaterialTheme.colorScheme.primary
                    val background by animateColorAsState(
                        targetValue = if (selected) {
                            selectedBackground
                        } else {
                            settingsItemBackgroundColor()
                        },
                        animationSpec = tween(160),
                        label = "default frontpage background",
                    )
                    fun moveBy(delta: Int) {
                        onOrderChanged(reorder.items.toMutableList().apply {
                            add(index + delta, removeAt(index))
                        })
                    }
                    val actions = buildList {
                        if (index > 0) add(CustomAccessibilityAction("Move up") { moveBy(-1); true })
                        if (index < reorder.items.lastIndex) {
                            add(CustomAccessibilityAction("Move down") { moveBy(1); true })
                        }
                        if (type in StoryType.additionalFrontpages) {
                            add(CustomAccessibilityAction("Remove frontpage") { onRemove(type); true })
                        }
                    }
                    Row(
                        modifier = Modifier
                            .zIndex(if (lifted) 1f else 0f)
                            .animateItem(placementSpec = if (lifted) null else spring(stiffness = 400f))
                            .graphicsLayer { translationY = reorder.translationFor(type) }
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(focusedBackground(type, background))
                            .border(
                                width = 1.dp,
                                color = accent.copy(alpha = maxOf(
                                    if (selected) 0.35f else 0f,
                                    if (type == focusFrontpage) 0.8f * focusPulse.value else 0f,
                                )),
                                shape = RoundedCornerShape(12.dp),
                            )
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 52.dp)
                            .padding(end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Only the name/icon area selects the default. The drag and remove controls
                        // are siblings, so a short tap on the handle cannot bubble into selection.
                        Row(
                            modifier = Modifier.weight(1f)
                                .defaultMinSize(minHeight = 52.dp)
                                .selectable(selected, role = Role.RadioButton, onClick = { onDefaultSelected(type) })
                                .semantics { customActions = actions }
                                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painterResource(type.menuIcon),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = if (selected) accent else HarmonicTheme.colors.drawable,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = type.label,
                                modifier = Modifier.weight(1f, fill = false),
                                color = HarmonicTheme.colors.textPrimary,
                                fontFamily = ProductSansFontFamily,
                                fontSize = 16.sp,
                                lineHeight = 20.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (selected) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "DEFAULT",
                                    modifier = Modifier
                                        .background(accent.copy(alpha = 0.12f), RoundedCornerShape(5.dp))
                                        .padding(horizontal = 6.dp, vertical = 3.dp),
                                    color = accent,
                                    fontFamily = ProductSansFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 9.sp,
                                    lineHeight = 12.sp,
                                    letterSpacing = 0.5.sp,
                                    maxLines = 1,
                                )
                            }
                        }
                        if (type in StoryType.additionalFrontpages) {
                            IconButton(onClick = { onRemove(type) }) {
                                Icon(
                                    painterResource(Res.drawable.ic_close),
                                    contentDescription = "Remove ${type.label}",
                                    modifier = Modifier.size(20.dp),
                                    tint = HarmonicTheme.colors.drawable,
                                )
                            }
                            FrontpageInfoButton(type, onClick = { infoFrontpage = type })
                        }
                        Box(
                            modifier = Modifier.size(48.dp).pointerInput(reorder, type) {
                                detectDragGestures(
                                    onDragStart = { reorder.start(type) },
                                    onDragEnd = { reorder.finish(); onOrderChanged(reorder.items) },
                                    onDragCancel = reorder::cancel,
                                    onDrag = { change, amount -> change.consume(); reorder.move(amount.y) },
                                )
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(Res.drawable.ic_drag_handle),
                                contentDescription = "Drag to reorder ${type.label}",
                                tint = HarmonicTheme.colors.drawable,
                            )
                        }
                    }
                }
                if (available.isNotEmpty()) {
                    item(key = "add-frontpages-heading") {
                        Text(
                            text = "Add frontpage",
                            modifier = Modifier.animateItem().padding(start = 24.dp, top = 20.dp, bottom = 8.dp)
                                .semantics { heading() },
                            color = HarmonicTheme.colors.textSecondary,
                            fontFamily = ProductSansFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                    }
                    items(available, key = { "add-${it.name}" }) { type ->
                        Row(
                            modifier = Modifier.animateItem()
                                .padding(horizontal = 16.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(focusedBackground(type, settingsItemBackgroundColor()))
                                .border(
                                    1.dp,
                                    focusAccent.copy(alpha = if (type == focusFrontpage) 0.8f * focusPulse.value else 0f),
                                    RoundedCornerShape(12.dp),
                                )
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 52.dp)
                                .clickable(role = Role.Button, onClick = { onAdd(type) })
                                .semantics { contentDescription = "Add ${type.label}" }
                                .padding(start = 16.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painterResource(type.menuIcon),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = HarmonicTheme.colors.drawable,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = type.label,
                                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                                color = HarmonicTheme.colors.textPrimary,
                                fontFamily = ProductSansFontFamily,
                                fontSize = 16.sp,
                                lineHeight = 20.sp,
                            )
                            FrontpageInfoButton(type, onClick = { infoFrontpage = type })
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                Icon(
                                    painterResource(Res.drawable.ic_add),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = {
                    onReset()
                    scope.launch { listState.animateScrollToItem(0) }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                    )
                    .padding(16.dp)
                    .widthIn(min = 140.dp)
                    .onSizeChanged { resetButtonHeight = with(density) { it.height.toDp() } }
                    .shadow(6.dp, resetButtonShape, clip = false)
                    .sharedHazeBackground(
                        hazeState = hazeState,
                        surfaceColor = HarmonicTheme.colors.overlayButton.copy(alpha = 0.8f),
                        shape = resetButtonShape,
                    )
                    .semantics { contentDescription = "Reset frontpage order and default" },
                shape = resetButtonShape,
                containerColor = Color.Transparent,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                ),
                contentColor = HarmonicTheme.colors.overlayButtonContent,
                icon = { Icon(painterResource(Res.drawable.ic_refresh), contentDescription = null) },
                text = { Text("Reset", fontFamily = ProductSansFontFamily, fontWeight = FontWeight.SemiBold) },
            )
        }
    }
    infoFrontpage?.let { type ->
        FrontpageInfoDialog(type, onDismiss = { infoFrontpage = null })
    }
}

/** Owns only the in-progress gesture; the completed order is persisted by the route. */
private class FrontpageReorderState(initial: List<StoryType>, private val listState: LazyListState) {
    var items by mutableStateOf(initial)
        private set
    var draggedType by mutableStateOf<StoryType?>(null)
        private set
    var draggedTop by mutableFloatStateOf(0f)
        private set
    var drop by mutableStateOf<FrontpageDrop?>(null)
        private set
    private var draggedHeight = 0
    private var orderBeforeDrag = initial
    val draggedBottom get() = draggedTop + draggedHeight
    private val draggedItem get() = listState.layoutInfo.visibleItemsInfo.find { it.key == draggedType?.name }

    fun translationFor(type: StoryType): Float {
        val offset = listState.layoutInfo.visibleItemsInfo.find { it.key == type.name }?.offset ?: return 0f
        if (type == draggedType) return draggedTop - offset
        val settling = drop?.takeIf { it.type == type } ?: return 0f
        // Keep the released surface in place if the last reorder has not been laid out yet,
        // then interpolate to its actual slot. Persistence and remeasurement cannot snap it.
        return (settling.top - offset) * (1f - settling.progress.value)
    }

    fun updateItems(next: List<StoryType>) {
        if (next == items) return
        items = next
        draggedType = null
        drop = null
    }

    fun start(type: StoryType) {
        val item = listState.layoutInfo.visibleItemsInfo.find { it.key == type.name } ?: return
        orderBeforeDrag = items
        drop = null
        draggedType = type
        draggedTop = item.offset.toFloat()
        draggedHeight = item.size
    }

    fun move(delta: Float) {
        val type = draggedType ?: return
        draggedTop += delta
        val center = draggedTop + draggedHeight / 2f
        val target = listState.layoutInfo.visibleItemsInfo.find {
            it.key != type.name && center >= it.offset && center < it.offset + it.size &&
                items.any { type -> type.name == it.key }
        } ?: return
        val from = items.indexOf(type)
        val to = items.indexOfFirst { it.name == target.key }
        // Wait for the lazy layout to reflect the previous move before moving again.
        if (draggedItem?.index != from + 1) return
        items = items.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun finish() {
        drop = draggedType?.let { FrontpageDrop(it, draggedTop) }
        draggedType = null
    }

    fun completeDrop(completed: FrontpageDrop) {
        if (drop === completed) drop = null
    }

    fun cancel() {
        items = orderBeforeDrag
        finish()
    }
}

private class FrontpageDrop(val type: StoryType, val top: Float) {
    val progress = Animatable(0f)
}
