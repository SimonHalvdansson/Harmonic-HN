package com.simon.harmonichackernews;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.simon.harmonichackernews.utils.AccountUtils;
import com.simon.harmonichackernews.utils.ViewUtils;

public class LoginDialogFragment extends AppCompatDialogFragment {

    public static String TAG = "tag_login_dialog";

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View rootView = inflater.inflate(R.layout.login_dialog, null);
        builder.setView(rootView);
        AlertDialog dialog = builder.create();

        TextInputEditText usernameInput = rootView.findViewById(R.id.login_dialog_username);
        TextInputEditText passwordInput = rootView.findViewById(R.id.login_dialog_password);
        MaterialButton cancelButton = rootView.findViewById(R.id.login_dialog_cancel);
        MaterialButton saveButton = rootView.findViewById(R.id.login_dialog_save);
        Button infoButton = rootView.findViewById(R.id.login_dialog_more_info);
        LinearLayout infoContainer = rootView.findViewById(R.id.login_dialog_info_container);

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
                AccountUtils.setAccountDetails(getContext(), usernameInput.getText().toString(), passwordInput.getText().toString());
                dismiss();
            }
        });

        infoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                infoButton.setVisibility(View.GONE);
                infoContainer.setVisibility(View.VISIBLE);
            }
        });

        return dialog;
    }
}
