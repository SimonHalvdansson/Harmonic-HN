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
import coil.target.ImageViewTarget;

import java.util.Objects;

public class FaviconLoader {

    public static void loadFavicon(String url, ImageView into, Context ctx, String faviconProvider) {
        loadFavicon(url, into, ctx, faviconProvider, false);
    }

    public static void loadFavicon(String url, ImageView into, Context ctx, String faviconProvider, boolean fadeIn) {
        try {
            String host = Utils.getDomainName(url);
            String faviconUrl = getFaviconUrlForHost(host, faviconProvider);
            if (faviconUrl.equals(into.getTag(R.id.favicon_request_url))) {
                return;
            }
            int faviconSize = Utils.pxFromDpInt(ctx.getResources(), 17);
            Drawable webDrawable = Objects.requireNonNull(ContextCompat.getDrawable(ctx, R.drawable.ic_public));
            applyFaviconThumbnailShape(into);
            into.setTag(R.id.favicon_request_url, faviconUrl);

            ImageRequest request = new ImageRequest.Builder(ctx)
                    .data(faviconUrl)
                    .size(faviconSize, faviconSize)
                    // Metadata shared-element snapshots draw the favicon and text together onto
                    // a software bitmap. A crossfade around a hardware bitmap makes that capture
                    // fail and causes the entire metadata row to be omitted from the transition.
                    .allowHardware(false)
                    .placeholder(webDrawable)
                    .error(webDrawable)
                    .fallback(webDrawable)
                    .crossfade(fadeIn)
                    .target(new ImageViewTarget(into) {
                        @Override
                        public void onStart(Drawable placeholder) {
                            if (faviconUrl.equals(into.getTag(R.id.favicon_request_url))) {
                                super.onStart(placeholder);
                            }
                        }

                        @Override
                        public void onError(Drawable error) {
                            if (faviconUrl.equals(into.getTag(R.id.favicon_request_url))) {
                                into.setTag(R.id.favicon_request_url, null);
                                super.onError(error);
                            }
                        }

                        @Override
                        public void onSuccess(Drawable result) {
                            if (faviconUrl.equals(into.getTag(R.id.favicon_request_url))) {
                                super.onSuccess(result);
                            }
                        }
                    })
                    .build();

            Coil.imageLoader(ctx).enqueue(request);
        } catch (Exception ignored){};
    }

    private static void applyFaviconThumbnailShape(ImageView into) {
        if (into.getBackground() == null) {
            into.setBackgroundResource(R.drawable.favicon_thumbnail_background);
        }
        into.setClipToOutline(true);
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
