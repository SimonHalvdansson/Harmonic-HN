package com.simon.harmonichackernews.format

sealed interface ChangelogBlock {
    data class Heading(val text: String) : ChangelogBlock
    data class Paragraph(val text: String) : ChangelogBlock
    data class Bullet(val text: String) : ChangelogBlock
}

/** Parses the deliberately small Markdown subset used by the bundled app changelog. */
fun parseChangelogMarkdown(markdown: String): List<ChangelogBlock> {
    val lines = markdown.trimStart('\uFEFF').lines()
    val blocks = mutableListOf<ChangelogBlock>()
    var index = 0
    while (index < lines.size) {
        val line = lines[index].trim()
        when {
            line.isEmpty() -> index++
            line.startsWith("#") -> {
                blocks += ChangelogBlock.Heading(line.trimStart('#').trim())
                index++
            }

            line.startsWith("- ") -> {
                blocks += ChangelogBlock.Bullet(line.removePrefix("- ").trim())
                index++
            }

            else -> {
                val paragraph = mutableListOf<String>()
                while (index < lines.size) {
                    val paragraphLine = lines[index].trim()
                    if (
                        paragraphLine.isEmpty() ||
                        paragraphLine.startsWith("#") ||
                        paragraphLine.startsWith("- ")
                    ) {
                        break
                    }
                    paragraph += paragraphLine
                    index++
                }
                blocks += ChangelogBlock.Paragraph(paragraph.joinToString(" "))
            }
        }
    }
    return blocks
}
