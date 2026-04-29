package com.simon.harmonichackernews.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.SettingsActivity;

public abstract class BaseSettingsFragment extends PreferenceFragmentCompat {

    protected @Nullable String getToolbarTitle() {
        return null;
    }

    protected boolean showNavigationIcon() {
        return true;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View preferenceView = super.onCreateView(inflater, container, savedInstanceState);

        String title = getToolbarTitle();
        if (title == null) {
            return preferenceView;
        }

        LinearLayout wrapper = new LinearLayout(requireContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setFitsSystemWindows(true);
        wrapper.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        MaterialToolbar toolbar = new MaterialToolbar(requireContext(), null,
                androidx.appcompat.R.attr.toolbarStyle);
        toolbar.setTitle(title);
        toolbar.setTitleCentered(false);
        ViewCompat.setAccessibilityHeading(toolbar, true);

        SettingsCallback callback = getSettingsCallback();
        boolean twoPane = callback != null && callback.isTwoPane();

        if (!twoPane && showNavigationIcon()) {
            toolbar.setNavigationIcon(R.drawable.ic_action_back);
            toolbar.setNavigationOnClickListener(v -> {
                if (getActivity() != null) {
                    getActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            });
        }

        wrapper.addView(toolbar);
        wrapper.addView(preferenceView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        return wrapper;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setDivider(null);
    }

    @NonNull
    @Override
    public RecyclerView onCreateRecyclerView(@NonNull LayoutInflater inflater, @NonNull ViewGroup parent, @Nullable Bundle savedInstanceState) {
        RecyclerView recycler = super.onCreateRecyclerView(inflater, parent, savedInstanceState);
        // Only set fitsSystemWindows when there is no toolbar wrapper handling insets
        if (getToolbarTitle() == null) {
            recycler.setFitsSystemWindows(true);
        }
        recycler.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View view) {
                if (view instanceof TextView && view.getId() == android.R.id.title) {
                    ViewCompat.setAccessibilityHeading(view, true);
                }
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View view) {
            }
        });
        return recycler;
    }

    protected SettingsCallback getSettingsCallback() {
        if (getActivity() instanceof SettingsCallback) {
            return (SettingsCallback) getActivity();
        }
        return null;
    }

    protected void restartSettingsActivity() {
        SettingsCallback callback = getSettingsCallback();
        if (callback != null) {
            callback.onRequestRestart();
        }
        Intent intent = new Intent(getContext(), SettingsActivity.class);
        intent.putExtra(SettingsActivity.EXTRA_REQUEST_RESTART, true);
        requireContext().startActivity(intent);
        requireActivity().finish();
    }

    protected void changePrefStatus(Preference pref, boolean newStatus) {
        if (pref != null) {
            pref.setEnabled(newStatus);
            if (pref.getIcon() != null) {
                pref.getIcon().setAlpha(newStatus ? 255 : 120);
            }
        }
    }
}
