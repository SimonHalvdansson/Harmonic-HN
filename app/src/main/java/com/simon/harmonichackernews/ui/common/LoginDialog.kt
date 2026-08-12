package com.simon.harmonichackernews.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.simon.harmonichackernews.AndroidAppComposition
import com.simon.harmonichackernews.platform.AndroidExternalLinkLauncher
import com.simon.harmonichackernews.platform.HackerNewsAccount
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.login_dialog_success
import com.simon.harmonichackernews.utils.Utils
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private const val HackerNewsLoginUrl = "https://news.ycombinator.com/login"

@Composable
fun LoginDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val loginSuccess = stringResource(Res.string.login_dialog_success)
    val appComposition = remember(context) { AndroidAppComposition.get(context) }
    val accounts = appComposition.platform.accounts
    val hackerNewsUserService = appComposition.hackerNewsUser
    SharedLoginDialog(
        onDismiss = onDismiss,
        attemptLogin = { username, password ->
            accounts.saveAccount(HackerNewsAccount(username, password))
            hackerNewsUserService.login()
        },
        continueLogin = hackerNewsUserService::continueLoginWithCaptcha,
        onLoginSucceeded = {
            Utils.toast(loginSuccess, context)
            onDismiss()
        },
        onLoginFailed = { coroutineScope.launch { accounts.clearAccount() } },
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
