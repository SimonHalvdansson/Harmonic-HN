package com.simon.harmonichackernews.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.network.UserActions
import com.simon.harmonichackernews.ui.settings.SettingsAlertDialog
import com.simon.harmonichackernews.ui.settings.SettingsDialogTextButton
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.AccountUtils
import com.simon.harmonichackernews.utils.Utils
import okhttp3.Response

private const val HackerNewsLoginUrl = "https://news.ycombinator.com/login"

/** Compose replacement for the XML login dialog and its nested CAPTCHA flow. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoginDialog(
    onDismiss: () -> Unit,
    onAccountStateChanged: () -> Unit,
) {
    val context = LocalContext.current
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var showInformation by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var captchaChallenge by remember { mutableStateOf<UserActions.CaptchaChallenge?>(null) }
    val loginFailure = stringResource(R.string.login_dialog_failure)
    val loginSuccess = stringResource(R.string.login_dialog_success)
    val captchaCancelled = stringResource(R.string.login_dialog_captcha_cancelled)
    val credentialsValid = username.isNotBlank() && password.isNotEmpty()

    fun finishLogin(response: Response) {
        response.close()
        loading = false
        Utils.toast(loginSuccess, context)
        onAccountStateChanged()
        onDismiss()
    }

    fun failLogin(message: String = loginFailure) {
        AccountUtils.deleteAccountDetails(context)
        loading = false
        error = message
    }

    fun continueLogin(challenge: UserActions.CaptchaChallenge, response: String) {
        captchaChallenge = null
        loading = true
        UserActions.continueLoginWithCaptcha(
            context,
            challenge,
            response,
            object : UserActions.ActionCallback {
                override fun onSuccess(response: Response) = finishLogin(response)

                override fun onFailure(summary: String?, response: String?) = failLogin()

                override fun onCaptchaRequired(challenge: UserActions.CaptchaChallenge) {
                    loading = false
                    captchaChallenge = challenge
                }
            },
        )
    }

    fun attemptLogin() {
        if (!credentialsValid || loading) return
        error = null
        loading = true
        AccountUtils.setAccountDetails(context, username.trim(), password)
        UserActions.login(
            context,
            object : UserActions.ActionCallback {
                override fun onSuccess(response: Response) = finishLogin(response)

                override fun onFailure(summary: String?, response: String?) = failLogin()

                override fun onCaptchaRequired(challenge: UserActions.CaptchaChallenge) {
                    loading = false
                    captchaChallenge = challenge
                }
            },
        )
    }

    SettingsAlertDialog(
        onDismissRequest = {
            if (!loading && captchaChallenge == null) onDismiss()
        },
        title = {
            Text(
                text = stringResource(R.string.login_dialog_title),
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
                        .padding(
                            top = dimensionResource(R.dimen.login_dialog_section_spacing),
                        ),
                    enabled = !loading,
                    label = { Text(stringResource(R.string.login_dialog_username)) },
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
                        .padding(
                            top = dimensionResource(R.dimen.login_dialog_field_spacing),
                        ),
                    enabled = !loading,
                    label = { Text(stringResource(R.string.login_dialog_password)) },
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
                                        R.drawable.ic_visibility_off
                                    } else {
                                        R.drawable.ic_visibility
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
                        onDone = { attemptLogin() },
                    ),
                    singleLine = true,
                )
                Text(
                    text = stringResource(R.string.login_dialog_local_information),
                    modifier = Modifier.padding(
                        top = dimensionResource(R.dimen.login_dialog_section_spacing),
                        bottom = dimensionResource(R.dimen.login_dialog_small_spacing),
                    ),
                    color = HarmonicTheme.colors.textPrimary,
                    fontFamily = ProductSansFontFamily,
                    fontSize = 13.sp,
                )
                AnimatedVisibility(visible = !showInformation) {
                    OutlinedButton(onClick = { showInformation = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_info),
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.login_dialog_how_it_works))
                    }
                }
                AnimatedVisibility(visible = showInformation) {
                    Column {
                        Text(
                            text = stringResource(R.string.login_dialog_information),
                            modifier = Modifier.padding(
                                top = dimensionResource(R.dimen.login_dialog_small_spacing),
                            ),
                            color = HarmonicTheme.colors.textPrimary,
                            fontFamily = ProductSansFontFamily,
                            fontSize = 13.sp,
                        )
                        Text(
                            text = stringResource(R.string.login_dialog_troubleshooting),
                            modifier = Modifier.padding(
                                top = dimensionResource(R.dimen.login_dialog_info_spacing),
                            ),
                            color = HarmonicTheme.colors.textPrimary,
                            fontFamily = ProductSansFontFamily,
                            fontSize = 13.sp,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.login_dialog_create_account_explanation),
                    modifier = Modifier.padding(
                        top = dimensionResource(R.dimen.login_dialog_info_spacing),
                        bottom = dimensionResource(R.dimen.login_dialog_small_spacing),
                    ),
                    color = HarmonicTheme.colors.textPrimary,
                    fontFamily = ProductSansFontFamily,
                    fontSize = 13.sp,
                )
                OutlinedButton(
                    onClick = { Utils.launchInExternalBrowser(context, HackerNewsLoginUrl) },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_open_in_browser),
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.login_dialog_create_account))
                }
                AnimatedVisibility(visible = loading) {
                    Row(
                        modifier = Modifier.padding(
                            top = dimensionResource(R.dimen.login_dialog_section_spacing),
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LoadingIndicator(
                            modifier = Modifier.size(
                                dimensionResource(
                                    R.dimen.login_dialog_loading_indicator_size,
                                ),
                            ),
                        )
                        Spacer(
                            Modifier.width(
                                dimensionResource(R.dimen.cache_stories_value_start_spacing),
                            ),
                        )
                        Text(
                            text = stringResource(R.string.login_dialog_loading),
                            color = HarmonicTheme.colors.textPrimary,
                            fontFamily = ProductSansFontFamily,
                            fontSize = 14.sp,
                        )
                    }
                }
                error?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.padding(
                            top = dimensionResource(R.dimen.login_dialog_section_spacing),
                        ),
                        color = MaterialTheme.colorScheme.error,
                        fontFamily = ProductSansFontFamily,
                        fontSize = 14.sp,
                    )
                }
            }
        },
        scrollableContent = true,
        dismissButton = {
            SettingsDialogTextButton(
                onClick = onDismiss,
                enabled = !loading,
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        confirmButton = {
            SettingsDialogTextButton(
                onClick = { attemptLogin() },
                enabled = credentialsValid && !loading,
            ) {
                Text(stringResource(R.string.login_dialog_action))
            }
        },
    )

    captchaChallenge?.let { challenge ->
        CaptchaDialog(
            challenge = challenge,
            onDismiss = {
                captchaChallenge = null
                failLogin(captchaCancelled)
            },
            onCaptchaResponse = { response ->
                continueLogin(challenge, response)
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginDialogPreview() {
    HarmonicTheme {
        LoginDialog(
            onDismiss = {},
            onAccountStateChanged = {},
        )
    }
}
