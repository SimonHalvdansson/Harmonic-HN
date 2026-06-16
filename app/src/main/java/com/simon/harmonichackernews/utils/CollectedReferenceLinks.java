package com.simon.harmonichackernews.utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CollectedReferenceLinks {

    private static final Pattern REFERENCE_MARKER_PATTERN =
            Pattern.compile("\\G\\s*(?:\\[(\\d{1,3})\\]\\s*(?::|\\.|-|\\u2013|\\u2014)?|(\\d{1,3})\\s*:)\\s*");
    private static final Set<String> COMMON_BARE_DOMAIN_TLDS = new HashSet<>(Arrays.asList(
            "app", "biz", "blog", "cloud", "com", "dev", "edu", "fm", "gov", "info",
            "io", "mil", "net", "news", "org", "site", "tech", "tv", "wiki", "xyz"
    ));

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
        List<CollectedNode> collectedNodes = new ArrayList<>();

        collectStandaloneLinkNodes(nodes, collectedNodes, nodesToRemove);
        collectTrailingReferenceNodes(nodes, collectedNodes, nodesToRemove);

        if (collectedNodes.isEmpty()) {
            return Result.empty(inputHtml);
        }

        Collections.sort(collectedNodes, (first, second) -> Integer.compare(first.index, second.index));
        List<ContentBlock> contentBlocks = buildContentBlocks(nodes, collectedNodes, nodesToRemove);
        List<ReferenceLink> links = new ArrayList<>();
        for (CollectedNode collectedNode : collectedNodes) {
            links.addAll(collectedNode.links);
        }

        for (Node node : nodesToRemove) {
            node.remove();
        }

        return new Result(body.html().trim(), links, contentBlocks);
    }

    private static void collectStandaloneLinkNodes(
            List<Node> nodes,
            List<CollectedNode> collectedNodes,
            List<Node> nodesToRemove) {
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (isIgnorable(node)) {
                continue;
            }

            List<ReferenceLink> parsedLinks = parseUnnumberedLinkNode(node);
            if (parsedLinks.isEmpty()) {
                continue;
            }
            if (!hasStandaloneLineBoundaries(nodes, i, node)) {
                continue;
            }

            collectedNodes.add(new CollectedNode(i, node, parsedLinks));
            addNodeToRemove(nodesToRemove, node);
        }
    }

    private static void collectTrailingReferenceNodes(
            List<Node> nodes,
            List<CollectedNode> collectedNodes,
            List<Node> nodesToRemove) {
        List<Node> trailingIgnorableNodes = new ArrayList<>();

        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node node = nodes.get(i);
            if (hasCollectedNode(collectedNodes, node)) {
                addNodesToRemove(nodesToRemove, trailingIgnorableNodes);
                trailingIgnorableNodes.clear();
                continue;
            }
            if (isIgnorable(node)) {
                trailingIgnorableNodes.add(node);
                continue;
            }

            List<ReferenceLink> parsedLinks = parseReferenceNode(node);
            if (parsedLinks.isEmpty()) {
                break;
            }

            collectedNodes.add(new CollectedNode(i, node, parsedLinks));
            addNodeToRemove(nodesToRemove, node);
            addNodesToRemove(nodesToRemove, trailingIgnorableNodes);
            trailingIgnorableNodes.clear();
        }
    }

    private static void addNodesToRemove(List<Node> nodesToRemove, List<Node> nodes) {
        for (Node node : nodes) {
            addNodeToRemove(nodesToRemove, node);
        }
    }

    private static void addNodeToRemove(List<Node> nodesToRemove, Node node) {
        if (!containsNode(nodesToRemove, node)) {
            nodesToRemove.add(node);
        }
    }

    private static boolean hasCollectedNode(List<CollectedNode> collectedNodes, Node node) {
        for (CollectedNode collectedNode : collectedNodes) {
            if (collectedNode.node == node) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsNode(List<Node> nodes, Node node) {
        for (Node existingNode : nodes) {
            if (existingNode == node) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasStandaloneLineBoundaries(List<Node> nodes, int index, Node node) {
        if (node instanceof Element) {
            Element element = (Element) node;
            if (isBlockLineBoundaryElement(element)) {
                return true;
            }
        }
        return hasLineBoundaryBefore(nodes, index) && hasLineBoundaryAfter(nodes, index);
    }

    private static boolean hasLineBoundaryBefore(List<Node> nodes, int index) {
        for (int i = index - 1; i >= 0; i--) {
            Node node = nodes.get(i);
            if (isBlankTextNode(node)) {
                continue;
            }
            return isLineBreakElement(node)
                    || isBlockLineBoundaryNode(node)
                    || isTextBoundaryBefore(node);
        }
        return true;
    }

    private static boolean hasLineBoundaryAfter(List<Node> nodes, int index) {
        for (int i = index + 1; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (isBlankTextNode(node)) {
                continue;
            }
            return isLineBreakElement(node)
                    || isBlockLineBoundaryNode(node)
                    || isTextBoundaryAfter(node);
        }
        return true;
    }

    private static boolean isBlankTextNode(Node node) {
        return node instanceof TextNode && ((TextNode) node).isBlank();
    }

    private static boolean isLineBreakElement(Node node) {
        return node instanceof Element && "br".equalsIgnoreCase(((Element) node).tagName());
    }

    private static boolean isBlockLineBoundaryNode(Node node) {
        return node instanceof Element && isBlockLineBoundaryElement((Element) node);
    }

    private static boolean isBlockLineBoundaryElement(Element element) {
        String tagName = element.tagName().toLowerCase(Locale.US);
        return "p".equals(tagName)
                || "div".equals(tagName)
                || "li".equals(tagName);
    }

    private static boolean isTextBoundaryBefore(Node node) {
        return node instanceof TextNode && endsWithLineBreak(((TextNode) node).getWholeText());
    }

    private static boolean isTextBoundaryAfter(Node node) {
        return node instanceof TextNode && startsWithLineBreak(((TextNode) node).getWholeText());
    }

    private static boolean startsWithLineBreak(String text) {
        return text != null && !text.isEmpty() && (text.charAt(0) == '\n' || text.charAt(0) == '\r');
    }

    private static boolean endsWithLineBreak(String text) {
        return text != null
                && !text.isEmpty()
                && (text.charAt(text.length() - 1) == '\n' || text.charAt(text.length() - 1) == '\r');
    }

    private static List<ContentBlock> buildContentBlocks(
            List<Node> nodes,
            List<CollectedNode> collectedNodes,
            List<Node> nodesToRemove) {
        List<ContentBlock> blocks = new ArrayList<>();
        StringBuilder html = new StringBuilder();

        for (Node node : nodes) {
            CollectedNode collectedNode = getCollectedNode(collectedNodes, node);
            if (collectedNode != null) {
                addTextBlock(blocks, html);
                for (ReferenceLink link : collectedNode.links) {
                    blocks.add(ContentBlock.link(link));
                }
                continue;
            }

            if (containsNode(nodesToRemove, node)) {
                continue;
            }

            html.append(node.outerHtml());
        }

        addTextBlock(blocks, html);
        return blocks;
    }

    private static CollectedNode getCollectedNode(List<CollectedNode> collectedNodes, Node node) {
        for (CollectedNode collectedNode : collectedNodes) {
            if (collectedNode.node == node) {
                return collectedNode;
            }
        }
        return null;
    }

    private static void addTextBlock(List<ContentBlock> blocks, StringBuilder html) {
        String value = html.toString().trim();
        if (!value.isEmpty()) {
            blocks.add(ContentBlock.text(value));
        }
        html.setLength(0);
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
            links.add(new ReferenceLink(getReferenceNumber(marker), getReferenceMarkerLabel(marker), url, label));
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

            links.add(new ReferenceLink(getReferenceNumber(marker), getReferenceMarkerLabel(marker), url, label));
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

    private static boolean startsWithReferenceMarker(String text) {
        if (text == null) {
            return false;
        }
        return REFERENCE_MARKER_PATTERN.matcher(text).lookingAt();
    }

    private static String getReferenceNumber(Matcher marker) {
        String bracketedNumber = marker.group(1);
        return bracketedNumber != null ? bracketedNumber : marker.group(2);
    }

    private static String getReferenceMarkerLabel(Matcher marker) {
        String bracketedNumber = marker.group(1);
        if (bracketedNumber != null) {
            return "[" + bracketedNumber + "]";
        }
        return marker.group(2) + ":";
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
        if (!lower.matches("[a-z0-9][a-z0-9.-]*\\.[a-z]{2,}(:\\d+)?(/\\S*)?")) {
            return false;
        }

        String host = lower;
        int slash = host.indexOf('/');
        if (slash >= 0) {
            host = host.substring(0, slash);
        }
        int colon = host.indexOf(':');
        if (colon >= 0) {
            host = host.substring(0, colon);
        }
        String[] labels = host.split("\\.");
        if (labels.length < 2) {
            return false;
        }

        // Bare domains are a convenience feature. Keep this conservative so dotted identifiers
        // such as browser.ml.enable do not get promoted into collected reference links.
        String tld = labels[labels.length - 1];
        return tld.length() == 2 || COMMON_BARE_DOMAIN_TLDS.contains(tld);
    }

    private static class CollectedNode {
        private final int index;
        private final Node node;
        private final List<ReferenceLink> links;

        private CollectedNode(int index, Node node, List<ReferenceLink> links) {
            this.index = index;
            this.node = node;
            this.links = links;
        }
    }

    public static class Result {
        private final String bodyHtml;
        private final List<ReferenceLink> links;
        private final List<ContentBlock> contentBlocks;

        private Result(String bodyHtml, List<ReferenceLink> links, List<ContentBlock> contentBlocks) {
            this.bodyHtml = bodyHtml == null ? "" : bodyHtml;
            this.links = Collections.unmodifiableList(new ArrayList<>(links));
            this.contentBlocks = Collections.unmodifiableList(new ArrayList<>(contentBlocks));
        }

        public static Result empty(String bodyHtml) {
            return new Result(bodyHtml, Collections.emptyList(), Collections.emptyList());
        }

        public String getBodyHtml() {
            return bodyHtml;
        }

        public List<ReferenceLink> getLinks() {
            return links;
        }

        public List<ContentBlock> getContentBlocks() {
            return contentBlocks;
        }

        public boolean hasLinks() {
            return !links.isEmpty();
        }

        public boolean hasInterleavedLinks() {
            boolean hasSeenLink = false;
            for (ContentBlock block : contentBlocks) {
                if (block.isLink()) {
                    hasSeenLink = true;
                } else if (hasSeenLink) {
                    return true;
                }
            }
            return false;
        }
    }

    public static class ContentBlock {
        private final String bodyHtml;
        private final ReferenceLink link;

        private ContentBlock(String bodyHtml, ReferenceLink link) {
            this.bodyHtml = bodyHtml;
            this.link = link;
        }

        private static ContentBlock text(String bodyHtml) {
            return new ContentBlock(bodyHtml, null);
        }

        private static ContentBlock link(ReferenceLink link) {
            return new ContentBlock(null, link);
        }

        public boolean isLink() {
            return link != null;
        }

        public String getBodyHtml() {
            return bodyHtml;
        }

        public ReferenceLink getLink() {
            return link;
        }
    }

    public static class ReferenceLink {
        private final String number;
        private final String markerLabel;
        private final String url;
        private final String label;
        private String resolvedTitle;

        private ReferenceLink(String number, String url, String label) {
            this(number, number == null ? null : "[" + number + "]", url, label);
        }

        private ReferenceLink(String number, String markerLabel, String url, String label) {
            this.number = number;
            this.markerLabel = markerLabel;
            this.url = url;
            this.label = label;
        }

        public String getNumber() {
            return number;
        }

        public boolean hasNumber() {
            return number != null && !number.isEmpty();
        }

        public String getMarkerLabel() {
            return markerLabel;
        }

        public String getUrl() {
            return url;
        }

        public String getLabel() {
            return label;
        }

        public String getResolvedTitle() {
            return resolvedTitle;
        }

        public void setResolvedTitle(String resolvedTitle) {
            this.resolvedTitle = resolvedTitle;
        }
    }
}
