package com.simon.harmonichackernews.ui.common

import com.simon.harmonichackernews.resources.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.network.HackerNewsActionResult
import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge
import com.simon.harmonichackernews.network.HackerNewsCaptchaFormField
import com.simon.harmonichackernews.presentation.LoginWorkflow
import com.simon.harmonichackernews.ui.settings.SettingsAlertDialog
import com.simon.harmonichackernews.ui.settings.SettingsDialogTextButton
import com.simon.harmonichackernews.ui.settings.SettingsDialogDismissState
import com.simon.harmonichackernews.ui.settings.CompleteSettingsDialogDismissal
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private sealed interface LoginStatus {
    data object Idle : LoginStatus
    data object Loading : LoginStatus
    data class Error(val message: String) : LoginStatus
}

private const val LOGIN_STATUS_FADE_IN_MILLIS = 180
private const val LOGIN_STATUS_FADE_OUT_MILLIS = 120
private const val LOGIN_STATUS_RESIZE_MILLIS = 220

// Kept only with the dialog's saved UI state, alongside its existing username/password draft.
// Native CAPTCHA views may reload after recreation; the pending operation must remain available.
internal val LoginCaptchaChallengeSaver = Saver<HackerNewsCaptchaChallenge?, Any>(
    save = { challenge -> challenge?.let(::loginCaptchaSavedValues) },
    restore = ::restoreLoginCaptcha,
)

private fun loginCaptchaSavedValues(challenge: HackerNewsCaptchaChallenge): ArrayList<Any>? {
    if (challenge.actionUrl.length > 8_192 || challenge.siteKey.length > 2_048 ||
        challenge.formFields.size > 64 ||
        challenge.formFields.any { it.name.length > 1_024 || it.value.length > 16_384 } ||
        challenge.formFields.sumOf { it.name.length + it.value.length } > 131_072
    ) return null
    return arrayListOf<Any>(challenge.actionUrl, challenge.siteKey, challenge.useCookies).apply {
        challenge.formFields.forEach { add(it.name); add(it.value) }
    }
}

private fun restoreLoginCaptcha(saved: Any): HackerNewsCaptchaChallenge? {
    val values = saved as? List<*> ?: return null
    if (values.size !in 3..131 || (values.size - 3) % 2 != 0) return null
    val action = values[0] as? String ?: return null
    val siteKey = values[1] as? String ?: return null
    val cookies = values[2] as? Boolean ?: return null
    val fields = (3 until values.size step 2).map { index ->
        HackerNewsCaptchaFormField(
            values[index] as? String ?: return null,
            values[index + 1] as? String ?: return null,
        )
    }
    return HackerNewsCaptchaChallenge(action, siteKey, fields, cookies)
        .takeIf { loginCaptchaSavedValues(it) != null }
}

