package com.simon.harmonichackernews

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.simon.harmonichackernews.ui.navigation.MainLaunchIntentRouter
import com.simon.harmonichackernews.ui.navigation.MainNavigationController
import com.simon.harmonichackernews.ui.navigation.MainNavigationHost.install
import com.simon.harmonichackernews.settings.CommentNavigationPreferences
import com.simon.harmonichackernews.utils.ThemeUtils

class MainActivity : BaseActivity() {
    internal lateinit var navigationController: MainNavigationController
        private set
    private lateinit var launchIntentRouter: MainLaunchIntentRouter

    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeUtils.setupTheme(this)

        navigationController = install(this, savedInstanceState)
        launchIntentRouter = MainLaunchIntentRouter(navigationController)
        // A singleTask can be recreated with saved navigation state while also receiving a new
        // deep link or feature intent. Always apply that launch intent after restoring state so
        // the newly requested destination wins.
        consumeLaunchIntent(getIntent())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeLaunchIntent(intent)
    }

    private fun consumeLaunchIntent(intent: Intent?) {
        if (launchIntentRouter.route(intent)) {
            // Navigation now owns the destination. Keeping the one-shot action as the Activity's
            // current intent would apply it again during a configuration-driven recreation.
            setIntent(Intent(this, MainActivity::class.java))
        }
    }

    protected override fun onSaveInstanceState(outState: Bundle) {
        navigationController.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    protected override fun onDestroy() {
        navigationController.onDestroy()
        super.onDestroy()
    }

    protected override fun onStart() {
        super.onStart()
        navigationController.onStart()
    }

    protected override fun onResume() {
        super.onResume()
        navigationController.onResume()
    }

    protected override fun onStop() {
        navigationController.onStop()
        super.onStop()
    }

    public override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val volumeNavigationMode = harmonicAppComposition
            .userSettings.comments.volumeNavigationMode
        if (CommentNavigationPreferences.DISABLED != volumeNavigationMode) {
            val topLevelOnly =
                CommentNavigationPreferences.TOP_LEVEL == volumeNavigationMode
            val coordinator = navigationController.getCommentsCoordinator()
            if (coordinator != null && coordinator.isAdded && coordinator.isBottomSheetFullyExpanded) {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    coordinator.navigateToNextComment(topLevelOnly, true)
                    return true
                } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    coordinator.navigateToPreviousComment(topLevelOnly, true)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    internal fun restartAfterSettingsChange() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            recreate()
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(launchIntent)
    }

    internal fun setImmersiveContentEnabled(enabled: Boolean) {
        val insetsController = WindowCompat.getInsetsController(
            window,
            window.decorView
        )
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    public override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        navigationController.onConfigurationChanged(newConfig)
    }
}
