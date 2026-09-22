package com.simon.harmonichackernews.settings

enum class CollectedLinksMode(val label: String) {
    Off("Off"), Small("1 line"), Expanded("2 lines");

    companion object {
        fun from(enabled: Boolean, expanded: Boolean): CollectedLinksMode = when {
            !enabled -> Off
            expanded -> Expanded
            else -> Small
        }
    }
}
