package com.simon.harmonichackernews.network;

import android.util.Log;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.simon.harmonichackernews.data.Comment;
import org.json.JSONException;
import java.util.Set;

public class HNAPICommentLoader {
    private static final String TAG = "HNAPICommentLoader";

    
    public interface CommentLoadListener {
        void onCommentLoaded(Comment comment);
        void onCommentFailed(int commentId);
    }
    
    private final RequestQueue queue;
    private final Object requestTag;
    private final Set<String> filteredUsers;
    private final CommentLoadListener listener;
    
    public HNAPICommentLoader(RequestQueue queue, Object requestTag, Set<String> filteredUsers, CommentLoadListener listener) {
        this.queue = queue;
        this.requestTag = requestTag;
        this.filteredUsers = filteredUsers;
        this.listener = listener;
    }
    
    public void loadComment(int commentId, int depth) {
        String url = "https://hacker-news.firebaseio.com/v0/item/" + commentId + ".json";
        
        StringRequest request = new StringRequest(Request.Method.GET, url,
            response -> {
                try {
                    Comment comment = JSONParser.parseOfficialHNCommentResponse(response);
                    if (comment != null && comment.by != null && !filteredUsers.contains(comment.by.toLowerCase())) {
                        comment.depth = depth;
                        listener.onCommentLoaded(comment);
                    } else {
                        Log.w(TAG, "Skipping HN API comment, commentId=" + commentId
                                + ", parsed=" + (comment != null)
                                + ", hasAuthor=" + (comment != null && comment.by != null)
                                + ", responseLength=" + (response == null ? 0 : response.length()));
                        listener.onCommentFailed(commentId);
                    }
                } catch (JSONException e) {
                    Log.w(TAG, "Failed to parse HN API comment, commentId=" + commentId
                            + ", responseLength=" + (response == null ? 0 : response.length()), e);
                    listener.onCommentFailed(commentId);
                }
            },
            error -> {
                Log.w(TAG, "HN API comment request failed, commentId=" + commentId + ": " + VolleyErrorUtils.describe(error), error);
                listener.onCommentFailed(commentId);
            });

        request.setTag(requestTag);
        request.setRetryPolicy(new DefaultRetryPolicy(10000, 2, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        queue.add(request);
    }

}
