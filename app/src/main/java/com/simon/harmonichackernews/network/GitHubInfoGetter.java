package com.simon.harmonichackernews.network;

import android.content.Context;
import android.text.TextUtils;
import android.util.Xml;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.simon.harmonichackernews.data.RepoInfo;

import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GitHubInfoGetter {

    public static boolean isValidGitHubUrl(String url) {
        String regex = "^(https|http)://github\\.com/[^/]+/[^/]+(/.*)?$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(url);
        return matcher.matches();
    }

    public static void getInfo(String githubUrl, Context ctx, NetworkComponent.GetterCallback callback) {
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
                                repoInfo.owner = owner.optString("login");
                            }

                            repoInfo.name = jsonResponse.optString("name");
                            repoInfo.about = jsonResponse.optString("description");
                            repoInfo.website = jsonResponse.optString("homepage");
                            if (!TextUtils.isEmpty(repoInfo.website) && repoInfo.website.equals("null")) {
                                repoInfo.website = null;
                            }

                            if (jsonResponse.has("license")) {
                                JSONObject license = jsonResponse.getJSONObject("license");
                                repoInfo.license = license.optString("spdx_id");
                            }

                            repoInfo.language = jsonResponse.optString("language");
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

}
