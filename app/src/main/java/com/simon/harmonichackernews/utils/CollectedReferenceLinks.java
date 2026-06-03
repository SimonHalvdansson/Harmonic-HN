package com.simon.harmonichackernews.utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CollectedReferenceLinks {

    private static final Pattern REFERENCE_MARKER_PATTERN =
            Pattern.compile("\\G\\s*\\[(\\d{1,3})\\]\\s*(?::|\\.|-|\\u2013|\\u2014)?\\s*");

    private CollectedReferenceLinks() {
    }

    public static Result parse(String inputHtml) {
        if (inputHtml == null || inputHtml.isEmpty()) {
            return Result.empty(inputHtml);
        }

        Document document = Jsoup.parse(inputHtml, "", Parser.htmlParser());
        document.outputSettings().prettyPrint(false);

        Element body = document.body();
        List<Node> nodes = new ArrayList<>(body.childNodes());
        if (nodes.isEmpty()) {
            return Result.empty(inputHtml);
        }

        List<Node> nodesToRemove = new ArrayList<>();
        List<Node> trailingIgnorableNodes = new ArrayList<>();
        List<ReferenceLink> links = new ArrayList<>();
        boolean hasNumberedLinks = false;

        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node node = nodes.get(i);
            if (isIgnorable(node)) {
                trailingIgnorableNodes.add(node);
                continue;
            }

            List<ReferenceLink> parsedLinks = parseReferenceNode(node);
            if (parsedLinks.isEmpty()) {
                parsedLinks = parseUnnumberedLinkNode(node);
            }
            if (parsedLinks.isEmpty()) {
                break;
            }

            links.addAll(0, parsedLinks);
            hasNumberedLinks = hasNumberedLinks || containsNumberedLinks(parsedLinks);
            nodesToRemove.add(node);
            nodesToRemove.addAll(trailingIgnorableNodes);
            trailingIgnorableNodes.clear();
        }

        if (links.isEmpty() || (!hasNumberedLinks && links.size() < 2)) {
            return Result.empty(inputHtml);
        }

        for (Node node : nodesToRemove) {
            node.remove();
        }

        return new Result(body.html().trim(), links);
    }

    private static boolean isIgnorable(Node node) {
        if (node instanceof TextNode) {
            return ((TextNode) node).isBlank();
        }
        if (node instanceof Element) {
            Element element = (Element) node;
            return ("br".equalsIgnoreCase(element.tagName()) || isReferenceContainerTag(element))
                    && element.text().trim().isEmpty();
        }
        return false;
    }

    private static List<ReferenceLink> parseReferenceNode(Node node) {
        if (node instanceof Element) {
            Element element = (Element) node;
            if (!isReferenceContainerTag(element)) {
                return Collections.emptyList();
            }
            return parseReferenceFragment(element.html());
        }
        if (node instanceof TextNode) {
            return parseBareReferenceText(((TextNode) node).getWholeText());
        }
        return Collections.emptyList();
    }

    private static List<ReferenceLink> parseUnnumberedLinkNode(Node node) {
        if (node instanceof Element) {
            Element element = (Element) node;
            if (isAnchorTag(element)) {
                ReferenceLink link = parseUnnumberedAnchor(element);
                if (link == null) {
                    return Collections.emptyList();
                }
                return Collections.singletonList(link);
            }
            if (!isReferenceContainerTag(element)) {
                return Collections.emptyList();
            }
            return parseUnnumberedLinkFragment(element.html());
        }
        if (node instanceof TextNode) {
            return parseUnnumberedLinkText(((TextNode) node).getWholeText());
        }
        return Collections.emptyList();
    }

    private static boolean isReferenceContainerTag(Element element) {
        String tagName = element.tagName().toLowerCase(Locale.US);
        return "p".equals(tagName)
                || "div".equals(tagName)
                || "span".equals(tagName)
                || "li".equals(tagName);
    }

    private static boolean isAnchorTag(Element element) {
        return "a".equalsIgnoreCase(element.tagName()) && element.hasAttr("href");
    }

    private static List<ReferenceLink> parseReferenceFragment(String html) {
        Document fragment = Jsoup.parseBodyFragment(html == null ? "" : html, "");
        fragment.outputSettings().prettyPrint(false);
        String text = normalizeReferenceWhitespace(fragment.body().text());
        if (!startsWithReferenceMarker(text)) {
            return Collections.emptyList();
        }

        Elements anchors = fragment.select("a[href]");
        if (!anchors.isEmpty()) {
            return parseAnchoredReferenceText(text, anchors);
        }
        return parseBareReferenceText(text);
    }

    private static List<ReferenceLink> parseUnnumberedLinkFragment(String html) {
        Document fragment = Jsoup.parseBodyFragment(html == null ? "" : html, "");
        fragment.outputSettings().prettyPrint(false);
        String text = normalizeReferenceWhitespace(fragment.body().text());
        if (text.isEmpty() || startsWithReferenceMarker(text)) {
            return Collections.emptyList();
        }

        Elements anchors = fragment.select("a[href]");
        if (!anchors.isEmpty()) {
            return parseUnnumberedAnchoredLinkText(text, anchors);
        }
        return parseUnnumberedLinkText(text);
    }

    private static List<ReferenceLink> parseAnchoredReferenceText(String text, Elements anchors) {
        List<ReferenceLink> links = new ArrayList<>();
        int position = 0;

        for (Element anchor : anchors) {
            Matcher marker = REFERENCE_MARKER_PATTERN.matcher(text);
            marker.region(position, text.length());
            if (!marker.lookingAt()) {
                return Collections.emptyList();
            }

            position = marker.end();
            String displayLabel = normalizeReferenceWhitespace(anchor.text());
            if (displayLabel.isEmpty() || !startsWithAt(text, displayLabel, position)) {
                return Collections.emptyList();
            }

            position += displayLabel.length();
            position = skipInterReferenceSeparators(text, position);

            String url = normalizeUrl(anchor.attr("href"));
            if (!isUsableUrl(url)) {
                return Collections.emptyList();
            }
            String label = getAnchorLabel(displayLabel, url);
            links.add(new ReferenceLink(marker.group(1), url, label));
        }

        return position == text.length() ? links : Collections.emptyList();
    }

    private static List<ReferenceLink> parseUnnumberedAnchoredLinkText(String text, Elements anchors) {
        List<ReferenceLink> links = new ArrayList<>();
        int position = 0;

        for (Element anchor : anchors) {
            position = skipInterReferenceSeparators(text, position);

            String url = normalizeUrl(anchor.attr("href"));
            if (!isUsableUrl(url)) {
                return Collections.emptyList();
            }

            String displayLabel = normalizeReferenceWhitespace(anchor.text());
            if (displayLabel.isEmpty()) {
                return Collections.emptyList();
            }
            if (!startsWithAt(text, displayLabel, position)) {
                return Collections.emptyList();
            }

            position += displayLabel.length();
            String label = getAnchorLabel(displayLabel, url);
            links.add(new ReferenceLink(null, url, label));
        }

        position = skipInterReferenceSeparators(text, position);
        return position == text.length() ? links : Collections.emptyList();
    }

    private static List<ReferenceLink> parseBareReferenceText(String text) {
        String normalizedText = normalizeReferenceWhitespace(text);
        if (!startsWithReferenceMarker(normalizedText)) {
            return Collections.emptyList();
        }

        List<ReferenceLink> links = new ArrayList<>();
        int position = 0;

        while (position < normalizedText.length()) {
            Matcher marker = REFERENCE_MARKER_PATTERN.matcher(normalizedText);
            marker.region(position, normalizedText.length());
            if (!marker.lookingAt()) {
                return Collections.emptyList();
            }
            position = marker.end();

            int urlStart = position;
            while (position < normalizedText.length() && !isBareUrlTerminator(normalizedText.charAt(position))) {
                position++;
            }

            String label = trimTrailingUrlPunctuation(normalizedText.substring(urlStart, position));
            String url = normalizeUrl(label);
            if (!isUsableUrl(url)) {
                return Collections.emptyList();
            }

            links.add(new ReferenceLink(marker.group(1), url, label));
            position = skipInterReferenceSeparators(normalizedText, position);
        }

        return links;
    }

    private static List<ReferenceLink> parseUnnumberedLinkText(String text) {
        String normalizedText = normalizeReferenceWhitespace(text);
        if (normalizedText.isEmpty() || startsWithReferenceMarker(normalizedText)) {
            return Collections.emptyList();
        }

        List<ReferenceLink> links = new ArrayList<>();
        int position = 0;

        while (position < normalizedText.length()) {
            position = skipInterReferenceSeparators(normalizedText, position);
            if (position >= normalizedText.length()) {
                break;
            }

            int urlStart = position;
            while (position < normalizedText.length() && !isBareUrlTerminator(normalizedText.charAt(position))) {
                position++;
            }

            String label = trimTrailingUrlPunctuation(normalizedText.substring(urlStart, position));
            String url = normalizeUrl(label);
            if (!isUsableUrl(url)) {
                return Collections.emptyList();
            }

            links.add(new ReferenceLink(null, url, label));
        }

        return links;
    }

    private static ReferenceLink parseUnnumberedAnchor(Element anchor) {
        String url = normalizeUrl(anchor.attr("href"));
        if (!isUsableUrl(url)) {
            return null;
        }

        String displayLabel = normalizeReferenceWhitespace(anchor.text());
        if (displayLabel.isEmpty()) {
            return null;
        }

        return new ReferenceLink(null, url, getAnchorLabel(displayLabel, url));
    }

    private static String getAnchorLabel(String displayLabel, String url) {
        if (displayLabel.endsWith("...")) {
            String prefix = displayLabel.substring(0, displayLabel.length() - 3);
            if (url.startsWith(prefix)) {
                return url;
            }
        }
        return displayLabel;
    }

    private static boolean containsNumberedLinks(List<ReferenceLink> links) {
        for (ReferenceLink link : links) {
            if (link.hasNumber()) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWithReferenceMarker(String text) {
        if (text == null) {
            return false;
        }
        return REFERENCE_MARKER_PATTERN.matcher(text).lookingAt();
    }

    private static boolean startsWithAt(String text, String value, int position) {
        return position >= 0
                && position + value.length() <= text.length()
                && text.regionMatches(position, value, 0, value.length());
    }

    private static int skipInterReferenceSeparators(String text, int position) {
        while (position < text.length()) {
            char current = text.charAt(position);
            if (Character.isWhitespace(current) || current == ',' || current == ';' || current == '|') {
                position++;
            } else {
                break;
            }
        }
        return position;
    }

    private static boolean isBareUrlTerminator(char value) {
        return Character.isWhitespace(value) || value == ',' || value == ';' || value == '|';
    }

    private static String normalizeReferenceWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00a0', ' ').trim().replaceAll("\\s+", " ");
    }

    private static String normalizeUrl(String value) {
        String url = trimTrailingUrlPunctuation(Jsoup.parse(value == null ? "" : value).text().trim()
                .replace("&#x2F;", "/")
                .replace("&#47;", "/"));
        if (url.startsWith("//")) {
            return "https:" + url;
        }
        if (url.startsWith("/")) {
            return "https://news.ycombinator.com" + url;
        }
        if (!url.contains("://") && looksLikeDomain(url)) {
            return "https://" + url;
        }
        return url;
    }

    private static String trimTrailingUrlPunctuation(String value) {
        String trimmed = value == null ? "" : value.trim();
        while (trimmed.length() > 0) {
            char last = trimmed.charAt(trimmed.length() - 1);
            if (last == '.' || last == ',' || last == ';' || last == ':' || last == '!' || last == '?') {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            } else {
                break;
            }
        }
        return trimmed;
    }

    private static boolean isUsableUrl(String url) {
        return url != null
                && (url.startsWith("http://") || url.startsWith("https://"))
                && url.length() > "https://".length();
    }

    private static boolean looksLikeDomain(String value) {
        if (value == null || value.contains("/") && value.startsWith("/")) {
            return false;
        }
        String lower = value.toLowerCase(Locale.US);
        return lower.matches("[a-z0-9][a-z0-9.-]*\\.[a-z]{2,}(:\\d+)?(/\\S*)?");
    }

    public static class Result {
        private final String bodyHtml;
        private final List<ReferenceLink> links;

        private Result(String bodyHtml, List<ReferenceLink> links) {
            this.bodyHtml = bodyHtml == null ? "" : bodyHtml;
            this.links = Collections.unmodifiableList(new ArrayList<>(links));
        }

        public static Result empty(String bodyHtml) {
            return new Result(bodyHtml, Collections.emptyList());
        }

        public String getBodyHtml() {
            return bodyHtml;
        }

        public List<ReferenceLink> getLinks() {
            return links;
        }

        public boolean hasLinks() {
            return !links.isEmpty();
        }
    }

    public static class ReferenceLink {
        private final String number;
        private final String url;
        private final String label;

        private ReferenceLink(String number, String url, String label) {
            this.number = number;
            this.url = url;
            this.label = label;
        }

        public String getNumber() {
            return number;
        }

        public boolean hasNumber() {
            return number != null && !number.isEmpty();
        }

        public String getUrl() {
            return url;
        }

        public String getLabel() {
            return label;
        }
    }
}
