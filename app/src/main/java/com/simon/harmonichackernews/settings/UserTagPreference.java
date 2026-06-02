package com.simon.harmonichackernews.settings;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.UserDialogFragment;
import com.simon.harmonichackernews.UserTagDialogFragment;
import com.simon.harmonichackernews.utils.Utils;

public class UserTagPreference extends Preference {

    public static final String EMPTY_KEY = "pref_user_tags_empty";
    public static final String USER_KEY_PREFIX = "pref_user_tag_";

    private final @Nullable FragmentManager fragmentManager;
    private final @Nullable Runnable onChanged;
    private final boolean empty;
    private final String username;
    private String tag;

    public static UserTagPreference empty(@NonNull Context context) {
        return new UserTagPreference(context, null, null, null, null);
    }

    public UserTagPreference(@NonNull Context context,
                             @Nullable String username,
                             @Nullable String tag,
                             @Nullable FragmentManager fragmentManager,
                             @Nullable Runnable onChanged) {
        super(context);
        this.empty = TextUtils.isEmpty(username);
        this.username = username;
        this.tag = tag;
        this.fragmentManager = fragmentManager;
        this.onChanged = onChanged;

        setKey(empty ? EMPTY_KEY : USER_KEY_PREFIX + username);
        setLayoutResource(R.layout.user_tag_item);
        setSelectable(!empty);
        setIconSpaceReserved(false);
        setOnPreferenceClickListener(preference -> {
            openUser();
            return true;
        });
    }

    public void setTag(@Nullable String tag) {
        this.tag = tag;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        android.widget.TextView textView = (android.widget.TextView) holder.findViewById(R.id.user_tag_item_text);
        View userIcon = holder.findViewById(R.id.user_tag_item_icon);
        View editButton = holder.findViewById(R.id.user_tag_item_edit);
        View deleteButton = holder.findViewById(R.id.user_tag_item_delete);

        if (empty) {
            textView.setText("No user with tags");
            textView.setTextColor(com.google.android.material.color.MaterialColors.getColor(textView, R.attr.textColorDisabled));
            if (userIcon != null) {
                userIcon.setVisibility(View.GONE);
            }
            if (editButton != null) {
                editButton.setVisibility(View.GONE);
            }
            if (deleteButton != null) {
                deleteButton.setVisibility(View.GONE);
            }
            return;
        }

        textView.setTextColor(com.google.android.material.color.MaterialColors.getColor(textView, R.attr.storyColorNormal));
        textView.setText(TextUtils.isEmpty(tag) ? username : username + " (" + tag + ")");

        if (userIcon != null) {
            userIcon.setVisibility(View.VISIBLE);
        }
        if (editButton != null) {
            editButton.setVisibility(View.VISIBLE);
            editButton.setOnClickListener(v -> editTag());
        }

        if (deleteButton != null) {
            deleteButton.setVisibility(View.VISIBLE);
            deleteButton.setOnClickListener(v -> deleteTag());
        }
    }

    private void openUser() {
        if (empty || fragmentManager == null) {
            return;
        }
        UserDialogFragment.showUserDialog(fragmentManager, username, accepted -> {
            if (onChanged != null) {
                onChanged.run();
            }
        });
    }

    private void editTag() {
        if (fragmentManager == null) {
            return;
        }

        UserTagDialogFragment.show(fragmentManager, username, Utils.getUserTag(getContext(), username), tag -> {
            if (onChanged != null) {
                onChanged.run();
            }
        });
    }

    private void deleteTag() {
        Utils.setUserTag(getContext(), username, "");
        if (onChanged != null) {
            onChanged.run();
        }
    }
}
