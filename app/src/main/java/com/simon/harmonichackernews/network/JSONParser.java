package com.simon.harmonichackernews.network;

import android.text.TextUtils;

import com.simon.harmonichackernews.data.Comment;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.utils.StoryUpdate;
import com.simon.harmonichackernews.utils.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JSONParser {

    public final static String ALGOLIA_ERROR_STRING = "{\"status\":404,\"error\":\"Not Found\"}";
    private static final String JSON_NULL_LITERAL = "null";

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

            if (hit.has("url") && !hit.getString("url").equals(JSON_NULL_LITERAL) && !hit.getString("url").isEmpty()) {
                story.url = hit.getString("url");
                story.isLink = true;
            } else {
                story.url = "https://news.ycombinator.com/item?id=" + story.id;
                story.isLink = false;
            }

            if (hit.has("story_text") && !hit.getString("story_text").equals(JSON_NULL_LITERAL)) {
                story.text = preprocessHtml(hit.getString("story_text"));
            }

            if (isComment) {
                story.isComment = true;
                story.text = preprocessHtml(hit.optString("comment_text", ""));
                story.commentMasterTitle = hit.getString("story_title");
                story.commentMasterId = hit.getInt("story_id");
                story.parentId = hit.optInt("parent_id", 0);
                if (hit.has("story_url") && !hit.getString("story_url").equals(JSON_NULL_LITERAL)) {
                    story.commentMasterUrl = hit.getString("story_url");
                    story.isLink = true;
                } else {
                    story.isLink = false;
                }
                if (!TextUtils.isEmpty(story.title) && story.title.equals(JSON_NULL_LITERAL)) {
                    story.title = "Comment by " + story.by;
                }
            }

            updatePdfProperties(story);

            stories.add(story);
        }

        return stories;
    }

    public static boolean updateStoryWithHNJson(String response, Story story, boolean isHistory) throws JSONException {
        if (response.equals(JSON_NULL_LITERAL)) {
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
                isHistory ? story.time : jsonObject.getInt("time"),
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
        story.parentId = jsonObject.optInt("parent", 0);

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
        if (story == null || TextUtils.isEmpty(story.url) || TextUtils.isEmpty(story.title)) {
            return;
        }
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
            story.parentId = item.optInt("parent_id", 0);
            story.commentMasterId = item.optInt("story_id", 0);
            story.commentMasterTitle = item.optString("story_title", "");
        } else {
            story.title = item.getString("title");
            story.isLink = item.has("url") && !item.getString("url").equals(JSON_NULL_LITERAL) && !item.getString("url").equals("");

            if (story.isLink) {
                story.url = item.getString("url");
            } else {
                story.url = "https://news.ycombinator.com/item?id=" + story.id;
            }

            updatePdfProperties(story);
        }

        if (item.has("text") && !item.getString("text").equals(JSON_NULL_LITERAL)) {
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

    public static List<Comment> parseAlgoliaComments(JSONArray children, int[] prioTop, Set<String> filteredUsers) throws JSONException {
        List<Comment> topLevelComments = new ArrayList<>();

        for (int i = 0; i < children.length(); i++) {
            Comment comment = parseAlgoliaComment(children.getJSONObject(i), 0, filteredUsers);
            if (comment != null) {
                topLevelComments.add(comment);
            }
        }

        if (prioTop != null) {
            Collections.sort(topLevelComments, (a, b) -> Integer.compare(priorityIndex(a.id, prioTop), priorityIndex(b.id, prioTop)));
        }

        List<Comment> flatComments = new ArrayList<>();
        flattenComments(topLevelComments, flatComments);
        return flatComments;
    }

    private static Comment parseAlgoliaComment(JSONObject child, int depth, Set<String> filteredUsers) throws JSONException {
        String rawText = child.optString("text", "").trim();
        if (rawText.isEmpty() || JSON_NULL_LITERAL.equalsIgnoreCase(rawText)) {
            return null;
        }

        String author = child.optString("author", "").trim();
        if (filteredUsers != null && filteredUsers.contains(author.toLowerCase())) {
            return null;
        }

        JSONArray childrenArr = child.optJSONArray("children");
        int childCount = (childrenArr == null ? 0 : childrenArr.length());

        Comment comment = new Comment();
        comment.depth = depth;
        comment.parent = child.getInt("parent_id");
        comment.expanded = true;
        comment.by = author;
        comment.text = preprocessHtml(rawText);
        comment.time = child.getInt("created_at_i");
        comment.id = child.getInt("id");
        comment.children = childCount;
        comment.childComments = new ArrayList<>();

        if (childrenArr != null) {
            for (int i = 0; i < childrenArr.length(); i++) {
                Comment childComment = parseAlgoliaComment(childrenArr.getJSONObject(i), depth + 1, filteredUsers);
                if (childComment != null) {
                    comment.childComments.add(childComment);
                }
            }

            Collections.sort(comment.childComments, (a, b) -> Integer.compare(b.children, a.children));
        }

        return comment;
    }

    private static int priorityIndex(int commentId, int[] prioTop) {
        for (int i = 0; i < prioTop.length; i++) {
            if (prioTop[i] == commentId) {
                return i;
            }
        }
        return prioTop.length;
    }

    private static void flattenComments(List<Comment> source, List<Comment> destination) {
        for (Comment comment : source) {
            destination.add(comment);
            if (comment.childComments != null && !comment.childComments.isEmpty()) {
                flattenComments(comment.childComments, destination);
            }
        }
    }

    public static void updateStoryWithAlgoliaResponse(Story story, String response) {
        try {
            JSONObject item = new JSONObject(response);

            // count children in one go
            JSONArray children = item.optJSONArray("children");
            story.descendants = (children == null ? 0 : children.length());

            // timestamp, title, author, score—all with a single lookup each
            story.time   = item.optInt("created_at_i", story.time);
            story.title  = item.optString("title", story.title);
            story.score  = item.optInt("points",    story.score);
            story.by     = item.optString("author",   story.by);

            // pull url once, trim it, then check for empty or literal "null"
            String rawUrl = item.optString("url", "").trim();
            boolean hasValidUrl = !rawUrl.isEmpty() && !rawUrl.equalsIgnoreCase(JSON_NULL_LITERAL);
            story.isLink = hasValidUrl;

            // only set story.url once
            if (hasValidUrl) {
                story.url = rawUrl;
            } else {
                story.url = "https://news.ycombinator.com/item?id=" + story.id;
            }

            updatePdfProperties(story);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static String preprocessHtml(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // Linkify first, so we don't have to deal with &nbsp; from escapePreBlockWhitespace
        input = Utils.linkify(input);

        // Standardize code blocks: handle <pre><code> first, then standalone <code>
        input = input.replace("<pre><code>", "<pre><small>")
                .replace("</code></pre>", "</small></pre>")
                .replace("<code>", "<pre><small>")
                .replace("</code>", "</small></pre>");

        if (input.contains("<pre>")) {
            input = escapePreBlockWhitespace(input);
        }

        input = input.replace("<pre>", "<div><tt>").replace("</pre>", "</tt></div>");

        return input;
    }

    private static String escapePreBlockWhitespace(String input) {
        StringBuilder output = new StringBuilder(input.length());
        boolean insidePreBlock = false;

        for (int i = 0; i < input.length(); i++) {
            if (input.startsWith("<pre>", i)) {
                insidePreBlock = true;
                output.append("<pre>");
                i += "<pre>".length() - 1;
            } else if (input.startsWith("</pre>", i)) {
                insidePreBlock = false;
                output.append("</pre>");
                i += "</pre>".length() - 1;
            } else {
                char current = input.charAt(i);
                if (insidePreBlock && current == ' ') {
                    output.append("&nbsp;");
                } else if (insidePreBlock && current == '\n') {
                    output.append("<br>");
                } else {
                    output.append(current);
                }
            }
        }

        return output.toString();
    }

    // Official HN API parsing methods for fallback
    public static boolean updateStoryWithOfficialHNResponse(Story story, String response) {
        try {
            JSONObject jsonObject = new JSONObject(response);
            
            // Check if this is a valid story response
            if (!jsonObject.has("by") || jsonObject.names().length() == 2) {
                return false;
            }

            story.by = jsonObject.optString("by", "");
            story.id = jsonObject.optInt("id", story.id);
            story.score = jsonObject.optInt("score", 0);
            story.time = jsonObject.optInt("time", story.time);
            story.title = jsonObject.optString("title", story.title);
            story.descendants = jsonObject.optInt("descendants", 0);

            if (jsonObject.has("type") && jsonObject.getString("type").equals("comment")) {
                story.isComment = true;
                story.parentId = jsonObject.optInt("parent", 0);
                if (TextUtils.isEmpty(story.title)) {
                    story.title = "Comment by " + story.by;
                }
            }

            //if a story is dead, it might not have a title. Right now we only do the fallback if
            // the story is dead (can it have no title for another reason? The example post was
            // "flagged" but the JSON said dead=true so let's go with that). If more cases show up,
            //let's add those in then
            if (TextUtils.isEmpty(story.title) && jsonObject.optBoolean("dead", false)) {
                story.title = "[deleted]";
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
                JSONArray kidsArray = jsonObject.getJSONArray("kids");
                story.kids = new int[kidsArray.length()];
                for (int i = 0; i < kidsArray.length(); i++) {
                    story.kids[i] = kidsArray.getInt(i);
                }
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
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static Comment parseOfficialHNCommentResponse(String response) throws JSONException {
        JSONObject jsonObject = new JSONObject(response);

        // Check if this is a deleted comment
        if (jsonObject.has("deleted") && jsonObject.getBoolean("deleted")) {
            return null;
        }

        Comment comment = new Comment();
        comment.id = jsonObject.getInt("id");
        comment.by = jsonObject.optString("by", "");
        comment.time = jsonObject.getInt("time");
        comment.parent = jsonObject.optInt("parent", 0);
        comment.expanded = true;

        if (jsonObject.has("text")) {
            comment.text = preprocessHtml(jsonObject.getString("text"));
        } else {
            comment.text = "";
        }

        if (jsonObject.has("kids")) {
            JSONArray kidsArray = jsonObject.getJSONArray("kids");
            comment.children = kidsArray.length();
            // Store kids for later loading
            comment.kidsIds = new int[kidsArray.length()];
            for (int i = 0; i < kidsArray.length(); i++) {
                comment.kidsIds[i] = kidsArray.getInt(i);
            }
        } else {
            comment.children = 0;
            comment.kidsIds = null;
        }

        return comment;
    }

}
