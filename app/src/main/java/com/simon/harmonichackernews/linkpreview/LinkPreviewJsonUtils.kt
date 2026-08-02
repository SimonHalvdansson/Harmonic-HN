package com.simon.harmonichackernews.linkpreview

import android.text.TextUtils
import org.json.JSONObject

internal object LinkPreviewJsonUtils {
    fun getString(jsonObject: JSONObject, key: String?): String? {
        val input = jsonObject.optString(key)
        if (TextUtils.isEmpty(input) || "null" == input) {
            return null
        }
        return input
    }
}
