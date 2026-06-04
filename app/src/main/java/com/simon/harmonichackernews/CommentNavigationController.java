package com.simon.harmonichackernews;

import android.content.Context;
import android.content.res.Resources;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.simon.harmonichackernews.adapters.CommentsRecyclerViewAdapter;
import com.simon.harmonichackernews.data.Comment;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.Utils;

import java.util.List;

final class CommentNavigationController {
    private static final int COMMENT_NAVIGATION_SPEED_STEP = 35;
    private static final int SEARCH_SCROLL_TOP_MIN_VISIBLE_COMMENT = 10;
    private static final int SEARCH_COMMENT_HIGHLIGHT_DURATION_MS = 1200;

    private final Host host;
    private int smoothScrollSpeedMultiplier = 1;
    private int searchedCommentScrollTopTargetId = -1;
    private boolean searchedCommentScrollTopPending = false;
    private int pendingSearchedCommentHighlightId = -1;
    private Runnable clearSearchedCommentHighlightRunnable;

    CommentNavigationController(Host host) {
        this.host = host;
    }

    int getSmoothScrollSpeedMultiplier() {
        return smoothScrollSpeedMultiplier;
    }

    boolean isSearchedCommentScrollTopPending() {
        return searchedCommentScrollTopPending;
    }

    void smoothScrollTop() {
        LinearLayoutManager layoutManager = host.getNavigationLayoutManager();
        RecyclerView recyclerView = host.getNavigationRecyclerView();
        if (layoutManager != null) {
            startCommentSmoothScrollWithScaledSpeed(0);
        } else if (recyclerView != null) {
            recyclerView.smoothScrollToPosition(0);
        }
    }

    void scrollTop() {
        RecyclerView recyclerView = host.getNavigationRecyclerView();
        if (recyclerView != null) {
            recyclerView.scrollToPosition(0);
        }
    }

    void scrollToSearchedComment(int targetPosition) {
        LinearLayoutManager layoutManager = host.getNavigationLayoutManager();
        RecyclerView recyclerView = host.getNavigationRecyclerView();
        if (layoutManager != null && host.isNavigationHostAdded()
                && SettingsUtils.shouldSmoothScrollComments(host.requireNavigationContext())) {
            startCommentSmoothScrollWithScaledSpeed(targetPosition);
        } else if (layoutManager != null) {
            layoutManager.scrollToPositionWithOffset(targetPosition, host.getTopInset());
        } else if (recyclerView != null) {
            recyclerView.scrollToPosition(targetPosition);
        }
    }

    void setPendingSearchedCommentHighlight(int targetPosition) {
        List<Comment> comments = host.getNavigationComments();
        RecyclerView recyclerView = host.getNavigationRecyclerView();
        CommentsRecyclerViewAdapter adapter = host.getNavigationAdapter();
        if (comments == null || recyclerView == null || adapter == null
                || targetPosition <= 0 || targetPosition >= comments.size()) {
            pendingSearchedCommentHighlightId = -1;
            return;
        }

        pendingSearchedCommentHighlightId = comments.get(targetPosition).id;
        cancelSearchedCommentHighlightClear();
        adapter.setHighlightedCommentId(-1);
        recyclerView.post(this::highlightPendingSearchedCommentIfReady);
        recyclerView.postDelayed(this::highlightPendingSearchedCommentIfReady, 50);
    }

    void highlightPendingSearchedCommentIfReady() {
        RecyclerView recyclerView = host.getNavigationRecyclerView();
        LinearLayoutManager layoutManager = host.getNavigationLayoutManager();
        CommentsRecyclerViewAdapter adapter = host.getNavigationAdapter();
        if (pendingSearchedCommentHighlightId == -1 || recyclerView == null || layoutManager == null || adapter == null) {
            return;
        }

        int targetPosition = findCommentPositionById(pendingSearchedCommentHighlightId);
        if (targetPosition == RecyclerView.NO_POSITION) {
            pendingSearchedCommentHighlightId = -1;
            return;
        }

        boolean targetVisible = isCommentPositionVisible(targetPosition);
        if (!targetVisible || recyclerView.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) {
            return;
        }

        int highlightedCommentId = pendingSearchedCommentHighlightId;
        pendingSearchedCommentHighlightId = -1;
        adapter.setHighlightedCommentId(highlightedCommentId);

        clearSearchedCommentHighlightRunnable = () -> {
            CommentsRecyclerViewAdapter currentAdapter = host.getNavigationAdapter();
            if (currentAdapter != null) {
                currentAdapter.clearHighlightedCommentId(highlightedCommentId);
            }
            clearSearchedCommentHighlightRunnable = null;
        };
        recyclerView.postDelayed(clearSearchedCommentHighlightRunnable, SEARCH_COMMENT_HIGHLIGHT_DURATION_MS);
    }

