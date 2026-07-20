package com.simon.harmonichackernews.settings;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
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
    private static final int NO_POSITION = RecyclerView.NO_POSITION;

    private RecyclerView.Adapter<?> segmentedListAdapter;
    private RecyclerView segmentedListRefreshView;
    private ViewTreeObserver.OnPreDrawListener segmentedListRefreshListener;
    private final RecyclerView.AdapterDataObserver segmentedListObserver =
            new RecyclerView.AdapterDataObserver() {
                @Override
                public void onChanged() {
                    refreshSegmentedListRows();
                }

                @Override
                public void onItemRangeChanged(int positionStart, int itemCount) {
                    refreshSegmentedListRows();
                }

                @Override
                public void onItemRangeChanged(int positionStart, int itemCount, @Nullable Object payload) {
                    refreshSegmentedListRows();
                }

                @Override
                public void onItemRangeInserted(int positionStart, int itemCount) {
                    refreshSegmentedListRows();
                }

                @Override
                public void onItemRangeRemoved(int positionStart, int itemCount) {
                    refreshSegmentedListRows();
                }

                @Override
                public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                    refreshSegmentedListRows();
                }
            };

    protected @Nullable String getToolbarTitle() {
        return null;
    }

    protected boolean showNavigationIcon() {
        return true;
    }

    protected boolean showNavigationIconInTwoPane() {
        return false;
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

        if (showNavigationIcon() && (!twoPane || showNavigationIconInTwoPane())) {
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
            toolbar.setNavigationOnClickListener(v -> {
                if (getActivity() != null) {
                    getActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            });
        }

        wrapper.addView(toolbar);
        View headerView = onCreateHeaderView(inflater, wrapper, savedInstanceState);
        if (headerView != null) {
            wrapper.addView(headerView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        wrapper.addView(preferenceView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        return wrapper;
    }

    protected @Nullable View onCreateHeaderView(
            @NonNull LayoutInflater inflater,
            @NonNull ViewGroup parent,
            @Nullable Bundle savedInstanceState) {
        return null;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setDivider(null);
        registerSegmentedListObserver();
        refreshSegmentedListRows();
    }

    @Override
    public void onDestroyView() {
        cancelSegmentedListRefresh();
        unregisterSegmentedListObserver();
        super.onDestroyView();
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
                styleSegmentedListChild(view);
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View view) {
            }
        });
        return recycler;
    }

    protected void refreshSegmentedListRows() {
        if (getView() == null) {
            return;
        }

        RecyclerView listView = getListView();
        cancelSegmentedListRefresh();
        segmentedListRefreshView = listView;
        segmentedListRefreshListener = new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                cancelSegmentedListRefresh();
                styleVisibleSegmentedListRows();
                return true;
            }
        };
        listView.getViewTreeObserver().addOnPreDrawListener(segmentedListRefreshListener);
        listView.invalidate();
    }

    private void cancelSegmentedListRefresh() {
        if (segmentedListRefreshView != null && segmentedListRefreshListener != null) {
            ViewTreeObserver observer = segmentedListRefreshView.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.removeOnPreDrawListener(segmentedListRefreshListener);
            }
        }
        segmentedListRefreshView = null;
        segmentedListRefreshListener = null;
    }

    private void styleVisibleSegmentedListRows() {
        if (getView() == null) {
            return;
        }

        RecyclerView listView = getListView();
        for (int i = 0; i < listView.getChildCount(); i++) {
            styleSegmentedListChild(listView.getChildAt(i));
        }
    }

    protected void styleSegmentedListChild(@NonNull View child) {
        RecyclerView listView = getListView();
        int position = listView.getChildAdapterPosition(child);
        Preference preference = getPreferenceAtPosition(position);

        if (!isSegmentedPreference(preference)) {
            child.setBackground(null);
            updateSegmentedListPadding(child, false);
            updateSegmentedListMargins(child, 0, 0, 0, 0);
            return;
        }

        boolean firstInSegment = !isSegmentedPosition(position - 1);
        boolean lastInSegment = !isSegmentedPosition(position + 1);
        boolean firstInList = getPreferenceAtPosition(position - 1) == null;
        boolean lastInList = getPreferenceAtPosition(position + 1) == null;
        int topMargin = 0;
        int bottomMargin = 0;
        if (firstInSegment) {
            topMargin = getResources().getDimensionPixelSize(
                    firstInList
                            ? R.dimen.settings_list_first_segment_top_margin
                            : R.dimen.settings_list_segment_top_margin);
        }
        if (lastInSegment && lastInList) {
            bottomMargin = getResources().getDimensionPixelSize(R.dimen.settings_list_segment_bottom_margin);
        } else if (!lastInSegment) {
            bottomMargin = getResources().getDimensionPixelSize(R.dimen.settings_list_segment_internal_gap);
        }

        updateSegmentedListMargins(
                child,
                getResources().getDimensionPixelSize(R.dimen.settings_list_segment_horizontal_margin),
                topMargin,
                getResources().getDimensionPixelSize(R.dimen.settings_list_segment_horizontal_margin),
                bottomMargin);
        updateSegmentedListPadding(child, true);

        child.setBackground(createSegmentedItemBackground(
                firstInSegment, lastInSegment, preference));
    }

    private void registerSegmentedListObserver() {
        unregisterSegmentedListObserver();
        segmentedListAdapter = getListView().getAdapter();
        if (segmentedListAdapter != null) {
            segmentedListAdapter.registerAdapterDataObserver(segmentedListObserver);
        }
    }

    private void unregisterSegmentedListObserver() {
        if (segmentedListAdapter != null) {
            segmentedListAdapter.unregisterAdapterDataObserver(segmentedListObserver);
            segmentedListAdapter = null;
        }
    }

    private boolean isSegmentedPosition(int position) {
        return isSegmentedPreference(getPreferenceAtPosition(position));
    }

    private boolean isSegmentedPreference(@Nullable Preference preference) {
        return preference != null && !(preference instanceof PreferenceCategory);
    }

    @Nullable
    private Preference getPreferenceAtPosition(int position) {
        if (position == NO_POSITION || position < 0 || getPreferenceScreen() == null) {
            return null;
        }

        return findVisiblePreferenceAtPosition(getPreferenceScreen(), new int[]{position});
    }

    @Nullable
    private Preference findVisiblePreferenceAtPosition(@NonNull PreferenceGroup group, @NonNull int[] position) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference preference = group.getPreference(i);
            if (!preference.isVisible()) {
                continue;
            }

            if (position[0] == 0) {
                return preference;
            }
            position[0]--;

            if (preference instanceof PreferenceGroup) {
                Preference nestedPreference = findVisiblePreferenceAtPosition((PreferenceGroup) preference, position);
                if (nestedPreference != null) {
                    return nestedPreference;
                }
            }
        }
        return null;
    }

    private void updateSegmentedListMargins(
            @NonNull View child,
            int start,
            int top,
            int end,
            int bottom) {
        ViewGroup.LayoutParams layoutParams = child.getLayoutParams();
        if (!(layoutParams instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }

        ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) layoutParams;
        boolean changed = marginParams.getMarginStart() != start
                || marginParams.topMargin != top
                || marginParams.getMarginEnd() != end
                || marginParams.bottomMargin != bottom;
        if (!changed) {
            return;
        }

        marginParams.setMarginStart(start);
        marginParams.topMargin = top;
        marginParams.setMarginEnd(end);
        marginParams.bottomMargin = bottom;
        child.setLayoutParams(marginParams);
    }

    private void updateSegmentedListPadding(@NonNull View child, boolean segmented) {
        if (child.getTag(R.id.settings_segment_original_padding_top) == null) {
            child.setTag(R.id.settings_segment_original_padding_start, child.getPaddingStart());
            child.setTag(R.id.settings_segment_original_padding_top, child.getPaddingTop());
            child.setTag(R.id.settings_segment_original_padding_end, child.getPaddingEnd());
            child.setTag(R.id.settings_segment_original_padding_bottom, child.getPaddingBottom());
        }

        int start = (int) child.getTag(R.id.settings_segment_original_padding_start);
        int top = (int) child.getTag(R.id.settings_segment_original_padding_top);
        int end = (int) child.getTag(R.id.settings_segment_original_padding_end);
        int bottom = (int) child.getTag(R.id.settings_segment_original_padding_bottom);
        int verticalExtra = segmented
                ? getResources().getDimensionPixelSize(R.dimen.settings_list_segment_vertical_padding_extra)
                : 0;

        child.setPaddingRelative(start, top + verticalExtra, end, bottom + verticalExtra);
    }

    @NonNull
    private Drawable createSegmentedItemBackground(
            boolean firstInSegment,
            boolean lastInSegment,
            @Nullable Preference preference) {
        boolean mainToggle = preference instanceof AiSummaryEnabledPreference;
        GradientDrawable content = createSegmentedShape(firstInSegment, lastInSegment, mainToggle);
        content.setColor(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_activated},
                        new int[0]
                },
                new int[]{
                        resolveThemeColor(mainToggle
                                ? R.attr.settingsMainToggleColor
                                : R.attr.settingsHeaderSelectedColor),
                        resolveThemeColor(R.attr.settingsSegmentColor)
                }));

        GradientDrawable mask = createSegmentedShape(firstInSegment, lastInSegment, mainToggle);
        mask.setColor(0xffffffff);

        return new RippleDrawable(
                ColorStateList.valueOf(resolveThemeColor(androidx.appcompat.R.attr.colorControlHighlight)),
                content,
                mask);
    }

    @NonNull
    private GradientDrawable createSegmentedShape(
            boolean firstInSegment, boolean lastInSegment, boolean mainToggle) {
        float radius = getResources().getDimension(mainToggle
                ? R.dimen.settings_list_main_toggle_corner_radius
                : R.dimen.settings_list_segment_corner_radius);
        float topRadius = firstInSegment ? radius : 0;
        float bottomRadius = lastInSegment ? radius : 0;

        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadii(new float[]{
                topRadius, topRadius,
                topRadius, topRadius,
                bottomRadius, bottomRadius,
                bottomRadius, bottomRadius
        });
        return drawable;
    }

    private int resolveThemeColor(int attr) {
        TypedValue value = new TypedValue();
        if (requireContext().getTheme().resolveAttribute(attr, value, true)) {
            if (value.resourceId != 0) {
                return ContextCompat.getColor(requireContext(), value.resourceId);
            }
            return value.data;
        }
        return 0;
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
