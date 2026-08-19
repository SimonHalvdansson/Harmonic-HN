package com.simon.harmonichackernews.utils

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node

/** Extracts standalone and trailing reference links without any platform URL or HTML APIs. */
object CollectedReferenceLinks {
    private val referenceMarker = Regex(
        "^\\s*(?:\\[(\\d{1,3})]|(\\d{1,3})\\s*:)[\\s:.-]*(.*)$",
    )
    private val standaloneTags = setOf("a", "p", "div", "span", "li")
    private val urlPattern = Regex("https?://[^\\s,;|]+", RegexOption.IGNORE_CASE)
    private val bareDomainPattern = Regex(
        "[a-z0-9][a-z0-9.-]*\\.[a-z]{2,}(?::\\d+)?(?:/\\S*)?",
        RegexOption.IGNORE_CASE,
    )
    private val whitespacePattern = Regex("\\s+")

    fun parse(inputHtml: String?): Result {
        if (inputHtml.isNullOrEmpty()) return Result.empty(inputHtml)
        val document = Ksoup.parse(inputHtml)
        document.outputSettings().prettyPrint(false)
        val nodes = document.body().childNodes().toList()
        if (nodes.isEmpty()) return Result.empty(inputHtml)
        val nodeHtml = nodes.map { it.outerHtml() }

        val collected = linkedMapOf<Int, List<ReferenceLink>>()
        var stillInTrailingReferences = true
        for (index in nodes.indices.reversed()) {
            val node = nodes[index]
            if (nodeHtml[index].isBlank()) continue
            val links = parseNode(
                node,
                requireReferenceMarker = stillInTrailingReferences,
                outerHtml = nodeHtml[index],
            )
            if (links.isNotEmpty()) {
                collected[index] = links
            } else {
                stillInTrailingReferences = false
            }
        }

        nodes.forEachIndexed { index, node ->
            if (index !in collected) {
                parseStandaloneNode(node, nodeHtml[index])
                    .takeIf(List<ReferenceLink>::isNotEmpty)?.let {
                    collected[index] = it
                }
            }
        }
        if (collected.isEmpty()) return Result.empty(inputHtml)

        val body = buildString {
            nodeHtml.forEachIndexed { index, html -> if (index !in collected) append(html) }
        }.trim()
        val links = collected.entries.sortedBy { it.key }.flatMap { it.value }
        val contentBlocks = buildList {
            val html = StringBuilder()
            nodeHtml.forEachIndexed { index, node ->
                val nodeLinks = collected[index]
                if (nodeLinks == null) {
                    html.append(node)
                } else {
                    flushTextBlock(html, this)
                    nodeLinks.forEach { add(ContentBlock.link(it)) }
                }
            }
            flushTextBlock(html, this)
        }
        return Result(body, links, contentBlocks)
    }

    private fun parseNode(
        node: Node,
        requireReferenceMarker: Boolean,
        outerHtml: String,
    ): List<ReferenceLink> {
        val element = node as? Element
        val text = normalize(element?.text() ?: Ksoup.parse(outerHtml).text())
        if (text.isEmpty()) return emptyList()
        val marker = referenceMarker.matchEntire(text)
        if (requireReferenceMarker && marker == null) return emptyList()
        val number = marker?.groupValues?.let { it[1].ifBlank { it[2] } }?.ifBlank { null }
        val markerLabel = number?.let { "[$it]" }
        val anchors = element?.select("a[href]").orEmpty()
        if (anchors.isNotEmpty()) {
            return anchors.mapNotNull { anchor ->
                normalizeUrl(anchor.attr("href")).takeIf(::isUsableUrl)?.let { url ->
                    ReferenceLink(number, markerLabel, url, normalize(anchor.text()).ifBlank { url })
                }
            }
        }
        val source = marker?.groupValues?.getOrNull(3).orEmpty().ifBlank { text }
        return findUrls(source).map { url -> ReferenceLink(number, markerLabel, url, url) }
    }

    private fun parseStandaloneNode(node: Node, outerHtml: String): List<ReferenceLink> {
        val element = node as? Element ?: return emptyList()
        val tag = element.tagName().lowercase()
        if (tag !in standaloneTags) return emptyList()
        val text = normalize(element.text())
        if (text.isEmpty()) return emptyList()
        val links = parseNode(node, requireReferenceMarker = false, outerHtml = outerHtml)
        if (links.isEmpty()) return emptyList()
        val remaining = links.fold(text) { value, link ->
            value.replace(link.label.orEmpty(), "").replace(link.url.orEmpty(), "")
        }.replace(Regex("[\\s,;|.-]+"), "")
        return links.takeIf { remaining.isEmpty() }.orEmpty()
    }

    private fun findUrls(text: String): List<String> {
        val explicit = urlPattern.findAll(text).map { trimPunctuation(it.value) }.toList()
        if (explicit.isNotEmpty()) return explicit
        return bareDomainPattern.findAll(text).map { "https://${trimPunctuation(it.value)}" }.toList()
    }

    private fun normalizeUrl(value: String): String {
        val decoded = Ksoup.parse(value).text().trim().replace("&#x2F;", "/").replace("&#47;", "/")
        val normalized = when {
            decoded.startsWith("//") -> "https:$decoded"
            decoded.startsWith("/") -> HackerNewsLinks.BASE_URL + decoded
            "://" !in decoded && bareDomainPattern.matches(decoded) -> "https://$decoded"
            else -> decoded
        }
        return trimPunctuation(normalized)
    }

    private fun isUsableUrl(value: String): Boolean =
        (value.startsWith("https://") || value.startsWith("http://")) && value.length > 8

    private fun trimPunctuation(value: String): String = value.trim().trimEnd('.', ',', ';', ':', '!', '?')

    private fun normalize(value: String): String =
        value.replace('\u00a0', ' ').trim().replace(whitespacePattern, " ")

    private fun flushTextBlock(html: StringBuilder, target: MutableList<ContentBlock>) {
        val value = html.toString().trim()
        if (value.isNotEmpty()) target += ContentBlock.text(value)
        html.clear()
    }

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
