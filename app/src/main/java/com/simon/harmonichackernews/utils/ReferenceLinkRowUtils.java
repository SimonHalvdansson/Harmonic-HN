package com.simon.harmonichackernews.utils;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.network.FaviconLoader;

public final class ReferenceLinkRowUtils {

    private ReferenceLinkRowUtils() {
    }

    public static String getReferenceLinkLabel(CollectedReferenceLinks.ReferenceLink link) {
        String resolvedTitle = link.getResolvedTitle();
        if (!TextUtils.isEmpty(resolvedTitle)) {
            return resolvedTitle.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        }

        String label = link.getLabel();
        if (TextUtils.isEmpty(label)) {
            return link.getUrl();
        }
        return label.replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    public static View createReferenceLinkRow(
            LinearLayout container,
            CollectedReferenceLinks.ReferenceLink link,
            String font,
            float labelTextSize,
            String contentDescription,
            @Nullable String faviconProvider,
            @Nullable View.OnClickListener clickListener) {
        Context context = container.getContext();
        View row = LayoutInflater.from(context).inflate(R.layout.reference_link_row, container, false);
        row.setClickable(clickListener != null);
        row.setFocusable(clickListener != null);

        TextView number = row.findViewById(R.id.reference_link_number);
        if (link.hasNumber()) {
            number.setVisibility(View.VISIBLE);
            number.setText(link.getMarkerLabel());
            FontUtils.setTypefaceForFont(number, font, true, 13);
        } else {
            number.setVisibility(View.GONE);
        }

        TextView label = row.findViewById(R.id.reference_link_label);
        label.setText(getReferenceLinkLabel(link));
        FontUtils.setTypefaceForFont(label, font, false, labelTextSize);

        row.setContentDescription(contentDescription);
        if (clickListener != null) {
            row.setOnClickListener(clickListener);
        }
        ImageView favicon = row.findViewById(R.id.reference_link_favicon);
        if (faviconProvider != null) {
            FaviconLoader.loadFavicon(link.getUrl(), favicon, context, faviconProvider);
        }

        return row;
    }
}
