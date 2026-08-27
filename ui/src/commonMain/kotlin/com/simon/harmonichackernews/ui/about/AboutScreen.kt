package com.simon.harmonichackernews.ui.about

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_attribution
import com.simon.harmonichackernews.resources.ic_link_preview_github
import com.simon.harmonichackernews.resources.ic_policy
import com.simon.harmonichackernews.resources.ic_system_update_alt
import com.simon.harmonichackernews.resources.settings_section_about
import com.simon.harmonichackernews.ui.common.HarmonicTopAppBar
import com.simon.harmonichackernews.ui.common.OutlinedButton
import com.simon.harmonichackernews.ui.theme.GoogleSansFlexRoundedFontFamily
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

const val DefaultAboutBody =
    "Harmonic is developed by me, Simon Halvdansson, although since 2023 " +
        "the app is open source.\n\nThe name 'Harmonic' comes from me choosing to study " +
        "harmonic analysis at around the time of Harmonic's inception. I guess you could say " +
        "something about 'waves' and 'news' but that's the rationale. Since then, I've finished " +
        "my PhD in harmonic analysis and don't have the same amount of time to work on Harmonic " +
        "but it's still my favorite pet project and I use the app daily. Seeing others contribute, " +
        "open meaningful issues on GitHub or just tell me they like the app is always super nice " +
        "and I'm very thankful to the community for helping with the maintenance. Together I " +
        "think we can keep Harmonic the (in my opinion) best Hacker News client for Android!"

private const val FeedbackBody =
    "Please submit it by opening an issue or starting a discussion on Harmonic’s GitHub page"

@Composable
fun AboutScreen(
    versionLabel: String,
    appIcon: Painter,
    onBack: () -> Unit,
    onOpenGithub: () -> Unit,
    onOpenChangelog: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
    showNavigation: Boolean = true,
    sidePadding: Dp = 0.dp,
    topBarHeight: Dp = 64.dp,
    topBarNavigationHeight: Dp = 56.dp,
    topBarNavigationInset: Dp = 0.dp,
    aboutBody: String = DefaultAboutBody,
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
        HarmonicTopAppBar(
            title = stringResource(Res.string.settings_section_about),
            onBack = if (showNavigation) onBack else null,
            toolbarHeight = topBarHeight,
            navigationHeight = topBarNavigationHeight,
            navigationInset = topBarNavigationInset,
            platformTextStyle = platformTextStyle,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(
                    WindowInsets.systemBars.only(WindowInsetsSides.Bottom),
                )
                .padding(
                    start = 16.dp + sidePadding,
                    top = 20.dp,
                    end = 16.dp + sidePadding,
                    bottom = 8.dp,
                ),
        ) {
            AboutIdentity(
                versionLabel = versionLabel,
                appIcon = appIcon,
                platformTextStyle = platformTextStyle,
            )

            Text(
                text = aboutBody,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 16.dp),
                color = colors.textPrimary,
                fontFamily = ProductSansFontFamily,
                fontSize = 16.sp,
                style = platformTextStyle,
            )

            FeedbackCard(platformTextStyle)
            Spacer(modifier = Modifier.height(16.dp))
            AboutActionButton(
                text = "Harmonic on GitHub",
                icon = painterResource(Res.drawable.ic_link_preview_github),
                onClick = onOpenGithub,
                platformTextStyle = platformTextStyle,
            )
            AboutActionButton(
                text = "Changelog",
                icon = painterResource(Res.drawable.ic_system_update_alt),
                onClick = onOpenChangelog,
                platformTextStyle = platformTextStyle,
            )
            AboutActionButton(
                text = "Licenses",
                icon = painterResource(Res.drawable.ic_attribution),
                onClick = onOpenLicenses,
                platformTextStyle = platformTextStyle,
            )
            AboutActionButton(
                text = "Privacy policy",
                icon = painterResource(Res.drawable.ic_policy),
                onClick = onOpenPrivacy,
                platformTextStyle = platformTextStyle,
            )
        }
    }
}

@Composable
private fun AboutIdentity(
    versionLabel: String,
    appIcon: Painter,
    platformTextStyle: TextStyle,
) {
    val colors = HarmonicTheme.colors
    Row(modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Image(
            painter = appIcon,
            contentDescription = "Harmonic app icon",
            modifier = Modifier.size(56.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Box(modifier = Modifier.fillMaxHeight()) {
            Text(
                text = "Harmonic",
                modifier = Modifier.offset(y = (-2).dp).semantics { heading() },
                color = colors.storyNormal,
                fontFamily = GoogleSansFlexRoundedFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = fixedDpTextSize(29.dp),
                style = platformTextStyle,
            )
            Text(
                text = versionLabel,
                modifier = Modifier.offset(y = 34.dp),
                color = colors.storyDisabled,
                fontFamily = GoogleSansFlexRoundedFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = fixedDpTextSize(13.dp),
                style = platformTextStyle,
            )
        }
    }
}

@Composable
private fun FeedbackCard(platformTextStyle: TextStyle) {
    val colors = HarmonicTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(Res.drawable.ic_link_preview_github),
            contentDescription = null,
            modifier = Modifier.size(24.dp).alpha(0.9f),
            colorFilter = ColorFilter.tint(colors.onSecondaryContainer),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Have feedback?",
                color = colors.onSecondaryContainer,
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                style = platformTextStyle,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = FeedbackBody,
                color = colors.onSecondaryContainer,
                fontFamily = ProductSansFontFamily,
                fontSize = 14.sp,
                style = platformTextStyle,
            )
        }
    }
}

@Composable
private fun AboutActionButton(
    text: String,
    icon: Painter,
    onClick: () -> Unit,
    platformTextStyle: TextStyle = TextStyle.Default,
) {
    val colors = HarmonicTheme.colors
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(3.5.dp)
            .clearAndSetSemantics {
                contentDescription = text
                role = Role.Button
                semanticsOnClick {
                    onClick()
                    true
                }
            },
        shape = CircleShape,
        border = BorderStroke(0.75.dp, colors.outlineVariant),
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Image(
                painter = icon,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
                    .alpha(0.8f)
                    .size(24.dp),
                colorFilter = ColorFilter.tint(colors.storyNormal),
            )
            Text(
                text = text,
                modifier = Modifier.align(Alignment.Center).offset(x = 12.dp, y = (-0.5).dp),
                color = colors.storyNormal,
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                style = platformTextStyle,
            )
        }
    }
}

@Composable
private fun fixedDpTextSize(size: Dp) = with(LocalDensity.current) { size.toSp() }
