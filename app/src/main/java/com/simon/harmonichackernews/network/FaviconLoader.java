package com.simon.harmonichackernews.network;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;

import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.Utils;

import coil.Coil;
import coil.request.ImageRequest;

import java.util.Objects;

public class FaviconLoader {

    public static void loadFavicon(String url, ImageView into, Context ctx, String faviconProvider) {
        try {
            String host = Utils.getDomainName(url);
            int faviconSize = Utils.pxFromDpInt(ctx.getResources(), 17);
            Drawable webDrawable = Objects.requireNonNull(ContextCompat.getDrawable(ctx, R.drawable.ic_action_web));

            ImageRequest request = new ImageRequest.Builder(ctx)
                    .data(getFaviconUrlForHost(host, faviconProvider))
                    .size(faviconSize, faviconSize)
                    .placeholder(webDrawable)
                    .error(webDrawable)
                    .fallback(webDrawable)
                    .target(into)
                    .build();

            Coil.imageLoader(ctx).enqueue(request);
        } catch (Exception ignored){};
    }

    public static String getFaviconUrl(String url, String faviconProvider) throws Exception {
        return getFaviconUrlForHost(Utils.getDomainName(url), faviconProvider);
    }

    public static String getFaviconUrlSchema(String faviconProvider) {
        return getFaviconUrlForHost("{host}", faviconProvider);
    }

    private static String getFaviconUrlForHost(String host, String faviconProvider) {
        switch (SettingsUtils.sanitizeFaviconProvider(faviconProvider)) {
            case SettingsUtils.FAVICON_PROVIDER_TWENTY:
                return "https://twenty-icons.com/" + host;
            case SettingsUtils.FAVICON_PROVIDER_GOOGLE:
                return "https://www.google.com/s2/favicons?domain=" + host + "&sz=128";
            case SettingsUtils.FAVICON_PROVIDER_DUCKDUCKGO:
                return "https://icons.duckduckgo.com/ip3/" + host + ".ico";
            default:
                return "https://www.google.com/s2/favicons?domain=" + host + "&sz=128";
        }
    }

}
