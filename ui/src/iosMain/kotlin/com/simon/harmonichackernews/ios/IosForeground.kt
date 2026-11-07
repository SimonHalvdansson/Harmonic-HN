package com.simon.harmonichackernews.ios

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.app.HarmonicSceneComposition
import com.simon.harmonichackernews.navigation.MainNavigationSnapshot
import com.simon.harmonichackernews.ui.settings.PortableAppForeground
import com.simon.harmonichackernews.ui.stories.StoriesScreenController

@Composable
internal fun BoxScope.IosAppForeground(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    navigation: MainNavigationSnapshot,
    storiesController: StoriesScreenController?,
) {
    PortableAppForeground(
        app = app,
        scene = scene,
        navigation = navigation,
        storiesController = storiesController,
        showAccountCreation = false,
        captchaDialog = { challenge, cancel, respond ->
            IosCaptchaDialog(challenge, cancel, respond)
        },
    )
}
