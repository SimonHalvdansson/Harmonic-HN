package com.simon.harmonichackernews.utils

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode

/** Extracts standalone and trailing reference links without any platform URL or HTML APIs. */
object CollectedReferenceLinks {
    private val referenceMarkerPattern = Regex(
        "\\s*(?:\\[(\\d{1,3})]\\s*(?::|\\.|-|\\u2013|\\u2014)?|(\\d{1,3})\\s*:)\\s*",
    )
    private val commonBareDomainTlds = setOf(
        "app", "biz", "blog", "cloud", "com", "dev", "edu", "fm", "gov", "info",
        "io", "mil", "net", "news", "online", "org", "site", "store", "tech", "tv",
        "wiki", "xyz",
    )
    private val possibleBareDomainPattern = Regex(
        "[a-z0-9][a-z0-9.-]*\\.[a-z]{2,}(?::\\d+)?(?:/\\S*)?",
        RegexOption.IGNORE_CASE,
    )
    private val whitespacePattern = Regex("\\s+")

    fun parse(inputHtml: String?): Result {
        if (inputHtml.isNullOrEmpty()) return Result.empty(inputHtml)
        // Most HN comments have no links. Avoid building a DOM for those rows while still sending
        // anchors, explicit URLs, and possible bare domains through the legacy-compatible parser.
        if (!mayContainLink(inputHtml)) return Result.empty(inputHtml)

        val document = Ksoup.parse(inputHtml)
        document.outputSettings().prettyPrint(false)
        val body = document.body()
        val nodes = body.childNodes().toList()
        if (nodes.isEmpty()) return Result.empty(inputHtml)

        val nodesToRemove = mutableListOf<Node>()
        val collectedNodes = mutableListOf<CollectedNode>()
        collectStandaloneLinkNodes(nodes, collectedNodes, nodesToRemove)
        collectTrailingReferenceNodes(nodes, collectedNodes, nodesToRemove)
        if (collectedNodes.isEmpty()) return Result.empty(inputHtml)

        collectedNodes.sortBy(CollectedNode::index)
        val contentBlocks = buildContentBlocks(nodes, collectedNodes, nodesToRemove)
        val links = collectedNodes.flatMap(CollectedNode::links)
        nodesToRemove.forEach(Node::remove)
        return Result(body.html().trim(), links, contentBlocks)
    }

    private fun mayContainLink(inputHtml: String): Boolean =
        inputHtml.indexOf("<a", ignoreCase = true) >= 0 ||
            inputHtml.indexOf("http://", ignoreCase = true) >= 0 ||
            inputHtml.indexOf("https://", ignoreCase = true) >= 0 ||
            possibleBareDomainPattern.containsMatchIn(inputHtml)

    private fun collectStandaloneLinkNodes(
        nodes: List<Node>,
        collectedNodes: MutableList<CollectedNode>,
        nodesToRemove: MutableList<Node>,
    ) {
        nodes.forEachIndexed { index, node ->
            if (isIgnorable(node)) return@forEachIndexed
            val parsedLinks = parseUnnumberedLinkNode(node)
            if (parsedLinks.isEmpty() || !hasStandaloneLineBoundaries(nodes, index, node)) {
                return@forEachIndexed
            }
            collectedNodes += CollectedNode(index, node, parsedLinks)
            addNodeToRemove(nodesToRemove, node)
        }
    }

    private fun collectTrailingReferenceNodes(
        nodes: List<Node>,
        collectedNodes: MutableList<CollectedNode>,
        nodesToRemove: MutableList<Node>,
    ) {
        val trailingIgnorableNodes = mutableListOf<Node>()
        for (index in nodes.indices.reversed()) {
            val node = nodes[index]
            if (collectedNodes.any { it.node === node }) {
                addNodesToRemove(nodesToRemove, trailingIgnorableNodes)
                trailingIgnorableNodes.clear()
                continue
            }
            if (isIgnorable(node)) {
                trailingIgnorableNodes += node
                continue
            }

            val parsedLinks = parseReferenceNode(node)
            if (parsedLinks.isEmpty()) break
            collectedNodes += CollectedNode(index, node, parsedLinks)
            addNodeToRemove(nodesToRemove, node)
            addNodesToRemove(nodesToRemove, trailingIgnorableNodes)
            trailingIgnorableNodes.clear()
        }
    }

    private fun addNodesToRemove(target: MutableList<Node>, nodes: List<Node>) {
        nodes.forEach { addNodeToRemove(target, it) }
    }

    private fun addNodeToRemove(target: MutableList<Node>, node: Node) {
        if (target.none { it === node }) target += node
    }

