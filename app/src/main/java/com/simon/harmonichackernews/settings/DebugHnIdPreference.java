package com.simon.harmonichackernews.settings;

import android.content.Context;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.textfield.TextInputEditText;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.databinding.PreferenceDebugHnIdBinding;

public class DebugHnIdPreference extends Preference {

    private static final long OPEN_DEBOUNCE_MILLIS = 500;

    public interface OnOpenIdListener {
        void onOpenId(int id);
    }

    private String inputValue = "";
    private OnOpenIdListener onOpenIdListener;
    private long lastOpenTimeMillis;

    public DebugHnIdPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_debug_hn_id);
        setSelectable(false);
    }

    public DebugHnIdPreference(Context context) {
        this(context, null);
    }

    public void setOnOpenIdListener(OnOpenIdListener listener) {
        onOpenIdListener = listener;
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        holder.itemView.setClickable(false);
        holder.itemView.setFocusable(false);

        PreferenceDebugHnIdBinding binding = PreferenceDebugHnIdBinding.bind(holder.itemView);
        TextInputEditText input = binding.debugHnIdInput;

        Object oldWatcher = input.getTag(R.id.debug_hn_id_input);
        if (oldWatcher instanceof TextWatcher) {
            input.removeTextChangedListener((TextWatcher) oldWatcher);
        }

        input.setText(inputValue);
        input.setSelection(input.length());

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                inputValue = editable.toString();
                binding.debugHnIdInputLayout.setError(null);
            }
        };
        input.addTextChangedListener(watcher);
        input.setTag(R.id.debug_hn_id_input, watcher);

        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                openId(binding);
                return true;
            }
            return false;
        });
        binding.debugOpenHnId.setOnClickListener(view -> openId(binding));
    }

    private void openId(PreferenceDebugHnIdBinding binding) {
        String hnId = inputValue.trim();
        if (TextUtils.isEmpty(hnId) || !TextUtils.isDigitsOnly(hnId)) {
            binding.debugHnIdInputLayout.setError("Enter a numeric HN ID");
            return;
        }

        int id;
        try {
            id = Integer.parseInt(hnId);
        } catch (NumberFormatException e) {
            binding.debugHnIdInputLayout.setError("HN ID is too large");
            return;
        }

        if (id <= 0) {
            binding.debugHnIdInputLayout.setError("Enter a positive HN ID");
            return;
        }

        binding.debugHnIdInputLayout.setError(null);
        if (onOpenIdListener != null) {
            long now = SystemClock.elapsedRealtime();
            if (now - lastOpenTimeMillis < OPEN_DEBOUNCE_MILLIS) {
                return;
            }
            lastOpenTimeMillis = now;
            onOpenIdListener.onOpenId(id);
        }
    }
}
