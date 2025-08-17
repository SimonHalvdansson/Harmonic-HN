package com.simon.harmonichackernews.network;

import android.content.Context;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.simon.harmonichackernews.data.Comment;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.utils.SettingsUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AlgoliaFallbackManager implements HNAPICommentLoader.CommentLoadListener {
    
    public interface FallbackListener {
        void onAlgoliaSuccess(String response);
        void onAlgoliaFailed();
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
            loadWithAlgolia(storyId, cachedResponse);
        } else {
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
                // If Algolia fails, try HN API
                if (error.networkResponse != null && 
                    (error.networkResponse.statusCode == 404 || error.networkResponse.statusCode >= 500) ||
                    error instanceof com.android.volley.TimeoutError) {
                    loadWithHNAPI(storyId);
                } else {
                    listener.onAlgoliaFailed();
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
                    listener.onHNAPIStoryLoaded(story);
                    
                    // Start loading all comments
                    if (story.kids != null && story.kids.length > 0) {
                        loadAllComments(story.kids);
                    } else {
                        // No comments
                        listener.onAllCommentsLoaded(new java.util.ArrayList<>());
                    }
                } else {
                    listener.onHNAPIFailed();
                }
            },
            error -> {
                error.printStackTrace();
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
        
        // Check if we're done (including failed ones)
        if (loadedCommentIds.size() >= totalExpectedComments) {
            List<Comment> orderedComments = treeBuilder.buildOrderedTree();
            listener.onAllCommentsLoaded(orderedComments);
        }
    }
}
