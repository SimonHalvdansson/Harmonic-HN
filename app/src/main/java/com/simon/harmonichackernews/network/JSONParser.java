package com.simon.harmonichackernews.network;

import android.text.TextUtils;

import com.simon.harmonichackernews.adapters.CommentsRecyclerViewAdapter;
import com.simon.harmonichackernews.data.Comment;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.utils.StoryUpdate;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class JSONParser {

    public final static String ALGOLIA_ERROR_STRING = "{\"status\":404,\"error\":\"Not Found\"}";

    public static List<Story> algoliaJsonToStories(String response) throws JSONException {
        List<Story> stories = new ArrayList<>();

        JSONObject parentObject = new JSONObject(response);

        JSONArray hits = parentObject.getJSONArray("hits");

        for (int i = 0; i < hits.length(); i++) {
            JSONObject hit = hits.getJSONObject(i);

            Story story = new Story();

            boolean isComment = hit.getJSONArray("_tags").get(0).equals("comment");

            story.title = isComment ? hit.getString("story_title") : hit.optString("title");
            story.score = hit.optInt("points");
            story.by = hit.getString("author");
            story.descendants = hit.optInt("num_comments");
            story.id = Integer.parseInt(hit.getString("objectID"));
            story.time = hit.getInt("created_at_i");
            story.loaded = true;
            story.loadingFailed = false;
            story.clicked = false;

            if (hit.has("url") && !hit.getString("url").equals("null") && !hit.getString("url").isEmpty()) {
                story.url = hit.getString("url");
                story.isLink = true;
            } else {
                story.url = "https://news.ycombinator.com/item?id=" + story.id;
                story.isLink = false;
            }

            if (hit.has("story_text") && !hit.getString("story_text").equals("null")) {
                story.text = hit.getString("story_text");
            }

            if (isComment) {
                story.isComment = true;
                story.text = hit.getString("comment_text");
                story.commentMasterTitle = hit.getString("story_title");
                story.commentMasterId = hit.getInt("story_id");
                if (hit.has("story_url") && !hit.getString("story_url").equals("null")) {
                    story.commentMasterUrl = hit.getString("story_url");
                    story.isLink = true;
                } else {
                    story.isLink = false;
                }
                if (!TextUtils.isEmpty(story.title) && story.title.equals("null")) {
                    story.title = "Comment by " + story.by;
                }
            }

            updatePdfProperties(story);

            stories.add(story);
        }

        return stories;
    }

    public static boolean updateStoryWithHNJson(String response, Story story) throws JSONException {
        if (response.equals("null")) {
            return false;
        }

        JSONObject jsonObject = new JSONObject(response);

        if (jsonObject.names().length() == 2 || !jsonObject.has("by")) {
            return false;
        }

        if (jsonObject.has("type") && jsonObject.getString("type").equals("comment")) {
            return updateStoryWithHNCommentJson(jsonObject, story);
        }

        story.update(
                jsonObject.getString("by"),
                jsonObject.getInt("id"),
                jsonObject.getInt("score"),
                jsonObject.getInt("time"),
                jsonObject.getString("title")
        );

        if (jsonObject.has("descendants")) {
            story.descendants = jsonObject.getInt("descendants");
        } else {
            story.descendants = 0;
        }

        if (jsonObject.has("type") && jsonObject.getString("type").equals("job")) {
            story.isJob = true;
        }

        if (jsonObject.has("type") && jsonObject.getString("type").equals("poll") && jsonObject.has("parts")) {
            JSONArray pollOptionsJson = jsonObject.getJSONArray("parts");
            int[] pollOptions = new int[pollOptionsJson.length()];
            for (int i = 0; i < pollOptionsJson.length(); i++) {
                pollOptions[i] = pollOptionsJson.getInt(i);
            }

            story.pollOptions = pollOptions;
        }

        if (jsonObject.has("kids")) {
            JSONArray kidsJsonArray = jsonObject.getJSONArray("kids");
            int[] kids = new int[kidsJsonArray.length()];

            for (int i = 0; i < kidsJsonArray.length(); i++) {
                kids[i] = kidsJsonArray.getInt(i);
            }

            story.kids = kids;
        }

        if (jsonObject.has("url")) {
            story.url = jsonObject.getString("url");
            story.isLink = true;
        } else {
            story.url = "https://news.ycombinator.com/item?id=" + story.id;
            story.isLink = false;
        }

        if (jsonObject.has("text")) {
            story.text = preprocessHtml(jsonObject.getString("text"));
        }

        updatePdfProperties(story);

        story.loaded = true;
        story.loadingFailed = false;

        return true;
    }

    public static boolean updateStoryWithHNCommentJson(JSONObject jsonObject, Story story) throws JSONException {
        if (jsonObject.has("deleted") && jsonObject.getBoolean("deleted")) {
            return false;
        }

        // setting the score to -1 means it doesn't get shown
        story.update(
                jsonObject.getString("by"),
                jsonObject.getInt("id"),
                0,
                jsonObject.getInt("time"),
                "Comment by " + jsonObject.getString("by")
        );

        story.isComment = true;

        if (jsonObject.has("kids")) {
            story.descendants = jsonObject.getJSONArray("kids").length();
        } else {
            story.descendants = 0;
        }

        if (jsonObject.has("kids")) {
            JSONArray kidsJsonArray = jsonObject.getJSONArray("kids");
            int[] kids = new int[kidsJsonArray.length()];

            for (int i = 0; i < kidsJsonArray.length(); i++) {
                kids[i] = kidsJsonArray.getInt(i);
            }

            story.kids = kids;
        }

        story.url = "https://news.ycombinator.com/item?id=" + story.id;
        story.isLink = false;
        if (jsonObject.has("text")) {
            story.text = preprocessHtml(jsonObject.getString("text"));
        }

        story.loaded = true;
        story.loadingFailed = false;

        return true;
    }

    public static void updatePdfProperties(Story story) {
        if (story.url.endsWith(".pdf") || story.title.endsWith("[pdf]") || story.title.endsWith("(pdf)")) {
            story.pdfTitle = story.title;

            String[] suffixes = {" [pdf]", "[pdf]", " (pdf)", "(pdf)"};
            for (String suffix : suffixes) {
                if (story.pdfTitle.endsWith(suffix)) {
                    story.pdfTitle = story.pdfTitle.substring(0, story.pdfTitle.length() - suffix.length());
                    break;
                }
            }
        }
    }

    public static boolean updateStoryInformation(Story story, JSONObject item, boolean forceRefresh, int oldCommentCount, int newCommentCount) throws JSONException {
        boolean changed;
        String newTitle = item.getString("title");
        String oldFormattedTime = story.getTimeFormatted();

        int newScore = item.optInt("points", 0);

        if (TextUtils.isEmpty(story.title)) {
            changed = true;
        } else {
            changed = (!newTitle.equals(story.title) ||
                    newScore != story.score ||
                    !oldFormattedTime.equals(story.getTimeFormatted()) ||
                    oldCommentCount != newCommentCount);
        }

        story.time = item.getInt("created_at_i");

        if (item.getString("type").equals("comment")) {
            story.title = "Comment by " + item.getString("author");
            story.isLink = false;
            story.url = "https://news.ycombinator.com/item?id=" + item.getString("story_id");
            story.isComment = true;
        } else {
            story.title = item.getString("title");
            story.isLink = item.has("url") && !item.getString("url").equals("null") && !item.getString("url").equals("");

            if (story.isLink) {
                story.url = item.getString("url");
            } else {
                story.url = "https://news.ycombinator.com/item?id=" + story.id;
            }

            updatePdfProperties(story);
        }

        if (item.has("text") && !item.getString("text").equals("null")) {
            story.text = preprocessHtml(item.getString("text"));
        }

        story.descendants = newCommentCount - 1; // -1 for header
        story.id = item.getInt("id");
        story.score = item.optInt("points", 0);
        story.by = item.getString("author");
        story.loaded = true;

        if (forceRefresh) {
            StoryUpdate.updateStory(story);
        }

        return changed;
    }

    public static boolean readChildAndParseSubchilds(JSONObject child, List<Comment> comments, CommentsRecyclerViewAdapter adapter, int depth, int[] prioTop) throws JSONException {
        /*
         * Remark: Right now we're only updating old comments, not deleting those who are not there
         * anymore but that is probably just nice
         */
        // this is to be able to say if we should resort the list if we use non-default sorting
        boolean placedNew = false;

        if (!child.has("text") || child.getString("text").equals("null")) {
            return false;
        }

        JSONArray children = child.has("children") ? child.getJSONArray("children") : null;

        Comment comment = new Comment();

        comment.depth = depth;
        comment.parent = child.getInt("parent_id");
        comment.expanded = true;
        comment.by = child.getString("author");
        comment.text = preprocessHtml(child.getString("text"));
        comment.time = child.getInt("created_at_i");
        comment.id = child.getInt("id");
        comment.children = children == null ? 0 : children.length();

        // Let's see if a comment with this ID already is placed, in that case we'll just replace it and call notifyitemchanged
        boolean newComment = true;

        for (int i = 1; i < comments.size(); i++) {
            if (comments.get(i).id == comment.id) {
                newComment = false;
                Comment oldComment = comments.get(i);
                if (!oldComment.text.equals(comment.text)) {
                    oldComment.text = comment.text;
                    adapter.notifyItemChanged(i);
                }

                break;
            }
        }

        if (newComment) {
            placedNew = true;
            // now for placing, if it's a top level comment we should attempt to follow the prioList
            if (comment.depth == 0) {

                int prioIndex = -1;
                if (prioTop != null) {
                    // only attempt to change the index if priotop is nonnull
                    for (int i = 0; i < prioTop.length; i++) {
                        if (prioTop[i] == comment.id) {
                            prioIndex = i;
                            break;
                        }
                    }
                }

                if (prioIndex == -1) {
                    // add it last
                    comments.add(comments.size(), comment);
                    adapter.notifyItemInserted(comments.size() - 1);
                } else {
                    if (prioIndex == 0) {
                        comments.add(1, comment);
                        adapter.notifyItemInserted(1);
                    } else {
                        // lets find the last top level comment which has priority higher than prioIndex
                        int prioBeforeIndex = -1;

                        for (int i = 0; i < comments.size(); i++) {
                            // search all comments...
                            if (comments.get(i).depth == 0) {
                                // and only care about top level ones...
                                int searchId = comments.get(i).id;
                                // and check if its id
                                for (int j = 0; j < prioIndex; j++) {
                                    // is higher priority
                                    if (prioTop[j] == searchId) {
                                        // if so, lets save the index
                                        prioBeforeIndex = i;
                                        break;
                                    }
                                }
                            }
                        }
                        // if we are priority and can't find anything with higher priority, we should be placed at the top
                        if (prioBeforeIndex == -1) {
                            comments.add(1, comment);
                            adapter.notifyItemInserted(1);
                        } else {
                            // otherwise, lets search for the next depth = 0 after the comment with higher priority
                            int newLocation = -1;

                            // we found parent, lets start searching for when it ends
                            for (int i = prioBeforeIndex + 1; i < comments.size(); i++) {
                                if (comments.get(i).depth == 0) {
                                    // next time we find a depth zero comment, need to insert ours there
                                    newLocation = i;
                                    break;
                                }
                            }

                            // if we didn't find a new location, then just place it at the bottom
                            if (newLocation == -1) {
                                comments.add(comments.size(), comment);
                                adapter.notifyItemInserted(comments.size() - 1);
                            } else {
                                comments.add(newLocation, comment);
                                adapter.notifyItemInserted(newLocation);
                            }
                        }
                    }
                }
            } else {
                // if it's not a top level comment, let's find its parent and place so that top answers have many children
                boolean foundParent = false;

                for (int i = 1; i < comments.size(); i++) {
                    if (comments.get(i).id == comment.parent) {
                        /*OLD
                        *
                        * comments.add(i + 1, comment);
                            adapter.notifyItemInserted(i + 1);
                            * break;
                        *
                        *BELOW IS NOT QUITE WORKING YET TODO
                        * */

                        foundParent = true;
                        // having found the parent, lets keep going until we find a comment with fewer children or depth goes up
                        boolean placed = false;
                        for (int j = 1; j < comments.size() - i; j++) {
                            Comment candidate = comments.get(i + j);
                            if (candidate.parent == comment.parent && candidate.children <= comment.children) {
                                placed = true;
                                comments.add(i + j, comment);
                                adapter.notifyItemInserted(i + j);
                                break;
                            }
                        }

                        if (!placed) {
                            comments.add(i + 1, comment);
                            adapter.notifyItemInserted(i + 1);
                        }
                        break;
                    }
                }

                // and if we can't find the parent, lets put it at the bottom
                if (!foundParent) {
                    comments.add(comments.size(), comment);
                    adapter.notifyItemInserted(comments.size() - 1);
                }
            }
        }

        if (children != null) {
            for (int i = 0; i < children.length(); i++) {
                boolean childPlaced = readChildAndParseSubchilds(children.getJSONObject(i), comments, adapter, depth + 1, prioTop);
                if (childPlaced) {
                    placedNew = true;
                }
            }
        }
        return placedNew;
    }

    public static boolean updateStoryWithAlgoliaResponse(Story story, String response) {
        try {
            JSONObject item = new JSONObject(response);

            // for comment count we need to manually count
            story.descendants = item.getJSONArray("children").length();

            story.time = item.getInt("created_at_i");
            story.title = item.getString("title");

            story.isLink = item.has("url") && !item.getString("url").equals("null") && !item.getString("url").equals("");
            story.time = item.getInt("created_at_i");

            if (story.isLink) {
                story.url = item.getString("url");
            } else {
                story.url = "https://news.ycombinator.com/item?id=" + story.id;
            }

            updatePdfProperties(story);

            story.score = item.optInt("points", 0);
            story.by = item.getString("author");

            return true;
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static String preprocessHtml(String input) {
        input = input.replace("<code>", "<pre><small>").replace("</code>", "</small></pre>");

        if (input.contains("<pre>")) {
            for (int i = 0; i < input.length() - 2; i++) {
                if (input.charAt(i) == ' ') {
                    String upUntilNow = input.substring(0, i);
                    if (upUntilNow.contains("<pre>")) {
                        if (upUntilNow.lastIndexOf("<pre>") > upUntilNow.lastIndexOf("</pre>")) {
                            input = upUntilNow + "&nbsp;" + input.substring(i + 1);
                        }
                    }
                } else if (input.charAt(i) == '\n') {
                    String upUntilNow = input.substring(0, i);
                    if (upUntilNow.contains("<pre>")) {
                        if (upUntilNow.lastIndexOf("<pre>") > upUntilNow.lastIndexOf("</pre>")) {
                            input = upUntilNow + "<br>" + input.substring(i + 1);
                        }
                    }

                }
            }
        }

        input = input.replace("<pre>", "<div><tt>").replace("</pre>", "</tt></div>");

        return input;
    }

}