@Composable
fun LoginDialog(
    onDismiss: () -> Unit,
    workflow: LoginWorkflow,
    onLoginSucceeded: () -> Unit,
    onCreateAccount: () -> Unit,
    captchaDialog: @Composable (
        challenge: HackerNewsCaptchaChallenge,
        onDismiss: () -> Unit,
        onResponse: (String) -> Unit,
    ) -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var showInformation by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var captchaChallenge by rememberSaveable(stateSaver = LoginCaptchaChallengeSaver) {
        mutableStateOf<HackerNewsCaptchaChallenge?>(null)
    }
    var captchaStage by rememberSaveable { mutableIntStateOf(0) }
    val keyboardAction = remember { SettingsDialogDismissState() }
    val coroutineScope = rememberCoroutineScope()
    val loginFailure = stringResource(Res.string.login_dialog_failure)
    val captchaCancelled = stringResource(Res.string.login_dialog_captcha_cancelled)
    val credentialsValid = username.isNotBlank() && password.isNotEmpty()
    val canInteract = !loading && !keyboardAction.isClosing && captchaChallenge == null && !showInformation

    fun failLogin(message: String = loginFailure) {
        loading = false
        error = message
    }

    fun handleLoginResult(result: HackerNewsActionResult) {
        when (result) {
            is HackerNewsActionResult.Success -> {
                loading = false
                onLoginSucceeded()
            }
            is HackerNewsActionResult.Failure -> failLogin()
            is HackerNewsActionResult.Captcha -> {
                loading = false
                captchaStage++
                captchaChallenge = result.challenge
            }
        }
    }

    fun submitLogin() {
        if (!credentialsValid || !canInteract) return
        val enteredUsername = username
        val enteredPassword = password
        keyboardAction.request {
            error = null
            loading = true
            coroutineScope.launch {
                handleLoginResult(workflow.login(enteredUsername, enteredPassword))
            }
        }
    }
    fun dismissLogin() {
        if (canInteract) keyboardAction.request(onDismiss)
    }

    SettingsAlertDialog(
        onDismissRequest = ::dismissLogin,
        modifier = Modifier.testTag("login-dialog"),
        properties = DialogProperties(dismissOnBackPress = canInteract, dismissOnClickOutside = canInteract),
        title = {
            Text(
                text = stringResource(Res.string.login_dialog_title),
                modifier = Modifier.semantics { heading() },
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 27.sp,
            )
        },
        text = {
            PlatformDisableDialogWindowAnimations()
            CompleteSettingsDialogDismissal(keyboardAction)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { if (canInteract) username = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login-username")
                        .padding(top = HarmonicDimens.login_dialog_section_spacing)
                        .semantics { contentType = ContentType.Username },
                    enabled = !loading,
                    label = { Text(stringResource(Res.string.login_dialog_username)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { if (canInteract) password = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login-password")
                        .padding(top = HarmonicDimens.login_dialog_field_spacing)
                        .semantics { contentType = ContentType.Password },
                    enabled = !loading,
                    label = { Text(stringResource(Res.string.login_dialog_password)) },
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(enabled = canInteract, onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                painter = painterResource(
                                    if (passwordVisible) {
                                        Res.drawable.ic_visibility_off
                                    } else {
                                        Res.drawable.ic_visibility
                                    },
                                ),
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            )
                        }
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = { submitLogin() },
                    ),
                    singleLine = true,
                )
                Text(
                    text = stringResource(Res.string.login_dialog_local_information),
                    modifier = Modifier.padding(
                        top = HarmonicDimens.login_dialog_section_spacing,
                        bottom = HarmonicDimens.login_dialog_small_spacing,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = ProductSansFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                )
                OutlinedButton(enabled = canInteract, onClick = {
                    keyboardAction.request { showInformation = true }
                }) {
                    Icon(painterResource(Res.drawable.ic_info), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.login_dialog_how_it_works),
                        lineHeight = 16.sp,
                    )
                }
                Text(
                    text = stringResource(Res.string.login_dialog_create_account_explanation),
                    modifier = Modifier.padding(
                        top = HarmonicDimens.login_dialog_info_spacing,
                        bottom = HarmonicDimens.login_dialog_small_spacing,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = ProductSansFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                )
                OutlinedButton(enabled = canInteract, onClick = { keyboardAction.request(onCreateAccount) }) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_open_in_browser),
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.login_dialog_create_account))
                }
                val status = when {
                    loading -> LoginStatus.Loading
                    error != null -> LoginStatus.Error(error.orEmpty())
                    else -> LoginStatus.Idle
                }
                AnimatedContent(
                    targetState = status,
                    transitionSpec = {
                        val resizeDelay = if (initialState == LoginStatus.Idle) {
                            0
                        } else {
                            LOGIN_STATUS_FADE_OUT_MILLIS
                        }
                        // Reserve the full status area before painting the incoming content.
                        // Keep the old area intact until its outgoing content has faded out.
                        (fadeIn(
                            tween(
                                durationMillis = LOGIN_STATUS_FADE_IN_MILLIS,
                                delayMillis = resizeDelay + LOGIN_STATUS_RESIZE_MILLIS,
                            ),
                        ) togetherWith fadeOut(tween(LOGIN_STATUS_FADE_OUT_MILLIS)))
                            .using(
                                SizeTransform { _, _ ->
                                    tween(
                                        durationMillis = LOGIN_STATUS_RESIZE_MILLIS,
                                        delayMillis = resizeDelay,
                                    )
                                },
                            )
                    },
                ) { currentStatus ->
                    when (currentStatus) {
                        LoginStatus.Idle -> Unit
                        LoginStatus.Loading -> Row(
                            modifier = Modifier.padding(
                                top = HarmonicDimens.login_dialog_section_spacing,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            HarmonicLoadingIndicator(
                                modifier = Modifier.size(
                                    HarmonicDimens.login_dialog_loading_indicator_size,
                                ),
                            )
                            Spacer(
                                Modifier.width(
                                    HarmonicDimens.cache_stories_value_start_spacing,
                                ),
                            )
                            Text(
                                text = stringResource(Res.string.login_dialog_loading),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = ProductSansFontFamily,
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                            )
                        }
                        is LoginStatus.Error -> Text(
                            text = currentStatus.message,
                            modifier = Modifier.padding(
                                top = HarmonicDimens.login_dialog_section_spacing,
                            ),
                            color = MaterialTheme.colorScheme.error,
                            fontFamily = ProductSansFontFamily,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        },
        scrollableContent = true,
        dismissButton = {
            SettingsDialogTextButton(onClick = ::dismissLogin, enabled = canInteract) {
                Text(stringResource(Res.string.common_cancel))
            }
        },
        confirmButton = {
            SettingsDialogTextButton(
                onClick = { submitLogin() },
                enabled = credentialsValid && canInteract,
            ) {
                Text(stringResource(Res.string.login_dialog_action))
            }
        },
        foreground = {
            if (showInformation) {
                SettingsAlertDialog(
                    onDismissRequest = { showInformation = false },
                    text = {
                        Text(
                            text = stringResource(Res.string.login_dialog_information) + "\n\n" +
                                stringResource(Res.string.login_dialog_troubleshooting),
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = ProductSansFontFamily,
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                        )
                    },
                    scrollableContent = true,
                    confirmButton = {
                        SettingsDialogTextButton(onClick = { showInformation = false }) {
                            Text("OK")
                        }
                    },
                )
            }

            captchaChallenge?.let { challenge ->
                val stage = captchaStage
                key(stage) {
                    captchaDialog(
                        challenge,
                        {
                            if (captchaStage == stage && captchaChallenge != null) {
                                captchaChallenge = null
                                loading = true
                                coroutineScope.launch {
                                    workflow.cancelCaptcha(challenge)
                                    failLogin(captchaCancelled)
                                }
                            }
                        },
                        { response ->
                            if (captchaStage == stage && captchaChallenge != null) {
                                captchaChallenge = null
                                loading = true
                                coroutineScope.launch {
                                    handleLoginResult(workflow.continueWithCaptcha(challenge, response))
                                }
                            }
                        },
                    )
                }
            }
        },
    )
}

@Composable
fun CaptchaDialogLayout(
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
    continueEnabled: Boolean = true,
    webContent: @Composable () -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .heightIn(max = HarmonicDimens.captcha_dialog_max_height)
            .fillMaxHeight(),
        title = {
            Text(
                text = stringResource(Res.string.captcha_dialog_title),
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
            )
        },
        text = {
            Column(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    webContent()
                    if (loading) {
                        HarmonicLoadingIndicator(
                            modifier = Modifier.size(
                                HarmonicDimens.captcha_dialog_loading_indicator_size,
                            ),
                        )
                    }
                }
                error?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.padding(top = HarmonicDimens.captcha_dialog_error_top_spacing),
                        color = MaterialTheme.colorScheme.error,
                        fontFamily = ProductSansFontFamily,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        },
        scrollableContent = true,
        neutralButton = onRetry?.let { retry ->
            { SettingsDialogTextButton(onClick = retry) { Text(stringResource(Res.string.ai_model_retry)) } }
        },
        dismissButton = {
            SettingsDialogTextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.common_cancel))
            }
        },
        confirmButton = {
            SettingsDialogTextButton(onClick = onContinue, enabled = continueEnabled) {
                Text(stringResource(Res.string.captcha_dialog_continue))
            }
        },
    )
}
