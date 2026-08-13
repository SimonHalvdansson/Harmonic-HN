package com.simon.harmonichackernews.ui.settings

import android.graphics.Bitmap
import android.text.format.DateFormat
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.ColorUtils
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.settings.PaletteTintPreferences
import com.simon.harmonichackernews.settings.StoryPreviewPreferences
import com.simon.harmonichackernews.settings.ThemePreferences
import com.simon.harmonichackernews.ui.common.rememberHarmonicFilterColors
import com.simon.harmonichackernews.utils.AndroidAppearanceState

fun composeThemeLabel(value: String, fallback: String = ThemePreferences.DEFAULT): String {
    return harmonicThemeLabel(value, fallback)
}

@Composable
fun ThemeSelectionDialog(
    nighttime: Boolean,
    onDismiss: () -> Unit,
    onThemeChanged: () -> Unit,
) {
    val context = LocalContext.current
    val app = LocalHarmonicUiDependencies.current
    val presenter = remember(app) { AppearanceSettingsPresenter(app.settings) }
    val selected = if (nighttime) {
        presenter.snapshot.appearance.nighttimeTheme
    } else {
        presenter.snapshot.appearance.theme
    }
    SharedThemeSelectionDialog(
        nighttime = nighttime,
        selected = selected,
        onThemeSelected = { theme ->
            presenter.setTheme(theme, nighttime).forEach { effect ->
                if (effect == SettingsPlatformEffect.ThemeChanged) onThemeChanged()
            }
            onDismiss()
        },
        onDismiss = onDismiss,
        previewPalettes = { theme -> themePreviewPalettes(theme) },
    )
}

@Composable
private fun themePreviewPalettes(
    theme: String,
): Pair<ThemePreviewPalette, ThemePreviewPalette?> {
    val dark = ThemePreviewPalette(
        background = colorResource(R.color.background),
        surface = colorResource(R.color.darkerBackground),
        accent = colorResource(R.color.colorPrimary),
        text = colorResource(R.color.darkStoryColorNormal),
        secondaryText = colorResource(R.color.darkTextColorDefault),
        dark = true,
    )
    val materialDark = ThemePreviewPalette(
        background = colorResource(R.color.material_you_neutral_900),
        surface = colorResource(R.color.material_you_neutral_variant20),
        accent = colorResource(R.color.material_you_third_400),
        text = colorResource(R.color.darkStoryColorNormal),
        secondaryText = colorResource(R.color.material_you_secondary_70),
        dark = true,
    )
    val grayBackground = colorResource(R.color.grayBackground)
    val darkText = colorResource(R.color.darkStoryColorNormal)
    val gray = ThemePreviewPalette(
        background = grayBackground,
        surface = Color(ColorUtils.blendARGB(grayBackground.toArgb(), darkText.toArgb(), 0.07f)),
        accent = colorResource(R.color.colorPrimary),
        text = darkText,
        secondaryText = colorResource(R.color.darkTextColorDefault),
        dark = true,
    )
    val black = ThemePreviewPalette(
        background = Color.Black,
        surface = Color.Black,
        accent = colorResource(R.color.colorPrimary),
        text = colorResource(R.color.darkStoryColorNormal),
        secondaryText = colorResource(R.color.darkTextColorDefault),
        dark = true,
    )
    val hacker = ThemePreviewPalette(
        background = colorResource(R.color.hackerBackground),
        surface = colorResource(R.color.hackerSurfaceContainer),
        accent = colorResource(R.color.hackerAccent),
        text = colorResource(R.color.hackerTextColor),
        secondaryText = colorResource(R.color.hackerTextColorDisabled),
        dark = true,
    )
    val lightBackground = colorResource(R.color.lightBackground)
    val lightText = colorResource(R.color.lightStoryColorNormal)
    val light = ThemePreviewPalette(
        background = lightBackground,
        surface = Color(
            ColorUtils.blendARGB(lightBackground.toArgb(), Color.White.toArgb(), 0.56f),
        ),
        accent = colorResource(R.color.colorPrimaryGreen),
        text = lightText,
        secondaryText = colorResource(R.color.lightTextColorDefault),
        dark = false,
    )
    val hackerNews = ThemePreviewPalette(
        background = colorResource(R.color.hackerNewsBackground),
        surface = colorResource(R.color.hackerNewsSurfaceContainer),
        accent = colorResource(R.color.hackerNewsAccent),
        text = colorResource(R.color.hackerNewsStoryColorNormal),
        secondaryText = colorResource(R.color.hackerNewsTextColorDisabled),
        dark = false,
    )
    val materialLight = ThemePreviewPalette(
        background = colorResource(R.color.material_you_neutral_50),
        surface = colorResource(R.color.material_you_neutral_variant95),
        accent = colorResource(R.color.material_you_third_400),
        text = lightText,
        secondaryText = colorResource(R.color.material_you_secondary_40),
        dark = false,
    )
    val white = ThemePreviewPalette(
        background = Color.White,
        surface = Color(
            ColorUtils.blendARGB(Color.White.toArgb(), lightText.toArgb(), 0.035f),
        ),
        accent = colorResource(R.color.colorPrimaryGreen),
        text = lightText,
        secondaryText = colorResource(R.color.lightTextColorDefault),
        dark = false,
    )
    return when (theme) {
        "material_daynight" -> materialLight to materialDark
        "darklight_daynight" -> light to dark
        "amoledwhite_daynight" -> white to black
        "material_light" -> materialLight to null
        "material_dark" -> materialDark to null
        "light" -> light to null
        "hacker" -> hacker to null
        "hacker_news" -> hackerNews to null
        "amoled" -> black to null
        "white" -> white to null
        "gray" -> gray to null
        else -> dark to null
    }
}

@Composable
fun NighttimeRangeDialog(
    onDismiss: () -> Unit,
    onRangeSelected: () -> Unit,
) {
    val context = LocalContext.current
    val current = remember(context) { AndroidAppearanceState.nighttimeSchedule(context) }
    SharedNighttimeRangeDialog(
        initialHours = current,
        is24Hour = DateFormat.is24HourFormat(context),
        onRangeSelected = { fromHour, fromMinute, toHour, toMinute ->
            AndroidAppearanceState.saveNighttimeSchedule(
                fromHour,
                fromMinute,
                toHour,
                toMinute,
                context,
            )
            onRangeSelected()
        },
        onDismiss = onDismiss,
    )
}

@Composable
fun WelcomeSettingsDialog(
    styleChooser: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val app = LocalHarmonicUiDependencies.current
    val presenter = remember(app) { AppearanceSettingsPresenter(app.settings) }
    LaunchedEffect(Unit) {
        app.aiModelDefaults.ensureInitialDefault()
    }
    val storyPreferences = presenter.snapshot.story
    SharedWelcomeSettingsDialog(
        styleChooser = styleChooser,
        initialExpressive = !styleChooser ||
            storyPreferences.font != "productsans" ||
            storyPreferences.tintCardUsingPreview ||
            storyPreferences.previewImageMode != StoryPreviewPreferences.OFF,
        onApplyPreset = { expressive ->
            presenter.applyWelcomePreset(expressive)
            if (!styleChooser) AndroidAppearanceState.markWelcomeShown(context)
            onDismiss()
        },
        onDismiss = onDismiss,
        filterButtonColors = rememberHarmonicFilterColors(),
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
fun PaletteTintDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val app = LocalHarmonicUiDependencies.current
    val presenter = remember(app) { AppearanceSettingsPresenter(app.settings) }
    val paletteConfig = presenter.snapshot.story.paletteTintConfigKey
    SharedPaletteTintDialog(
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
