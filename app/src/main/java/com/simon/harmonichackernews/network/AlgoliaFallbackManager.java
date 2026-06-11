package com.simon.harmonichackernews.network;

import android.content.Context;
import android.util.Log;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.simon.harmonichackernews.data.Comment;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.utils.SettingsUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AlgoliaFallbackManager implements HNAPICommentLoader.CommentLoadListener {
    private static final String TAG = "AlgoliaFallbackManager";

    
    public interface FallbackListener {
        void onAlgoliaSuccess(String response);
        void onAlgoliaFailed(boolean noInternet);
        void onUsingFallback();
        void onHNAPIStoryLoaded(Story story);
        void onHNAPIFailed();
        void onAllCommentsLoaded(List<Comment> comments);
    }
    
    private final Context context;
    private final RequestQueue queue;
    private final Object requestTag;
    private final Set<String> filteredUsers;
    private final FallbackListener listener;
    
    private CommentTreeBuilder treeBuilder;
    private HNAPICommentLoader commentLoader;
    private Set<Integer> allCommentIds = new HashSet<>();
    private Set<Integer> loadedCommentIds = new HashSet<>();
    private int totalExpectedComments = 0;
    
    public AlgoliaFallbackManager(Context context, RequestQueue queue, Object requestTag, 
                                Set<String> filteredUsers, FallbackListener listener) {
        this.context = context;
        this.queue = queue;
        this.requestTag = requestTag;
        this.filteredUsers = filteredUsers;
        this.listener = listener;
        this.commentLoader = new HNAPICommentLoader(queue, requestTag, filteredUsers, this);
    }
    
    public void loadComments(int storyId, String cachedResponse) {
        if (SettingsUtils.shouldUseAlgoliaAPI(context)) {
            Log.d(TAG, "Loading storyId=" + storyId + " with Algolia, hasCachedResponse=" + (cachedResponse != null));
            loadWithAlgolia(storyId, cachedResponse);
        } else {
            Log.d(TAG, "Loading storyId=" + storyId + " with HN API because Algolia is disabled");
            loadWithHNAPI(storyId);
        }
    }
    
    private void loadWithAlgolia(int storyId, String cachedResponse) {
        String url = "https://hn.algolia.com/api/v1/items/" + storyId;
        
        StringRequest request = new StringRequest(Request.Method.GET, url,
            response -> {
                listener.onAlgoliaSuccess(response);
            },
            error -> {
                Log.w(TAG, "Algolia request failed for storyId=" + storyId + ": " + describeVolleyError(error), error);
                // If Algolia fails, try HN API
                if (error.networkResponse != null && 
                    (error.networkResponse.statusCode == 404 || error.networkResponse.statusCode >= 500) ||
                    error instanceof com.android.volley.TimeoutError) {

                    Log.d(TAG, "Falling back to HN API for storyId=" + storyId);
                    loadWithHNAPI(storyId);
                    listener.onUsingFallback();
                } else {
                    listener.onAlgoliaFailed(error.networkResponse == null);
                }
            });
            
        request.setTag(requestTag);
        request.setRetryPolicy(new DefaultRetryPolicy(15000, 2, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        queue.add(request);
    }
    
    private void loadWithHNAPI(int storyId) {
        String url = "https://hacker-news.firebaseio.com/v0/item/" + storyId + ".json";
        
        StringRequest request = new StringRequest(Request.Method.GET, url,
            response -> {
                Story story = new Story();
                if (JSONParser.updateStoryWithOfficialHNResponse(story, response)) {
                    Log.d(TAG, "HN API story loaded for storyId=" + storyId
                            + ", topLevelComments=" + (story.kids == null ? 0 : story.kids.length));
                    listener.onHNAPIStoryLoaded(story);
                    
                    // Start loading all comments
                    if (story.kids != null && story.kids.length > 0) {
                        loadAllComments(story.kids);
                    } else {
                        // No comments
                        listener.onAllCommentsLoaded(new java.util.ArrayList<>());
                    }
                } else {
                    Log.w(TAG, "HN API story parse failed for storyId=" + storyId
                            + ", responseLength=" + (response == null ? 0 : response.length()));
                    listener.onHNAPIFailed();
                }
            },
            error -> {
                Log.w(TAG, "HN API story request failed for storyId=" + storyId + ": " + describeVolleyError(error), error);
                listener.onHNAPIFailed();
            });
            
        request.setTag(requestTag);
        request.setRetryPolicy(new DefaultRetryPolicy(15000, 2, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        queue.add(request);
    }
    
    private void loadAllComments(int[] topLevelIds) {
        treeBuilder = new CommentTreeBuilder(topLevelIds);
        allCommentIds.clear();
        loadedCommentIds.clear();
        totalExpectedComments = topLevelIds.length;
        Log.d(TAG, "Loading HN API comments, initialTopLevelCount=" + topLevelIds.length);
        
        // Discover all comment IDs by loading each comment and checking for children
        for (int commentId : topLevelIds) {
            allCommentIds.add(commentId);
            commentLoader.loadComment(commentId, 0);
        }
        
        totalExpectedComments = allCommentIds.size();
    }
    
    @Override
    public void onCommentLoaded(Comment comment) {
        treeBuilder.addComment(comment);
        loadedCommentIds.add(comment.id);
        
        // Load children if they exist
        if (comment.kidsIds != null && comment.kidsIds.length > 0) {
            for (int childId : comment.kidsIds) {
                if (!allCommentIds.contains(childId)) {
                    allCommentIds.add(childId);
                    totalExpectedComments++;
                    commentLoader.loadComment(childId, comment.depth + 1);
                }
            }
        }
        
        // Check if all comments are loaded
        if (loadedCommentIds.size() >= totalExpectedComments) {
            List<Comment> orderedComments = treeBuilder.buildOrderedTree();
            listener.onAllCommentsLoaded(orderedComments);
        }
    }
    
    @Override
    public void onCommentFailed(int commentId) {
        loadedCommentIds.add(commentId); // Mark as processed even if failed
        Log.w(TAG, "HN API comment failed, commentId=" + commentId
                + ", loadedOrFailed=" + loadedCommentIds.size()
                + ", totalExpected=" + totalExpectedComments);
        
        // Check if we're done (including failed ones)
        if (loadedCommentIds.size() >= totalExpectedComments) {
            List<Comment> orderedComments = treeBuilder.buildOrderedTree();
            listener.onAllCommentsLoaded(orderedComments);
        }
    }

    private static String describeVolleyError(VolleyError error) {
        if (error == null) {
            return "unknown VolleyError";
        }
        String status = error.networkResponse == null
                ? "noNetworkResponse"
                : "statusCode=" + error.networkResponse.statusCode;
        return error.getClass().getSimpleName() + ", " + status + ", message=" + error.getMessage();
    }
}
