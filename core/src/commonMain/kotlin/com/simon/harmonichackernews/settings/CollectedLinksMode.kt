package com.simon.harmonichackernews.settings

enum class CollectedLinksMode(val label: String) {
    Off("Off"), Small("Small"), Expanded("Expanded");

    companion object {
        fun from(enabled: Boolean, expanded: Boolean): CollectedLinksMode = when {
            !enabled -> Off
            expanded -> Expanded
            else -> Small
        }
    }
}
