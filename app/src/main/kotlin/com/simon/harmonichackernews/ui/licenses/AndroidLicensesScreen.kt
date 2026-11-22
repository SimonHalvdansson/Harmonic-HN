package com.simon.harmonichackernews.ui.licenses

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.app.CommonLicenseCatalog
import com.simon.harmonichackernews.app.LicenseEntry
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies

private val AndroidLicenses = listOf(
    LicenseEntry.apache2(
        "AndroidSVG", "Paul LeBeau, Cave Rock Software Ltd.", "https://bigbadaboom.github.io/androidsvg/",
        "Copyright 2013-2018 Cave Rock Software Ltd",
    ),
    LicenseEntry.apache2("AndroidX", "Google", "https://developer.android.com/jetpack/androidx"),
)

@Composable
fun AndroidLicensesScreen(
    showNotice: Boolean,
    onShowNotice: () -> Unit,
    onBack: () -> Unit,
    onOpenLicense: (String) -> Unit,
    singlePane: Boolean,
    modifier: Modifier = Modifier,
) {
    val includeLocalAi = LocalHarmonicUiDependencies.current.localModels?.isIncluded == true
    val licenses = remember(includeLocalAi) { licenseEntries(includeLocalAi) }
    LicensesScreen(
        licenses = licenses,
        showNotice = showNotice,
        onShowNotice = onShowNotice,
        onBack = onBack,
        onOpenLicense = onOpenLicense,
        modifier = modifier,
        sidePadding = if (singlePane) dimensionResource(R.dimen.single_view_side_margin) else 0.dp,
        topBarHeight = dimensionResource(R.dimen.compose_settings_toolbar_height),
        topBarNavigationHeight = dimensionResource(R.dimen.detail_toolbar_navigation_height),
        topBarNavigationInset = dimensionResource(R.dimen.detail_toolbar_navigation_inset),
        platformTextStyle = TextStyle(
            platformStyle = PlatformTextStyle(includeFontPadding = true),
        ),
    )
}

private fun licenseEntries(includeLocalAi: Boolean): List<LicenseEntryUi> = buildList {
    addAll(CommonLicenseCatalog.complete(AndroidLicenses, includeLocalAi))
}
