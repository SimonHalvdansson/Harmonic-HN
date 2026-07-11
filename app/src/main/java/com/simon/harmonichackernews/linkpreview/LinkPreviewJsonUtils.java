package com.simon.harmonichackernews.linkpreview;

import android.text.TextUtils;

import org.json.JSONObject;

final class LinkPreviewJsonUtils {

    private LinkPreviewJsonUtils() {
    }

    static String getString(JSONObject jsonObject, String key) {
        String input = jsonObject.optString(key);
        if (TextUtils.isEmpty(input) || "null".equals(input)) {
            return null;
        }
        return input;
    }
}
