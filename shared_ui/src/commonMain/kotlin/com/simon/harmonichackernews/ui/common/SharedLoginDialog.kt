package com.simon.harmonichackernews.ui.common

import com.simon.harmonichackernews.resources.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.network.HackerNewsActionResult
import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge
import com.simon.harmonichackernews.ui.settings.SettingsAlertDialog
import com.simon.harmonichackernews.ui.settings.SettingsDialogTextButton
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SharedLoginDialog(
    onDismiss: () -> Unit,
    attemptLogin: suspend (username: String, password: String) -> HackerNewsActionResult,
    continueLogin: suspend (
        challenge: HackerNewsCaptchaChallenge,
        response: String,
    ) -> HackerNewsActionResult,
    onLoginSucceeded: () -> Unit,
    onLoginFailed: () -> Unit,
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
    var error by remember { mutableStateOf<String?>(null) }
    var captchaChallenge by remember { mutableStateOf<HackerNewsCaptchaChallenge?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val loginFailure = stringResource(Res.string.login_dialog_failure)
    val captchaCancelled = stringResource(Res.string.login_dialog_captcha_cancelled)
    val credentialsValid = username.isNotBlank() && password.isNotEmpty()

    fun failLogin(message: String = loginFailure) {
        onLoginFailed()
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
                captchaChallenge = result.challenge
            }
        }
    }

    fun submitLogin() {
        if (!credentialsValid || loading) return
        error = null
        loading = true
        coroutineScope.launch {
            handleLoginResult(attemptLogin(username.trim(), password))
        }
    }

    SettingsAlertDialog(
        onDismissRequest = {
            if (!loading && captchaChallenge == null) onDismiss()
        },
        title = {
            Text(
                text = stringResource(Res.string.login_dialog_title),
                color = HarmonicTheme.colors.textPrimary,
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 27.sp,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = HarmonicDimens.login_dialog_section_spacing),
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
                    onValueChange = { password = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = HarmonicDimens.login_dialog_field_spacing),
                    enabled = !loading,
                    label = { Text(stringResource(Res.string.login_dialog_password)) },
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                painter = painterResource(
                                    if (passwordVisible) {
                                        Res.drawable.ic_visibility_off
                                    } else {
                                        Res.drawable.ic_visibility
                                    },
                                ),
                                contentDescription = null,
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
                    color = HarmonicTheme.colors.textPrimary,
                    fontFamily = ProductSansFontFamily,
                    fontSize = 13.sp,
                )
                AnimatedVisibility(visible = !showInformation) {
                    OutlinedButton(onClick = { showInformation = true }) {
                        Icon(painterResource(Res.drawable.ic_info), contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(Res.string.login_dialog_how_it_works),
                            lineHeight = 16.sp,
                        )
                    }
                }
                AnimatedVisibility(visible = showInformation) {
                    Column {
                        Text(
                            text = stringResource(Res.string.login_dialog_information),
                            modifier = Modifier.padding(top = HarmonicDimens.login_dialog_small_spacing),
                            color = HarmonicTheme.colors.textPrimary,
                            fontFamily = ProductSansFontFamily,
                            fontSize = 13.sp,
                        )
                        Text(
                            text = stringResource(Res.string.login_dialog_troubleshooting),
                            modifier = Modifier.padding(top = HarmonicDimens.login_dialog_info_spacing),
                            color = HarmonicTheme.colors.textPrimary,
                            fontFamily = ProductSansFontFamily,
                            fontSize = 13.sp,
                        )
                    }
                }
                Text(
                    text = stringResource(Res.string.login_dialog_create_account_explanation),
                    modifier = Modifier.padding(
                        top = HarmonicDimens.login_dialog_info_spacing,
                        bottom = HarmonicDimens.login_dialog_small_spacing,
                    ),
                    color = HarmonicTheme.colors.textPrimary,
                    fontFamily = ProductSansFontFamily,
                    fontSize = 13.sp,
                )
                OutlinedButton(onClick = onCreateAccount) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_open_in_browser),
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.login_dialog_create_account))
                }
                AnimatedVisibility(visible = loading) {
                    Row(
                        modifier = Modifier.padding(top = HarmonicDimens.login_dialog_section_spacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HarmonicLoadingIndicator(
                            modifier = Modifier.size(HarmonicDimens.login_dialog_loading_indicator_size),
                        )
                        Spacer(Modifier.width(HarmonicDimens.cache_stories_value_start_spacing))
                        Text(
                            text = stringResource(Res.string.login_dialog_loading),
                            color = HarmonicTheme.colors.textPrimary,
                            fontFamily = ProductSansFontFamily,
                            fontSize = 14.sp,
                        )
                    }
                }
                error?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.padding(top = HarmonicDimens.login_dialog_section_spacing),
                        color = MaterialTheme.colorScheme.error,
                        fontFamily = ProductSansFontFamily,
                        fontSize = 14.sp,
                    )
                }
            }
        },
        scrollableContent = true,
        dismissButton = {
            SettingsDialogTextButton(onClick = onDismiss, enabled = !loading) {
                Text(stringResource(Res.string.common_cancel))
            }
        },
        confirmButton = {
            SettingsDialogTextButton(
                onClick = { submitLogin() },
                enabled = credentialsValid && !loading,
            ) {
                Text(stringResource(Res.string.login_dialog_action))
            }
        },
    )

    captchaChallenge?.let { challenge ->
        captchaDialog(
            challenge,
            {
                captchaChallenge = null
                failLogin(captchaCancelled)
            },
            { response ->
                captchaChallenge = null
                loading = true
                coroutineScope.launch {
                    handleLoginResult(continueLogin(challenge, response))
                }
            },
        )
    }
}

@Composable
fun SharedCaptchaDialogLayout(
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
    webContent: @Composable () -> Unit,
) {
    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.captcha_dialog_title),
                color = HarmonicTheme.colors.textPrimary,
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HarmonicDimens.captcha_dialog_content_height),
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
                    )
                }
            }
        },
        dismissButton = {
            SettingsDialogTextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.common_cancel))
            }
        },
        confirmButton = {
            SettingsDialogTextButton(onClick = onContinue) {
                Text(stringResource(Res.string.captcha_dialog_continue))
            }
        },
    )
}
