package com.simon.harmonichackernews;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.simon.harmonichackernews.databinding.LoginDialogBinding;
import com.google.android.material.textfield.TextInputEditText;
import com.simon.harmonichackernews.network.UserActions;
import com.simon.harmonichackernews.utils.AccountUtils;
import com.simon.harmonichackernews.utils.Utils;
import com.simon.harmonichackernews.utils.ViewUtils;

import okhttp3.Response;

public class LoginDialogFragment extends AppCompatDialogFragment {

    public static String TAG = "tag_login_dialog";
    private static final String HACKER_NEWS_LOGIN_URL = "https://news.ycombinator.com/login";

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());
        LoginDialogBinding binding = LoginDialogBinding.inflate(requireActivity().getLayoutInflater());
        builder.setView(binding.getRoot());
        AlertDialog dialog = builder.create();

        TextInputEditText usernameInput = binding.loginDialogUsername;
        TextInputEditText passwordInput = binding.loginDialogPassword;
        Button cancelButton = binding.loginDialogCancel;
        Button saveButton = binding.loginDialogSave;
        Button infoButton = binding.loginDialogMoreInfo;
        Button createAccountButton = binding.loginDialogCreateAccount;
        LinearLayout infoContainer = binding.loginDialogInfoContainer;
        LinearLayout loadingContainer = binding.loginDialogLoadingContainer;
        TextView errorText = binding.loginDialogError;
        ProgressBar progressBar = binding.loginDialogProgress;

        usernameInput.addTextChangedListener(new ViewUtils.SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                boolean usernameHasText = !TextUtils.isEmpty(usernameInput.getText().toString());
                boolean passwordHasText = !TextUtils.isEmpty(passwordInput.getText().toString());

                saveButton.setEnabled(usernameHasText && passwordHasText);
            }
        });

        passwordInput.addTextChangedListener(new ViewUtils.SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                boolean usernameHasText = !TextUtils.isEmpty(usernameInput.getText().toString());
                boolean passwordHasText = !TextUtils.isEmpty(passwordInput.getText().toString());

                saveButton.setEnabled(usernameHasText && passwordHasText);
            }
        });

        boolean usernameHasText = !TextUtils.isEmpty(usernameInput.getText().toString());
        boolean passwordHasText = !TextUtils.isEmpty(passwordInput.getText().toString());

        saveButton.setEnabled(usernameHasText && passwordHasText);

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                errorText.setVisibility(View.GONE);
                loadingContainer.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.VISIBLE);
                usernameInput.setEnabled(false);
                passwordInput.setEnabled(false);
                cancelButton.setEnabled(false);
                saveButton.setEnabled(false);

                //actually try to log in here and see if it works out
                AccountUtils.setAccountDetails(getContext(), usernameInput.getText().toString(), passwordInput.getText().toString());

                UserActions.login(getContext(), new UserActions.ActionCallback() {
                    @Override
                    public void onSuccess(Response response) {
                        if (getContext() == null) {
                            return;
                        }

                        Utils.toast("Login successful", getContext());
                        notifyAccountStateChanged();
                        dismiss();
                    }

                    @Override
                    public void onCaptchaRequired(UserActions.CaptchaChallenge challenge) {
                        if (getContext() == null) {
                            return;
                        }

                        loadingContainer.setVisibility(View.GONE);
                        progressBar.setVisibility(View.GONE);

                        CaptchaDialogFragment.show(getParentFragmentManager(), challenge, new CaptchaDialogFragment.Listener() {
                            @Override
                            public void onCaptchaResponse(UserActions.CaptchaChallenge challenge, String captchaResponse) {
                                if (getContext() == null) {
                                    return;
                                }

                                loadingContainer.setVisibility(View.VISIBLE);
                                progressBar.setVisibility(View.VISIBLE);

                                UserActions.continueLoginWithCaptcha(getContext(), challenge, captchaResponse, new UserActions.ActionCallback() {
                                    @Override
                                    public void onSuccess(Response response) {
                                        if (getContext() == null) {
                                            return;
                                        }

                                        Utils.toast("Login successful", getContext());
                                        notifyAccountStateChanged();
                                        dismiss();
                                    }

                                    @Override
                                    public void onFailure(String summary, String response) {
                                        if (getContext() == null) {
                                            return;
                                        }

                                        AccountUtils.deleteAccountDetails(getContext());
                                        loadingContainer.setVisibility(View.GONE);
                                        progressBar.setVisibility(View.GONE);
                                        usernameInput.setEnabled(true);
                                        passwordInput.setEnabled(true);
                                        cancelButton.setEnabled(true);
                                        saveButton.setEnabled(true);
                                        errorText.setText("Login failed. Check your username and password, then try again.");
                                        errorText.setVisibility(View.VISIBLE);
                                    }
                                });
                            }

                            @Override
                            public void onCaptchaCancelled() {
                                if (getContext() == null) {
                                    return;
                                }

                                AccountUtils.deleteAccountDetails(getContext());
                                usernameInput.setEnabled(true);
                                passwordInput.setEnabled(true);
                                cancelButton.setEnabled(true);
                                saveButton.setEnabled(true);
                                errorText.setText("Login requires completing the Hacker News captcha.");
                                errorText.setVisibility(View.VISIBLE);
                            }
                        });
                    }

                    @Override
                    public void onFailure(String summary, String response) {
                        if (getContext() == null) {
                            return;
                        }

                        AccountUtils.deleteAccountDetails(getContext());
                        loadingContainer.setVisibility(View.GONE);
                        progressBar.setVisibility(View.GONE);
                        usernameInput.setEnabled(true);
                        passwordInput.setEnabled(true);
                        cancelButton.setEnabled(true);
                        saveButton.setEnabled(true);
                        errorText.setText("Login failed. Check your username and password, then try again.");
                        errorText.setVisibility(View.VISIBLE);
                    }
                });
            }
        });

        infoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                infoButton.setVisibility(View.GONE);
                infoContainer.setVisibility(View.VISIBLE);
            }
        });

        createAccountButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Utils.launchInExternalBrowser(requireContext(), HACKER_NEWS_LOGIN_URL);
            }
        });

        return dialog;
    }

    private void notifyAccountStateChanged() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).onAccountStateChanged();
        }
    }
}