    void clearSearchedCommentHighlight() {
        pendingSearchedCommentHighlightId = -1;
        cancelSearchedCommentHighlightClear();
        CommentsRecyclerViewAdapter adapter = host.getNavigationAdapter();
        if (adapter != null) {
            adapter.setHighlightedCommentId(-1);
        }
    }

    void setSearchedCommentScrollTopTarget(int targetPosition) {
        List<Comment> comments = host.getNavigationComments();
        CommentsRecyclerViewAdapter adapter = host.getNavigationAdapter();
        if (comments == null || adapter == null || adapter.showUpdate || targetPosition <= 0 || targetPosition >= comments.size()) {
            clearSearchedCommentScrollTopTarget();
            return;
        }

        if (getVisibleCommentNumber(targetPosition) <= SEARCH_SCROLL_TOP_MIN_VISIBLE_COMMENT) {
            clearSearchedCommentScrollTopTarget();
            return;
        }

        searchedCommentScrollTopTargetId = comments.get(targetPosition).id;
        searchedCommentScrollTopPending = true;
        hideSearchScrollTopFab();
    }

    void clearSearchedCommentScrollTopTarget() {
        searchedCommentScrollTopTargetId = -1;
        searchedCommentScrollTopPending = false;
        hideSearchScrollTopFab();
    }

    void updateSearchedCommentScrollTopVisibility(boolean clearWhenAwayFromTarget) {
        ExtendedFloatingActionButton searchScrollTopFab = host.getSearchScrollTopFab();
        LinearLayoutManager layoutManager = host.getNavigationLayoutManager();
        if (searchScrollTopFab == null || layoutManager == null || searchedCommentScrollTopTargetId == -1) {
            return;
        }

        CommentsRecyclerViewAdapter adapter = host.getNavigationAdapter();
        if (adapter != null && adapter.showUpdate) {
            clearSearchedCommentScrollTopTarget();
            return;
        }

        int targetPosition = findCommentPositionById(searchedCommentScrollTopTargetId);
        if (targetPosition == RecyclerView.NO_POSITION
                || getVisibleCommentNumber(targetPosition) <= SEARCH_SCROLL_TOP_MIN_VISIBLE_COMMENT) {
            clearSearchedCommentScrollTopTarget();
            return;
        }

        boolean targetVisible = isCommentPositionVisible(targetPosition);

        if (targetVisible) {
            searchedCommentScrollTopPending = false;
            showSearchScrollTopFab();
        } else if (clearWhenAwayFromTarget) {
            clearSearchedCommentScrollTopTarget();
        }
    }

    void navigateToNextComment(boolean topLevelOnly, boolean scaleLongScrollSpeed) {
        if (!host.isNavigationHostAdded()) {
            return;
        }

        if (SettingsUtils.shouldSmoothScrollComments(host.requireNavigationContext())) {
            smoothScrollNext(topLevelOnly, scaleLongScrollSpeed);
        } else {
            scrollNext(topLevelOnly);
        }
    }

    void navigateToPreviousComment(boolean topLevelOnly, boolean scaleLongScrollSpeed) {
        if (!host.isNavigationHostAdded()) {
            return;
        }

        if (SettingsUtils.shouldSmoothScrollComments(host.requireNavigationContext())) {
            smoothScrollPrevious(topLevelOnly, scaleLongScrollSpeed);
        } else {
            scrollPrevious(topLevelOnly);
        }
    }

    void startCommentSmoothScrollWithScaledSpeed(int targetPosition) {
        int firstVisible = findFirstVisiblePosition();
        startCommentSmoothScroll(targetPosition, getCommentNavigationSpeedMultiplier(firstVisible, targetPosition, true));
    }

