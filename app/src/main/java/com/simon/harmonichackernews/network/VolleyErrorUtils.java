package com.simon.harmonichackernews.network;

import com.android.volley.VolleyError;

final class VolleyErrorUtils {

    private VolleyErrorUtils() {
    }

    static String describe(VolleyError error) {
        if (error == null) {
            return "unknown VolleyError";
        }
        String status = error.networkResponse == null
                ? "noNetworkResponse"
                : "statusCode=" + error.networkResponse.statusCode;
        return error.getClass().getSimpleName() + ", " + status + ", message=" + error.getMessage();
    }
}