    private fun hasStandaloneLineBoundaries(nodes: List<Node>, index: Int, node: Node): Boolean {
        if (node is Element && isBlockLineBoundaryElement(node)) return true
        return hasLineBoundaryBefore(nodes, index) && hasLineBoundaryAfter(nodes, index)
    }

    private fun hasLineBoundaryBefore(nodes: List<Node>, index: Int): Boolean {
        for (candidateIndex in index - 1 downTo 0) {
            val node = nodes[candidateIndex]
            if (isBlankTextNode(node)) continue
            return isLineBreakElement(node) ||
                isBlockLineBoundaryNode(node) ||
                (node is TextNode && endsWithLineBreak(node.getWholeText()))
        }
        return true
    }

    private fun hasLineBoundaryAfter(nodes: List<Node>, index: Int): Boolean {
        for (candidateIndex in index + 1 until nodes.size) {
            val node = nodes[candidateIndex]
            if (isBlankTextNode(node)) continue
            return isLineBreakElement(node) ||
                isBlockLineBoundaryNode(node) ||
                (node is TextNode && startsWithLineBreak(node.getWholeText()))
        }
        return true
    }

    private fun isBlankTextNode(node: Node): Boolean =
        node is TextNode && node.getWholeText().isBlank()

    private fun isLineBreakElement(node: Node): Boolean =
        node is Element && node.tagName().equals("br", ignoreCase = true)

    private fun isBlockLineBoundaryNode(node: Node): Boolean =
        node is Element && isBlockLineBoundaryElement(node)

    private fun isBlockLineBoundaryElement(element: Element): Boolean =
        element.tagName().lowercase() in setOf("p", "div", "li")

    private fun startsWithLineBreak(text: String): Boolean =
        text.firstOrNull() == '\n' || text.firstOrNull() == '\r'

    private fun endsWithLineBreak(text: String): Boolean =
        text.lastOrNull() == '\n' || text.lastOrNull() == '\r'

    private fun buildContentBlocks(
        nodes: List<Node>,
        collectedNodes: List<CollectedNode>,
        nodesToRemove: List<Node>,
    ): List<ContentBlock> = buildList {
        val html = StringBuilder()
        nodes.forEach { node ->
            val collectedNode = collectedNodes.firstOrNull { it.node === node }
            if (collectedNode != null) {
                flushTextBlock(html, this)
                collectedNode.links.forEach { add(ContentBlock.link(it)) }
            } else if (nodesToRemove.none { it === node }) {
                html.append(node.outerHtml())
            }
        }
        flushTextBlock(html, this)
    }

    private fun isIgnorable(node: Node): Boolean = when (node) {
        is TextNode -> node.getWholeText().isBlank()
        is Element ->
            (node.tagName().equals("br", ignoreCase = true) || isReferenceContainerTag(node)) &&
                node.text().isBlank()
        else -> false
    }

    private fun parseReferenceNode(node: Node): List<ReferenceLink> = when (node) {
        is Element -> if (isReferenceContainerTag(node)) parseReferenceFragment(node.html()) else emptyList()
        is TextNode -> parseBareReferenceText(node.getWholeText())
        else -> emptyList()
    }

    private fun parseUnnumberedLinkNode(node: Node): List<ReferenceLink> = when (node) {
        is Element -> when {
            isAnchorTag(node) -> listOfNotNull(parseUnnumberedAnchor(node))
            isReferenceContainerTag(node) -> parseUnnumberedLinkFragment(node.html())
            else -> emptyList()
        }
        is TextNode -> parseUnnumberedLinkText(node.getWholeText())
        else -> emptyList()
    }

    private fun isReferenceContainerTag(element: Element): Boolean =
        element.tagName().lowercase() in setOf("p", "div", "span", "li")

    private fun isAnchorTag(element: Element): Boolean =
        element.tagName().equals("a", ignoreCase = true) && element.hasAttr("href")

    private fun parseReferenceFragment(html: String?): List<ReferenceLink> {
        val fragment = Ksoup.parseBodyFragment(html.orEmpty())
        fragment.outputSettings().prettyPrint(false)
        val text = normalizeReferenceWhitespace(fragment.body().text())
        if (!startsWithReferenceMarker(text)) return emptyList()
        val anchors = fragment.select("a[href]").toList()
        return if (anchors.isNotEmpty()) parseAnchoredReferenceText(text, anchors)
        else parseBareReferenceText(text)
    }

