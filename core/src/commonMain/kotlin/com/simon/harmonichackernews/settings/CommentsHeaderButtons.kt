package com.simon.harmonichackernews.settings

/** Toolbar order, followed by actions traditionally available only in the overflow menu. */
enum class CommentsHeaderButton(val label: String) {
    USER("User"), COMMENT("Comment"), VOTE("Vote"), FAVORITE("Favorite"),
    BOOKMARK("Bookmark"), SUMMARIZE("Summarize"), SHARE("Share"), REFRESH("Refresh"),
    SIDE_BY_SIDE("Read side by side"), OPEN_PARENT("Open parent"), OPEN_TOP_LEVEL("Open top level"),
    SEARCH("Search comments"), SORT("Sort comments"), COMMENTS_BY_OP("Comments by OP"),
    OPEN_BROWSER("Open in browser"), DISABLE_AD_BLOCK("Disable AdBlock"), ARCHIVE("View on archive");

    val configurable: Boolean get() = this != OPEN_PARENT && this != OPEN_TOP_LEVEL

    internal val storageKey: String get() = "pref_comments_header_button_${name.lowercase()}"
}

/** Unset choices retain the original account-dependent toolbar defaults. */
data class CommentsHeaderButtons(val overrides: Map<CommentsHeaderButton, Boolean> = emptyMap()) {
    fun isShown(button: CommentsHeaderButton, hasAccount: Boolean, largeDetails: Boolean): Boolean =
        if (!button.configurable) false else overrides[button] ?: when (button) {
            CommentsHeaderButton.USER, CommentsHeaderButton.COMMENT, CommentsHeaderButton.VOTE,
            CommentsHeaderButton.FAVORITE, CommentsHeaderButton.SUMMARIZE, CommentsHeaderButton.SHARE -> true
            CommentsHeaderButton.BOOKMARK, CommentsHeaderButton.REFRESH -> !hasAccount || largeDetails
            CommentsHeaderButton.SIDE_BY_SIDE -> !hasAccount
            else -> false
        }

    /** Share keeps its existing priority when a narrow header cannot fit all chosen buttons. */
    fun inlineButtons(available: List<CommentsHeaderButton>, capacity: Int, hasAccount: Boolean,
        largeDetails: Boolean): List<CommentsHeaderButton> {
        val chosen = available.filter { isShown(it, hasAccount, largeDetails) }
        val slots = (capacity - 1).coerceAtLeast(0) // The overflow menu is always accessible.
        val share = CommentsHeaderButton.SHARE.takeIf { it in chosen && slots > 0 }
        val fitted = chosen.filter { it != share }.take(slots - if (share != null) 1 else 0).toSet()
        return chosen.filter { it in fitted || it == share }
    }
}
