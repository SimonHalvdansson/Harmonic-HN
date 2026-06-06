package com.simon.harmonichackernews.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.network.FaviconLoader;
import com.simon.harmonichackernews.utils.CollectedReferenceLinks;
import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.Utils;

final class ReferenceLinkRowBinder {
    private static final int REFERENCE_LINK_MIN_HEIGHT_DP = 38;
    private static final int REFERENCE_LINK_CORNER_RADIUS_DP = 6;
    private static final int REFERENCE_LINK_ICON_SIZE_DP = 17;

    private ReferenceLinkRowBinder() {
    }

    static boolean bind(
            LinearLayout container,
            @Nullable CollectedReferenceLinks.Result referenceLinks,
            boolean collectReferenceLinks,
            String font,
            float preferredTextSize,
            String faviconProvider) {
        if (!collectReferenceLinks || referenceLinks == null || !referenceLinks.hasLinks()) {
            container.removeAllViews();
            container.setVisibility(View.GONE);
            return false;
        }

        container.removeAllViews();
        container.setVisibility(View.VISIBLE);
        for (CollectedReferenceLinks.ReferenceLink link : referenceLinks.getLinks()) {
            container.addView(createRow(container, link, font, preferredTextSize, faviconProvider));
        }
        return true;
    }

    static View createRow(
            LinearLayout container,
            CollectedReferenceLinks.ReferenceLink link,
            String font,
            float preferredTextSize,
            String faviconProvider) {
        Context ctx = container.getContext();

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        row.setMinimumHeight(Utils.pxFromDpInt(ctx.getResources(), REFERENCE_LINK_MIN_HEIGHT_DP));
        row.setPadding(
                Utils.pxFromDpInt(ctx.getResources(), 8),
                Utils.pxFromDpInt(ctx.getResources(), 5),
                Utils.pxFromDpInt(ctx.getResources(), 8),
                Utils.pxFromDpInt(ctx.getResources(), 5));

        int cornerRadius = Utils.pxFromDpInt(ctx.getResources(), REFERENCE_LINK_CORNER_RADIUS_DP);

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.TRANSPARENT);
        background.setCornerRadius(cornerRadius);
        background.setStroke(
                Utils.pxFromDpInt(ctx.getResources(), 1),
                MaterialColors.getColor(container, R.attr.commentDividerColor));
        row.setBackground(background);
        row.setForeground(createRipple(container, cornerRadius));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = Utils.pxFromDpInt(ctx.getResources(), 4);
        row.setLayoutParams(rowParams);

        ImageView favicon = new ImageView(ctx);
        int iconSize = Utils.pxFromDpInt(ctx.getResources(), REFERENCE_LINK_ICON_SIZE_DP);
        LinearLayout.LayoutParams faviconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        faviconParams.rightMargin = Utils.pxFromDpInt(ctx.getResources(), 8);
        favicon.setLayoutParams(faviconParams);
        favicon.setImageResource(R.drawable.ic_action_web);
        favicon.setContentDescription(null);
        favicon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(favicon);

        if (link.hasNumber()) {
            TextView number = new TextView(ctx);
            number.setText("[" + link.getNumber() + "]");
            number.setTextColor(MaterialColors.getColor(container, R.attr.storyColorDisabled));
            FontUtils.setTypefaceForFont(number, font, true, 13);
            LinearLayout.LayoutParams numberParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            numberParams.rightMargin = Utils.pxFromDpInt(ctx.getResources(), 8);
            number.setLayoutParams(numberParams);
            row.addView(number);
        }

        TextView label = new TextView(ctx);
        label.setText(getLabel(link));
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setTextColor(MaterialColors.getColor(container, R.attr.storyColorNormal));
        FontUtils.setTypefaceForFont(label, font, false, Math.max(12f, preferredTextSize - 2f));
        label.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));
        row.addView(label);

        row.setContentDescription(getContentDescription(link));
        row.setOnClickListener(v -> Utils.openLinkMaybeHN(v.getContext(), link.getUrl()));
        FaviconLoader.loadFavicon(link.getUrl(), favicon, ctx, faviconProvider);

        return row;
    }

    private static Drawable createRipple(View source, int cornerRadius) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(cornerRadius);
        return new RippleDrawable(
                ColorStateList.valueOf(MaterialColors.getColor(source, android.R.attr.colorControlHighlight)),
                null,
                mask);
    }

    private static String getContentDescription(CollectedReferenceLinks.ReferenceLink link) {
        if (link.hasNumber()) {
            return "Open reference link " + link.getNumber() + ": " + getLabel(link);
        }
        return "Open link: " + getLabel(link);
    }

    private static String getLabel(CollectedReferenceLinks.ReferenceLink link) {
        String label = link.getLabel();
        if (TextUtils.isEmpty(label)) {
            return link.getUrl();
        }
        return label.replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }
}
