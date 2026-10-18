package com.simon.harmonichackernews.settings

enum class QuoteHighlightColor(val storedValue: String, val label: String) {
    NEUTRAL("neutral", "Neutral"),
    MATERIAL("material", "Material"),
    ;
    companion object {
        fun fromStored(value: String?): QuoteHighlightColor =
            entries.firstOrNull { it.storedValue == value } ?: MATERIAL
    }
}

enum class QuoteHighlightStyle(val storedValue: String, val label: String) {
    BACKGROUND("background", "Background"),
    SIDE_LINE("side_line", "Side line"),
    BOTH("both", "Both"),
    ;
    companion object {
        fun fromStored(value: String?): QuoteHighlightStyle =
            entries.firstOrNull { it.storedValue == value } ?: BOTH
    }
}

enum class QuoteSpacing(val storedValue: String, val label: String) {
    COMFORTABLE("comfortable", "Comfortable"),
    COMPACT("compact", "Compact"),
    ;
    companion object {
        fun fromStored(value: String?): QuoteSpacing =
            entries.firstOrNull { it.storedValue == value } ?: COMFORTABLE
    }
}
