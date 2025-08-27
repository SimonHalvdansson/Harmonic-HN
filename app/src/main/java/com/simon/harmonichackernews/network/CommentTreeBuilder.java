package com.simon.harmonichackernews.network;

import com.simon.harmonichackernews.data.Comment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommentTreeBuilder {
    
    private final Map<Integer, Comment> commentMap = new HashMap<>();
    private final Map<Integer, List<Comment>> childrenMap = new HashMap<>();
    private final int[] topLevelIds;
    
    public CommentTreeBuilder(int[] topLevelIds) {
        this.topLevelIds = topLevelIds;
    }
    
    public void addComment(Comment comment) {
        commentMap.put(comment.id, comment);
        
        // Add to children map if it has a parent
        if (comment.parent != 0) {
            List<Comment> list = childrenMap.get(comment.parent);
            if (list == null) {
                list = new ArrayList<>();
                childrenMap.put(comment.parent, list);
            }
            list.add(comment);
        }
    }
    
    public List<Comment> buildOrderedTree() {
        List<Comment> orderedComments = new ArrayList<>();
        
        // Process top-level comments in the order specified by topLevelIds
        for (int topLevelId : topLevelIds) {
            Comment topComment = commentMap.get(topLevelId);
            if (topComment != null) {
                topComment.depth = 0;
                orderedComments.add(topComment);
                addChildrenToList(orderedComments, topComment, 1);
            }
        }
        
        return orderedComments;
    }
    
    private void addChildrenToList(List<Comment> orderedComments, Comment parent, int depth) {
        List<Comment> children = childrenMap.get(parent.id);
        if (children == null) return;
        
        // Sort children by the order they appear in parent's kidsIds if available
        if (parent.kidsIds != null && parent.kidsIds.length > 0) {
            List<Comment> orderedChildren = new ArrayList<>();
            for (int childId : parent.kidsIds) {
                for (Comment child : children) {
                    if (child.id == childId) {
                        orderedChildren.add(child);
                        break;
                    }
                }
            }
            children = orderedChildren;
        }
        
        // Add children and their subtrees
        for (Comment child : children) {
            child.depth = depth;
            orderedComments.add(child);
            addChildrenToList(orderedComments, child, depth + 1);
        }
    }
    
    public int getTotalComments() {
        return commentMap.size();
    }
    
    public void clear() {
        commentMap.clear();
        childrenMap.clear();
    }
}
