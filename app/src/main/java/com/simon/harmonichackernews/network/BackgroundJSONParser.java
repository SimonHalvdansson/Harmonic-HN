package com.simon.harmonichackernews.network;

import android.os.Handler;
import android.os.Looper;

import com.simon.harmonichackernews.data.Story;

import org.json.JSONException;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    public interface StoryUpdateCallback {
        /**
         * Called on the main thread when parsing succeeds
         */
        void onParseSuccess();

        /**
         * Called on the main thread when parsing fails
         * @param error The exception that occurred
         */
        void onParseError(JSONException error);
    }



    /**
     * Parse Algolia JSON response on a background thread
     * @param jsonResponse The JSON string to parse
     * @param callback Callback to receive results on main thread
     */
    public static void parseAlgoliaJson(final String jsonResponse, final AlgoliaParseCallback callback) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Story> stories = JSONParser.algoliaJsonToStories(jsonResponse);

                    // Post result to main thread
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onParseSuccess(stories);
                        }
                    });
                } catch (final JSONException e) {
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
}
