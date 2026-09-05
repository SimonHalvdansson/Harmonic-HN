package com.simon.harmonichackernews.ui.licenses

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.app.LicenseEntry
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.library_logo_androidx
import com.simon.harmonichackernews.resources.library_logo_coil
import com.simon.harmonichackernews.resources.library_logo_compose_multiplatform
import com.simon.harmonichackernews.resources.library_logo_coroutines
import com.simon.harmonichackernews.resources.library_logo_ggml
import com.simon.harmonichackernews.resources.library_logo_haze
import com.simon.harmonichackernews.resources.library_logo_kmpalette
import com.simon.harmonichackernews.resources.library_logo_kotlin
import com.simon.harmonichackernews.resources.library_logo_ksoup
import com.simon.harmonichackernews.resources.library_logo_ktor
import com.simon.harmonichackernews.resources.library_logo_litert_lm
import com.simon.harmonichackernews.resources.library_logo_llama_cpp
import com.simon.harmonichackernews.resources.library_logo_material_components
import com.simon.harmonichackernews.resources.library_logo_materialistic
import com.simon.harmonichackernews.resources.library_logo_mlkit
import com.simon.harmonichackernews.resources.library_logo_pdfjs
import com.simon.harmonichackernews.resources.library_logo_readability
import com.simon.harmonichackernews.resources.settings_section_licenses
import com.simon.harmonichackernews.ui.common.HarmonicTopAppBar
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

typealias LicenseEntryUi = LicenseEntry

private val LicenseCardShape = RoundedCornerShape(24.dp)
private val LicenseIconShape = RoundedCornerShape(9.dp)
private val InsetLicenseIcons = setOf(
    "Kotlin standard library",
    "kotlinx.coroutines",
    "ML Kit GenAI APIs",
    "llama.cpp",
)

@Composable
fun LicensesScreen(
    licenses: List<LicenseEntryUi>,
    onBack: () -> Unit,
    onOpenLicense: (String) -> Unit,
    modifier: Modifier = Modifier,
    sidePadding: Dp = 0.dp,
    topBarHeight: Dp = 64.dp,
    topBarNavigationHeight: Dp = 56.dp,
    topBarNavigationInset: Dp = 0.dp,
    platformTextStyle: TextStyle = TextStyle.Default,
) {
    val colors = HarmonicTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
            ),
    ) {
        HarmonicTopAppBar(
            title = stringResource(Res.string.settings_section_licenses),
            onBack = onBack,
            navigationContentDescription = "Back to About",
            toolbarHeight = topBarHeight,
            navigationHeight = topBarNavigationHeight,
            navigationInset = topBarNavigationInset,
            platformTextStyle = platformTextStyle,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(
                    start = sidePadding,
                    top = 8.dp,
                    end = sidePadding,
                    bottom = 24.dp,
                ),
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(LicenseCardShape)
                        .background(colors.surfaceContainerHigh)
                        .border(1.dp, colors.outlineVariant, LicenseCardShape),
                ) {
                    LicenseHeader(platformTextStyle)
                    HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
                    licenses.forEachIndexed { index, license ->
                        LicenseRow(
                            license = license,
                            onClick = { onOpenLicense(license.url) },
                            platformTextStyle = platformTextStyle,
                        )
                        if (index != licenses.lastIndex) {
                            HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LicenseHeader(platformTextStyle: TextStyle) {
    val colors = HarmonicTheme.colors
    val style = TextStyle(
        fontFamily = ProductSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
    ).merge(platformTextStyle)
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            start = 16.dp,
            top = 11.dp,
            end = 16.dp,
            bottom = 10.dp,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(48.dp))
        Text(
            text = "DEPENDENCY",
            modifier = Modifier.weight(1f),
            color = colors.storyDisabled,
            style = style,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = "CREATOR", color = colors.storyDisabled, style = style)
    }
}

@Composable
private fun LicenseRow(
    license: LicenseEntryUi,
    onClick: () -> Unit,
    platformTextStyle: TextStyle,
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
            .clickable(role = Role.Button, onClick = onClick)
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
        LicenseIcon(license)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = license.name,
                color = colors.storyNormal,
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                style = platformTextStyle,
            )
            Text(
                text = license.licenseType,
                color = colors.storyDisabled,
                fontFamily = ProductSansFontFamily,
                fontSize = 13.sp,
                style = platformTextStyle,
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
                style = platformTextStyle,
            )
        }
    }
}

@Composable
private fun LicenseIcon(license: LicenseEntryUi) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(LicenseIconShape)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(licenseIconResource(license.name)),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .padding(if (license.name in InsetLicenseIcons) 4.dp else 0.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

private fun licenseIconResource(name: String): DrawableResource = when (name) {
    "AndroidX" -> Res.drawable.library_logo_androidx
    "Material Components for Android" -> Res.drawable.library_logo_material_components
    "Kotlin standard library" -> Res.drawable.library_logo_kotlin
    "kotlinx.coroutines" -> Res.drawable.library_logo_coroutines
    "Ktor" -> Res.drawable.library_logo_ktor
    "Compose Multiplatform" -> Res.drawable.library_logo_compose_multiplatform
    "Haze" -> Res.drawable.library_logo_haze
    "Coil" -> Res.drawable.library_logo_coil
    "Ksoup" -> Res.drawable.library_logo_ksoup
    "Palette algorithms" -> Res.drawable.library_logo_kmpalette
    "pdf.js" -> Res.drawable.library_logo_pdfjs
    "Readability" -> Res.drawable.library_logo_readability
    "Materialistic" -> Res.drawable.library_logo_materialistic
    "ML Kit GenAI APIs" -> Res.drawable.library_logo_mlkit
    "LiteRT-LM" -> Res.drawable.library_logo_litert_lm
    "llama.cpp" -> Res.drawable.library_logo_llama_cpp
    "ggml" -> Res.drawable.library_logo_ggml
    else -> error("Missing library icon for $name")
}
