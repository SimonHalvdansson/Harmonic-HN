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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DialogUtils {

    public static void showTextSelectionDialog(Context ctx, String text) {
        MaterialAlertDialogBuilder selectTextDialogBuilder = new MaterialAlertDialogBuilder(ctx);
        View rootView = LayoutInflater.from(ctx).inflate(R.layout.select_text_dialog, null);
        selectTextDialogBuilder.setView(rootView);

        HtmlTextView htmlTextView = rootView.findViewById(R.id.select_text_htmltextview);

        htmlTextView.setHtml(undoShortenedLinks(text));

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

    //made by chatgpt obviously
    public static String undoShortenedLinks(String inputHtml) {
        // Parse the HTML content. Use Parser.htmlParser() to ensure it parses as HTML.
        Document document = Jsoup.parse(inputHtml, "", Parser.htmlParser());

        // Select all <a> elements
        Elements links = document.select("a[href]");

        for (Element link : links) {
            String href = link.attr("href");
            String linkText = link.text();

            // Decode HTML entities in href and linkText
            String decodedHref = Jsoup.parse(href).text();
            String decodedLinkText = Jsoup.parse(linkText).text();

            // Check if link text ends with "..."
            if (decodedLinkText.endsWith("...")) {
                // Extract the prefix of the link text without "..."
                String linkTextPrefix = decodedLinkText.substring(0, decodedLinkText.length() - 3);

                // Check if the href starts with the link text prefix
                if (decodedHref.startsWith(linkTextPrefix)) {
                    // Replace the link text with the full href
                    link.text(decodedHref);
                }
            }
        }

        // Return the modified HTML as a string
        // To preserve the original HTML structure, extract the body’s inner HTML
        return document.body().html();
    }

}
