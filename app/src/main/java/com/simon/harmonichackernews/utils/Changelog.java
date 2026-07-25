package com.simon.harmonichackernews.utils;

import android.content.Context;
import android.text.Spanned;
import android.util.TypedValue;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.core.MarkwonTheme;

public class Changelog {
    private static final String CHANGELOG_ASSET = "changelog.md";
    private static final String FALLBACK_CHANGELOG = "Changelog unavailable.";
    private static final float BULLET_WIDTH_SP = 15f * 0.28f;
    private static final char UTF8_BOM = '\uFEFF';
    private static String cachedMarkdown;

    static public Spanned getFormatted(Context context) {
        int bulletWidth = Math.max(1, Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                BULLET_WIDTH_SP,
                context.getResources().getDisplayMetrics())));
        return Markwon.builder(context)
                .usePlugin(new AbstractMarkwonPlugin() {
                    @Override
                    public void configureTheme(@NonNull MarkwonTheme.Builder builder) {
                        builder.bulletWidth(bulletWidth);
                    }
                })
                .build()
                .toMarkdown(readMarkdown(context));
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