    void smoothScrollLast() {
        LinearLayoutManager layoutManager = host.getNavigationLayoutManager();
        List<Comment> comments = host.getNavigationComments();
        if (layoutManager == null || comments == null) {
            return;
        }

        int firstVisible = layoutManager.findFirstVisibleItemPosition();
        int toScrollTo = firstVisible;

        for (int i = firstVisible + 1; i < comments.size(); i++) {
            if (comments.get(i).depth == 0) {
                toScrollTo = i;
            }
        }

        startCommentSmoothScroll(toScrollTo, getCommentNavigationSpeedMultiplier(firstVisible, toScrollTo, true));
    }

    void scrollLast() {
        LinearLayoutManager layoutManager = host.getNavigationLayoutManager();
        RecyclerView recyclerView = host.getNavigationRecyclerView();
        List<Comment> comments = host.getNavigationComments();
        if (layoutManager == null || recyclerView == null || comments == null) {
            return;
        }

        int firstVisible = layoutManager.findFirstVisibleItemPosition();
        int toScrollTo = firstVisible;

        for (int i = firstVisible + 1; i < comments.size(); i++) {
            if (comments.get(i).depth == 0) {
                toScrollTo = i;
            }
        }

        scrollToCommentPosition(toScrollTo, host.getScreenHeightInPixels());
    }

    void updateNavigationVisibility() {
        View scrollNavigation = host.getScrollNavigationView();
        List<Comment> comments = host.getNavigationComments();
        if (host.shouldShowNavButtons() && scrollNavigation != null) {
            if (comments != null && comments.size() > 1 && scrollNavigation.getVisibility() == View.GONE) {
                scrollNavigation.setVisibility(View.VISIBLE);

                AlphaAnimation anim = new AlphaAnimation(0.0f, 1.0f);
                anim.setDuration(400);
                anim.setRepeatMode(Animation.REVERSE);
                scrollNavigation.startAnimation(anim);
            }
        }

        updateSearchScrollTopFabPosition();
    }

    void updateSearchScrollTopFabPosition() {
        ExtendedFloatingActionButton searchScrollTopFab = host.getSearchScrollTopFab();
        if (searchScrollTopFab == null) {
            return;
        }

        int targetBottomMargin;
        View scrollNavigation = host.getScrollNavigationView();
        if (host.shouldShowNavButtons() && scrollNavigation != null && scrollNavigation.getVisibility() == View.VISIBLE) {
            int navigationHeight = scrollNavigation.getHeight();
            if (navigationHeight <= 0) {
                navigationHeight = Utils.pxFromDpInt(host.getNavigationResources(), 56);
            }
            targetBottomMargin = host.getCommentsBottomInset() + host.getScrollNavigationBaseBottomMargin()
                    + navigationHeight + Utils.pxFromDpInt(host.getNavigationResources(), 16);
        } else {
            targetBottomMargin = host.getCommentsBottomInset() + host.getSearchScrollTopFabBaseBottomMargin();
        }

        FrameLayout.LayoutParams fabParams = (FrameLayout.LayoutParams) searchScrollTopFab.getLayoutParams();
        if (fabParams.bottomMargin != targetBottomMargin) {
            fabParams.setMargins(fabParams.leftMargin, fabParams.topMargin, fabParams.rightMargin, targetBottomMargin);
            searchScrollTopFab.setLayoutParams(fabParams);
        }
    }

    void clear() {
        clearSearchedCommentHighlight();
        clearSearchedCommentScrollTopTarget();
    }

    private boolean isCommentPositionVisible(int targetPosition) {
        LinearLayoutManager layoutManager = host.getNavigationLayoutManager();
        if (layoutManager == null) {
            return false;
        }

        return targetPosition >= layoutManager.findFirstVisibleItemPosition()
                && targetPosition <= layoutManager.findLastVisibleItemPosition()
                && layoutManager.findViewByPosition(targetPosition) != null;
    }

    private void cancelSearchedCommentHighlightClear() {
        RecyclerView recyclerView = host.getNavigationRecyclerView();
        if (recyclerView != null && clearSearchedCommentHighlightRunnable != null) {
            recyclerView.removeCallbacks(clearSearchedCommentHighlightRunnable);
        }
        clearSearchedCommentHighlightRunnable = null;
    }

