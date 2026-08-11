package com.simon.harmonichackernews.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import com.simon.harmonichackernews.R

/** Android dimension adapter for the platform-neutral settings top app bar. */
@Composable
fun HarmonicTopAppBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    navigationContentDescription: String = "Navigate up",
) {
    SharedHarmonicTopAppBar(
        title = title,
        onBack = onBack,
        modifier = modifier,
        navigationContentDescription = navigationContentDescription,
        toolbarHeight = dimensionResource(R.dimen.compose_settings_toolbar_height),
        navigationHeight = dimensionResource(R.dimen.detail_toolbar_navigation_height),
        navigationInset = dimensionResource(R.dimen.detail_toolbar_navigation_inset),
        platformTextStyle = TextStyle(
            platformStyle = PlatformTextStyle(includeFontPadding = true),
        ),
    )
}
