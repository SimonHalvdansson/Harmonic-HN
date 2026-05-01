package com.simon.harmonichackernews.linkpreview;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.simon.harmonichackernews.data.GitLabInfo;
import com.simon.harmonichackernews.network.NetworkComponent;

import org.json.JSONObject;

import java.util.List;

public class GitLabInfoGetter {

    public static boolean isValidGitLabUrl(String url) {
        return getProjectPath(url) != null;
    }

    public static void getInfo(String gitLabUrl, Context ctx, GetterCallback callback) {
        try {
            String projectPath = getProjectPath(gitLabUrl);
            if (TextUtils.isEmpty(projectPath)) {
                callback.onFailure("Invalid GitLab URL");
                return;
            }

            String apiUrl = "https://gitlab.com/api/v4/projects/" + Uri.encode(projectPath, "/").replace("/", "%2F");

            StringRequest stringRequest = new StringRequest(Request.Method.GET, apiUrl,
                    response -> {
                        try {
                            JSONObject jsonResponse = new JSONObject(response);
                            GitLabInfo gitLabInfo = new GitLabInfo();

                            gitLabInfo.name = readJsonProp(jsonResponse, "name");
                            gitLabInfo.namespace = readJsonProp(jsonResponse, "namespace");
                            gitLabInfo.description = readJsonProp(jsonResponse, "description");
                            gitLabInfo.website = readJsonProp(jsonResponse, "web_url");
                            gitLabInfo.visibility = readJsonProp(jsonResponse, "visibility");
                            gitLabInfo.stars = jsonResponse.optInt("star_count");
                            gitLabInfo.forks = jsonResponse.optInt("forks_count");

                            if (jsonResponse.has("namespace") && !jsonResponse.get("namespace").toString().equals("null")) {
                                JSONObject namespace = jsonResponse.getJSONObject("namespace");
                                gitLabInfo.namespace = readJsonProp(namespace, "full_path");
                            }

                            callback.onSuccess(gitLabInfo);

                        } catch (Exception e) {
                            callback.onFailure("Failed to parse GitLab API response");
                            e.printStackTrace();
                        }
                    },
                    error -> {
                        error.printStackTrace();
                        callback.onFailure("Couldn't connect to GitLab API");
                    });

            RequestQueue queue = NetworkComponent.getRequestQueueInstance(ctx);
            queue.add(stringRequest);

        } catch (Exception e) {
            callback.onFailure("Invalid GitLab URL");
        }
    }

    private static String getProjectPath(String url) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host == null) {
                return null;
            }

            host = host.toLowerCase();
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }

            if (!host.equals("gitlab.com")) {
                return null;
            }

            List<String> segments = uri.getPathSegments();
            if (segments.size() < 2) {
                return null;
            }

            int projectPathEnd = segments.size();
            for (int i = 0; i < segments.size(); i++) {
                if (segments.get(i).equals("-")) {
                    projectPathEnd = i;
                    break;
                }
            }

            if (projectPathEnd < 2) {
                return null;
            }

            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < projectPathEnd; i++) {
                if (i > 0) {
                    builder.append("/");
                }
                builder.append(segments.get(i));
            }
            return builder.toString();

        } catch (Exception e) {
            return null;
        }
    }

    private static String readJsonProp(JSONObject jsonObject, String key) {
        String input = jsonObject.optString(key);

        if (TextUtils.isEmpty(input) || input.equals("null")) {
            return null;
        }

        return input;
    }

    public interface GetterCallback {
        void onSuccess(GitLabInfo gitLabInfo);

        void onFailure(String reason);
    }
}