    private fun parseUnnumberedLinkFragment(html: String?): List<ReferenceLink> {
        val fragment = Ksoup.parseBodyFragment(html.orEmpty())
        fragment.outputSettings().prettyPrint(false)
        val text = normalizeReferenceWhitespace(fragment.body().text())
        if (text.isEmpty() || startsWithReferenceMarker(text)) return emptyList()
        val anchors = fragment.select("a[href]").toList()
        return if (anchors.isNotEmpty()) parseUnnumberedAnchoredLinkText(text, anchors)
        else parseUnnumberedLinkText(text)
    }

    private fun parseAnchoredReferenceText(
        text: String,
        anchors: List<Element>,
    ): List<ReferenceLink> {
        val links = mutableListOf<ReferenceLink>()
        var position = 0
        anchors.forEach { anchor ->
            val marker = referenceMarkerAt(text, position) ?: return emptyList()
            position = marker.range.last + 1
            val displayLabel = normalizeReferenceWhitespace(anchor.text())
            if (displayLabel.isEmpty() || !text.startsWith(displayLabel, position)) return emptyList()
            position += displayLabel.length
            position = skipInterReferenceSeparators(text, position)

            val url = normalizeUrl(anchor.attr("href"))
            if (!isUsableUrl(url)) return emptyList()
            links += ReferenceLink(
                referenceNumber(marker),
                referenceMarkerLabel(marker),
                url,
                getAnchorLabel(displayLabel, url),
            )
        }
        return links.takeIf { position == text.length }.orEmpty()
    }

    private fun parseUnnumberedAnchoredLinkText(
        text: String,
        anchors: List<Element>,
    ): List<ReferenceLink> {
        val links = mutableListOf<ReferenceLink>()
        var position = 0
        anchors.forEach { anchor ->
            position = skipInterReferenceSeparators(text, position)
            val url = normalizeUrl(anchor.attr("href"))
            if (!isUsableUrl(url)) return emptyList()

            val displayLabel = normalizeReferenceWhitespace(anchor.text())
            if (displayLabel.isEmpty() || !text.startsWith(displayLabel, position)) return emptyList()
            position += displayLabel.length
            links += ReferenceLink(null, url, getAnchorLabel(displayLabel, url))
        }
        position = skipInterReferenceSeparators(text, position)
        return links.takeIf { position == text.length }.orEmpty()
    }

    private fun parseBareReferenceText(text: String): List<ReferenceLink> {
        val normalizedText = normalizeReferenceWhitespace(text)
        if (!startsWithReferenceMarker(normalizedText)) return emptyList()

        val links = mutableListOf<ReferenceLink>()
        var position = 0
        while (position < normalizedText.length) {
            val marker = referenceMarkerAt(normalizedText, position) ?: return emptyList()
            position = marker.range.last + 1
            val urlStart = position
            while (position < normalizedText.length && !isBareUrlTerminator(normalizedText[position])) {
                position++
            }

            val label = trimTrailingUrlPunctuation(normalizedText.substring(urlStart, position))
            val url = normalizeBareUrl(label)
            if (!isUsableUrl(url)) return emptyList()
            links += ReferenceLink(
                referenceNumber(marker),
                referenceMarkerLabel(marker),
                url,
                label,
            )
            position = skipInterReferenceSeparators(normalizedText, position)
        }
        return links
    }

    private fun parseUnnumberedLinkText(text: String): List<ReferenceLink> {
        val normalizedText = normalizeReferenceWhitespace(text)
        if (normalizedText.isEmpty() || startsWithReferenceMarker(normalizedText)) return emptyList()

        val links = mutableListOf<ReferenceLink>()
        var position = 0
        while (position < normalizedText.length) {
            position = skipInterReferenceSeparators(normalizedText, position)
            if (position >= normalizedText.length) break
            val urlStart = position
            while (position < normalizedText.length && !isBareUrlTerminator(normalizedText[position])) {
                position++
            }

            val label = trimTrailingUrlPunctuation(normalizedText.substring(urlStart, position))
            val url = normalizeBareUrl(label)
            if (!isUsableUrl(url)) return emptyList()
            links += ReferenceLink(null, url, label)
        }
        return links
    }

    private fun parseUnnumberedAnchor(anchor: Element): ReferenceLink? {
        val url = normalizeUrl(anchor.attr("href"))
        if (!isUsableUrl(url)) return null
        val displayLabel = normalizeReferenceWhitespace(anchor.text())
        if (displayLabel.isEmpty()) return null
        return ReferenceLink(null, url, getAnchorLabel(displayLabel, url))
    }

    private fun getAnchorLabel(displayLabel: String, url: String): String {
        if (displayLabel.endsWith("...")) {
            val prefix = displayLabel.dropLast(3)
            if (url.startsWith(prefix)) return url
        }
        return displayLabel
    }

    private fun startsWithReferenceMarker(text: String): Boolean = referenceMarkerAt(text, 0) != null

