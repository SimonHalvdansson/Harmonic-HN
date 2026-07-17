package com.simon.harmonichackernews.network;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative fallback extraction for pages whose description metadata is not useful. */
final class HtmlDescriptionExtractor {
    private static final int MIN_DESCRIPTION_CHARS = 32;
    private static final int MIN_LETTER_CHARS = 20;
    private static final int MIN_LATIN_WORDS = 5;
    private static final int MAX_CANDIDATE_CHARS = 600;
    private static final Pattern WORD_PATTERN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}'’_-]*");
    private static final Pattern SENTENCE_PUNCTUATION_PATTERN = Pattern.compile("[.!?。！？]");
    private static final Pattern POSITIVE_CONTAINER_PATTERN = Pattern.compile(
            "(?:^|[-_\\s])(article|articlebody|article-body|body|content|entry|main|post|story|text)"
                    + "(?:$|[-_\\s])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NEGATIVE_CONTAINER_PATTERN = Pattern.compile(
            "(?:^|[-_\\s])(ad|advert|author|bio|breadcrumb|caption|comment|comments|consent|cookie|"
                    + "credit|date|dialog|footer|header|login|menu|meta|modal|nav|newsletter|popup|"
                    + "promo|recommend|related|reply|share|sidebar|signup|social|subscribe|tag|widget)"
                    + "(?:$|[-_\\s])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BOILERPLATE_PATTERN = Pattern.compile(
            "^(advertisement|all rights reserved|click here|enable javascript|home|homepage|loading|"
                    + "log in|read more|sign in|sign up|skip to|subscribe|welcome)(?:[.!\\s]|$)|"
                    + "^(please enable (?:java ?script|js)|this site uses cookies|we use cookies|your browser)|"
                    + "^by .{0,120}\\bisbn\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKUP_OR_STYLE_PATTERN = Pattern.compile(
            "<[a-z][^>]*>|\\{[^}]{0,160}:|(?:^|[;{])\\s*[a-z-]{2,}\\s*:",
            Pattern.CASE_INSENSITIVE);

    private HtmlDescriptionExtractor() {
    }

    static String chooseDescription(
            String metadataDescription,
            Document document,
            String pageTitle,
            String fallbackTitle) {
        String cleanedMetadata = clean(metadataDescription);
        if (isMeaningful(cleanedMetadata, pageTitle, fallbackTitle)) {
            return cleanedMetadata;
        }

        String extracted = extract(document, pageTitle, fallbackTitle);
        return extracted.isEmpty() ? cleanedMetadata : extracted;
    }

    static boolean isMeaningful(String value, String pageTitle, String fallbackTitle) {
        String cleaned = clean(value);
        String qualityText = withoutProviderBoilerplate(cleaned);
        if (cleaned.length() < MIN_DESCRIPTION_CHARS
                || countLetters(cleaned) < MIN_LETTER_CHARS
                || BOILERPLATE_PATTERN.matcher(cleaned).find()
                || MARKUP_OR_STYLE_PATTERN.matcher(cleaned).find()
                || duplicatesTitle(qualityText, pageTitle)
                || duplicatesTitle(qualityText, fallbackTitle)) {
            return false;
        }

        int letterCount = countLetters(cleaned);
        int latinLetterCount = countLatinLetters(cleaned);
        return latinLetterCount * 2 < letterCount || countWords(cleaned) >= MIN_LATIN_WORDS;
    }

    private static String extract(Document document, String pageTitle, String fallbackTitle) {
        Element bestParagraph = null;
        String bestText = "";
        int bestScore = Integer.MIN_VALUE;
        int paragraphIndex = 0;
        for (Element paragraph : document.select("p")) {
            String text = clean(paragraph.text());
            if (!isUsableParagraph(paragraph, text, pageTitle, fallbackTitle)) {
                paragraphIndex++;
                continue;
            }

            int score = scoreParagraph(paragraph, text, paragraphIndex);
            if (score > bestScore) {
                bestParagraph = paragraph;
                bestText = text;
                bestScore = score;
            }
            paragraphIndex++;
        }
        if (bestParagraph != null) {
            return truncate(bestText);
        }

        for (Element container : document.select("[itemprop~=articleBody], article, main, [role=main]")) {
            if (isExcluded(container)) {
                continue;
            }
            String text = withoutLeadingTitle(clean(container.text()), pageTitle);
            if (isMeaningful(text, pageTitle, fallbackTitle)) {
                return truncate(text);
            }
        }
        return "";
    }

    private static boolean isUsableParagraph(
            Element paragraph,
            String text,
            String pageTitle,
            String fallbackTitle) {
        if (!isMeaningful(text, pageTitle, fallbackTitle) || isExcluded(paragraph)) {
            return false;
        }

        int linkedChars = 0;
        for (Element link : paragraph.select("a")) {
            linkedChars += clean(link.text()).length();
        }
        return linkedChars <= text.length() * 0.35f;
    }

    private static boolean isExcluded(Element element) {
        for (Element current = element; current != null; current = current.parent()) {
            String tag = current.tagName();
            if ("aside".equals(tag)
                    || "code".equals(tag)
                    || "dialog".equals(tag)
                    || "footer".equals(tag)
                    || "form".equals(tag)
                    || "header".equals(tag)
                    || "li".equals(tag)
                    || "nav".equals(tag)
                    || "pre".equals(tag)) {
                return true;
            }

            String identifiers = current.id() + " " + current.className();
            if (NEGATIVE_CONTAINER_PATTERN.matcher(identifiers).find()
                    || current.hasAttr("hidden")
                    || "true".equalsIgnoreCase(current.attr("aria-hidden"))) {
                return true;
            }
            String style = current.attr("style").toLowerCase(Locale.US).replace(" ", "");
            if (style.contains("display:none") || style.contains("visibility:hidden")) {
                return true;
            }
        }
        return false;
    }

    private static int scoreParagraph(Element paragraph, String text, int paragraphIndex) {
        int score = Math.min(text.length(), 240) / 4;
        if (SENTENCE_PUNCTUATION_PATTERN.matcher(text).find()) {
            score += 20;
        }

        boolean positiveContainerFound = false;
        for (Element current = paragraph.parent(); current != null; current = current.parent()) {
            if ("article".equals(current.tagName())
                    || current.hasAttr("itemprop")
                    && current.attr("itemprop").toLowerCase(Locale.US).contains("articlebody")) {
                score += 500;
                positiveContainerFound = true;
                break;
            }
            if ("main".equals(current.tagName()) || "main".equalsIgnoreCase(current.attr("role"))) {
                score += 400;
                positiveContainerFound = true;
                break;
            }
            String identifiers = current.id() + " " + current.className();
            if (POSITIVE_CONTAINER_PATTERN.matcher(identifiers).find()) {
                positiveContainerFound = true;
            }
        }
        if (positiveContainerFound) {
            score += 250;
        }

        return score - Math.min(paragraphIndex, 100) * 3;
    }

    private static boolean duplicatesTitle(String value, String pageTitle) {
        String normalizedValue = normalizeComparable(value);
        String normalizedTitle = normalizeComparable(pageTitle);
        return !normalizedTitle.isEmpty()
                && (normalizedValue.equals(normalizedTitle)
                || normalizedValue.startsWith(normalizedTitle)
                && normalizedValue.length() - normalizedTitle.length() < 16);
    }

    private static String withoutLeadingTitle(String value, String pageTitle) {
        String cleanedTitle = clean(pageTitle);
        if (!cleanedTitle.isEmpty()
                && value.regionMatches(true, 0, cleanedTitle, 0, cleanedTitle.length())) {
            return clean(value.substring(cleanedTitle.length()).replaceFirst("^[|:–—\\-\\s]+", ""));
        }
        return value;
    }

    private static String withoutProviderBoilerplate(String value) {
        return clean(value.replaceFirst(
                "(?i)\\.?\\s*Contribute to .+ development by creating an account on GitHub\\.?$",
                ""));
    }

    private static String normalizeComparable(String value) {
        return clean(value).toLowerCase(Locale.US).replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    private static int countWords(String value) {
        int count = 0;
        Matcher matcher = WORD_PATTERN.matcher(value);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static int countLetters(String value) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (Character.isLetter(value.charAt(i))) {
                count++;
            }
        }
        return count;
    }

    private static int countLatinLetters(String value) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (Character.UnicodeScript.of(value.charAt(i)) == Character.UnicodeScript.LATIN) {
                count++;
            }
        }
        return count;
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String truncate(String value) {
        String cleaned = clean(value);
        if (cleaned.length() <= MAX_CANDIDATE_CHARS) {
            return cleaned;
        }
        int lastSpace = cleaned.lastIndexOf(' ', MAX_CANDIDATE_CHARS - 1);
        int end = lastSpace >= MAX_CANDIDATE_CHARS * 0.75f ? lastSpace : MAX_CANDIDATE_CHARS;
        return cleaned.substring(0, end).trim() + "…";
    }
}
