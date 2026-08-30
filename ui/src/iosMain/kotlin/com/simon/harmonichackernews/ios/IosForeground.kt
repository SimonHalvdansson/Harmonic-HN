package com.simon.harmonichackernews.ios

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.app.HarmonicSceneComposition
import com.simon.harmonichackernews.navigation.MainNavigationSnapshot
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.quanta
import com.simon.harmonichackernews.ui.settings.PortableAppForeground
import com.simon.harmonichackernews.ui.stories.StoriesComposeController
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun BoxScope.IosAppForeground(
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
        appIcon = painterResource(Res.drawable.quanta),
        captchaMessage = "The CAPTCHA handoff is not yet connected to the iOS in-app " +
            "browser. Open Hacker News in Safari to complete the challenge.",
    )
}
