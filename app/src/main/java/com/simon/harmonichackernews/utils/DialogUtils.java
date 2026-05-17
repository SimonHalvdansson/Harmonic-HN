package com.simon.harmonichackernews.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.simon.harmonichackernews.R;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.sufficientlysecure.htmltextview.HtmlTextView;
import org.sufficientlysecure.htmltextview.OnClickATagListener;

public class DialogUtils {

    public static void showTextSelectionDialog(Context ctx, String text) {
        MaterialAlertDialogBuilder selectTextDialogBuilder = new MaterialAlertDialogBuilder(ctx);
        View rootView = LayoutInflater.from(ctx).inflate(R.layout.select_text_dialog, null);
        selectTextDialogBuilder.setView(rootView);

        HtmlTextView htmlTextView = rootView.findViewById(R.id.select_text_htmltextview);

        htmlTextView.setHtml(expandShortenedAnchorText(text));

        htmlTextView.setOnClickATagListener(new OnClickATagListener() {
            @Override
            public boolean onClick(View widget, String spannedText, @Nullable String href) {
                Utils.openLinkMaybeHN(ctx, href);
                return true;
            }
        });

        final AlertDialog selectTextDialog = selectTextDialogBuilder.create();

        selectTextDialog.show();
    }

    private static String expandShortenedAnchorText(String inputHtml) {
        Document document = Jsoup.parse(inputHtml, "", Parser.htmlParser());
        Elements links = document.select("a[href]");

        for (Element link : links) {
            String href = link.attr("href");
            String linkText = link.text();

            String decodedHref = Jsoup.parse(href).text();
            String decodedLinkText = Jsoup.parse(linkText).text();

            if (decodedLinkText.endsWith("...")) {
                String linkTextPrefix = decodedLinkText.substring(0, decodedLinkText.length() - 3);
                if (decodedHref.startsWith(linkTextPrefix)) {
                    link.text(decodedHref);
                }
            }
        }

        return document.body().html();
    }

}
