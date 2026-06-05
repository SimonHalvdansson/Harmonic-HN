package com.simon.harmonichackernews;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.simon.harmonichackernews.databinding.CaptchaDialogBinding;
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
        CaptchaDialogBinding binding = CaptchaDialogBinding.inflate(requireActivity().getLayoutInflater());
        builder.setView(binding.getRoot());
        hideKeyboard();

        webView = binding.captchaDialogWebview;
        ProgressBar progressBar = binding.captchaDialogProgress;

        if (challenge == null) {
            binding.captchaDialogError.setText("Captcha challenge could not be loaded. Please try again.");
            binding.captchaDialogError.setVisibility(View.VISIBLE);
            binding.captchaDialogContinue.setEnabled(false);
        } else {
            configureWebView(progressBar);
            webView.loadDataWithBaseURL(HN_BASE_URL, challenge.getCaptchaHtml(), "text/html", "UTF-8", null);
        }

        binding.captchaDialogCancel.setOnClickListener(view -> dismiss());
        binding.captchaDialogContinue.setOnClickListener(view -> {
            WebView currentWebView = webView;
            if (currentWebView == null) {
                return;
            }
            binding.captchaDialogError.setVisibility(View.GONE);
            currentWebView.evaluateJavascript(GET_CAPTCHA_RESPONSE_JS, new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    if (webView != currentWebView || !isAdded()) {
                        return;
                    }
                    String captchaResponse = decodeJavascriptString(value);
                    if (TextUtils.isEmpty(captchaResponse)) {
                        binding.captchaDialogError.setText("Complete the captcha before continuing.");
                        binding.captchaDialogError.setVisibility(View.VISIBLE);
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
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (view != webView) {
                    return;
                }
                progressBar.setVisibility(View.GONE);
                view.setVisibility(View.VISIBLE);
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
            webView.setWebViewClient(null);
            webView.stopLoading();
            if (webView.getParent() instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) webView.getParent()).removeView(webView);
            }
            webView.removeAllViews();
            webView.destroyDrawingCache();
            webView.destroy();
            webView = null;
        }
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        listener = null;
        challenge = null;
        super.onDestroy();
    }

    public interface Listener {
        void onCaptchaResponse(UserActions.CaptchaChallenge challenge, String captchaResponse);

        void onCaptchaCancelled();
    }
}
