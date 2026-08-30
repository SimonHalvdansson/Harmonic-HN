package com.simon.harmonichackernews.desktop

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.app.HarmonicSceneComposition
import com.simon.harmonichackernews.navigation.MainNavigationSnapshot
import com.simon.harmonichackernews.ui.settings.PortableAppForeground
import com.simon.harmonichackernews.ui.stories.StoriesComposeController

@Composable
internal fun BoxScope.DesktopAppForeground(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    navigation: MainNavigationSnapshot,
    storiesController: StoriesComposeController?,
) {
    PortableAppForeground(
        app = app,
        scene = scene,
        navigation = navigation,
        storiesController = storiesController,
        appIcon = rememberDesktopAppIconPainter(),
        captchaMessage = "The desktop host does not yet bundle an embedded browser engine " +
            "for Hacker News CAPTCHA challenges.",
    )
}
