package com.simon.harmonichackernews.network;

import android.content.Context;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;

import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.utils.Utils;
import com.squareup.picasso.Picasso;

import java.util.Objects;

public class FaviconLoader {

    public static void loadFavicon(String url, ImageView into, Context ctx, String faviconProvider) {
        try {
            String host = Utils.getDomainName(url);
            int faviconSize = Utils.pxFromDpInt(ctx.getResources(), 17);

            Picasso.get()
                    .load(getFaviconUrl(host, faviconProvider))
                    .resize(faviconSize, faviconSize)
                    .onlyScaleDown()
                    .placeholder(Objects.requireNonNull(ContextCompat.getDrawable(ctx, R.drawable.ic_action_web)))
                    .into(into);
        } catch (Exception ignored){};
    }

    private static String getFaviconUrl(String host, String faviconProvider) {
        switch (faviconProvider) {
            case "Favicon kit":
                return "https://api.faviconkit.com/" + host;
            case "Google":
                return "https://www.google.com/s2/favicons?domain=" + host + "&sz=128";
            case "DuckDuckGo":
                return "https://icons.duckduckgo.com/ip3/" + host + ".ico";
            default:
                return "https://www.google.com/s2/favicons?domain=" + host + "&sz=128";
        }
    }

}
