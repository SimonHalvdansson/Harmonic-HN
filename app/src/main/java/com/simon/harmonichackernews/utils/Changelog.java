package com.simon.harmonichackernews.utils;

import android.content.Context;
import android.text.Spanned;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.noties.markwon.Markwon;

public class Changelog {
    private static final String CHANGELOG_ASSET = "changelog.md";
    private static final String FALLBACK_CHANGELOG = "Changelog unavailable.";
    private static final char UTF8_BOM = '\uFEFF';
    private static String cachedMarkdown;

    static public Spanned getFormatted(Context context) {
        return Markwon.create(context).toMarkdown(readMarkdown(context));
    }

    static private String readMarkdown(Context context) {
        if (cachedMarkdown != null) {
            return cachedMarkdown;
        }

        try (InputStream inputStream = context.getAssets().open(CHANGELOG_ASSET);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            cachedMarkdown = outputStream.toString(StandardCharsets.UTF_8.name());
            if (!cachedMarkdown.isEmpty() && cachedMarkdown.charAt(0) == UTF8_BOM) {
                cachedMarkdown = cachedMarkdown.substring(1);
            }
        } catch (IOException e) {
            Utils.log("Failed to read changelog: " + e);
            cachedMarkdown = FALLBACK_CHANGELOG;
        }

        return cachedMarkdown;
    }
}
