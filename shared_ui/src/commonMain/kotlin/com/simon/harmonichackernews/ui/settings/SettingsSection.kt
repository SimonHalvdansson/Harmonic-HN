package com.simon.harmonichackernews.ui.settings

enum class SettingsSection(
    val route: String,
    val title: String,
) {
    Appearance("appearance", "Appearance"),
    Stories("stories", "Stories"),
    Comments("comments", "Comments"),
    WebLinks("web_links", "Web and links"),
    FiltersTags("filters_tags", "Filters and tags"),
    AiSummary("ai_summary", "AI summarization"),
    Data("data", "Data"),
    Debug("debug", "Debug"),
    About("about", "About"),
    Licenses("licenses", "Third-party licenses"),
    ;

    companion object {
        fun fromRoute(route: String): SettingsSection? = entries.firstOrNull { it.route == route }
    }
}
