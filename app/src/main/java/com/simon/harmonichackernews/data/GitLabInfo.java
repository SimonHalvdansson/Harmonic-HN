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
        return LinkPreviewFormatUtils.formatCount(stars, "star", "stars");
    }

    public String formatForks() {
        return LinkPreviewFormatUtils.formatCount(forks, "fork", "forks");
    }

    public String formatVisibility() {
        if (visibility == null) {
            return null;
        }

        return visibility.substring(0, 1).toUpperCase() + visibility.substring(1);
    }

    public String getShortenedUrl() {
        return LinkPreviewFormatUtils.shortenUrl(website);
    }
}
