package com.simon.harmonichackernews

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.ui.navigation.MainLaunchIntentRouter
import com.simon.harmonichackernews.ui.navigation.MainNavigationController
import com.simon.harmonichackernews.ui.navigation.MainNavigationHost.install
import com.simon.harmonichackernews.settings.CommentNavigationPreferences
import com.simon.harmonichackernews.utils.ThemeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        if (consumeCommentsBenchmarkIntent(intent)) {
            // Benchmark actions are one-shot too. Clearing the current intent keeps activity
            // recreation from reopening the fixture and contaminating the next sample.
            setIntent(Intent(this, MainActivity::class.java))
            return
        }
        if (launchIntentRouter.route(intent)) {
            // Navigation now owns the destination. Keeping the one-shot action as the Activity's
            // current intent would apply it again during a configuration-driven recreation.
            setIntent(Intent(this, MainActivity::class.java))
        }
    }

    /**
     * Deterministic Macrobenchmark entry point. The route is inert in every distributable build;
     * the benchmark-only package reads its bundled fixture, writes it through the production cache,
     * then opens the normal Comments destination so parsing and rendering remain representative.
     */
    private fun consumeCommentsBenchmarkIntent(intent: Intent?): Boolean {
        if (BuildConfig.APPLICATION_ID != COMMENTS_BENCHMARK_APPLICATION_ID) return false
        return when (intent?.action) {
            ACTION_BENCHMARK_SEED_COMMENTS -> {
                lifecycleScope.launch {
                    val payload = withContext(Dispatchers.IO) {
                        assets.open(COMMENTS_BENCHMARK_ASSET).bufferedReader().use { it.readText() }
                    }
                    check(harmonicAppComposition.storyCache.storeStory(COMMENTS_BENCHMARK_ID, payload)) {
                        "Could not seed the deterministic Comments benchmark fixture"
                    }
                    navigationController.openStory(StoryDestination(COMMENTS_BENCHMARK_ID))
                }
                true
            }
            ACTION_BENCHMARK_OPEN_COMMENTS -> {
                navigationController.openStory(StoryDestination(COMMENTS_BENCHMARK_ID))
                true
            }
            else -> false
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
            if (coordinator?.canNavigateCommentsWithVolumeButtons() == true) {
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
        ThemeUtils.setupTheme(this)
        harmonicAppComposition.appearance.refreshSelection()
        navigationController.onConfigurationChanged(newConfig)
    }

    private companion object {
        const val COMMENTS_BENCHMARK_APPLICATION_ID =
            "com.simon.harmonichackernews.compose.benchmark"
        const val ACTION_BENCHMARK_SEED_COMMENTS =
            "com.simon.harmonichackernews.action.BENCHMARK_SEED_COMMENTS"
        const val ACTION_BENCHMARK_OPEN_COMMENTS =
            "com.simon.harmonichackernews.action.BENCHMARK_OPEN_COMMENTS"
        const val COMMENTS_BENCHMARK_ASSET = "comments_benchmark_fixture.json"
        const val COMMENTS_BENCHMARK_ID = 990000001
    }
}