    private fun referenceMarkerAt(text: String, position: Int): MatchResult? =
        referenceMarkerPattern.find(text, position)?.takeIf { it.range.first == position }

    private fun referenceNumber(marker: MatchResult): String? =
        marker.groups[1]?.value ?: marker.groups[2]?.value

    private fun referenceMarkerLabel(marker: MatchResult): String? =
        marker.groups[1]?.value?.let { "[$it]" } ?: marker.groups[2]?.value?.let { "$it:" }

    private fun skipInterReferenceSeparators(text: String, start: Int): Int {
        var position = start
        while (position < text.length) {
            val current = text[position]
            if (current.isWhitespace() || current == ',' || current == ';' || current == '|') {
                position++
            } else {
                break
            }
        }
        return position
    }

    private fun isBareUrlTerminator(value: Char): Boolean =
        value.isWhitespace() || value == ',' || value == ';' || value == '|'

    private fun normalizeReferenceWhitespace(value: String): String =
        value.replace('\u00a0', ' ').trim().replace(whitespacePattern, " ")

    private fun normalizeUrl(value: String?): String {
        val url = trimTrailingUrlPunctuation(
            Ksoup.parse(value.orEmpty()).text().trim()
                .replace("&#x2F;", "/")
                .replace("&#47;", "/"),
        )
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> HackerNewsLinks.BASE_URL + url
            "://" !in url && looksLikeDomain(url) -> "https://$url"
            else -> url
        }
    }

    private fun normalizeBareUrl(value: String?): String {
        val url = trimTrailingUrlPunctuation(
            Ksoup.parse(value.orEmpty()).text().trim()
                .replace("&#x2F;", "/")
                .replace("&#47;", "/"),
        )
        return if (url.startsWith("/") && !url.startsWith("//")) url else normalizeUrl(url)
    }

    private fun trimTrailingUrlPunctuation(value: String?): String =
        value.orEmpty().trim().trimEnd('.', ',', ';', ':', '!', '?')

    private fun isUsableUrl(url: String?): Boolean =
        url != null &&
            (url.startsWith("http://") || url.startsWith("https://")) &&
            url.length > "https://".length

    private fun looksLikeDomain(value: String?): Boolean {
        if (value == null || ('/' in value && value.startsWith('/'))) return false
        val lower = value.lowercase()
        if (!possibleBareDomainPattern.matches(lower)) return false

        val host = lower.substringBefore('/').substringBefore(':')
        val labels = host.split('.')
        if (labels.size < 2) return false
        // Bare domains are only a convenience. Keep this conservative so dotted identifiers such
        // as browser.ml.enable are not promoted into collected links.
        val tld = labels.last()
        return tld.length == 2 || tld in commonBareDomainTlds
    }

    private fun flushTextBlock(html: StringBuilder, target: MutableList<ContentBlock>) {
        val value = html.toString().trim()
        if (value.isNotEmpty()) target += ContentBlock.text(value)
        html.clear()
    }

    private data class CollectedNode(
        val index: Int,
        val node: Node,
        val links: List<ReferenceLink>,
    )

    class Result(
        bodyHtml: String?,
        links: List<ReferenceLink>,
        contentBlocks: List<ContentBlock>,
    ) {
        val bodyHtml: String = bodyHtml.orEmpty()
        val links: List<ReferenceLink> = links.toList()
        val contentBlocks: List<ContentBlock> = contentBlocks.toList()

        fun hasLinks(): Boolean = links.isNotEmpty()
        fun hasInterleavedLinks(): Boolean {
            var seenLink = false
            return contentBlocks.any { block ->
                if (block.isLink()) seenLink = true
                seenLink && !block.isLink()
            }
        }

        companion object {
            fun empty(bodyHtml: String?) = Result(bodyHtml, emptyList(), emptyList())
        }
    }

    class ContentBlock private constructor(
        val bodyHtml: String?,
        private val link: ReferenceLink?,
    ) {
        fun isLink(): Boolean = link != null
        fun getLink(): ReferenceLink? = link

        companion object {
            fun text(bodyHtml: String?) = ContentBlock(bodyHtml, null)
            fun link(link: ReferenceLink?) = ContentBlock(null, link)
        }
    }

    class ReferenceLink(
        val number: String?,
        val markerLabel: String?,
        val url: String?,
        val label: String?,
    ) {
        var resolvedTitle: String? = null

        constructor(number: String?, url: String?, label: String?) : this(
            number,
            number?.let { "[$it]" },
            url,
            label,
        )

        fun hasNumber(): Boolean = !number.isNullOrEmpty()
    }
}
