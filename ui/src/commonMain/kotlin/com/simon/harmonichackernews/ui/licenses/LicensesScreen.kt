package com.simon.harmonichackernews.ui.licenses

import androidx.compose.material3.MaterialTheme
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
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
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
import com.simon.harmonichackernews.ui.theme.cardBackground
import com.simon.harmonichackernews.ui.theme.pageBackground
import com.simon.harmonichackernews.resources.HarmonicDimens
import com.simon.harmonichackernews.app.LicenseEntry
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.library_logo_androidx
import com.simon.harmonichackernews.resources.library_logo_coil
import com.simon.harmonichackernews.resources.library_logo_compose_multiplatform
import com.simon.harmonichackernews.resources.library_logo_compose_latex
import com.simon.harmonichackernews.resources.library_logo_katex
import com.simon.harmonichackernews.resources.library_logo_coroutines
import com.simon.harmonichackernews.resources.library_logo_flatbuffers
import com.simon.harmonichackernews.resources.library_logo_haze
import com.simon.harmonichackernews.resources.library_logo_kmpalette
import com.simon.harmonichackernews.resources.library_logo_kotlin
import com.simon.harmonichackernews.resources.library_logo_ksoup
import com.simon.harmonichackernews.resources.library_logo_ktor
import com.simon.harmonichackernews.resources.library_logo_kuri
import com.simon.harmonichackernews.resources.ic_open_in_new
import com.simon.harmonichackernews.resources.ic_translate
import com.simon.harmonichackernews.resources.library_logo_litert_lm
import com.simon.harmonichackernews.resources.library_logo_material_components
import com.simon.harmonichackernews.resources.library_logo_materialistic
import com.simon.harmonichackernews.resources.library_logo_mlkit
import com.simon.harmonichackernews.resources.library_logo_pdfjs
import com.simon.harmonichackernews.resources.library_logo_readability
import com.simon.harmonichackernews.resources.settings_section_licenses
import com.simon.harmonichackernews.ui.common.OutlinedButton
import com.simon.harmonichackernews.ui.settings.LocalSettingsLicenseUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.simon.harmonichackernews.ui.common.HarmonicTopAppBar
import com.simon.harmonichackernews.ui.theme.AppUiFontFamily
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

typealias LicenseEntryUi = LicenseEntry

private val LicenseCardShape = RoundedCornerShape(24.dp)
private val LicenseIconShape = RoundedCornerShape(9.dp)
private val InsetLicenseIcons = setOf(
    "FlatBuffers",
    "kuri",
    "Kotlin standard library",
    "kotlinx.coroutines",
    "kotlinx.serialization",
    "kotlinx.io",
    "kotlinx.collections.immutable",
    "ML Kit GenAI APIs",
    "KaTeX math fonts",
    "Unicode character data",
)

