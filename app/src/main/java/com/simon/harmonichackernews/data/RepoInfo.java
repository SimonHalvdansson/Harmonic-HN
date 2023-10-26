package com.simon.harmonichackernews.data;

public class RepoInfo {

    public String name;
    public String owner;
    public String about;
    public String website;
    public String license;
    public String language;
    public int stars;
    public int watching;
    public int forks;

    public String formatStars() {
        if (stars == 1) {
            return "1 star";
        }
        return kFormat(stars) + " stars";

    }

    public String formatWatching() {
        return kFormat(watching) + " watching";
    }

    public String formatForks() {
        if (forks == 1) {
            return "1 fork";
        }

        return kFormat(forks) + " forks";
    }

    public String getShortenedUrl() {
        if (website == null) {
            return null;
        }

        // Remove https:// prefix
        if (website.startsWith("https://")) {
            website = website.substring(8);
        } else if (website.startsWith("http://")) {
            website = website.substring(7);
        }

        // Remove www. prefix
        if (website.startsWith("www.")) {
            website = website.substring(4);
        }

        return website;
    }

    public String kFormat(int number) {
        if (number < 1000) {
            return String.valueOf(number);
        } else {
            // Round to the nearest 100 and then divide by 1000
            double rounded = Math.round((double) number / 100) * 100;
            String result = String.format("%.1fk", rounded / 1000);

            // Remove trailing ".0"
            if (result.endsWith(".0k")) {
                return result.substring(0, result.length() - 3) + "k";
            }
            return result;
        }
    }

}
