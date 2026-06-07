package com.simon.harmonichackernews.settings;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.SettingsActivity;
import com.simon.harmonichackernews.SettingsDetailActivity;
import com.simon.harmonichackernews.utils.ViewUtils;

public abstract class BaseSettingsFragment extends PreferenceFragmentCompat {

    private static final String STATE_LIST_LAYOUT_MANAGER = "state_list_layout_manager";

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
        wrapper.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setUpWrapperInsets(wrapper);

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
        setUpRecyclerInsets(recycler, getToolbarTitle() == null);
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

    private void setUpWrapperInsets(@NonNull LinearLayout wrapper) {
        ViewCompat.setOnApplyWindowInsetsListener(wrapper, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());

            v.setPadding(
                    Math.max(systemBars.left, cutout.left),
                    systemBars.top,
                    Math.max(systemBars.right, cutout.right),
                    0);

            return insets;
        });
        ViewUtils.requestApplyInsetsWhenAttached(wrapper);
    }

    private void setUpRecyclerInsets(@NonNull RecyclerView recycler, boolean handleTopAndSideInsets) {
        recycler.setClipToPadding(false);
        ViewCompat.setOnApplyWindowInsetsListener(recycler, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());

            int leftInset = handleTopAndSideInsets ? Math.max(systemBars.left, cutout.left) : 0;
            int topInset = handleTopAndSideInsets ? systemBars.top : 0;
            int rightInset = handleTopAndSideInsets ? Math.max(systemBars.right, cutout.right) : 0;

            v.setPadding(
                    leftInset,
                    topInset,
                    rightInset,
                    systemBars.bottom);

            return insets;
        });
        ViewUtils.requestApplyInsetsWhenAttached(recycler);
    }

    protected SettingsCallback getSettingsCallback() {
        if (getActivity() instanceof SettingsCallback) {
            return (SettingsCallback) getActivity();
        }
        return null;
    }

    @Nullable
    public Bundle saveListScrollState() {
        RecyclerView listView = getListView();
        RecyclerView.LayoutManager layoutManager = listView.getLayoutManager();
        if (layoutManager == null) {
            return null;
        }

        Parcelable state = layoutManager.onSaveInstanceState();
        if (state == null) {
            return null;
        }

        Bundle bundle = new Bundle();
        bundle.putParcelable(STATE_LIST_LAYOUT_MANAGER, state);
        return bundle;
    }

    public void restoreListScrollState(@Nullable Bundle state) {
        if (state == null) {
            return;
        }

        RecyclerView listView = getListView();
        state.setClassLoader(getClass().getClassLoader());
        Parcelable layoutState = state.getParcelable(STATE_LIST_LAYOUT_MANAGER);
        if (layoutState == null) {
            return;
        }

        listView.post(() -> {
            RecyclerView.LayoutManager layoutManager = listView.getLayoutManager();
            if (layoutManager != null) {
                layoutManager.onRestoreInstanceState(layoutState);
            }
        });
    }

    protected void restartSettingsActivity() {
        if (getActivity() instanceof SettingsDetailActivity) {
            ((SettingsDetailActivity) getActivity()).restartSettingsActivity();
            return;
        }

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
