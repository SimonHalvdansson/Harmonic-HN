/*
 * Copyright (C) 2013-2014 Dominik Schürmann <dominik@schuermann.eu>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sufficientlysecure.htmltextview;

import android.content.Context;
import android.text.Html;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.QuoteSpan;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;

import java.io.InputStream;
import java.util.Scanner;

public class HtmlTextView extends JellyBeanSpanFixTextView {

    public static final String TAG = "HtmlTextView";
    public static final boolean DEBUG = false;
    public int blockQuoteBackgroundColor = getResources().getColor(android.R.color.white);
    public int blockQuoteStripColor = getResources().getColor(android.R.color.black);
    public float blockQuoteStripWidth = 10F;
    public float blockQuoteGap = 20F;
    @Nullable
    private ClickableTableSpan clickableTableSpan;
    @Nullable
    private DrawTableLinkSpan drawTableLinkSpan;
    @Nullable
    private OnClickATagListener onClickATagListener;
    private float indent = 24.0f; // Default to 24px.

    private boolean removeTrailingWhiteSpace = true;

    public HtmlTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public HtmlTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public HtmlTextView(Context context) {
        super(context);
    }

    /**
     * @see org.sufficientlysecure.htmltextview.HtmlTextView#setHtml(int)
     */
    public void setHtml(@RawRes int resId) {
        setHtml(resId, null);
    }

    /**
     * @see org.sufficientlysecure.htmltextview.HtmlTextView#setHtml(String)
     */
    public void setHtml(@NonNull String html) {
        setHtml(html, null);
    }

    /**
     * Loads HTML from a raw resource, i.e., a HTML file in res/raw/.
     * This allows translatable resource (e.g., res/raw-de/ for german).
     * The containing HTML is parsed to Android's Spannable format and then displayed.
     *
     * @param resId       for example: R.raw.help
     * @param imageGetter for fetching images. Possible ImageGetter provided by this library:
     *                    HtmlLocalImageGetter and HtmlRemoteImageGetter
     */
    public void setHtml(@RawRes int resId, @Nullable Html.ImageGetter imageGetter) {
        InputStream inputStreamText = getContext().getResources().openRawResource(resId);

        setHtml(convertStreamToString(inputStreamText), imageGetter);
    }

    /**
     * Parses String containing HTML to Android's Spannable format and displays it in this TextView.
     * Using the implementation of Html.ImageGetter provided.
     *
     * @param html        String containing HTML, for example: "<b>Hello world!</b>"
     * @param imageGetter for fetching images. Possible ImageGetter provided by this library:
     *                    HtmlLocalImageGetter and HtmlRemoteImageGetter
     */
    public void setHtml(@NonNull String html, @Nullable Html.ImageGetter imageGetter) {
        Spanned styledText = HtmlFormatter.formatHtml(
                html, imageGetter, clickableTableSpan, drawTableLinkSpan,
                new HtmlFormatter.TagClickListenerProvider() {
                    @Override
                    public OnClickATagListener provideTagClickListener() {
                        return onClickATagListener;
                    }
                }, indent, removeTrailingWhiteSpace
        );
        replaceQuoteSpans(styledText);
        setText(styledText);

        // make links work
        setMovementMethod(LocalLinkMovementMethod.getInstance());
    }

    /**
     * The Html.fromHtml method has the behavior of adding extra whitespace at the bottom
     * of the parsed HTML displayed in for example a TextView. In order to remove this
     * whitespace call this method before setting the text with setHtml on this TextView.
     *
     * @param removeTrailingWhiteSpace true if the whitespace rendered at the bottom of a TextView
     *                                 after setting HTML should be removed.
     */
    public void setRemoveTrailingWhiteSpace(boolean removeTrailingWhiteSpace) {
        this.removeTrailingWhiteSpace = removeTrailingWhiteSpace;
    }

    public void setClickableTableSpan(@Nullable ClickableTableSpan clickableTableSpan) {
        this.clickableTableSpan = clickableTableSpan;
    }

    public void setDrawTableLinkSpan(@Nullable DrawTableLinkSpan drawTableLinkSpan) {
        this.drawTableLinkSpan = drawTableLinkSpan;
    }

    public void setOnClickATagListener(@Nullable OnClickATagListener onClickATagListener) {
        this.onClickATagListener = onClickATagListener;
    }

    /**
     * Add ability to increase list item spacing. Useful for configuring spacing based on device
     * screen size. This applies to ordered and unordered lists.
     *
     * @param px pixels to indent.
     */
    public void setListIndentPx(float px) {
        this.indent = px;
    }

    /**
     * http://stackoverflow.com/questions/309424/read-convert-an-inputstream-to-a-string
     */
    @NonNull
    private static String convertStreamToString(@NonNull InputStream is) {
        Scanner s = new Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    private void replaceQuoteSpans(Spanned spanned) {

        Spannable spannable = (Spannable) spanned;
        QuoteSpan[] quoteSpans = spannable.getSpans(0, spannable.length() - 1, QuoteSpan.class);
        for (QuoteSpan quoteSpan : quoteSpans) {
            int start = spannable.getSpanStart(quoteSpan);
            int end = spannable.getSpanEnd(quoteSpan);
            int flags = spannable.getSpanFlags(quoteSpan);
            spannable.removeSpan(quoteSpan);
            spannable.setSpan(new DesignQuoteSpan(
                            blockQuoteBackgroundColor,
                            blockQuoteStripColor,
                            blockQuoteStripWidth,
                            blockQuoteGap),
                    start,
                    end,
                    flags);
        }
    }


}