@Composable
fun LicensesScreen(
    licenses: List<LicenseEntryUi>,
    showNotice: Boolean,
    onShowNotice: () -> Unit,
    onBack: () -> Unit,
    onOpenLicense: (String) -> Unit,
    modifier: Modifier = Modifier,
    sidePadding: Dp = 0.dp,
    topBarHeight: Dp = 64.dp,
    topBarNavigationHeight: Dp = 56.dp,
    topBarNavigationInset: Dp = 0.dp,
    platformTextStyle: TextStyle = TextStyle.Default,
) {
    val selection = LocalSettingsLicenseUrl.current
    val selected = if (showNotice) licenses.find { it.url == selection.value } else null
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.pageBackground)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
            ),
    ) {
        HarmonicTopAppBar(
            title = selected?.name ?: stringResource(Res.string.settings_section_licenses),
            onBack = onBack,
            navigationContentDescription = if (selected == null) "Back" else "Back to licenses",
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
                        .widthIn(max = HarmonicDimens.fullscreen_content_max_width)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(LicenseCardShape)
                        .background(colors.cardBackground)
                        .border(1.dp, colors.outlineVariant, LicenseCardShape),
                ) {
                    if (selected != null) {
                        key(selected.url) { LicenseNotice(selected, onOpenLicense, platformTextStyle) }
                    } else {
                        LicenseHeader(platformTextStyle)
                        HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
                        licenses.forEachIndexed { index, license ->
                            LicenseRow(
                                license = license,
                                onClick = {
                                    selection.value = license.url
                                    onShowNotice()
                                },
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
}

@Composable
private fun LicenseNotice(license: LicenseEntry, openUrl: (String) -> Unit, platformTextStyle: TextStyle) {
    val notice by produceState("", license.noticeFile) {
        value = try {
            withContext(Dispatchers.IO) {
                val source = Res.readBytes("files/licenses/${license.noticeFile}").decodeToString()
                // Let text wrap to the viewport while retaining upstream paragraph breaks.
                source.trim().lines().joinToString("\n") { it.trim() }.split("\n\n")
                    .joinToString("\n\n") { it.replace('\n', ' ') }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            "Visit the project website to read its license."
        }
    }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(license.licenseType, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            fontFamily = AppUiFontFamily, color = colors.onSurface, style = platformTextStyle)
        Text(license.creator, fontSize = 14.sp, fontFamily = AppUiFontFamily,
            color = colors.onSurfaceVariant, style = platformTextStyle)
        OutlinedButton(onClick = { openUrl(license.url) }, modifier = Modifier.padding(vertical = 8.dp)) {
            Icon(painterResource(Res.drawable.ic_open_in_new), null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Project website", fontFamily = AppUiFontFamily)
        }
        SelectionContainer {
            Text(
                text = listOfNotNull(license.attribution, notice).joinToString("\n\n"),
                fontFamily = AppUiFontFamily,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = colors.onSurface,
                style = platformTextStyle,
            )
        }
        license.termsUrl?.let { url ->
            OutlinedButton(onClick = { openUrl(url) }) { Text("Read full terms") }
        }
    }
}

@Composable
private fun LicenseHeader(platformTextStyle: TextStyle) {
    val colors = MaterialTheme.colorScheme
    val style = TextStyle(
        fontFamily = AppUiFontFamily,
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
            color = colors.onSurfaceVariant,
            style = style,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = "CREATOR", color = colors.onSurfaceVariant, style = style)
    }
}

@Composable
private fun LicenseRow(
    license: LicenseEntryUi,
    onClick: () -> Unit,
    platformTextStyle: TextStyle,
) {
    val colors = MaterialTheme.colorScheme
    val description = buildString {
        append(license.name)
        append(", ")
        append(license.licenseType)
        if (license.creator.isNotEmpty()) {
            append(", by ")
            append(license.creator)
        }
        append(". View license details.")
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
                color = colors.onSurface,
                fontFamily = AppUiFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                style = platformTextStyle,
            )
            Text(
                text = license.licenseType,
                color = colors.onSurfaceVariant,
                fontFamily = AppUiFontFamily,
                fontSize = 13.sp,
                style = platformTextStyle,
            )
        }
        if (license.creator.isNotEmpty()) {
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = license.creator,
                modifier = Modifier.widthIn(max = 180.dp),
                color = colors.onSurfaceVariant,
                fontFamily = AppUiFontFamily,
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
        val icon = licenseIconResource(license.name)
        if (icon == null) {
            // License attribution must remain accessible even before a project logo is added.
            Text(
                text = license.name.take(1).uppercase(),
                color = Color(0xff303030),
                fontFamily = AppUiFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
        } else Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .padding(if (license.name in InsetLicenseIcons) 4.dp else 0.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

internal fun licenseIconResource(name: String): DrawableResource? = when (name) {
    "AndroidX" -> Res.drawable.library_logo_androidx
    "Material Components for Android" -> Res.drawable.library_logo_material_components
    "Kotlin standard library" -> Res.drawable.library_logo_kotlin
    "kotlinx.coroutines" -> Res.drawable.library_logo_coroutines
    "kotlinx.serialization" -> Res.drawable.library_logo_kotlin
    "FlatBuffers" -> Res.drawable.library_logo_flatbuffers
    "kotlinx.io" -> Res.drawable.library_logo_kotlin
    "kotlinx.collections.immutable" -> Res.drawable.library_logo_kotlin
    "Ktor" -> Res.drawable.library_logo_ktor
    "kuri" -> Res.drawable.library_logo_kuri
    "Unicode character data" -> Res.drawable.ic_translate
    "Compose Multiplatform" -> Res.drawable.library_logo_compose_multiplatform
    "Haze" -> Res.drawable.library_logo_haze
    "Coil" -> Res.drawable.library_logo_coil
    "Ksoup" -> Res.drawable.library_logo_ksoup
    "Compose LaTeX" -> Res.drawable.library_logo_compose_latex
    "KaTeX math fonts" -> Res.drawable.library_logo_katex
    "Palette algorithms" -> Res.drawable.library_logo_kmpalette
    "pdf.js" -> Res.drawable.library_logo_pdfjs
    "Readability" -> Res.drawable.library_logo_readability
    "Materialistic" -> Res.drawable.library_logo_materialistic
    "ML Kit GenAI APIs" -> Res.drawable.library_logo_mlkit
    "LiteRT-LM" -> Res.drawable.library_logo_litert_lm
    else -> null
}
