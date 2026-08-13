package com.simon.harmonichackernews.ui.navigation

import android.content.Intent
import android.os.Bundle
import com.simon.harmonichackernews.CommentsContract
import com.simon.harmonichackernews.data.toEditorDestination
import com.simon.harmonichackernews.data.toStoryDestinationOrNull
import com.simon.harmonichackernews.navigation.AppLaunchRequest
import com.simon.harmonichackernews.navigation.AppLaunchResult
import com.simon.harmonichackernews.navigation.AppLaunchRouter
import com.simon.harmonichackernews.navigation.AppDestinationCodec
import com.simon.harmonichackernews.navigation.EditorDestination
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.navigation.SubmissionsDestination
import com.simon.harmonichackernews.ui.debug.CoulombGasContract
import com.simon.harmonichackernews.ui.editor.ComposeEditorContract
import com.simon.harmonichackernews.ui.settings.SettingsIntents
import com.simon.harmonichackernews.ui.submissions.SubmissionsContract

/** Android decoder for the platform-neutral application launch router. */
internal class MainLaunchIntentRouter(
    private val navigation: MainNavigationController,
) {
    private val launches = AppLaunchRouter(navigation.navigationState)

    fun route(intent: Intent?): Boolean = when (
        val result = launches.route(intent?.toLaunchRequest() ?: AppLaunchRequest.Unknown)
    ) {
        AppLaunchResult.Routed -> true
        AppLaunchResult.Ignored -> false
        is AppLaunchResult.Invalid -> {
            navigation.showMessage(result.message)
            false
        }
    }

    private fun Intent.toLaunchRequest(): AppLaunchRequest = when {
        action == SettingsIntents.ACTION_OPEN_SETTINGS -> AppLaunchRequest.Settings(
            getStringExtra(SettingsIntents.EXTRA_SETTINGS_SECTION),
        )
        action == ComposeEditorContract.ACTION_OPEN_EDITOR -> AppLaunchRequest.Editor(
            decodedDestination<EditorDestination>()
                ?: (extras?.let(::Bundle) ?: Bundle()).toEditorDestination(),
        )
        action == SubmissionsContract.ACTION_OPEN_SUBMISSIONS -> AppLaunchRequest.Submissions(
            decodedDestination<SubmissionsDestination>()?.userName
                ?: getStringExtra(SubmissionsContract.EXTRA_USER),
        )
        action == CoulombGasContract.ACTION_OPEN -> AppLaunchRequest.CoulombGas
        Intent.ACTION_VIEW.equals(action, ignoreCase = true) ->
            AppLaunchRequest.ViewUrl(data?.toString())
        Intent.ACTION_SEND.equals(action, ignoreCase = true) -> AppLaunchRequest.SharedText(
            getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
        )
        else -> AppLaunchRequest.Story(directStoryDestination())
    }

    private fun Intent.directStoryDestination() =
        (extras?.let(::Bundle) ?: Bundle()).let { arguments ->
            decodedDestination<StoryDestination>() ?: if (
                arguments.getInt(CommentsContract.EXTRA_ID, -1) <= 0
            ) null
            else arguments.toStoryDestinationOrNull()
        }

    private inline fun <reified T> Intent.decodedDestination(): T? =
        AppDestinationCodec.decode(getStringExtra(AppDestinationCodec.ANDROID_PAYLOAD_EXTRA)) as? T
}
