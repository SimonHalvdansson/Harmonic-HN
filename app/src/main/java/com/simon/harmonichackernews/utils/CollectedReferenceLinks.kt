package com.simon.harmonichackernews.utils

import java.util.Arrays
import java.util.Collections
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import com.fleeksoft.ksoup.parser.Parser
import com.fleeksoft.ksoup.select.Elements

object CollectedReferenceLinks {
    private val REFERENCE_MARKER_PATTERN: Pattern =
        Pattern.compile("\\G\\s*(?:\\[(\\d{1,3})\\]\\s*(?::|\\.|-|\\u2013|\\u2014)?|(\\d{1,3})\\s*:)\\s*")
    private val COMMON_BARE_DOMAIN_TLDS: MutableSet<String?> = HashSet<String?>(
        mutableListOf<String?>(
            "app", "biz", "blog", "cloud", "com", "dev", "edu", "fm", "gov", "info",
            "io", "mil", "net", "news", "org", "site", "tech", "tv", "wiki", "xyz"
        )
    )

    fun parse(inputHtml: String?): Result {
        if (inputHtml == null || inputHtml.isEmpty()) {
            return Result.Companion.empty(inputHtml)
        }

        val document = Ksoup.parse(inputHtml, Parser.htmlParser(), "")
        document.outputSettings().prettyPrint(false)

        val body = document.body()
        val nodes: MutableList<Node> = ArrayList<Node>(body.childNodes())
        if (nodes.isEmpty()) {
            return Result.Companion.empty(inputHtml)
        }

        val nodesToRemove: MutableList<Node> = ArrayList<Node>()
        val collectedNodes: MutableList<CollectedNode> = ArrayList<CollectedNode>()

        collectStandaloneLinkNodes(nodes, collectedNodes, nodesToRemove)
        collectTrailingReferenceNodes(nodes, collectedNodes, nodesToRemove)

        if (collectedNodes.isEmpty()) {
            return Result.Companion.empty(inputHtml)
        }

        collectedNodes.sortBy { it.index }
        val contentBlocks = buildContentBlocks(nodes, collectedNodes, nodesToRemove)
        val links: MutableList<ReferenceLink> = ArrayList<ReferenceLink>()
        for (collectedNode in collectedNodes) {
            links.addAll(collectedNode.links)
        }

        for (node in nodesToRemove) {
            node.remove()
        }

        return CollectedReferenceLinks.Result(body.html().trim { it <= ' ' }, links, contentBlocks)
    }

    private fun collectStandaloneLinkNodes(
        nodes: MutableList<Node>,
        collectedNodes: MutableList<CollectedNode>,
        nodesToRemove: MutableList<Node>
    ) {
        for (i in nodes.indices) {
            val node: Node? = nodes.get(i)
            if (isIgnorable(node)) {
                continue
            }

            val parsedLinks = parseUnnumberedLinkNode(node)
            if (parsedLinks.isEmpty()) {
                continue
            }
            if (!hasStandaloneLineBoundaries(nodes, i, node)) {
                continue
            }

            collectedNodes.add(CollectedNode(i, node, parsedLinks))
            addNodeToRemove(nodesToRemove, node)
        }
    }

    private fun collectTrailingReferenceNodes(
        nodes: MutableList<Node>,
        collectedNodes: MutableList<CollectedNode>,
        nodesToRemove: MutableList<Node>
    ) {
        val trailingIgnorableNodes: MutableList<Node?> = ArrayList<Node?>()

        for (i in nodes.indices.reversed()) {
            val node: Node? = nodes.get(i)
            if (hasCollectedNode(collectedNodes, node)) {
                addNodesToRemove(nodesToRemove, trailingIgnorableNodes)
                trailingIgnorableNodes.clear()
                continue
            }
            if (isIgnorable(node)) {
                trailingIgnorableNodes.add(node)
                continue
            }

            val parsedLinks = parseReferenceNode(node)
            if (parsedLinks.isEmpty()) {
                break
            }

            collectedNodes.add(CollectedNode(i, node, parsedLinks))
            addNodeToRemove(nodesToRemove, node)
            addNodesToRemove(nodesToRemove, trailingIgnorableNodes)
            trailingIgnorableNodes.clear()
        }
    }

