package com.simon.harmonichackernews.data;

public class StackExchangeInfo {

    public String title;
    public String author;
    public String questionText;
    public String[] tags;
    public String site;
    public int score;
    public int answerCount;
    public int viewCount;
    public boolean isAnswered;
    public boolean hasAcceptedAnswer;

    public String formatScore() {
        return LinkPreviewFormatUtils.formatCount(score, "point", "points");
    }

    public String formatAnswerCount() {
        return LinkPreviewFormatUtils.formatCount(answerCount, "answer", "answers");
    }

    public String formatViewCount() {
        return LinkPreviewFormatUtils.formatCount(viewCount, "view", "views");
    }

    public String formatAnswerState() {
        if (hasAcceptedAnswer) {
            return "Accepted answer";
        }

        if (isAnswered) {
            return "Answered";
        }

        return "Unanswered";
    }

    public String formatTags() {
        if (tags == null || tags.length == 0) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < tags.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(tags[i]);
        }
        return builder.toString();
    }

    public String formatBy() {
        if (questionText != null) {
            return questionText;
        }

        if (author == null) {
            return site;
        }

        return author + " on " + site;
    }

    public String formatAuthor() {
        if (author == null) {
            return site;
        }

        return author;
    }
}
