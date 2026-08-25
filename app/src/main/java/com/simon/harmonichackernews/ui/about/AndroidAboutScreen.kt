package com.simon.harmonichackernews.ui.about

import android.graphics.Bitmap
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies

/** Android resources adapter for the shared About screen. */
@Composable
fun AndroidAboutScreen(
    onBack: () -> Unit,
    onOpenGithub: () -> Unit,
    onOpenChangelog: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
    showNavigation: Boolean = true,
    singlePane: Boolean = true,
) {
    val context = LocalContext.current
    val metadata = LocalHarmonicUiDependencies.current.metadata
    val iconSizePx = with(LocalDensity.current) { 56.dp.roundToPx() }
    val appIcon = remember(context, iconSizePx) {
        BitmapPainter(
            requireNotNull(AppCompatResources.getDrawable(context, R.mipmap.ic_launcher))
                .toBitmap(iconSizePx, iconSizePx, Bitmap.Config.ARGB_8888)
                .asImageBitmap(),
        )
    }
    AboutScreen(
        versionLabel = metadata.versionLabel,
        appIcon = appIcon,
        onBack = onBack,
        onOpenGithub = onOpenGithub,
        onOpenChangelog = onOpenChangelog,
        onOpenLicenses = onOpenLicenses,
        onOpenPrivacy = onOpenPrivacy,
        modifier = modifier,
        showNavigation = showNavigation,
        sidePadding = if (singlePane) dimensionResource(R.dimen.single_view_side_margin) else 0.dp,
        topBarHeight = dimensionResource(R.dimen.compose_settings_toolbar_height),
        topBarNavigationHeight = dimensionResource(R.dimen.detail_toolbar_navigation_height),
        topBarNavigationInset = dimensionResource(R.dimen.detail_toolbar_navigation_inset),
        platformTextStyle = TextStyle(
            platformStyle = PlatformTextStyle(includeFontPadding = true),
        ),
    )
}
