package com.simon.harmonichackernews.data;

public class GitLabInfo {

    public String name;
    public String namespace;
    public String description;
    public String website;
    public String language;
    public String visibility;
    public int stars;
    public int forks;

    public String formatStars() {
        if (stars == 1) {
            return "1 star";
        }
        return kFormat(stars) + " stars";
    }

    public String formatForks() {
        if (forks == 1) {
            return "1 fork";
        }
        return kFormat(forks) + " forks";
    }

    public String formatVisibility() {
        if (visibility == null) {
            return null;
        }

        return visibility.substring(0, 1).toUpperCase() + visibility.substring(1);
    }

    public String getShortenedUrl() {
        if (website == null) {
            return null;
        }

        if (website.startsWith("https://")) {
            website = website.substring(8);
        } else if (website.startsWith("http://")) {
            website = website.substring(7);
        }

        if (website.startsWith("www.")) {
            website = website.substring(4);
        }

        return website;
    }

    public String kFormat(int number) {
        if (number < 1000) {
            return String.valueOf(number);
        } else {
            double rounded = Math.round((double) number / 100) * 100;
            String result = String.format("%.1fk", rounded / 1000);

            if (result.endsWith(".0k")) {
                return result.substring(0, result.length() - 3) + "k";
            }
            return result;
        }
    }
}
