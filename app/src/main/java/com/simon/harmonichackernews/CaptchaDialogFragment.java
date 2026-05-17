package com.simon.harmonichackernews;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.simon.harmonichackernews.network.UserActions;

import org.json.JSONArray;
import org.json.JSONException;

public class CaptchaDialogFragment extends AppCompatDialogFragment {
    public static final String TAG = "tag_captcha_dialog";
    private static final String HN_BASE_URL = "https://news.ycombinator.com/";
    private static final String GET_CAPTCHA_RESPONSE_JS =
            "(function(){var el=document.getElementById('g-recaptcha-response');return el?el.value:'';})()";

    private UserActions.CaptchaChallenge challenge;
    private Listener listener;
    private WebView webView;
    private boolean submitted;
    private boolean cancelNotified;

    public CaptchaDialogFragment() {
    }

    private CaptchaDialogFragment(UserActions.CaptchaChallenge challenge, Listener listener) {
        this.challenge = challenge;
        this.listener = listener;
    }

    public static void show(FragmentManager fragmentManager,
                            UserActions.CaptchaChallenge challenge,
                            Listener listener) {
        new CaptchaDialogFragment(challenge, listener).show(fragmentManager, TAG);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View rootView = inflater.inflate(R.layout.captcha_dialog, null);
        builder.setView(rootView);
        hideKeyboard();

        webView = rootView.findViewById(R.id.captcha_dialog_webview);
        ProgressBar progressBar = rootView.findViewById(R.id.captcha_dialog_progress);
        TextView errorText = rootView.findViewById(R.id.captcha_dialog_error);
        Button cancelButton = rootView.findViewById(R.id.captcha_dialog_cancel);
        Button continueButton = rootView.findViewById(R.id.captcha_dialog_continue);

        if (challenge == null) {
            errorText.setText("Captcha challenge could not be loaded. Please try again.");
            errorText.setVisibility(View.VISIBLE);
            continueButton.setEnabled(false);
        } else {
            configureWebView(progressBar);
            webView.loadDataWithBaseURL(HN_BASE_URL, challenge.getCaptchaHtml(), "text/html", "UTF-8", null);
        }

        cancelButton.setOnClickListener(view -> dismiss());
        continueButton.setOnClickListener(view -> {
            errorText.setVisibility(View.GONE);
            webView.evaluateJavascript(GET_CAPTCHA_RESPONSE_JS, new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    String captchaResponse = decodeJavascriptString(value);
                    if (TextUtils.isEmpty(captchaResponse)) {
                        errorText.setText("Complete the captcha before continuing.");
                        errorText.setVisibility(View.VISIBLE);
                        return;
                    }

                    submitted = true;
                    dismiss();
                    if (listener != null) {
                        listener.onCaptchaResponse(challenge, captchaResponse);
                    }
                }
            });
        });

        return builder.create();
    }

    private void configureWebView(ProgressBar progressBar) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
            }
        });
    }

    private void hideKeyboard() {
        View currentFocus = requireActivity().getCurrentFocus();
        if (currentFocus == null) {
            return;
        }

        currentFocus.clearFocus();
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
        }
    }

    private String decodeJavascriptString(String value) {
        if (TextUtils.isEmpty(value) || "null".equals(value)) {
            return "";
        }

        try {
            return new JSONArray("[" + value + "]").optString(0, "");
        } catch (JSONException e) {
            return "";
        }
    }

    private void notifyCancelledIfNeeded() {
        if (!submitted && !cancelNotified && listener != null) {
            cancelNotified = true;
            listener.onCaptchaCancelled();
        }
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        notifyCancelledIfNeeded();
        super.onDismiss(dialog);
    }

    @Override
    public void onDestroyView() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroyView();
    }

    public interface Listener {
        void onCaptchaResponse(UserActions.CaptchaChallenge challenge, String captchaResponse);

        void onCaptchaCancelled();
    }
}
