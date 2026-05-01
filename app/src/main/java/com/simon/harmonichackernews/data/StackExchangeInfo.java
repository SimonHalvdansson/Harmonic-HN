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
        if (score == 1) {
            return "1 point";
        }
        return kFormat(score) + " points";
    }

    public String formatAnswerCount() {
        if (answerCount == 1) {
            return "1 answer";
        }
        return kFormat(answerCount) + " answers";
    }

    public String formatViewCount() {
        if (viewCount == 1) {
            return "1 view";
        }
        return kFormat(viewCount) + " views";
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

    private String kFormat(int number) {
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
