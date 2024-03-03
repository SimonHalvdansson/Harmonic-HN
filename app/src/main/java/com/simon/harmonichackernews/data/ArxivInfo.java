package com.simon.harmonichackernews.data;

import android.text.TextUtils;

import com.simon.harmonichackernews.utils.ArxivResolver;

public class ArxivInfo {

    public String arxivAbstract;
    public String[] authors;
    public String primaryCategory;
    public String arxivID;

    public String[] secondaryCategories;

    public String publishedDate;

    public String concatNames() {
        return TextUtils.join(", ", authors);
    }

    public String formatDate() {
        return publishedDate.substring(0, 10);
    }

    public String formatSubjects() {
        StringBuilder allSubjects = new StringBuilder(ArxivResolver.resolveFull(primaryCategory));

        for (String secondaryCategory : secondaryCategories) {
            allSubjects.append("; ").append(ArxivResolver.resolveFull(secondaryCategory));
        }
        return allSubjects.toString();
    }

    public String getPDFURL() {
        return "https://arxiv.org/pdf/" + arxivID + ".pdf";
    }

}


