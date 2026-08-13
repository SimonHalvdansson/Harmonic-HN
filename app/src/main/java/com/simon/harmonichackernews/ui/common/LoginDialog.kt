package com.simon.harmonichackernews.ui.common

import androidx.compose.runtime.Composable
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.login_dialog_success
import org.jetbrains.compose.resources.stringResource

private const val HackerNewsLoginUrl = "https://news.ycombinator.com/login"

@Composable
fun LoginDialog(
    onDismiss: () -> Unit,
) {
    val loginSuccess = stringResource(Res.string.login_dialog_success)
    val appComposition = LocalHarmonicUiDependencies.current
    SharedLoginDialog(
        onDismiss = onDismiss,
        workflow = appComposition.login,
        onLoginSucceeded = {
            appComposition.userMessages.show(loginSuccess)
            onDismiss()
        },
        onCreateAccount = {
            appComposition.links.open(HackerNewsLoginUrl, preferInApp = false)
        },
        captchaDialog = { challenge, dismiss, response ->
            CaptchaDialog(
                challenge = challenge,
                onDismiss = dismiss,
                onCaptchaResponse = response,
            )
        },
    )
}