    private int findCommentPositionById(int commentId) {
        List<Comment> comments = host.getNavigationComments();
        if (comments == null) {
            return RecyclerView.NO_POSITION;
        }

        for (int i = 1; i < comments.size(); i++) {
            if (comments.get(i).id == commentId) {
                return i;
            }
        }

        return RecyclerView.NO_POSITION;
    }

    private int getVisibleCommentNumber(int targetPosition) {
        CommentsRecyclerViewAdapter adapter = host.getNavigationAdapter();
        if (adapter == null) {
            return targetPosition;
        }

        int visibleComments = 0;
        for (int i = 1; i <= targetPosition && i < adapter.getItemCount(); i++) {
            if (CommentsRecyclerViewAdapter.isCommentViewType(adapter.getItemViewType(i))) {
                visibleComments++;
            }
        }

        return visibleComments;
    }

    private void showSearchScrollTopFab() {
        ExtendedFloatingActionButton searchScrollTopFab = host.getSearchScrollTopFab();
        if (searchScrollTopFab == null) {
            return;
        }

        updateSearchScrollTopFabPosition();
        if (searchScrollTopFab.getVisibility() != View.VISIBLE) {
            searchScrollTopFab.show();
            searchScrollTopFab.post(this::updateSearchScrollTopFabPosition);
        }
    }

    private void hideSearchScrollTopFab() {
        ExtendedFloatingActionButton searchScrollTopFab = host.getSearchScrollTopFab();
        if (searchScrollTopFab != null && searchScrollTopFab.getVisibility() == View.VISIBLE) {
            searchScrollTopFab.hide();
        }
    }

    private int findFirstVisiblePosition() {
        LinearLayoutManager layoutManager = host.getNavigationLayoutManager();
        if (layoutManager == null) {
            return 0;
        }

        int firstVisible = layoutManager.findFirstVisibleItemPosition();

        View firstVisibleView = layoutManager.findViewByPosition(firstVisible);
        if (firstVisibleView != null) {
            int top = firstVisibleView.getTop();
            int height = firstVisibleView.getHeight();
            int scrolled = height - Math.abs(top);

            if (scrolled <= host.getTopInset()) {
                firstVisible++;
            }
        }
        return firstVisible;
    }

    private void smoothScrollPrevious(boolean topLevelOnly, boolean scaleLongScrollSpeed) {
        LinearLayoutManager layoutManager = host.getNavigationLayoutManager();
        if (layoutManager != null) {
            int firstVisible = findFirstVisiblePosition();

            int toScrollTo = findPreviousCommentPosition(firstVisible, topLevelOnly);

            startCommentSmoothScroll(toScrollTo, getCommentNavigationSpeedMultiplier(firstVisible, toScrollTo, scaleLongScrollSpeed));
        }
    }

    private void scrollPrevious(boolean topLevelOnly) {
        LinearLayoutManager layoutManager = host.getNavigationLayoutManager();
        if (layoutManager != null) {
            int firstVisible = findFirstVisiblePosition();

            int toScrollTo = findPreviousCommentPosition(firstVisible, topLevelOnly);
            scrollToCommentPosition(toScrollTo, -host.getScreenHeightInPixels());
        }
    }

    private void smoothScrollNext(boolean topLevelOnly, boolean scaleLongScrollSpeed) {
        LinearLayoutManager layoutManager = host.getNavigationLayoutManager();
        if (layoutManager != null) {
            int firstVisible = findFirstVisiblePosition();

            int toScrollTo = findNextCommentPosition(firstVisible, topLevelOnly);

            startCommentSmoothScroll(toScrollTo, getCommentNavigationSpeedMultiplier(firstVisible, toScrollTo, scaleLongScrollSpeed));
        }
    }

    private void scrollNext(boolean topLevelOnly) {
        LinearLayoutManager layoutManager = host.getNavigationLayoutManager();
        if (layoutManager != null) {
            int firstVisible = findFirstVisiblePosition();

            int toScrollTo = findNextCommentPosition(firstVisible, topLevelOnly);
            scrollToCommentPosition(toScrollTo, host.getScreenHeightInPixels());
        }
    }

