package com.simon.harmonichackernews.linkpreview;

import android.content.Context;
import android.util.Xml;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.simon.harmonichackernews.data.ArxivInfo;
import com.simon.harmonichackernews.network.NetworkComponent;
import com.simon.harmonichackernews.utils.ArxivResolver;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArxivAbstractGetter {

    public static boolean isValidArxivUrl(String url) {
        String arxivUrlPattern = "^https?:\\/\\/arxiv\\.org\\/abs\\/((\\d{4}\\.\\d{4,5}(v\\d+)?)|([a-z\\-]+\\/\\d{2}\\d{4}))(\\.pdf)?$";

        Pattern pattern = Pattern.compile(arxivUrlPattern);
        Matcher matcher = pattern.matcher(url);

        return matcher.matches();
    }

    public static void getAbstract(String url, Context ctx, GetterCallback callback) {
        String arxivID = url.substring(url.lastIndexOf('/') + 1).replace(".pdf", "");

        StringRequest stringRequest = new StringRequest(Request.Method.GET, "http://export.arxiv.org/api/query?id_list=" + arxivID,
                response -> {
                    try {
                        XmlPullParser parser = Xml.newPullParser();
                        parser.setInput(new StringReader(response));
                        int eventType = parser.getEventType();

                        String abstractText = "";
                        List<String> authorList = new ArrayList<>();
                        String primaryCategoryText = "";
                        List<String> secondaryCategoryList = new ArrayList<>();
                        String publishedDateText = "";

                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            String tagName = parser.getName();

                            // Parsing the summary
                            if (eventType == XmlPullParser.START_TAG && "summary".equals(tagName)) {
                                parser.next();
                                abstractText = parser.getText();
                            }

                            // Parsing the authors
                            if (eventType == XmlPullParser.START_TAG && "name".equals(tagName)) {
                                parser.next();
                                authorList.add(parser.getText());
                            }

                            // Parsing the primary category
                            if (eventType == XmlPullParser.START_TAG && "primary_category".equals(tagName)) {
                                primaryCategoryText = parser.getAttributeValue(null, "term");
                            }

                            // Parsing secondary categories
                            if (eventType == XmlPullParser.START_TAG && "category".equals(tagName)) {
                                String category = parser.getAttributeValue(null, "term");
                                if (!category.equals(primaryCategoryText)) {
                                    secondaryCategoryList.add(category);
                                }
                            }

                            // Parsing the published date
                            if (eventType == XmlPullParser.START_TAG && "published".equals(tagName)) {
                                parser.next();
                                publishedDateText = parser.getText();
                            }

                            eventType = parser.next();
                        }

                        // Convert author list to array
                        String[] authorsArray = authorList.toArray(new String[0]);

                        // API 23 does not support Java 8 so we do this by hand
                        List<String> secondaryCategoriesFiltered = new ArrayList<>();
                        for (String category : secondaryCategoryList) {
                            if (ArxivResolver.isArxivSubjet(category)) {
                                secondaryCategoriesFiltered.add(category);
                            }
                        }

                        String[] secondaryCategoriesFilteredArray = secondaryCategoriesFiltered.toArray(new String[0]);

                        // Create ArxivInfo object
                        ArxivInfo info = new ArxivInfo();
                        info.arxivAbstract = abstractText;
                        info.authors = authorsArray;
                        info.primaryCategory = primaryCategoryText;
                        info.secondaryCategories = secondaryCategoriesFilteredArray;
                        info.publishedDate = publishedDateText;
                        info.arxivID = arxivID;

                        if (!abstractText.isEmpty() && authorsArray.length > 0 && !primaryCategoryText.isEmpty() && !publishedDateText.isEmpty()) {
                            callback.onSuccess(info);
                        } else {
                            callback.onFailure("Data not found");
                        }

                    } catch (XmlPullParserException | IOException e) {
                        callback.onFailure("Failed to parse ArXiv API response");
                        e.printStackTrace();
                    }
                },
                error -> {
                    error.printStackTrace();
                    callback.onFailure("Couldn't connect to ArXiv API");
                });

        RequestQueue queue = NetworkComponent.getRequestQueueInstance(ctx);
        queue.add(stringRequest);
    }

    public interface GetterCallback {
        void onSuccess(ArxivInfo arxivInfo);

        void onFailure(String reason);
    }
}
