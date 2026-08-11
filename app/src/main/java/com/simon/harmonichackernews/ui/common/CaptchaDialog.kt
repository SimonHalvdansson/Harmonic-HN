package com.simon.harmonichackernews.ui.common

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.dimensionResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge
import com.simon.harmonichackernews.ui.settings.SettingsAlertDialog
import com.simon.harmonichackernews.ui.settings.SettingsDialogTextButton
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.serialization.JsonStringCodec

private const val HackerNewsBaseUrl = "https://news.ycombinator.com/"
private const val CaptchaResponseScript =
    "(function(){var el=document.getElementById('g-recaptcha-response');return el?el.value:'';})()"

/**
 * Compose CAPTCHA dialog. The embedded browser is the only intentionally retained View because
 * Android does not provide a native Compose WebView.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CaptchaDialog(
    challenge: HackerNewsCaptchaChallenge,
    onDismiss: () -> Unit,
    onCaptchaResponse: (String) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
    }

    var loading by remember(challenge) { mutableStateOf(true) }
    var error by remember(challenge) { mutableStateOf<String?>(null) }
    val webDescription = stringResource(Res.string.captcha_dialog_web_content_description)
    val incompleteError = stringResource(Res.string.captcha_dialog_complete_error)
    val webView = remember(challenge) {
        WebView(context).apply webView@ {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(this@webView, true)
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    loading = false
                }
            }
            loadDataWithBaseURL(
                HackerNewsBaseUrl,
                challenge.captchaHtml,
                "text/html",
                "UTF-8",
                null,
            )
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.webViewClient = WebViewClient()
            webView.stopLoading()
            webView.removeAllViews()
            webView.destroy()
        }
    }

    SharedCaptchaDialogLayout(
        loading = loading,
        error = error,
        onDismiss = onDismiss,
        onContinue = {
            error = null
            webView.evaluateJavascript(CaptchaResponseScript) { value ->
                val response = decodeJavascriptString(value)
                if (response.isEmpty()) {
                    error = incompleteError
                } else {
                    onCaptchaResponse(response)
                }
            }
        },
        webContent = {
            AndroidView(
                factory = { webView },
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (loading) 0f else 1f)
                    .semantics { contentDescription = webDescription },
            )
        },
    )
}

private fun decodeJavascriptString(value: String?): String {
    return JsonStringCodec.decodeJavascriptString(value).orEmpty()
}

@Preview(showBackground = true)
@Composable
private fun CaptchaDialogPreview() {
    HarmonicTheme {
        SharedCaptchaDialogLayout(
            loading = false,
            error = null,
            onDismiss = {},
            onContinue = {},
            webContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("CAPTCHA")
                }
            },
        )
    }
}
