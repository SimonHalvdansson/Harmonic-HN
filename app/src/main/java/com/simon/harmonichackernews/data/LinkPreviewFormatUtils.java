package com.simon.harmonichackernews.data;

import androidx.annotation.Nullable;

import java.util.Locale;

final class LinkPreviewFormatUtils {

    private LinkPreviewFormatUtils() {
    }

    static String formatCount(int count, String singular, String plural) {
        if (count == 1) {
            return "1 " + singular;
        }
        return kFormat(count) + " " + plural;
    }

    static String kFormat(int number) {
        if (number < 1000) {
            return String.valueOf(number);
        }

        double rounded = Math.round((double) number / 100) * 100;
        String result = String.format(Locale.US, "%.1fk", rounded / 1000);
        if (result.endsWith(".0k")) {
            return result.substring(0, result.length() - 3) + "k";
        }
        return result;
    }

    @Nullable
    static String shortenUrl(@Nullable String url) {
        if (url == null) {
            return null;
        }

        String shortenedUrl = url;
        if (shortenedUrl.startsWith("https://")) {
            shortenedUrl = shortenedUrl.substring(8);
        } else if (shortenedUrl.startsWith("http://")) {
            shortenedUrl = shortenedUrl.substring(7);
        }

        if (shortenedUrl.startsWith("www.")) {
            shortenedUrl = shortenedUrl.substring(4);
        }

        return shortenedUrl;
    }
}
