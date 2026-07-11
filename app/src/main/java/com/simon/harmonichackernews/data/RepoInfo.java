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
        return LinkPreviewFormatUtils.formatCount(stars, "star", "stars");
    }

    public String formatWatching() {
        return LinkPreviewFormatUtils.kFormat(watching) + " watching";
    }

    public String formatForks() {
        return LinkPreviewFormatUtils.formatCount(forks, "fork", "forks");
    }

    public String getShortenedUrl() {
        return LinkPreviewFormatUtils.shortenUrl(website);
    }

}
