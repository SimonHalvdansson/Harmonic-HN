package com.simon.harmonichackernews.network

import android.content.Context
import org.json.JSONObject

object ArchiveOrgUrlGetter {
    fun getArchiveUrl(url: String?, ctx: Context, callback: GetterCallback) {
        val stringRequest = StringRequest(
            QueueRequest.Method.GET, "https://archive.org/wayback/available?url=" + url,
            QueueResponse.Listener { response: String? ->
                try {
                    val mainObject = JSONObject(response)
                    val archivedSnapshots = mainObject.getJSONObject("archived_snapshots")
                    if (archivedSnapshots.has("closest") && archivedSnapshots.getJSONObject("closest")
                            .getBoolean("available")
                    ) {
                        val closest = archivedSnapshots.getJSONObject("closest")
                        callback.onSuccess(closest.getString("url"))
                    } else {
                        callback.onFailure("No saved copy on archive.org found")
                    }
                } catch (e: Exception) {
                    callback.onFailure("Failed to parse archive.org API response")
                }
            }, QueueResponse.ErrorListener { error: NetworkError? ->
                error?.printStackTrace()
                callback.onFailure("Couldn't connect to archive.org API")
            })

        val queue = NetworkComponent.getRequestQueueInstance(ctx)
        queue.add<String?>(stringRequest)
    }

    interface GetterCallback {
        fun onSuccess(url: String?)

        fun onFailure(reason: String?)
    }
}
