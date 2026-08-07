package com.simon.harmonichackernews.linkpreview

import org.json.JSONObject

internal object LinkPreviewJsonUtils {
    fun getString(jsonObject: JSONObject, key: String): String? =
        jsonObject.optString(key).takeUnless { it.isEmpty() || it == "null" }
}
