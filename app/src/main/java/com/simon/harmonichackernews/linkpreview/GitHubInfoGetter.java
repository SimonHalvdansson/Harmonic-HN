package com.simon.harmonichackernews.linkpreview;

import android.content.Context;
import android.text.TextUtils;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.simon.harmonichackernews.data.RepoInfo;
import com.simon.harmonichackernews.network.NetworkComponent;

import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GitHubInfoGetter {

    public static boolean isValidGitHubUrl(String url) {
        String regex = "^(https|http)://github\\.com/[^/]+/[^/]+(/.*)?$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(url);
        return matcher.matches();
    }

    public static void getInfo(String githubUrl, Context ctx, GetterCallback callback) {
        try {
            String[] parts = githubUrl.split("github.com/")[1].split("/");
            String username = parts[0];
            String repoName = parts[1];

            String apiUrl = "https://api.github.com/repos/" + username + "/" + repoName;

            StringRequest stringRequest = new StringRequest(Request.Method.GET, apiUrl,
                    response -> {
                        try {
                            // Parse JSON response from GitHub API
                            JSONObject jsonResponse = new JSONObject(response);
                            RepoInfo repoInfo = new RepoInfo();

                            if (jsonResponse.has("owner")) {
                                JSONObject owner = jsonResponse.getJSONObject("owner");
                                repoInfo.owner = readJsonProp(owner, "login");
                            }

                            repoInfo.name = jsonResponse.optString("name");
                            repoInfo.about = readJsonProp(jsonResponse, "description");
                            repoInfo.website = readJsonProp(jsonResponse, "homepage");

                            if (jsonResponse.has("license") && !jsonResponse.get("license").toString().equals("null")) {
                                JSONObject license = jsonResponse.getJSONObject("license");
                                if (license.has("name") && license.getString("name").equals("Other")) {
                                    repoInfo.license = "Other";
                                } else {
                                    repoInfo.license = readJsonProp(license, "spdx_id");
                                }
                            }

                            repoInfo.language = readJsonProp(jsonResponse, "language");
                            repoInfo.stars = jsonResponse.optInt("stargazers_count");
                            repoInfo.watching = jsonResponse.optInt("subscribers_count");
                            repoInfo.forks = jsonResponse.optInt("forks_count");

                            callback.onSuccess(repoInfo);

                        } catch (Exception e) {
                            callback.onFailure("Failed to parse GitHub API response");
                            e.printStackTrace();
                        }
                    },
                    error -> {
                        error.printStackTrace();
                        callback.onFailure("Couldn't connect to GitHub API");
                    });

            RequestQueue queue = NetworkComponent.getRequestQueueInstance(ctx);
            queue.add(stringRequest);

        } catch (Exception e) {
            callback.onFailure("Invalid GitHub URL");
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
        void onSuccess(RepoInfo repoInfo);

        void onFailure(String reason);
    }

}