    private fun addNodesToRemove(nodesToRemove: MutableList<Node>, nodes: MutableList<Node?>) {
        for (node in nodes) {
            addNodeToRemove(nodesToRemove, node)
        }
    }

    private fun addNodeToRemove(nodesToRemove: MutableList<Node>, node: Node?) {
        if (!containsNode(nodesToRemove, node)) {
            nodesToRemove.add(node!!)
        }
    }

    private fun hasCollectedNode(collectedNodes: MutableList<CollectedNode>, node: Node?): Boolean {
        for (collectedNode in collectedNodes) {
            if (collectedNode.node === node) {
                return true
            }
        }
        return false
    }

    private fun containsNode(nodes: MutableList<Node>, node: Node?): Boolean {
        for (existingNode in nodes) {
            if (existingNode === node) {
                return true
            }
        }
        return false
    }

    private fun hasStandaloneLineBoundaries(
        nodes: MutableList<Node>,
        index: Int,
        node: Node?
    ): Boolean {
        if (node is Element) {
            val element = node
            if (isBlockLineBoundaryElement(element)) {
                return true
            }
        }
        return hasLineBoundaryBefore(nodes, index) && hasLineBoundaryAfter(nodes, index)
    }

    private fun hasLineBoundaryBefore(nodes: MutableList<Node>, index: Int): Boolean {
        for (i in index - 1 downTo 0) {
            val node: Node? = nodes.get(i)
            if (isBlankTextNode(node)) {
                continue
            }
            return isLineBreakElement(node)
                    || isBlockLineBoundaryNode(node)
                    || isTextBoundaryBefore(node)
        }
        return true
    }

    private fun hasLineBoundaryAfter(nodes: MutableList<Node>, index: Int): Boolean {
        for (i in index + 1..<nodes.size) {
            val node: Node? = nodes.get(i)
            if (isBlankTextNode(node)) {
                continue
            }
            return isLineBreakElement(node)
                    || isBlockLineBoundaryNode(node)
                    || isTextBoundaryAfter(node)
        }
        return true
    }

    private fun isBlankTextNode(node: Node?): Boolean {
        return node is TextNode && node.isBlank()
    }

    private fun isLineBreakElement(node: Node?): Boolean {
        return node is Element && "br".equals(node.tagName(), ignoreCase = true)
    }

    private fun isBlockLineBoundaryNode(node: Node?): Boolean {
        return node is Element && isBlockLineBoundaryElement(node)
    }

    private fun isBlockLineBoundaryElement(element: Element): Boolean {
        val tagName = element.tagName().lowercase()
        return "p" == tagName
                || "div" == tagName
                || "li" == tagName
    }

    private fun isTextBoundaryBefore(node: Node?): Boolean {
        return node is TextNode && endsWithLineBreak(node.getWholeText())
    }

    private fun isTextBoundaryAfter(node: Node?): Boolean {
        return node is TextNode && startsWithLineBreak(node.getWholeText())
    }

    private fun startsWithLineBreak(text: String?): Boolean {
        return text != null && !text.isEmpty() && (text.get(0) == '\n' || text.get(0) == '\r')
    }

    private fun endsWithLineBreak(text: String?): Boolean {
        return text != null && !text.isEmpty() && (text.get(text.length - 1) == '\n' || text.get(
            text.length - 1
        ) == '\r')
    }

