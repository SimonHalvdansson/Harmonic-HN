package com.simon.harmonichackernews.network;

import android.os.Handler;
import android.os.Looper;

import com.simon.harmonichackernews.data.Story;

import org.json.JSONException;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class BackgroundJSONParser {

    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface AlgoliaParseCallback {
        /**
         * Called on the main thread when parsing succeeds
         * @param stories List of parsed stories
         */
        void onParseSuccess(List<Story> stories);

        /**
         * Called on the main thread when parsing fails
         * @param error The exception that occurred
         */
        void onParseError(JSONException error);
    }

    public interface AlgoliaCommentsParseCallback {
        /**
         * Called on the main thread when parsing succeeds.
         *
         * @param response Parsed story and comments
         */
        void onParseSuccess(JSONParser.AlgoliaCommentsResponse response);

        /**
         * Called on the main thread when parsing fails.
         *
         * @param error The exception that occurred
         */
        void onParseError(IOException error);
    }

    /**
     * Parse Algolia JSON response on a background thread
     * @param jsonResponse The JSON string to parse
     * @param callback Callback to receive results on main thread
     */
    public static Future<?> parseAlgoliaJson(final String jsonResponse, final AlgoliaParseCallback callback) {
        return executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Story> stories = JSONParser.algoliaJsonToStories(jsonResponse);
                    if (Thread.interrupted()) {
                        return;
                    }

                    // Post result to main thread
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onParseSuccess(stories);
                        }
                    });
                } catch (final JSONException e) {
                    if (Thread.interrupted()) {
                        return;
                    }
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onParseError(e);
                        }
                    });
                }
            }
        });
    }

    /**
     * Parse an Algolia story-with-comments response on a background thread.
     *
     * @param jsonResponse The JSON string to parse
     * @param topLevelCommentIds Preferred top-level comment ordering
     * @param filteredUsers Usernames whose comments should be omitted
     * @param callback Callback to receive results on the main thread
     */
    public static Future<?> parseAlgoliaCommentsJson(
            final String jsonResponse,
            final int[] topLevelCommentIds,
            final Set<String> filteredUsers,
            final AlgoliaCommentsParseCallback callback) {
        return executorService.submit(() -> {
            try {
                final JSONParser.AlgoliaCommentsResponse response =
                        JSONParser.parseAlgoliaCommentsResponse(
                                jsonResponse,
                                topLevelCommentIds,
                                filteredUsers);
                if (Thread.interrupted()) {
                    return;
                }
                mainHandler.post(() -> callback.onParseSuccess(response));
            } catch (final IOException e) {
                if (Thread.interrupted()) {
                    return;
                }
                mainHandler.post(() -> callback.onParseError(e));
            }
        });
    }
}
