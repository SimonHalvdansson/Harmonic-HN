package com.simon.harmonichackernews;

import android.net.Uri;

import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.utils.SearchRelevanceUtils;

import java.util.ArrayList;
import java.util.List;

class StorySearchController {
    static final int ALGOLIA_HITS_INCREMENT = 200;

    private static final String[] SEARCH_SORT_LABELS = new String[]{"Relevance", "Newest"};
    private static final String[] SEARCH_DATE_RANGE_LABELS = new String[]{"All time", "Past day", "Past week", "Past month", "Past year"};
    private static final int[] SEARCH_DATE_RANGE_DAYS = new int[]{0, 1, 7, 30, 365};
    private static final String[] SEARCH_MINIMUM_POINTS_LABELS = new String[]{"Any points", "5+ points", "25+ points", "100+ points"};
    private static final int[] SEARCH_MINIMUM_POINTS = new int[]{0, 5, 25, 100};
    private static final String[] SEARCH_MINIMUM_COMMENTS_LABELS = new String[]{"Any comments", "5+ comments", "25+ comments", "100+ comments"};
    private static final int[] SEARCH_MINIMUM_COMMENTS = new int[]{0, 5, 25, 100};

    interface StoryFilter {
        boolean shouldFilterStory(Story story);
    }

    private int sortIndex = 0;
    private int dateRangeIndex = 0;
    private int minimumPointsIndex = 0;
    private int minimumCommentsIndex = 0;
    private boolean onlyClicked = false;

    void resetOptions() {
        sortIndex = 0;
        dateRangeIndex = 0;
        minimumPointsIndex = 0;
        minimumCommentsIndex = 0;
        onlyClicked = false;
    }

    String[] getSortLabels() {
        return SEARCH_SORT_LABELS;
    }

    String[] getDateRangeLabels() {
        return SEARCH_DATE_RANGE_LABELS;
    }

    String[] getMinimumPointsLabels() {
        return SEARCH_MINIMUM_POINTS_LABELS;
    }

    String[] getMinimumCommentsLabels() {
        return SEARCH_MINIMUM_COMMENTS_LABELS;
    }

    int getSortIndex() {
        return sortIndex;
    }

    void setSortIndex(int sortIndex) {
        this.sortIndex = sortIndex;
    }

    int getDateRangeIndex() {
        return dateRangeIndex;
    }

    void setDateRangeIndex(int dateRangeIndex) {
        this.dateRangeIndex = dateRangeIndex;
    }

    int getMinimumPointsIndex() {
        return minimumPointsIndex;
    }

    void setMinimumPointsIndex(int minimumPointsIndex) {
        this.minimumPointsIndex = minimumPointsIndex;
    }

    int getMinimumCommentsIndex() {
        return minimumCommentsIndex;
    }

    void setMinimumCommentsIndex(int minimumCommentsIndex) {
        this.minimumCommentsIndex = minimumCommentsIndex;
    }

    boolean isOnlyClicked() {
        return onlyClicked;
    }

    void toggleOnlyClicked() {
        onlyClicked = !onlyClicked;
    }

    String getSortLabel() {
        return SEARCH_SORT_LABELS[sortIndex];
    }

    String getDateRangeLabel() {
        return SEARCH_DATE_RANGE_LABELS[dateRangeIndex];
    }

    String getMinimumPointsLabel() {
        return SEARCH_MINIMUM_POINTS_LABELS[minimumPointsIndex];
    }

    String getMinimumCommentsLabel() {
        return SEARCH_MINIMUM_COMMENTS_LABELS[minimumCommentsIndex];
    }

    int getCurrentTopStoriesStartTime(StoryType storyType) {
        int currentTime = (int) (System.currentTimeMillis() / 1000);
        if (storyType == StoryType.LAST_24_HOURS) {
            return currentTime - 60 * 60 * 24;
        } else if (storyType == StoryType.LAST_48_HOURS) {
            return currentTime - 60 * 60 * 48;
        } else if (storyType == StoryType.LAST_WEEK) {
            return currentTime - 60 * 60 * 24 * 7;
        }

        return currentTime;
    }

    String buildTopStoriesUrl(int startTime, int hitsPerPage) {
        return Uri.parse("https://hn.algolia.com/api/v1/search")
                .buildUpon()
                .appendQueryParameter("tags", "story")
                .appendQueryParameter("numericFilters", "created_at_i>" + startTime)
                .appendQueryParameter("hitsPerPage", String.valueOf(hitsPerPage))
                .build()
                .toString();
    }

    String buildSearchUrl(String query, int hitsPerPage) {
        String endpoint = sortIndex == 0
                ? "https://hn.algolia.com/api/v1/search"
                : "https://hn.algolia.com/api/v1/search_by_date";
        Uri.Builder builder = Uri.parse(endpoint).buildUpon()
                .appendQueryParameter("query", query)
                .appendQueryParameter("tags", "story")
                .appendQueryParameter("hitsPerPage", String.valueOf(hitsPerPage))
                .appendQueryParameter("typoTolerance", "min");

        List<String> numericFilters = new ArrayList<>();
        int days = SEARCH_DATE_RANGE_DAYS[dateRangeIndex];
        if (days > 0) {
            long startTime = (System.currentTimeMillis() / 1000L) - (days * 24L * 60L * 60L);
            numericFilters.add("created_at_i>=" + startTime);
        }

        int minimumPoints = SEARCH_MINIMUM_POINTS[minimumPointsIndex];
        if (minimumPoints > 0) {
            numericFilters.add("points>=" + minimumPoints);
        }

        int minimumComments = SEARCH_MINIMUM_COMMENTS[minimumCommentsIndex];
        if (minimumComments > 0) {
            numericFilters.add("num_comments>=" + minimumComments);
        }

        if (!numericFilters.isEmpty()) {
            builder.appendQueryParameter("numericFilters", android.text.TextUtils.join(",", numericFilters));
        }

        return builder.build().toString();
    }

    boolean canLoadMoreResults(int rawParsedStoryCount, int hitsPerPage) {
        return rawParsedStoryCount >= hitsPerPage;
    }

    String normalizeQuery(String query) {
        return query == null ? "" : query.trim().toLowerCase();
    }

    boolean shouldIncludeOnlyClickedStory(Story story,
                                          String normalizedQuery,
                                          StoryFilter storyFilter) {
        if (story.title == null || !story.title.toLowerCase().contains(normalizedQuery)) {
            return false;
        }

        int minimumTime = getMinimumTimeSeconds();
        if (minimumTime > 0 && story.time < minimumTime) {
            return false;
        }

        int minimumPoints = SEARCH_MINIMUM_POINTS[minimumPointsIndex];
        if (minimumPoints > 0 && story.score < minimumPoints) {
            return false;
        }

        int minimumComments = SEARCH_MINIMUM_COMMENTS[minimumCommentsIndex];
        if (minimumComments > 0 && story.descendants < minimumComments) {
            return false;
        }

        return !storyFilter.shouldFilterStory(story);
    }

    void sortOnlyClickedResultsIfNeeded(List<Story> stories, String query) {
        if (sortIndex == 0) {
            SearchRelevanceUtils.sortStoriesByRelevance(stories, query);
        }
    }

    private int getMinimumTimeSeconds() {
        int days = SEARCH_DATE_RANGE_DAYS[dateRangeIndex];
        if (days <= 0) {
            return 0;
        }

        return (int) ((System.currentTimeMillis() / 1000L) - (days * 24L * 60L * 60L));
    }
}
