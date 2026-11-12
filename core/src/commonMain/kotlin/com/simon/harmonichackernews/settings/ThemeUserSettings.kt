package com.simon.harmonichackernews.settings

/** Display-only overrides. The settings editor and backups continue to use the stored reader. */
class ThemeUserSettings(
    private val stored: UserSettings,
    private val selection: () -> ThemeSelection,
) : UserSettings by stored {
    override val story: StoryPreferences
        get() = stored.story.forPureBlackDarkMode(selection().pureBlackDarkMode)
    override val comments: CommentPreferences
        get() = stored.comments.forPureBlackDarkMode(selection().pureBlackDarkMode)
}

fun StoryPreferences.forPureBlackDarkMode(active: Boolean): StoryPreferences =
    if (active) copy(displayStyle = DisplayStyle.FLAT, tintCardsFromImages = false) else this

fun CommentPreferences.forPureBlackDarkMode(active: Boolean): CommentPreferences =
    if (active) copy(
        displayStyle = DisplayStyle.FLAT,
        headerTintEnabled = false,
        tintHeader = false,
        headerTint = CommentsHeaderTint.OFF,
    ) else this
