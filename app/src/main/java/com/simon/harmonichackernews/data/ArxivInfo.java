package com.simon.harmonichackernews.data;

import com.simon.harmonichackernews.utils.ArxivResolver;

public class ArxivInfo {

    public String arxivAbstract;
    public String[] authors;
    public String primaryCategory;

    public String[] secondaryCategories;

    public String publishedDate;

    public String concatNames() {
        if (authors.length == 0) {
            return "";
        }

        String allNames = authors[0];
        for (int i = 1; i < authors.length; i++) {
            allNames = allNames + ", " + authors[i];
        }
        return allNames;
    }

    public String formatDate() {
        return publishedDate.substring(0, 10);
    }

    public String formatSubjects() {
        String allSubjects = ArxivResolver.resolveFull(primaryCategory);

        if (secondaryCategories.length == 0) {
            return allSubjects;
        }

        for (int i = 1; i < secondaryCategories.length; i++) {
            allSubjects = allSubjects + "; " + ArxivResolver.resolveFull(secondaryCategories[i]);
        }
        return allSubjects;
    }

}