    private fun buildContentBlocks(
        nodes: MutableList<Node>,
        collectedNodes: MutableList<CollectedNode>,
        nodesToRemove: MutableList<Node>
    ): MutableList<ContentBlock> {
        val blocks: MutableList<ContentBlock> = ArrayList<ContentBlock>()
        val html = StringBuilder()

        for (node in nodes) {
            val collectedNode = getCollectedNode(collectedNodes, node)
            if (collectedNode != null) {
                addTextBlock(blocks, html)
                for (link in collectedNode.links) {
                    blocks.add(ContentBlock.Companion.link(link))
                }
                continue
            }

            if (containsNode(nodesToRemove, node)) {
                continue
            }

            html.append(node.outerHtml())
        }

        addTextBlock(blocks, html)
        return blocks
    }

    private fun getCollectedNode(
        collectedNodes: MutableList<CollectedNode>,
        node: Node?
    ): CollectedNode? {
        for (collectedNode in collectedNodes) {
            if (collectedNode.node === node) {
                return collectedNode
            }
        }
        return null
    }

    private fun addTextBlock(blocks: MutableList<ContentBlock>, html: StringBuilder) {
        val value = html.toString().trim { it <= ' ' }
        if (!value.isEmpty()) {
            blocks.add(ContentBlock.Companion.text(value))
        }
        html.setLength(0)
    }

    private fun isIgnorable(node: Node?): Boolean {
        if (node is TextNode) {
            return node.isBlank()
        }
        if (node is Element) {
            val element = node
            return ("br".equals(element.tagName(), ignoreCase = true) || isReferenceContainerTag(
                element
            ))
                    && element.text().trim { it <= ' ' }.isEmpty()
        }
        return false
    }

    private fun parseReferenceNode(node: Node?): MutableList<ReferenceLink> {
        if (node is Element) {
            val element = node
            if (!isReferenceContainerTag(element)) {
                return mutableListOf<ReferenceLink>()
            }
            return parseReferenceFragment(element.html())
        }
        if (node is TextNode) {
            return parseBareReferenceText(node.getWholeText())
        }
        return mutableListOf<ReferenceLink>()
    }

    private fun parseUnnumberedLinkNode(node: Node?): MutableList<ReferenceLink> {
        if (node is Element) {
            val element = node
            if (isAnchorTag(element)) {
                val link = parseUnnumberedAnchor(element)
                if (link == null) {
                    return mutableListOf<ReferenceLink>()
                }
                return mutableListOf<ReferenceLink>(link)
            }
            if (!isReferenceContainerTag(element)) {
                return mutableListOf<ReferenceLink>()
            }
            return parseUnnumberedLinkFragment(element.html())
        }
        if (node is TextNode) {
            return parseUnnumberedLinkText(node.getWholeText())
        }
        return mutableListOf<ReferenceLink>()
    }

    private fun isReferenceContainerTag(element: Element): Boolean {
        val tagName = element.tagName().lowercase()
        return "p" == tagName
                || "div" == tagName
                || "span" == tagName
                || "li" == tagName
    }

    private fun isAnchorTag(element: Element): Boolean {
        return "a".equals(element.tagName(), ignoreCase = true) && element.hasAttr("href")
    }

    private fun parseReferenceFragment(html: String?): MutableList<ReferenceLink> {
        val fragment = Ksoup.parseBodyFragment(if (html == null) "" else html, "")
        fragment.outputSettings().prettyPrint(false)
        val text = normalizeReferenceWhitespace(fragment.body().text())
        if (!startsWithReferenceMarker(text)) {
            return mutableListOf<ReferenceLink>()
        }

        val anchors = fragment.select("a[href]")
        if (!anchors.isEmpty()) {
            return parseAnchoredReferenceText(text, anchors)
        }
        return parseBareReferenceText(text)
    }

    private fun parseUnnumberedLinkFragment(html: String?): MutableList<ReferenceLink> {
        val fragment = Ksoup.parseBodyFragment(if (html == null) "" else html, "")
        fragment.outputSettings().prettyPrint(false)
        val text = normalizeReferenceWhitespace(fragment.body().text())
        if (text.isEmpty() || startsWithReferenceMarker(text)) {
            return mutableListOf<ReferenceLink>()
        }

        val anchors = fragment.select("a[href]")
        if (!anchors.isEmpty()) {
            return parseUnnumberedAnchoredLinkText(text, anchors)
        }
        return parseUnnumberedLinkText(text)
    }

