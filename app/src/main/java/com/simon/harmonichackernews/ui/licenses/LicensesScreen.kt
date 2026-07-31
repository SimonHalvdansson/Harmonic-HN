package com.simon.harmonichackernews.ui.licenses

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager
import com.simon.harmonichackernews.ui.common.HarmonicTopAppBar
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import androidx.compose.ui.res.dimensionResource

private val IncludeFontPadding = PlatformTextStyle(includeFontPadding = true)
private val LicenseCardShape = RoundedCornerShape(24.dp)

private data class LicenseEntry(
    val name: String,
    val creator: String,
    val licenseType: String,
    val url: String,
)

private val CommonLicensesBeforeLocalAi = listOf(
    LicenseEntry(
        "AndroidX",
        "Google",
        "Apache License 2.0",
        "https://developer.android.com/jetpack/androidx",
    ),
    LicenseEntry(
        "Volley",
        "Google",
        "Apache License 2.0",
        "https://github.com/google/volley",
    ),
    LicenseEntry(
        "Material Components",
        "Google",
        "Apache License 2.0",
        "https://github.com/material-components/material-components-android",
    ),
)

private val LocalAiLicenses = listOf(
    LicenseEntry(
        "ML Kit GenAI APIs",
        "Google",
        "ML Kit Terms of Service",
        "https://developers.google.com/ml-kit/genai",
    ),
    LicenseEntry(
        "LiteRT-LM",
        "Google",
        "Apache License 2.0",
        "https://github.com/google-ai-edge/LiteRT-LM",
    ),
    LicenseEntry(
        "llama.cpp",
        "ggml-org",
        "MIT License",
        "https://github.com/ggml-org/llama.cpp",
    ),
    LicenseEntry(
        "ggml",
        "ggml-org",
        "MIT License",
        "https://github.com/ggml-org/ggml",
    ),
)

private val CommonLicensesAfterLocalAi = listOf(
    LicenseEntry(
        "Kotlin standard library",
        "JetBrains",
        "Apache License 2.0",
        "https://kotlinlang.org/",
    ),
    LicenseEntry(
        "kotlinx.coroutines",
        "JetBrains",
        "Apache License 2.0",
        "https://github.com/Kotlin/kotlinx.coroutines",
    ),
    LicenseEntry(
        "HtmlTextView",
        "SufficientlySecure",
        "Apache License 2.0",
        "https://github.com/SufficientlySecure/html-textview",
    ),
    LicenseEntry(
        "OkHttp",
        "Square",
        "Apache License 2.0",
        "https://square.github.io/okhttp/",
    ),
    LicenseEntry(
        "Coil",
        "Instacart",
        "Apache License 2.0",
        "https://coil-kt.github.io/coil/",
    ),
    LicenseEntry(
        "pdf.js",
        "Mozilla",
        "Apache License 2.0",
        "https://mozilla.github.io/pdf.js/",
    ),
    LicenseEntry(
        "Readability",
        "Mozilla",
        "Apache License 2.0",
        "https://github.com/mozilla/readability",
    ),
    LicenseEntry(
        "Materialistic",
        "Hidroh",
        "Apache License 2.0",
        "https://github.com/hidroh/materialistic",
    ),
    LicenseEntry(
        "jsoup",
        "Jonathan Hedley",
        "MIT License",
        "https://jsoup.org/",
    ),
    LicenseEntry(
        "Markwon",
        "Noties",
        "Apache License 2.0",
        "https://noties.io/Markwon/",
    ),
    LicenseEntry(
        "SwipeBackLayout",
        "Gongwen",
        "Apache License 2.0",
        "https://github.com/gongwen/SwipeBackLayout",
    ),
)

@Composable
fun LicensesScreen(
    onBack: () -> Unit,
    onOpenLicense: (String) -> Unit,
    singlePane: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = HarmonicTheme.colors
    val licenses = remember { licenseEntries() }
    val sidePadding = if (singlePane) {
        dimensionResource(R.dimen.single_view_side_margin)
    } else {
        0.dp
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.systemBars.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
            )
            .background(colors.background),
    ) {
        HarmonicTopAppBar(
            title = "Third-party licenses",
            onBack = onBack,
            navigationContentDescription = "Back to About",
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(
                    WindowInsets.systemBars.only(WindowInsetsSides.Bottom),
                )
                .padding(
                    start = sidePadding,
                    top = 8.dp,
                    end = sidePadding,
                    bottom = 24.dp,
                ),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(LicenseCardShape)
                        .background(colors.surfaceContainerHigh)
                        .border(1.dp, colors.outlineVariant, LicenseCardShape),
                ) {
                    LicenseHeader()
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = colors.outlineVariant,
                    )

                    licenses.forEachIndexed { index, license ->
                        LicenseRow(
                            license = license,
                            onClick = { onOpenLicense(license.url) },
                        )
                        if (index != licenses.lastIndex) {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = colors.outlineVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LicenseHeader() {
    val colors = HarmonicTheme.colors
    val style = TextStyle(
        fontFamily = ProductSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        platformStyle = IncludeFontPadding,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                top = 10.dp,
                end = 16.dp,
                bottom = 9.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "DEPENDENCY",
            modifier = Modifier.weight(1f),
            color = colors.storyDisabled,
            style = style,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = "CREATOR",
            color = colors.storyDisabled,
            style = style,
        )
    }
}

@Composable
private fun LicenseRow(
    license: LicenseEntry,
    onClick: () -> Unit,
) {
    val colors = HarmonicTheme.colors
    val description = buildString {
        append(license.name)
        append(", ")
        append(license.licenseType)
        if (license.creator.isNotEmpty()) {
            append(", by ")
            append(license.creator)
        }
        append(". Open project page.")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clickable(
                role = Role.Button,
                onClick = onClick,
            )
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                semanticsOnClick {
                    onClick()
                    true
                }
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = license.name,
                color = colors.storyNormal,
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                style = TextStyle(platformStyle = IncludeFontPadding),
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = license.licenseType,
                color = colors.storyDisabled,
                fontFamily = ProductSansFontFamily,
                fontSize = 13.sp,
                style = TextStyle(platformStyle = IncludeFontPadding),
            )
        }

        if (license.creator.isNotEmpty()) {
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = license.creator,
                modifier = Modifier.widthIn(max = 180.dp),
                color = colors.storyDisabled,
                fontFamily = ProductSansFontFamily,
                fontSize = 13.sp,
                textAlign = TextAlign.End,
                style = TextStyle(platformStyle = IncludeFontPadding),
            )
        }
    }
}

private fun licenseEntries(): List<LicenseEntry> = buildList {
    addAll(CommonLicensesBeforeLocalAi)
    if (LocalAiRuntimeManager.isLocalAiIncluded()) {
        addAll(LocalAiLicenses)
    }
    addAll(CommonLicensesAfterLocalAi)
}

@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Preview(name = "Foldable", device = Devices.FOLDABLE, showBackground = true)
@Composable
private fun LicensesScreenPreview() {
    HarmonicTheme {
        LicensesScreen(
            onBack = {},
            onOpenLicense = {},
            singlePane = true,
        )
    }
}
