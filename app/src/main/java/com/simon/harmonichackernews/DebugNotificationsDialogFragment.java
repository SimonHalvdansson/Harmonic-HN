package com.simon.harmonichackernews;

import android.app.Dialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.simon.harmonichackernews.network.RepliesChecker;
import com.simon.harmonichackernews.utils.ViewUtils;

public class DebugNotificationsDialogFragment extends AppCompatDialogFragment {

    public static final String TAG = "tag_debug_notifications_dialog";

    private static final int ACTION_ENABLE = 1;
    private static final int ACTION_TEST = 2;

    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private int pendingPermissionAction = 0;
    private String pendingPermissionUsername;

    private TextInputLayout usernameLayout;
    private TextInputEditText usernameInput;
    private TextView statusText;
    private MaterialButton testButton;
    private MaterialButton enableButton;
    private MaterialButton disableButton;
    private LinearProgressIndicator loadingIndicator;
    private ViewUtils.SimpleTextWatcher usernameWatcher;
    private boolean loading;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (!isAdded()) {
                        clearPendingPermissionAction();
                        return;
                    }
                    if (!granted) {
                        setStatus("Notification permission denied.");
                        clearPendingPermissionAction();
                        return;
                    }

                    int action = pendingPermissionAction;
                    String username = pendingPermissionUsername;
                    clearPendingPermissionAction();

                    if (action == ACTION_ENABLE) {
                        enableNotifications(username);
                    } else if (action == ACTION_TEST) {
                        testNotification(username);
                    }
                }
        );
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View rootView = inflater.inflate(R.layout.debug_notifications_dialog, null);
        builder.setTitle("Debug notifications");
        builder.setView(rootView);

        usernameLayout = rootView.findViewById(R.id.debug_notifications_username_layout);
        usernameInput = rootView.findViewById(R.id.debug_notifications_username);
        statusText = rootView.findViewById(R.id.debug_notifications_status);
        testButton = rootView.findViewById(R.id.debug_notifications_test);
        enableButton = rootView.findViewById(R.id.debug_notifications_enable);
        disableButton = rootView.findViewById(R.id.debug_notifications_disable);
        loadingIndicator = rootView.findViewById(R.id.debug_notifications_loading);

        usernameInput.setText(RepliesChecker.getConfiguredUsername(requireContext()));
        usernameWatcher = new ViewUtils.SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                usernameLayout.setError(null);
                updateButtonStates();
            }
        };
        usernameInput.addTextChangedListener(usernameWatcher);

        testButton.setOnClickListener(view -> {
            String username = getUsernameOrShowError();
            if (!TextUtils.isEmpty(username)) {
                runWithNotificationPermission(ACTION_TEST, username);
            }
        });

        enableButton.setOnClickListener(view -> {
            String username = getUsernameOrShowError();
            if (!TextUtils.isEmpty(username)) {
                runWithNotificationPermission(ACTION_ENABLE, username);
            }
        });

        disableButton.setOnClickListener(view -> {
            RepliesChecker.disable(requireContext());
            setStatus("Reply notifications turned off.");
            updateButtonStates();
        });

        updateButtonStates();
        setInitialStatus();

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> updateButtonStates());
        return dialog;
    }

    @Override
    public void onDestroyView() {
        if (usernameInput != null && usernameWatcher != null) {
            usernameInput.removeTextChangedListener(usernameWatcher);
        }
        if (testButton != null) {
            testButton.setOnClickListener(null);
        }
        if (enableButton != null) {
            enableButton.setOnClickListener(null);
        }
        if (disableButton != null) {
            disableButton.setOnClickListener(null);
        }

        usernameWatcher = null;
        usernameLayout = null;
        usernameInput = null;
        statusText = null;
        testButton = null;
        enableButton = null;
        disableButton = null;
        loadingIndicator = null;

        super.onDestroyView();
    }

    public static void show(FragmentManager fm) {
        new DebugNotificationsDialogFragment().show(fm, TAG);
    }

    private void runWithNotificationPermission(int action, String username) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingPermissionAction = action;
            pendingPermissionUsername = username;
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            return;
        }

        if (action == ACTION_ENABLE) {
            enableNotifications(username);
        } else if (action == ACTION_TEST) {
            testNotification(username);
        }
    }

    private void enableNotifications(String username) {
        setLoading(true);
        setStatus("Setting up reply notifications...");
        RepliesChecker.enable(requireContext(), username, success -> {
            if (getContext() == null || usernameInput == null) {
                return;
            }
            setLoading(false);
            if (success) {
                usernameInput.setText(RepliesChecker.getConfiguredUsername(requireContext()));
                setStatus("Reply notifications are active for " + RepliesChecker.getConfiguredUsername(requireContext()) + ".");
            } else {
                setStatus("Could not enable reply notifications for " + username + ".");
            }
            updateButtonStates();
        });
    }

    private void testNotification(String username) {
        setLoading(true);
        setStatus("Looking for a recent reply...");
        RepliesChecker.sendLatestDebugNotification(requireContext(), username, result -> {
            if (getContext() == null) {
                return;
            }
            setLoading(false);
            switch (result) {
                case SENT:
                    setStatus("Sent a notification for the latest recent reply.");
                    break;
                case NO_RECENT_REPLY:
                    setStatus("No recent reply found for " + username + ".");
                    break;
                case USER_NOT_FOUND:
                    setStatus("Could not find HN user " + username + ".");
                    break;
                case FAILED:
                default:
                    setStatus("Could not send a test notification.");
                    break;
            }
            updateButtonStates();
        });
    }

    private String getUsernameOrShowError() {
        String username = usernameInput.getText() == null ? "" : usernameInput.getText().toString().trim();
        if (TextUtils.isEmpty(username)) {
            usernameLayout.setError("Enter a username");
            return "";
        }
        usernameLayout.setError(null);
        return username;
    }

    private void setInitialStatus() {
        String configuredUsername = RepliesChecker.getConfiguredUsername(requireContext());
        if (TextUtils.isEmpty(configuredUsername)) {
            setStatus("");
        } else {
            setStatus("Reply notifications are on for " + configuredUsername + ".");
        }
    }

    private void updateButtonStates() {
        if (usernameInput == null || testButton == null || enableButton == null || disableButton == null) {
            return;
        }

        boolean hasUsername = usernameInput.getText() != null && !TextUtils.isEmpty(usernameInput.getText().toString().trim());
        testButton.setEnabled(hasUsername && !loading);
        enableButton.setEnabled(hasUsername && !loading);
        disableButton.setVisibility(RepliesChecker.notificationsAreActive(requireContext()) ? View.VISIBLE : View.GONE);
        disableButton.setEnabled(!loading);
    }

    private void setLoading(boolean loading) {
        this.loading = loading;
        if (loadingIndicator == null) {
            return;
        }
        loadingIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
        updateButtonStates();
    }

    private void setStatus(String status) {
        if (statusText != null) {
            statusText.setText(status);
        }
    }

    private void clearPendingPermissionAction() {
        pendingPermissionAction = 0;
        pendingPermissionUsername = null;
    }
}
