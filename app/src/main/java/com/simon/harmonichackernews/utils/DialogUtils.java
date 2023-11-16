package com.simon.harmonichackernews.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.simon.harmonichackernews.R;

import org.sufficientlysecure.htmltextview.HtmlTextView;
import org.sufficientlysecure.htmltextview.OnClickATagListener;

public class DialogUtils {

    public static void showTextSelectionDialog(Context ctx, String text) {
        MaterialAlertDialogBuilder selectTextDialogBuilder = new MaterialAlertDialogBuilder(ctx);
        View rootView = LayoutInflater.from(ctx).inflate(R.layout.select_text_dialog, null);
        selectTextDialogBuilder.setView(rootView);

        HtmlTextView htmlTextView = rootView.findViewById(R.id.select_text_htmltextview);
        htmlTextView.setHtml(text);

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

}
