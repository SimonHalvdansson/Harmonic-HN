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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.simon.harmonichackernews.ui.common.SharedHarmonicTopAppBar
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily

data class LicenseEntryUi(
    val name: String,
    val creator: String,
    val licenseType: String,
    val url: String,
)

private val LicenseCardShape = RoundedCornerShape(24.dp)

@Composable
fun SharedLicensesScreen(
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
                WindowInsets.systemBars.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
            ),
    ) {
        SharedHarmonicTopAppBar(
            title = "Third-party licenses",
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
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
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
