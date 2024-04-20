package com.simon.harmonichackernews.network;

import android.content.Context;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;

import org.json.JSONObject;


public class ArchiveOrgUrlGetter {

    public static void getArchiveUrl(String url, final Context ctx, GetterCallback callback) {
        StringRequest stringRequest = new StringRequest(com.android.volley.Request.Method.GET, "http://archive.org/wayback/available?url=" + url,
                response -> {

                    try {
                        JSONObject mainObject = new JSONObject(response);
                        JSONObject archivedSnapshots = mainObject.getJSONObject("archived_snapshots");
                        if (archivedSnapshots.has("closest") && archivedSnapshots.getJSONObject("closest").getBoolean("available")) {
                            JSONObject closest = archivedSnapshots.getJSONObject("closest");
                            callback.onSuccess(closest.getString("url"));

                        } else {
                            callback.onFailure("No saved copy on archive.org found");
                        }

                    } catch (Exception e) {
                        callback.onFailure("Failed to parse archive.org API response");
                    }

                }, error -> {
            error.printStackTrace();
            callback.onFailure("Couldn't connect to archive.org API");

        });

        RequestQueue queue = NetworkComponent.getRequestQueueInstance(ctx);
        queue.add(stringRequest);
    }

    public interface GetterCallback {
        void onSuccess(String url);

        void onFailure(String reason);
    }

}