    private fun parseAnchoredReferenceText(
        text: String,
        anchors: Elements
    ): MutableList<ReferenceLink> {
        val links: MutableList<ReferenceLink> = ArrayList<ReferenceLink>()
        var position = 0

        for (anchor in anchors) {
            val marker = REFERENCE_MARKER_PATTERN.matcher(text)
            marker.region(position, text.length)
            if (!marker.lookingAt()) {
                return mutableListOf<ReferenceLink>()
            }

            position = marker.end()
            val displayLabel = normalizeReferenceWhitespace(anchor.text())
            if (displayLabel.isEmpty() || !startsWithAt(text, displayLabel, position)) {
                return mutableListOf<ReferenceLink>()
            }

            position += displayLabel.length
            position = skipInterReferenceSeparators(text, position)

            val url = normalizeUrl(anchor.attr("href"))
            if (!isUsableUrl(url)) {
                return mutableListOf<ReferenceLink>()
            }
            val label = getAnchorLabel(displayLabel, url)
            links.add(
                ReferenceLink(
                    getReferenceNumber(marker),
                    getReferenceMarkerLabel(marker),
                    url,
                    label
                )
            )
        }

        return if (position == text.length) links else mutableListOf<ReferenceLink>()
    }

    private fun parseUnnumberedAnchoredLinkText(
        text: String,
        anchors: Elements
    ): MutableList<ReferenceLink> {
        val links: MutableList<ReferenceLink> = ArrayList<ReferenceLink>()
        var position = 0

        for (anchor in anchors) {
            position = skipInterReferenceSeparators(text, position)

            val url = normalizeUrl(anchor.attr("href"))
            if (!isUsableUrl(url)) {
                return mutableListOf<ReferenceLink>()
            }

            val displayLabel = normalizeReferenceWhitespace(anchor.text())
            if (displayLabel.isEmpty()) {
                return mutableListOf<ReferenceLink>()
            }
            if (!startsWithAt(text, displayLabel, position)) {
                return mutableListOf<ReferenceLink>()
            }

            position += displayLabel.length
            val label = getAnchorLabel(displayLabel, url)
            links.add(ReferenceLink(null, url, label))
        }

        position = skipInterReferenceSeparators(text, position)
        return if (position == text.length) links else mutableListOf<ReferenceLink>()
    }

    private fun parseBareReferenceText(text: String?): MutableList<ReferenceLink> {
        val normalizedText = normalizeReferenceWhitespace(text)
        if (!startsWithReferenceMarker(normalizedText)) {
            return mutableListOf<ReferenceLink>()
        }

        val links: MutableList<ReferenceLink> = ArrayList<ReferenceLink>()
        var position = 0

        while (position < normalizedText.length) {
            val marker = REFERENCE_MARKER_PATTERN.matcher(normalizedText)
            marker.region(position, normalizedText.length)
            if (!marker.lookingAt()) {
                return mutableListOf<ReferenceLink>()
            }
            position = marker.end()

            val urlStart = position
            while (position < normalizedText.length && !isBareUrlTerminator(
                    normalizedText.get(
                        position
                    )
                )
            ) {
                position++
            }

            val label = trimTrailingUrlPunctuation(normalizedText.substring(urlStart, position))
            val url = normalizeBareUrl(label)
            if (!isUsableUrl(url)) {
                return mutableListOf<ReferenceLink>()
            }

            links.add(
                ReferenceLink(
                    getReferenceNumber(marker),
                    getReferenceMarkerLabel(marker),
                    url,
                    label
                )
            )
            position = skipInterReferenceSeparators(normalizedText, position)
        }

        return links
    }

