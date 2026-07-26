package com.simon.harmonichackernews.utils;

import android.text.TextUtils;

import com.simon.harmonichackernews.data.Story;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class SearchRelevanceUtils {

    public static void sortStoriesByRelevance(List<Story> stories, String query) {
        String normalizedQuery = normalize(query);
        if (stories == null || stories.size() < 2 || TextUtils.isEmpty(normalizedQuery)) {
            return;
        }

        Map<Story, Integer> relevanceScores = new IdentityHashMap<>(stories.size());
        for (Story story : stories) {
            relevanceScores.put(story, score(story, normalizedQuery));
        }

        Collections.sort(stories, (left, right) -> {
            int leftScore = relevanceScores.get(left);
            int rightScore = relevanceScores.get(right);

            if (leftScore != rightScore) {
                return Integer.compare(rightScore, leftScore);
            }

            if (left.score != right.score) {
                return Integer.compare(right.score, left.score);
            }

            if (left.descendants != right.descendants) {
                return Integer.compare(right.descendants, left.descendants);
            }

            return Integer.compare(right.time, left.time);
        });
    }

    private static int score(Story story, String normalizedQuery) {
        if (story == null || story.title == null) {
            return 0;
        }

        String title = normalize(story.title);
        int phraseIndex = title.indexOf(normalizedQuery);
        if (phraseIndex < 0) {
            return 0;
        }

        int score = 10_000;
        if (title.equals(normalizedQuery)) {
            score += 20_000;
        }
        if (phraseIndex == 0) {
            score += 8_000;
        }
        if (isWordBoundaryMatch(title, phraseIndex, normalizedQuery.length())) {
            score += 4_000;
        }

        score += Math.max(0, 2_000 - (phraseIndex * 100));
        score += Math.max(0, 1_000 - title.length());

        return score;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.trim().toLowerCase();
    }

    private static boolean isWordBoundaryMatch(String title, int start, int length) {
        int end = start + length;
        return isBoundary(title, start - 1) && isBoundary(title, end);
    }

    private static boolean isBoundary(String title, int index) {
        return index < 0
                || index >= title.length()
                || !Character.isLetterOrDigit(title.charAt(index));
    }
}
