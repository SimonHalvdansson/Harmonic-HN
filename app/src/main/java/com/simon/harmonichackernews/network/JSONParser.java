package com.simon.harmonichackernews.network;

import android.text.TextUtils;
import android.util.JsonReader;
import android.util.JsonToken;

import com.simon.harmonichackernews.data.Comment;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.utils.StoryUpdate;
import com.simon.harmonichackernews.utils.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class JSONParser {

    public final static String ALGOLIA_ERROR_STRING = "{\"status\":404,\"error\":\"Not Found\"}";
    private static final String JSON_NULL_LITERAL = "null";
    private static final int CACHED_STORY_SUMMARY_VERSION = 1;

    private static boolean hasOnlyTwoTopLevelFields(JSONObject jsonObject) {
        JSONArray names = jsonObject.names();
        return names != null && names.length() == 2;
    }

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
                updateStoryText(story, hit.getString("story_text"));
            }

            if (isComment) {
                story.isComment = true;
                updateStoryText(story, hit.optString("comment_text", ""));
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

            updateTitleBadgeProperties(story);

            stories.add(story);
        }

        return stories;
    }

    public static boolean updateStoryWithHNJson(String response, Story story, boolean isHistory) throws JSONException {
        if (TextUtils.isEmpty(response) || JSON_NULL_LITERAL.equals(response)) {
            return false;
        }

        JSONObject jsonObject = new JSONObject(response);

        if (hasOnlyTwoTopLevelFields(jsonObject) || !jsonObject.has("by")) {
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
            updateStoryText(story, jsonObject.getString("text"));
        }

        updateTitleBadgeProperties(story);

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
            updateStoryText(story, jsonObject.getString("text"));
        }

        story.loaded = true;
        story.loadingFailed = false;

        return true;
    }

    public static void updateTitleBadgeProperties(Story story) {
        if (story == null || TextUtils.isEmpty(story.url) || TextUtils.isEmpty(story.title)) {
            return;
        }

        story.pdfTitle = null;
        story.videoTitle = null;

        String[] pdfSuffixes = {" [pdf]", "[pdf]", " (pdf)", "(pdf)"};
        String[] videoSuffixes = {" [video]", "[video]", " (video)", "(video)"};
        if (endsWithIgnoreCase(story.url, ".pdf") || hasTitleSuffix(story.title, pdfSuffixes)) {
            story.pdfTitle = stripTitleSuffix(story.title, pdfSuffixes);
        } else if (hasTitleSuffix(story.title, videoSuffixes)) {
            story.videoTitle = stripTitleSuffix(story.title, videoSuffixes);
        }
    }

    private static boolean hasTitleSuffix(String title, String[] suffixes) {
        for (String suffix : suffixes) {
            if (endsWithIgnoreCase(title, suffix)) {
                return true;
            }
        }
        return false;
    }

    private static String stripTitleSuffix(String title, String[] suffixes) {
        for (String suffix : suffixes) {
            if (endsWithIgnoreCase(title, suffix)) {
                return title.substring(0, title.length() - suffix.length());
            }
        }
        return title;
    }

    private static boolean endsWithIgnoreCase(String value, String suffix) {
        return value.length() >= suffix.length()
                && value.regionMatches(true, value.length() - suffix.length(), suffix, 0, suffix.length());
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

            updateTitleBadgeProperties(story);
        }

        if (item.has("text") && !item.getString("text").equals(JSON_NULL_LITERAL)) {
            updateStoryText(story, item.getString("text"));
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

    public static AlgoliaCommentsResponse parseAlgoliaCommentsResponse(String response, int[] prioTop, Set<String> filteredUsers) throws IOException {
        JsonReader reader = new JsonReader(new StringReader(response));
        AlgoliaCommentsResponse result = new AlgoliaCommentsResponse();
        List<Comment> topLevelComments = new ArrayList<>();

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "title":
                    result.title = nextStringOrDefault(reader, result.title);
                    break;
                case "points":
                    result.points = nextIntOrDefault(reader, result.points);
                    break;
                case "created_at_i":
                    result.createdAt = nextIntOrDefault(reader, result.createdAt);
                    break;
                case "type":
                    result.type = nextStringOrDefault(reader, result.type);
                    break;
                case "author":
                    result.author = nextStringOrDefault(reader, result.author);
                    break;
                case "story_id":
                    result.storyId = nextIntOrDefault(reader, result.storyId);
                    break;
                case "parent_id":
                    result.parentId = nextIntOrDefault(reader, result.parentId);
                    break;
                case "story_title":
                    result.storyTitle = nextStringOrDefault(reader, result.storyTitle);
                    break;
                case "url":
                    result.url = nextStringOrDefault(reader, result.url);
                    break;
                case "text":
                    result.text = nextStringOrDefault(reader, result.text);
                    break;
                case "id":
                    result.id = nextIntOrDefault(reader, result.id);
                    break;
                case "children":
                    reader.beginArray();
                    while (reader.hasNext()) {
                        Comment comment = parseAlgoliaComment(reader, 0, filteredUsers);
                        if (comment != null) {
                            topLevelComments.add(comment);
                        }
                    }
                    reader.endArray();
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();
        reader.close();

        if (prioTop != null) {
            Collections.sort(topLevelComments, (a, b) -> Integer.compare(priorityIndex(a.id, prioTop), priorityIndex(b.id, prioTop)));
        }

        flattenComments(topLevelComments, result.comments);
        return result;
    }

    private static Comment parseAlgoliaComment(JsonReader reader, int depth, Set<String> filteredUsers) throws IOException {
        String rawText = "";
        String author = "";
        int parentId = 0;
        int createdAt = 0;
        int id = 0;
        int childCount = 0;
        List<Comment> childComments = new ArrayList<>();

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "text":
                    rawText = nextStringOrDefault(reader, "").trim();
                    break;
                case "author":
                    author = nextStringOrDefault(reader, "").trim();
                    break;
                case "parent_id":
                    parentId = nextIntOrDefault(reader, parentId);
                    break;
                case "created_at_i":
                    createdAt = nextIntOrDefault(reader, createdAt);
                    break;
                case "id":
                    id = nextIntOrDefault(reader, id);
                    break;
                case "children":
                    reader.beginArray();
                    while (reader.hasNext()) {
                        childCount++;
                        Comment childComment = parseAlgoliaComment(reader, depth + 1, filteredUsers);
                        if (childComment != null) {
                            childComments.add(childComment);
                        }
                    }
                    reader.endArray();
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        if (rawText.isEmpty() || JSON_NULL_LITERAL.equalsIgnoreCase(rawText)) {
            return null;
        }
        if (filteredUsers != null && filteredUsers.contains(author.toLowerCase())) {
            return null;
        }

        Comment comment = new Comment();
        comment.depth = depth;
        comment.parent = parentId;
        comment.expanded = true;
        comment.by = author;
        comment.text = preprocessHtml(rawText);
        comment.time = createdAt;
        comment.id = id;
        comment.children = childCount;
        comment.childComments = childComments;

        if (!comment.childComments.isEmpty()) {
            Collections.sort(comment.childComments, (a, b) -> Integer.compare(b.children, a.children));
        }

        return comment;
    }

    private static String nextStringOrDefault(JsonReader reader, String defaultValue) throws IOException {
        JsonToken token = reader.peek();
        if (token == JsonToken.NULL) {
            reader.nextNull();
            return defaultValue;
        }
        if (token == JsonToken.STRING || token == JsonToken.NUMBER) {
            return reader.nextString();
        }
        reader.skipValue();
        return defaultValue;
    }

    private static int nextIntOrDefault(JsonReader reader, int defaultValue) throws IOException {
        JsonToken token = reader.peek();
        if (token == JsonToken.NULL) {
            reader.nextNull();
            return defaultValue;
        }
        if (token == JsonToken.NUMBER) {
            return reader.nextInt();
        }
        if (token == JsonToken.STRING) {
            try {
                return Integer.parseInt(reader.nextString());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        reader.skipValue();
        return defaultValue;
    }

    public static class AlgoliaCommentsResponse {
        public final List<Comment> comments = new ArrayList<>();
        private String title = "";
        private int points = 0;
        private int createdAt = 0;
        private String type = "";
        private String author = "";
        private int storyId = 0;
        private int parentId = 0;
        private String storyTitle = "";
        private String url = "";
        private String text = "";
        private int id = 0;

        public boolean updateStoryInformation(Story story, boolean forceRefresh, int oldCommentCount) {
            String oldFormattedTime = story.getTimeFormatted();
            int newCommentCount = comments.size() + 1;
            boolean changed;

            if (TextUtils.isEmpty(story.title)) {
                changed = true;
            } else {
                changed = (!title.equals(story.title) ||
                        points != story.score ||
                        !oldFormattedTime.equals(story.getTimeFormatted()) ||
                        oldCommentCount != newCommentCount);
            }

            story.time = createdAt;

            if ("comment".equals(type)) {
                story.title = "Comment by " + author;
                story.isLink = false;
                story.url = "https://news.ycombinator.com/item?id=" + storyId;
                story.isComment = true;
                story.parentId = parentId;
                story.commentMasterId = storyId;
                story.commentMasterTitle = storyTitle;
            } else {
                story.title = title;
                story.isLink = !TextUtils.isEmpty(url) && !JSON_NULL_LITERAL.equals(url);

                if (story.isLink) {
                    story.url = url;
                } else {
                    story.url = "https://news.ycombinator.com/item?id=" + story.id;
                }

                updateTitleBadgeProperties(story);
            }

            if (!TextUtils.isEmpty(text) && !JSON_NULL_LITERAL.equals(text)) {
                updateStoryText(story, text);
            }

            story.descendants = comments.size();
            story.id = id;
            story.score = points;
            story.by = author;
            story.loaded = true;

            if (forceRefresh) {
                StoryUpdate.updateStory(story);
            }

            return changed;
        }
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

            updateTitleBadgeProperties(story);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static String compactAlgoliaStoryResponse(String response, int fallbackId) {
        if (TextUtils.isEmpty(response)
                || JSON_NULL_LITERAL.equals(response)
                || ALGOLIA_ERROR_STRING.equals(response)) {
            return null;
        }

        try {
            JSONObject item = new JSONObject(response);
            JSONObject summary = new JSONObject();
            int id = item.optInt("id", fallbackId);
            if (id <= 0) {
                id = fallbackId;
            }

            summary.put("cache_version", CACHED_STORY_SUMMARY_VERSION);
            summary.put("id", id);
            summary.put("type", item.optString("type", "story"));
            summary.put("title", item.optString("title", ""));
            summary.put("author", item.optString("author", ""));
            summary.put("points", item.optInt("points", 0));
            summary.put("created_at_i", item.optInt("created_at_i", 0));
            summary.put("descendants", countAlgoliaComments(item.optJSONArray("children")));
            putNonNullString(summary, "url", item.optString("url", ""));

            if (item.has("story_id")) {
                summary.put("story_id", item.optInt("story_id", 0));
            }
            if (item.has("parent_id")) {
                summary.put("parent_id", item.optInt("parent_id", 0));
            }
            putNonNullString(summary, "story_title", item.optString("story_title", ""));
            putNonNullString(summary, "story_url", item.optString("story_url", ""));

            return summary.toString();
        } catch (JSONException e) {
            return null;
        }
    }

    public static boolean updateStoryWithCachedStorySummary(Story story, String response) {
        if (story == null || TextUtils.isEmpty(response) || JSON_NULL_LITERAL.equals(response)) {
            return false;
        }

        try {
            JSONObject item = new JSONObject(response);
            int id = item.optInt("id", story.id);
            if (id <= 0) {
                return false;
            }

            story.id = id;
            story.time = item.optInt("created_at_i", item.optInt("time", story.time));
            story.score = item.optInt("points", item.optInt("score", story.score));
            story.by = item.optString("author", item.optString("by", story.by));
            story.descendants = item.has("descendants")
                    ? item.optInt("descendants", story.descendants)
                    : countAlgoliaComments(item.optJSONArray("children"));

            String type = item.optString("type", "");
            if ("comment".equals(type)) {
                story.isComment = true;
                story.title = "Comment by " + story.by;
                story.isLink = false;
                story.parentId = item.optInt("parent_id", 0);
                story.commentMasterId = item.optInt("story_id", 0);
                story.commentMasterTitle = item.optString("story_title", "");
                story.commentMasterUrl = item.optString("story_url", "");
                int urlId = story.commentMasterId > 0 ? story.commentMasterId : story.id;
                story.url = "https://news.ycombinator.com/item?id=" + urlId;
            } else {
                story.isComment = false;
                story.title = item.optString("title", story.title);
                String rawUrl = item.optString("url", "").trim();
                boolean hasValidUrl = !rawUrl.isEmpty() && !rawUrl.equalsIgnoreCase(JSON_NULL_LITERAL);
                story.isLink = hasValidUrl;
                story.url = hasValidUrl ? rawUrl : "https://news.ycombinator.com/item?id=" + story.id;
                story.isJob = "job".equals(type);
            }

            updateTitleBadgeProperties(story);
            story.loaded = true;
            story.loadingFailed = false;
            return !TextUtils.isEmpty(story.title);
        } catch (JSONException e) {
            return false;
        }
    }

    private static void putNonNullString(JSONObject object, String key, String value) throws JSONException {
        if (!TextUtils.isEmpty(value) && !JSON_NULL_LITERAL.equalsIgnoreCase(value)) {
            object.put(key, value);
        }
    }

    private static int countAlgoliaComments(JSONArray children) throws JSONException {
        if (children == null) {
            return 0;
        }

        int count = children.length();
        for (int i = 0; i < children.length(); i++) {
            JSONObject child = children.optJSONObject(i);
            if (child == null) {
                continue;
            }
            count += countAlgoliaComments(child.optJSONArray("children"));
        }
        return count;
    }

    private static void updateStoryText(Story story, String rawText) {
        String text = preprocessHtml(rawText);
        if (!TextUtils.equals(story.text, text)) {
            story.spannedText = null;
            story.collectedReferenceLinksSource = null;
            story.collectedReferenceLinks = null;
            story.collectedReferenceLinksSpannedText = null;
        }
        story.text = text;
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
            if (TextUtils.isEmpty(response) || JSON_NULL_LITERAL.equals(response)) {
                return false;
            }

            JSONObject jsonObject = new JSONObject(response);
            
            // Check if this is a valid story response
            if (!jsonObject.has("by") || hasOnlyTwoTopLevelFields(jsonObject)) {
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
                updateStoryText(story, jsonObject.getString("text"));
            }

            updateTitleBadgeProperties(story);
            
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
