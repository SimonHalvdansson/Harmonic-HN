package com.simon.harmonichackernews;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import com.simon.harmonichackernews.data.Story;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Keeps the in-memory story screen state while its activity is recreated. */
public class StoriesViewModel extends ViewModel {

    @Nullable
    private State state;

    @Nullable
    State getState() {
        return state;
    }

    void setState(@Nullable State state) {
        this.state = state;
    }

    static final class State {
        final ArrayList<Story> mainStories = new ArrayList<>();
        final ArrayList<Story> searchStories = new ArrayList<>();
        final ArrayList<Story> bookmarkStories = new ArrayList<>();
        final ArrayList<Story> userItemListStories = new ArrayList<>();
        final Set<Integer> userItemListCommentIds = new HashSet<>();

        @Nullable String mainTypeLabel;
        @Nullable String searchTypeLabel;
        int mainVisibleStoryCount;
        int searchVisibleStoryCount;
        boolean mainShowLoadMoreButton;
        boolean searchShowLoadMoreButton;

        boolean searching;
        String lastSearch = "";
        int mainLoadedTo;
        int searchLoadedTo;
        boolean mainShowingCached;
        boolean searchShowingCached;
        boolean mainLoadingFailed;
        boolean mainLoadingFailedServerError;
        boolean mainLoadingFailedRateLimited;
        boolean searchLoadingFailed;
        boolean searchLoadingFailedServerError;
        boolean searchLoadingFailedRateLimited;
        int mainAlgoliaHitsPerPage;
        int searchAlgoliaHitsPerPage;
        int mainLastAlgoliaTopStoriesStartTime;
        int searchLastAlgoliaTopStoriesStartTime;

        long lastLoaded;
        boolean updateButtonShowing;
        int userItemListFilter;
        long frontPageDayUtcMillis = -1L;
        @Nullable String scrapedFrontpageNextPageUrl;

        int mainFirstVisiblePosition = -1;
        int mainFirstVisibleTop;
        int searchFirstVisiblePosition = -1;
        int searchFirstVisibleTop;
        boolean appBarCollapsed;

        int searchSortIndex;
        int searchDateRangeIndex;
        int searchMinimumPointsIndex;
        int searchMinimumCommentsIndex;
        boolean searchOnlyClicked;
    }
}