    private fun parseUnnumberedLinkText(text: String?): MutableList<ReferenceLink> {
        val normalizedText = normalizeReferenceWhitespace(text)
        if (normalizedText.isEmpty() || startsWithReferenceMarker(normalizedText)) {
            return mutableListOf<ReferenceLink>()
        }

        val links: MutableList<ReferenceLink> = ArrayList<ReferenceLink>()
        var position = 0

        while (position < normalizedText.length) {
            position = skipInterReferenceSeparators(normalizedText, position)
            if (position >= normalizedText.length) {
                break
            }

            val urlStart = position
            while (position < normalizedText.length && !isBareUrlTerminator(
                    normalizedText.get(
                        position
                    )
                )
            ) {
                position++
            }

            val label = trimTrailingUrlPunctuation(normalizedText.substring(urlStart, position))
            val url = normalizeBareUrl(label)
            if (!isUsableUrl(url)) {
                return mutableListOf<ReferenceLink>()
            }

            links.add(ReferenceLink(null, url, label))
        }

        return links
    }

    private fun parseUnnumberedAnchor(anchor: Element): ReferenceLink? {
        val url = normalizeUrl(anchor.attr("href"))
        if (!isUsableUrl(url)) {
            return null
        }

        val displayLabel = normalizeReferenceWhitespace(anchor.text())
        if (displayLabel.isEmpty()) {
            return null
        }

        return ReferenceLink(null, url, getAnchorLabel(displayLabel, url))
    }

    private fun getAnchorLabel(displayLabel: String, url: String): String {
        if (displayLabel.endsWith("...")) {
            val prefix = displayLabel.substring(0, displayLabel.length - 3)
            if (url.startsWith(prefix)) {
                return url
            }
        }
        return displayLabel
    }

    private fun startsWithReferenceMarker(text: String?): Boolean {
        if (text == null) {
            return false
        }
        return REFERENCE_MARKER_PATTERN.matcher(text).lookingAt()
    }

    private fun getReferenceNumber(marker: Matcher): String? {
        val bracketedNumber = marker.group(1)
        return if (bracketedNumber != null) bracketedNumber else marker.group(2)
    }

    private fun getReferenceMarkerLabel(marker: Matcher): String {
        val bracketedNumber = marker.group(1)
        if (bracketedNumber != null) {
            return "[" + bracketedNumber + "]"
        }
        return marker.group(2) + ":"
    }

    private fun startsWithAt(text: String, value: String, position: Int): Boolean {
        return position >= 0 && position + value.length <= text.length && text.regionMatches(
            position,
            value,
            0,
            value.length
        )
    }

    private fun skipInterReferenceSeparators(text: String, position: Int): Int {
        var position = position
        while (position < text.length) {
            val current = text.get(position)
            if (Character.isWhitespace(current) || current == ',' || current == ';' || current == '|') {
                position++
            } else {
                break
            }
        }
        return position
    }

    private fun isBareUrlTerminator(value: Char): Boolean {
        return Character.isWhitespace(value) || value == ',' || value == ';' || value == '|'
    }

    private fun normalizeReferenceWhitespace(value: String?): String {
        if (value == null) {
            return ""
        }
        return value.replace('\u00a0', ' ').trim { it <= ' ' }.replace("\\s+".toRegex(), " ")
    }

    private fun normalizeUrl(value: String?): String {
        val url = trimTrailingUrlPunctuation(
            Ksoup.parse(if (value == null) "" else value).text().trim { it <= ' ' }
                .replace("&#x2F;", "/")
                .replace("&#47;", "/"))
        if (url.startsWith("//")) {
            return "https:" + url
        }
        if (url.startsWith("/")) {
            return "https://news.ycombinator.com" + url
        }
        if (!url.contains("://") && looksLikeDomain(url)) {
            return "https://" + url
        }
        return url
    }

    private fun normalizeBareUrl(value: String?): String {
        val url = trimTrailingUrlPunctuation(
            Ksoup.parse(if (value == null) "" else value).text().trim { it <= ' ' }
                .replace("&#x2F;", "/")
                .replace("&#47;", "/"))
        if (url.startsWith("/") && !url.startsWith("//")) {
            return url
        }
        return normalizeUrl(url)
    }

