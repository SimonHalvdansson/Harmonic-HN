package com.simon.harmonichackernews.ui.licenses

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager

private val CommonLicensesBeforeLocalAi = listOf(
    LicenseEntryUi("AndroidX", "Google", "Apache License 2.0", "https://developer.android.com/jetpack/androidx"),
    LicenseEntryUi("Ktor", "JetBrains", "Apache License 2.0", "https://ktor.io/"),
    LicenseEntryUi(
        "Material Components",
        "Google",
        "Apache License 2.0",
        "https://github.com/material-components/material-components-android",
    ),
)

private val LocalAiLicenses = listOf(
    LicenseEntryUi("ML Kit GenAI APIs", "Google", "ML Kit Terms of Service", "https://developers.google.com/ml-kit/genai"),
    LicenseEntryUi("LiteRT-LM", "Google", "Apache License 2.0", "https://github.com/google-ai-edge/LiteRT-LM"),
    LicenseEntryUi("llama.cpp", "ggml-org", "MIT License", "https://github.com/ggml-org/llama.cpp"),
    LicenseEntryUi("ggml", "ggml-org", "MIT License", "https://github.com/ggml-org/ggml"),
)

private val CommonLicensesAfterLocalAi = listOf(
    LicenseEntryUi("Kotlin standard library", "JetBrains", "Apache License 2.0", "https://kotlinlang.org/"),
    LicenseEntryUi("kotlinx.coroutines", "JetBrains", "Apache License 2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
    LicenseEntryUi("Coil", "Coil contributors", "Apache License 2.0", "https://coil-kt.github.io/coil/"),
    LicenseEntryUi("pdf.js", "Mozilla", "Apache License 2.0", "https://mozilla.github.io/pdf.js/"),
    LicenseEntryUi("Readability", "Mozilla", "Apache License 2.0", "https://github.com/mozilla/readability"),
    LicenseEntryUi("Materialistic", "Hidroh", "Apache License 2.0", "https://github.com/hidroh/materialistic"),
    LicenseEntryUi("Ksoup", "FleekSoft", "MIT License", "https://github.com/fleeksoft/ksoup"),
    LicenseEntryUi("KMPalette", "Jordan Dixon", "Apache License 2.0", "https://github.com/jordond/KMPalette"),
)

@Composable
fun LicensesScreen(
    onBack: () -> Unit,
    onOpenLicense: (String) -> Unit,
    singlePane: Boolean,
    modifier: Modifier = Modifier,
) {
    val licenses = remember { licenseEntries() }
    SharedLicensesScreen(
        licenses = licenses,
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

private fun licenseEntries(): List<LicenseEntryUi> = buildList {
    addAll(CommonLicensesBeforeLocalAi)
    if (LocalAiRuntimeManager.isLocalAiIncluded()) addAll(LocalAiLicenses)
    addAll(CommonLicensesAfterLocalAi)
}
