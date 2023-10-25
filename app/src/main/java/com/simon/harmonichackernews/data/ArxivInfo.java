package com.simon.harmonichackernews.data;

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
        return publishedDate;
    }

    public String formatSubjects() {
        String allSubjects = primaryCategory;

        if (secondaryCategories.length == 0) {
            return allSubjects;
        }

        for (int i = 1; i < secondaryCategories.length; i++) {
            allSubjects = allSubjects + "\n" + secondaryCategories[i];
        }
        return allSubjects;
    }

}