    private fun trimTrailingUrlPunctuation(value: String?): String {
        var trimmed = if (value == null) "" else value.trim { it <= ' ' }
        while (trimmed.length > 0) {
            val last = trimmed.get(trimmed.length - 1)
            if (last == '.' || last == ',' || last == ';' || last == ':' || last == '!' || last == '?') {
                trimmed = trimmed.substring(0, trimmed.length - 1)
            } else {
                break
            }
        }
        return trimmed
    }

    private fun isUsableUrl(url: String?): Boolean {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"))
                && url.length > "https://".length
    }

    private fun looksLikeDomain(value: String?): Boolean {
        if (value == null || value.contains("/") && value.startsWith("/")) {
            return false
        }
        val lower = value.lowercase()
        if (!lower.matches("[a-z0-9][a-z0-9.-]*\\.[a-z]{2,}(:\\d+)?(/\\S*)?".toRegex())) {
            return false
        }

        var host = lower
        val slash = host.indexOf('/')
        if (slash >= 0) {
            host = host.substring(0, slash)
        }
        val colon = host.indexOf(':')
        if (colon >= 0) {
            host = host.substring(0, colon)
        }
        val labels = host.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (labels.size < 2) {
            return false
        }

        // Bare domains are a convenience feature. Keep this conservative so dotted identifiers
        // such as browser.ml.enable do not get promoted into collected reference links.
        val tld = labels[labels.size - 1]
        return tld.length == 2 || COMMON_BARE_DOMAIN_TLDS.contains(tld)
    }

    private class CollectedNode(
        val index: Int,
        val node: Node?,
        val links: MutableList<ReferenceLink>
    )

    class Result(
        bodyHtml: String?,
        links: MutableList<ReferenceLink>,
        contentBlocks: MutableList<ContentBlock>
    ) {
        val bodyHtml: String
        val links: MutableList<ReferenceLink>
        val contentBlocks: MutableList<ContentBlock>

        init {
            this.bodyHtml = if (bodyHtml == null) "" else bodyHtml
            this.links =
                Collections.unmodifiableList<ReferenceLink>(ArrayList<ReferenceLink>(links))
            this.contentBlocks =
                Collections.unmodifiableList<ContentBlock>(ArrayList<ContentBlock>(contentBlocks))
        }

        fun hasLinks(): Boolean {
            return !links.isEmpty()
        }

        fun hasInterleavedLinks(): Boolean {
            var hasSeenLink = false
            for (block in contentBlocks) {
                if (block.isLink()) {
                    hasSeenLink = true
                } else if (hasSeenLink) {
                    return true
                }
            }
            return false
        }

        companion object {
            fun empty(bodyHtml: String?): Result {
                return Result(
                    bodyHtml,
                    mutableListOf<ReferenceLink>(),
                    mutableListOf<ContentBlock>()
                )
            }
        }
    }

    class ContentBlock private constructor(
        val bodyHtml: String?,
        private val link: ReferenceLink?
    ) {
        fun isLink(): Boolean {
            return link != null
        }

        fun getLink(): ReferenceLink? {
            return link
        }

        companion object {
            fun text(bodyHtml: String?): ContentBlock {
                return ContentBlock(bodyHtml, null)
            }

            fun link(link: ReferenceLink?): ContentBlock {
                return ContentBlock(null, link)
            }
        }
    }

    class ReferenceLink(
        val number: String?,
        val markerLabel: String?,
        val url: String?,
        val label: String?
    ) {
        var resolvedTitle: String? = null

        constructor(number: String?, url: String?, label: String?) : this(
            number,
            if (number == null) null else "[" + number + "]",
            url,
            label
        )

        fun hasNumber(): Boolean {
            return number != null && !number.isEmpty()
        }
    }
}
