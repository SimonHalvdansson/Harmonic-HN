package com.simon.harmonichackernews.widget

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import com.simon.harmonichackernews.ui.widget.LocalWidgetTextStyle
import com.simon.harmonichackernews.StoryTypeMenuPolicy
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.harmonicAppComposition
import com.simon.harmonichackernews.network.WidgetConfiguration
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.widget.WidgetConfigScreen
import com.simon.harmonichackernews.ui.HarmonicUiDependencies
import com.simon.harmonichackernews.ui.ProvideHarmonicUiDependencies

/** Android widget-result host for the portable configuration screen. */
object WidgetConfigComposeHost {
    fun interface Listener {
        fun onConfirm(configuration: WidgetConfiguration)
    }

    @JvmStatic
    fun install(
        activity: ComponentActivity,
        initialConfiguration: WidgetConfiguration,
        listener: Listener,
    ) {
        val composeView = ComposeView(activity).apply {
            id = R.id.widget_config_compose
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val app = activity.harmonicAppComposition
                val scene = remember { app.createScene() }
                DisposableEffect(scene) { onDispose { scene.close() } }
                val dependencies = remember(scene) { HarmonicUiDependencies(app, scene) }
                val headlineFamilyName = remember { widgetHeadlineFontFamily(activity) }
                val headlineFamily = remember(headlineFamilyName) {
                    headlineFamilyName?.let(::widgetPreviewFontFamily)
                }
                val appearance = app.appearance
                val selection by appearance.selections.collectAsState(initial = appearance.selection())
                CompositionLocalProvider(LocalWidgetTextStyle provides TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true))) {
                    ProvideHarmonicUiDependencies(dependencies) {
                        HarmonicTheme(selection = selection) {
                            val story = app.userSettings.story
                            WidgetConfigScreen(
                                initialConfiguration = initialConfiguration,
                                frontpages = StoryTypeMenuPolicy.frontpages(story.additionalFrontpages, story.frontpageOrder),
                                onConfirm = listener::onConfirm,
                                onBack = activity::finish,
                                paletteTintConfigKey = story.paletteTintConfigKey,
                                headlineFontFamily = headlineFamily,
                                headlineFontLabel = headlineFamilyName?.let(::widgetFontDisplayName).orEmpty(),
                            )
                        }
                    }
                }
            }
        }
        activity.setContentView(composeView)
    }
}