    private void scrollToCommentPosition(int targetPosition, int missingViewScrollAmount) {
        LinearLayoutManager layoutManager = host.getNavigationLayoutManager();
        RecyclerView recyclerView = host.getNavigationRecyclerView();
        if (layoutManager == null || recyclerView == null) {
            return;
        }

        View toScrollToCommentView = layoutManager.findViewByPosition(targetPosition);
        if (toScrollToCommentView != null) {
            recyclerView.scrollBy(0, toScrollToCommentView.getTop() - host.getTopInset());
            return;
        }

        toScrollToCommentView = layoutManager.findViewByPosition(targetPosition);
        int guard = 0;
        int maxScrollAttempts = Math.max(1, layoutManager.getItemCount());
        while (toScrollToCommentView == null && guard++ < maxScrollAttempts) {
            recyclerView.scrollBy(0, missingViewScrollAmount);
            toScrollToCommentView = layoutManager.findViewByPosition(targetPosition);
        }
        if (toScrollToCommentView != null) {
            recyclerView.scrollBy(0, toScrollToCommentView.getTop() - host.getTopInset());
        }
    }

    private void startCommentSmoothScroll(int targetPosition, int speedMultiplier) {
        LinearLayoutManager layoutManager = host.getNavigationLayoutManager();
        RecyclerView.SmoothScroller smoothScroller = host.getNavigationSmoothScroller();
        if (layoutManager == null || smoothScroller == null) {
            return;
        }

        smoothScrollSpeedMultiplier = Math.max(1, speedMultiplier);
        smoothScroller.setTargetPosition(targetPosition);
        layoutManager.startSmoothScroll(smoothScroller);
    }

    private int getCommentNavigationSpeedMultiplier(int fromPosition, int toPosition, boolean scaleLongScrollSpeed) {
        if (!scaleLongScrollSpeed) {
            return 1;
        }

        int commentDistance = Math.abs(toPosition - fromPosition);
        return ((commentDistance - 1) / COMMENT_NAVIGATION_SPEED_STEP) + 1;
    }

    private int findPreviousCommentPosition(int firstVisible, boolean topLevelOnly) {
        List<Comment> comments = host.getNavigationComments();
        if (comments == null || comments.isEmpty()) {
            return 0;
        }

        int safeFirstVisible = Math.max(0, Math.min(firstVisible, comments.size() - 1));

        if (safeFirstVisible <= 0) {
            return 0;
        }

        if (!topLevelOnly) {
            return Math.max(safeFirstVisible - 1, 0);
        }

        for (int i = safeFirstVisible - 1; i >= 0; i--) {
            if (comments.get(i).depth == 0 || i == 0) {
                return i;
            }
        }

        return 0;
    }

    private int findNextCommentPosition(int firstVisible, boolean topLevelOnly) {
        List<Comment> comments = host.getNavigationComments();
        if (comments == null || comments.isEmpty()) {
            return 0;
        }

        int safeFirstVisible = Math.max(0, Math.min(firstVisible, comments.size() - 1));

        if (!topLevelOnly) {
            return Math.min(safeFirstVisible + 1, comments.size() - 1);
        }

        for (int i = safeFirstVisible + 1; i < comments.size(); i++) {
            if (comments.get(i).depth == 0) {
                return i;
            }
        }

        return safeFirstVisible;
    }

    interface Host {
        boolean isNavigationHostAdded();

        Context requireNavigationContext();

        Resources getNavigationResources();

        @Nullable
        List<Comment> getNavigationComments();

        @Nullable
        CommentsRecyclerViewAdapter getNavigationAdapter();

        @Nullable
        RecyclerView getNavigationRecyclerView();

        @Nullable
        LinearLayoutManager getNavigationLayoutManager();

        @Nullable
        RecyclerView.SmoothScroller getNavigationSmoothScroller();

        int getTopInset();

        int getScreenHeightInPixels();

        boolean shouldShowNavButtons();

        @Nullable
        View getScrollNavigationView();

        @Nullable
        ExtendedFloatingActionButton getSearchScrollTopFab();

        int getCommentsBottomInset();

        int getScrollNavigationBaseBottomMargin();

        int getSearchScrollTopFabBaseBottomMargin();
    }
}
