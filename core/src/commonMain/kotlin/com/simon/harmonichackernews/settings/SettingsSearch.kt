package com.simon.harmonichackernews.settings

/** Search metadata is separate from preference values: accounts, keys and user data are never indexed. */
data class SettingsSearchEntry(
    val id: String,
    val screenRoute: String,
    val title: String,
    val path: List<String>,
    val keywords: String = "",
    val settingTitle: String? = title,
    val aliases: List<String> = emptyList(),
)

/** A small, pre-tokenized offline index. Every query word must match, in any order. */
class SettingsSearchIndex(entries: List<SettingsSearchEntry>) {
    private data class IndexedEntry(
        val entry: SettingsSearchEntry,
        val title: String,
        val titleWords: List<String>,
        val keywordWords: List<String>,
        val pathWords: List<String>,
        val aliases: List<String>,
    )

    private val indexed = entries.map {
        val title = normalize(it.title)
        IndexedEntry(it, title, words(title), words(normalize(it.keywords + " " + it.aliases.joinToString(" "))),
            words(normalize(it.path.joinToString(" "))), it.aliases.map(::normalize))
    }

    fun search(query: String): List<SettingsSearchEntry> {
        val normalized = normalize(query)
        val terms = words(normalized).distinct()
        if (terms.isEmpty()) return emptyList()
        return indexed.mapNotNull { indexed ->
            var score = 0
            for (term in terms) {
                score += when {
                    term in indexed.titleWords -> 100
                    indexed.titleWords.any { it.startsWith(term) } -> 80
                    term in indexed.keywordWords -> 60
                    indexed.keywordWords.any { it.startsWith(term) } -> 50
                    indexed.pathWords.any { it.startsWith(term) } -> 20
                    else -> return@mapNotNull null
                }
            }
            score += when {
                indexed.title == normalized -> 1000
                normalized in indexed.aliases -> 1000
                indexed.title.startsWith(normalized) -> 500
                indexed.title.contains(normalized) -> 200
                else -> 0
            }
            indexed.entry to score
        }.sortedByDescending { it.second }.map { it.first }
    }

    private fun normalize(value: String): String =
        value.lowercase().map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("").trim().split(Regex("\\s+")).joinToString(" ")

    private fun words(value: String): List<String> = value.split(' ').filter { it.isNotEmpty() }
}
