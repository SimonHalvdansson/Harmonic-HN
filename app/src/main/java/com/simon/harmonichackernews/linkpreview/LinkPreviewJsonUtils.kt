package com.simon.harmonichackernews.linkpreview

import com.simon.harmonichackernews.serialization.JsonObject as JSONObject

internal object LinkPreviewJsonUtils {
    fun getString(jsonObject: JSONObject, key: String): String? =
        jsonObject.optString(key).takeUnless { it.isEmpty() || it == "null" }
}
