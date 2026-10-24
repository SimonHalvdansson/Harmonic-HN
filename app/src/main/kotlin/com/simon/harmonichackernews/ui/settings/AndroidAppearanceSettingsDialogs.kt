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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.settings.NighttimeSchedule
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.common.rememberAndroidHarmonicFilterColors

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
                NighttimeSchedule(
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
fun AndroidWelcomeScreen(
    styleChooser: Boolean,
    onDismiss: () -> Unit,
) {
    val app = LocalHarmonicUiDependencies.current
    val presenter = remember(app) { AppearanceSettingsPresenter(app.settings) }
    LaunchedEffect(Unit) {
        app.aiModelDefaults.ensureInitialDefault()
    }
    val storyPreferences = presenter.snapshot.story
    WelcomeScreen(
        styleChooser = styleChooser,
        initialExpressive = !styleChooser || storyPreferences.displayStyle != DisplayStyle.FLAT,
        storyPreferences = storyPreferences,
        commentPreferences = presenter.snapshot.comments,
        onApplyPreset = { expressive ->
            presenter.applyWelcomePreset(expressive)
            if (!styleChooser) app.launchState.completeWelcome()
            onDismiss()
        },
        filterButtonColors = rememberAndroidHarmonicFilterColors(),
        launcherIcon = { WelcomeLauncherIcon() },
    )
}

@Composable
private fun WelcomeLauncherIcon() {
    val context = LocalContext.current
    // Rasterize at the header's displayed size, so the animated layer stays sharp.
    val size = 114.dp
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
