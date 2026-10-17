package com.simon.harmonichackernews.settings

enum class CommentsHeaderLayout(val storedValue: String, val label: String) {
    STANDARD("standard", "Standard"),
    BALANCED("balanced", "Balanced");

    companion object {
        fun fromStored(value: String?) = entries.firstOrNull { it.storedValue == value } ?: STANDARD
    }
}

enum class CommentsHeaderEdge(val storedValue: String, val label: String) {
    FADE("fade", "Fade"),
    ROUNDED("rounded", "Rounded"),
    SHADOW("shadow", "Shadow");

    companion object {
        fun fromStored(value: String?) = if (value == "hard") ROUNDED
            else entries.firstOrNull { it.storedValue == value } ?: FADE
    }
}

enum class CommentsHeaderTint(val storedValue: String, val label: String) {
    OFF("off", "Off"), THEME("theme", "Theme"), IMAGE("image", "Image");

    companion object {
        fun fromStored(value: String?, legacyEnabled: Boolean = true) =
            entries.firstOrNull { it.storedValue == value }
                ?: if (legacyEnabled) IMAGE else OFF
    }
}

/** Uses the existing persisted visibility/layout pair, including older backups. */
enum class CommentsHeaderImage(val label: String) {
    OFF("Off"), SMALL("Small"), LARGE("Large");

    companion object {
        fun from(showImage: Boolean, layout: CommentsHeaderLayout) = when {
            !showImage -> OFF
            layout == CommentsHeaderLayout.BALANCED -> SMALL
            else -> LARGE
        }
    }
}

/** Presentation of poster, votes and comments; image-less posts keep all details accessible. */
enum class CommentsHeaderMetaInfo(val storedValue: String, val label: String) {
    ON_IMAGE("on_image", "Integrated"), SMALL("small", "Line"), BUTTONS("buttons", "Large");

    fun resolve(hasAccount: Boolean): CommentsHeaderMetaInfo = when {
        this == BUTTONS && !hasAccount -> SMALL
        else -> this
    }

    companion object {
        fun fromStored(value: String?, legacyCombined: Boolean = false) =
            entries.firstOrNull { it.storedValue == value }
                ?: if (value == null && legacyCombined) BUTTONS else SMALL
    }
}
