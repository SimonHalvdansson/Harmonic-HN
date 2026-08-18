package com.simon.harmonichackernews.ui.settings

import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.settings_section_about
import com.simon.harmonichackernews.resources.settings_section_ai_summary
import com.simon.harmonichackernews.resources.settings_section_appearance
import com.simon.harmonichackernews.resources.settings_section_comments
import com.simon.harmonichackernews.resources.settings_section_data
import com.simon.harmonichackernews.resources.settings_section_debug
import com.simon.harmonichackernews.resources.settings_section_debug_link_previews
import com.simon.harmonichackernews.resources.settings_section_filters_tags
import com.simon.harmonichackernews.resources.settings_section_licenses
import com.simon.harmonichackernews.resources.settings_section_stories
import com.simon.harmonichackernews.resources.settings_section_web_links
import org.jetbrains.compose.resources.StringResource

enum class SettingsSection(
    val route: String,
    val titleResource: StringResource,
) {
    Appearance("appearance", Res.string.settings_section_appearance),
    Stories("stories", Res.string.settings_section_stories),
    Comments("comments", Res.string.settings_section_comments),
    WebLinks("web_links", Res.string.settings_section_web_links),
    FiltersTags("filters_tags", Res.string.settings_section_filters_tags),
    AiSummary("ai_summary", Res.string.settings_section_ai_summary),
    Data("data", Res.string.settings_section_data),
    Debug("debug", Res.string.settings_section_debug),
    DebugLinkPreviews("debug_link_previews", Res.string.settings_section_debug_link_previews),
    About("about", Res.string.settings_section_about),
    Licenses("licenses", Res.string.settings_section_licenses),
    ;

    companion object {
        fun fromRoute(route: String): SettingsSection? = entries.firstOrNull { it.route == route }
    }
}
