package com.simon.harmonichackernews.widget

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.network.WidgetConfiguration
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.widget.SharedWidgetConfigScreen

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
                HarmonicTheme {
                    SharedWidgetConfigScreen(initialConfiguration, listener::onConfirm)
                }
            }
        }
        activity.setContentView(composeView)
    }
}
