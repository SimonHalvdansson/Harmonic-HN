package com.simon.harmonichackernews.ui.settings

import android.graphics.Bitmap
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.settings.PaletteTintPreferences
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.ui.common.rememberAndroidHarmonicFilterColors

@Composable
fun AndroidThemeSelectionDialog(
    nighttime: Boolean,
    onDismiss: () -> Unit,
    onThemeChanged: () -> Unit,
) {
    val app = LocalHarmonicUiDependencies.current
    val presenter = remember(app) { AppearanceSettingsPresenter(app.settings) }
    val selected = if (nighttime) {
        presenter.snapshot.appearance.nighttimeTheme
    } else {
        presenter.snapshot.appearance.theme
    }
    ThemeSelectionDialog(
        nighttime = nighttime,
        selected = selected,
        onThemeSelected = { theme ->
            presenter.setTheme(theme, nighttime).forEach { effect ->
                if (effect == SettingsPlatformEffect.ThemeChanged) onThemeChanged()
            }
            onDismiss()
        },
        onDismiss = onDismiss,
        previewPalettes = { ThemePreviewCatalog.palettes(it) },
    )
}

@Composable
fun AndroidNighttimeRangeDialog(
    onDismiss: () -> Unit,
    onRangeSelected: () -> Unit,
) {
    val appearance = LocalHarmonicUiDependencies.current.appearance
    val current = remember(appearance) { appearance.schedule.toIntArray() }
    NighttimeRangeDialog(
        initialHours = current,
        is24Hour = LocalHarmonicUiDependencies.current.platform.timeFormatting
            .uses24HourClock(),
        onRangeSelected = { fromHour, fromMinute, toHour, toMinute ->
            appearance.saveSchedule(
                com.simon.harmonichackernews.settings.NighttimeSchedule(
                    fromHour,
                    fromMinute,
                    toHour,
                    toMinute,
                ),
            )
            onRangeSelected()
        },
        onDismiss = onDismiss,
    )
}

@Composable
fun AndroidWelcomeSettingsDialog(
    styleChooser: Boolean,
    onDismiss: () -> Unit,
) {
    val app = LocalHarmonicUiDependencies.current
    val presenter = remember(app) { AppearanceSettingsPresenter(app.settings) }
    LaunchedEffect(Unit) {
        app.aiModelDefaults.ensureInitialDefault()
    }
    val storyPreferences = presenter.snapshot.story
    WelcomeSettingsDialog(
        styleChooser = styleChooser,
        initialExpressive = !styleChooser ||
            storyPreferences.font != "productsans" ||
            storyPreferences.tintCardUsingPreview ||
            storyPreferences.previewImageMode != StoryPreviewMode.OFF,
        onApplyPreset = { expressive ->
            presenter.applyWelcomePreset(expressive)
            if (!styleChooser) app.appearance.markWelcomeShown()
            onDismiss()
        },
        onDismiss = onDismiss,
        filterButtonColors = rememberAndroidHarmonicFilterColors(),
        launcherIcon = { WelcomeLauncherIcon() },
    )
}

@Composable
private fun WelcomeLauncherIcon() {
    val context = LocalContext.current
    val size = 64.dp
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val bitmap = remember(context, sizePx) {
        requireNotNull(AppCompatResources.getDrawable(context, R.mipmap.ic_launcher))
            .toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            .asImageBitmap()
    }

    Image(
        bitmap = bitmap,
        contentDescription = "Harmonic",
        modifier = Modifier
            .size(size)
            .clip(CircleShape),
    )
}


@Composable
fun AndroidPaletteTintDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val app = LocalHarmonicUiDependencies.current
    val presenter = remember(app) { AppearanceSettingsPresenter(app.settings) }
    val paletteConfig = presenter.snapshot.story.paletteTintConfigKey
    PaletteTintDialog(
        initialMode = PaletteTintPreferences.sanitizeMode(paletteConfig),
        initialStrength = PaletteTintPreferences.strength(paletteConfig),
        initialColorfulness = PaletteTintPreferences.colorfulness(paletteConfig),
        initialTone = PaletteTintPreferences.tone(paletteConfig),
        onSettingsChanged = { mode, strength, colorfulness, tone ->
            presenter.setPaletteTint(
                mode,
                strength,
                colorfulness,
                tone,
            )
        },
        onReset = { presenter.clearPaletteTint() },
        onDismiss = onDismiss,
    )
}
