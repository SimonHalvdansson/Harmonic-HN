package com.simon.harmonichackernews.network;

import android.content.Context;
import android.util.Xml;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArxivAbstractGetter {

    public static boolean isValidArxivUrl(String url) {
        String arxivUrlPattern = "^https?:\\/\\/arxiv\\.org\\/abs\\/((\\d{4}\\.\\d{4,5}(v\\d+)?)|([a-z\\-]+\\/\\d{2}\\d{4}))(\\.pdf)?$";

        Pattern pattern = Pattern.compile(arxivUrlPattern);
        Matcher matcher = pattern.matcher(url);

        return matcher.matches();
    }

    public static void getAbstract(String url, Context ctx, NetworkComponent.GetterCallback callback) {
        String arxivID = url.substring(url.lastIndexOf('/') + 1).replace(".pdf", "");

        StringRequest stringRequest = new StringRequest(Request.Method.GET, "http://export.arxiv.org/api/query?id_list=" + arxivID,
                response -> {
                    try {
                        XmlPullParser parser = Xml.newPullParser();
                        parser.setInput(new StringReader(response));
                        int eventType = parser.getEventType();
                        String abstractText = "";

                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            if (eventType == XmlPullParser.START_TAG && "summary".equals(parser.getName())) {
                                parser.next();
                                abstractText = parser.getText();
                                break;
                            }
                            eventType = parser.next();
                        }

                        if (!abstractText.isEmpty()) {
                            callback.onSuccess(abstractText);
                        } else {
                            callback.onFailure("Abstract not found");
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
}


