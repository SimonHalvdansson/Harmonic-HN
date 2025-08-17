package com.simon.harmonichackernews.network;

import android.content.Context;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.simon.harmonichackernews.data.Comment;
import org.json.JSONException;
import java.util.Set;

public class HNAPICommentLoader {
    
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
                    if (comment != null && !filteredUsers.contains(comment.by.toLowerCase())) {
                        comment.depth = depth;
                        listener.onCommentLoaded(comment);
                    } else {
                        listener.onCommentFailed(commentId);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    listener.onCommentFailed(commentId);
                }
            },
            error -> {
                error.printStackTrace();
                listener.onCommentFailed(commentId);
            });

        request.setTag(requestTag);
        request.setRetryPolicy(new DefaultRetryPolicy(10000, 2, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        queue.add(request);
    }
}
