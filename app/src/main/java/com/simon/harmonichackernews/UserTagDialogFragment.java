package com.simon.harmonichackernews;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.simon.harmonichackernews.databinding.TagDialogBinding;
import com.google.android.material.textfield.TextInputEditText;
import com.simon.harmonichackernews.utils.Utils;

public class UserTagDialogFragment extends AppCompatDialogFragment {

    public static final String TAG = "tag_user_tag_dialog";
    private static final String EXTRA_USER_NAME = "com.simon.harmonichackernews.EXTRA_TAG_USER_NAME";
    private static final String EXTRA_CURRENT_TAG = "com.simon.harmonichackernews.EXTRA_CURRENT_USER_TAG";

    private TextInputEditText editText;
    private String userName;
    private UserTagCallback callback;

    public static void show(FragmentManager fm,
                            String userName,
                            String currentTag,
                            @Nullable UserTagCallback callback) {
        UserTagDialogFragment fragment = new UserTagDialogFragment();
        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_USER_NAME, userName);
        bundle.putString(EXTRA_CURRENT_TAG, currentTag);
        fragment.setArguments(bundle);
        fragment.setCallback(callback);
        fragment.show(fm, TAG);
    }

    public void setCallback(@Nullable UserTagCallback callback) {
        this.callback = callback;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);

        Context context = requireContext();
        Bundle bundle = requireArguments();
        userName = bundle.getString(EXTRA_USER_NAME, "");
        String currentTag = bundle.getString(EXTRA_CURRENT_TAG, "");

        TagDialogBinding binding = TagDialogBinding.inflate(getLayoutInflater());
        editText = binding.tagDialogEdittext;
        Button cancel = binding.tagDialogCancel;
        Button save = binding.tagDialogSave;

        editText.setText(currentTag);
        if (!TextUtils.isEmpty(currentTag)) {
            editText.setSelection(currentTag.length());
        }

        cancel.setOnClickListener(v -> dismiss());
        save.setOnClickListener(v -> saveTag());

        Dialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(binding.getRoot())
                .create();
        dialog.setOnShowListener(dialogInterface -> focusTagInput());
        return dialog;
    }

    @Override
    public void onDestroyView() {
        editText = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        callback = null;
        super.onDestroy();
    }

    private void focusTagInput() {
        TextInputEditText currentEditText = editText;
        if (currentEditText == null) {
            return;
        }

        Dialog dialog = getDialog();
        Window window = dialog == null ? null : dialog.getWindow();
        if (window != null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }

        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        currentEditText.post(() -> {
            if (editText != currentEditText) {
                return;
            }

            currentEditText.requestFocus();
            if (imm != null) {
                imm.showSoftInput(currentEditText, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private void saveTag() {
        TextInputEditText currentEditText = editText;
        String tag = currentEditText != null && currentEditText.getText() != null
                ? currentEditText.getText().toString().trim()
                : "";

        Utils.setUserTag(requireContext(), userName, tag);
        if (callback != null) {
            callback.onTagSaved(tag);
        }
        dismiss();
    }

    public interface UserTagCallback {
        void onTagSaved(String tag);
    }
}
