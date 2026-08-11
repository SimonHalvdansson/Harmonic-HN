package com.simon.harmonichackernews.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.simon.harmonichackernews.network.HackerNewsUserService
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.platform.AndroidCredentialStore
import com.simon.harmonichackernews.platform.AndroidExternalLinkLauncher
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.login_dialog_success
import com.simon.harmonichackernews.utils.AccountUtils
import com.simon.harmonichackernews.utils.Utils
import org.jetbrains.compose.resources.stringResource

private const val HackerNewsLoginUrl = "https://news.ycombinator.com/login"

@Composable
fun LoginDialog(
    onDismiss: () -> Unit,
    onAccountStateChanged: () -> Unit,
) {
    val context = LocalContext.current
    val loginSuccess = stringResource(Res.string.login_dialog_success)
    val hackerNewsUserService = remember(context) {
        HackerNewsUserService(NetworkComponent.hackerNewsSession, AndroidCredentialStore(context))
    }
    SharedLoginDialog(
        onDismiss = onDismiss,
        attemptLogin = { username, password ->
            AccountUtils.setAccountDetails(context, username, password)
            hackerNewsUserService.login()
        },
        continueLogin = hackerNewsUserService::continueLoginWithCaptcha,
        onLoginSucceeded = {
            Utils.toast(loginSuccess, context)
            onAccountStateChanged()
            onDismiss()
        },
        onLoginFailed = { AccountUtils.deleteAccountDetails(context) },
        onCreateAccount = { AndroidExternalLinkLauncher.openExternalBrowser(context, HackerNewsLoginUrl) },
        captchaDialog = { challenge, dismiss, response ->
            CaptchaDialog(
                challenge = challenge,
                onDismiss = dismiss,
                onCaptchaResponse = response,
            )
        },
    )
}
