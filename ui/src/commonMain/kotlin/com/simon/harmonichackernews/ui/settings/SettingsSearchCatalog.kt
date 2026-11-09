package com.simon.harmonichackernews.ui.settings

import com.simon.harmonichackernews.settings.SettingsSearchEntry

/** Hosts supply the same capabilities they use when rendering Settings. */
data class SettingsSearchCapabilities(
    val webLinks: WebLinksSettingsCapabilities = WebLinksSettingsCapabilities(),
    val notifications: Boolean = true,
    val systemSettings: Boolean = true,
    val volumeNavigation: Boolean = true,
    val transparentStatusBar: Boolean = true,
    val twoPane: Boolean = false,
    val commentsSwipeBack: Boolean = false,
)

internal data class SettingsSearchAvailability(
    val capabilities: SettingsSearchCapabilities = SettingsSearchCapabilities(),
    val loggedIn: Boolean = false,
    val debug: Boolean = false,
    val cloudAi: Boolean = true,
    val cloudAiSupported: Boolean = true,
    val geminiNano: Boolean = false,
    val nighttimeScheme: Boolean = false,
    val localAi: Boolean = false,
    val accountPreview: Boolean = false,
)

/** Use the visible row title as the target; synonyms never appear in the preference UI. */
internal fun settingsSearchCatalog(
    labels: Map<SettingsSection, String>,
    availability: SettingsSearchAvailability,
): List<SettingsSearchEntry> = buildList {
    val capabilities = availability.capabilities
    fun path(section: SettingsSection): List<String> = when (section) {
        SettingsSection.Theme, SettingsSection.Font, SettingsSection.PaletteTint,
        SettingsSection.StoriesHeader -> listOf(labels.getValue(SettingsSection.Appearance))
        SettingsSection.CommentsSpacing, SettingsSection.ThreadDepth, SettingsSection.QuoteHighlighting,
        SettingsSection.UserAvatars -> listOf(labels.getValue(SettingsSection.Comments))
        SettingsSection.ReaderMode -> listOf(labels.getValue(SettingsSection.WebLinks))
        SettingsSection.ArticleCategories -> listOf(labels.getValue(SettingsSection.AiSummary))
        SettingsSection.CategoryExploration -> listOf(labels.getValue(SettingsSection.AiSummary), labels.getValue(SettingsSection.ArticleCategories))
        SettingsSection.StoriesSpacing, SettingsSection.Frontpages -> listOf(labels.getValue(SettingsSection.Stories))
        SettingsSection.Licenses -> listOf(labels.getValue(SettingsSection.About))
        SettingsSection.Glass, SettingsSection.DebugLinkPreviews -> listOf(labels.getValue(SettingsSection.Debug))
        else -> emptyList()
    } + labels.getValue(section)

    fun page(section: SettingsSection, keywords: String = "", aliases: List<String> = emptyList()) {
        add(SettingsSearchEntry(section.route, section.route, labels.getValue(section), path(section),
            keywords, settingTitle = null, aliases = aliases))
    }
    fun group(section: SettingsSection, category: String, vararg settings: Pair<String, String>) {
        settings.forEach { (title, keywords) ->
            add(SettingsSearchEntry("${section.route}/$title", section.route, title,
                path(section) + listOfNotNull(category.takeIf { it.isNotEmpty() }), keywords))
        }
    }

    page(SettingsSection.Appearance, "look design layout customize")
    page(SettingsSection.Theme, "theme dark light black amoled oled dynamic material you colors colours",
        aliases = listOf("dark mode", "light mode", "theme"))
    page(SettingsSection.Font, "typeface typography fonts letters text size comments stories",
        aliases = listOf("font", "fonts", "typeface"))
    page(SettingsSection.PaletteTint, "image color colour vibrant muted saturation")
    page(SettingsSection.StoriesHeader, "frontpage selector tabs dropdown compact")
    page(SettingsSection.Stories, "posts frontpage feed articles")
    page(SettingsSection.Comments, "discussion threads replies")
    page(SettingsSection.StoriesSpacing, "stories whitespace padding margins density compact dividers")
    page(SettingsSection.CommentsSpacing, "comments whitespace padding margins density compact dividers")
    for (section in listOf(SettingsSection.StoriesSpacing, SettingsSection.CommentsSpacing)) {
        group(section, "Layout",
            "Vertical spacing" to "whitespace padding margins density compact space",
            "Dividers" to "separators horizontal lines")
    }
    page(SettingsSection.HeaderDisplay, "comments header toolbar post details buttons")
    page(SettingsSection.ThreadDepth, "indent indentation nesting rainbow lines")
    page(SettingsSection.QuoteHighlighting, "blockquote quotations quoted text")
    page(SettingsSection.UserAvatars, "avatar avatars identicon profile pictures images",
        aliases = listOf("avatars", "profile pictures"))
    page(SettingsSection.WebLinks, "browser websites hyperlinks")
    if (capabilities.webLinks.readerMode) page(SettingsSection.ReaderMode, "reading readability distraction free article font")
    page(SettingsSection.Frontpages, "feeds categories tabs subscriptions unslop hide ai")
    page(SettingsSection.FiltersTags, "block mute exclude keywords users domains tags")
    page(SettingsSection.AiSummary,
        "artificial intelligence llm ask chat summarize summarise gemini nano gemma qwen llama phi local offline download models" +
            if (availability.cloudAiSupported) " ollama openai anthropic cloud" else "")
    page(SettingsSection.ArticleCategories, "embeddinggemma embeddings categorize categorization categories labels topics similarity sensitivity cutoff experimental")
    page(SettingsSection.CategoryExploration, "how categories work embeddings cosine similarity map umap tsne pca examples")
    group(SettingsSection.AiSummary, "", "Article categories" to "tag stories embeddings embeddinggemma labels topics")
    group(SettingsSection.ArticleCategories, "", "Categorize stories" to "enable embeddings", "Similarity cutoff" to "sensitivity threshold",
        "Input token limit" to "embeddings context length maximum 128 256 512 1024")
    page(SettingsSection.Data, "storage cache backup restore export import bookmarks history")
    page(SettingsSection.About, "version github privacy changelog updates")
    page(SettingsSection.Licenses, "open source libraries copyright dependencies")

    if (capabilities.transparentStatusBar) group(SettingsSection.Appearance, "Visual",
        "Transparent status bar" to "edge to edge system clock icons transparency")
    group(SettingsSection.Appearance, "Other", "Preset" to "style default compact reset appearance")
    if (capabilities.twoPane) group(SettingsSection.Appearance, "Two panes",
        "Extra side padding" to "margins width space tablet")
    group(SettingsSection.Theme, "Night schedule",
        "Use a nighttime color scheme" to "automatic scheduled dark mode night theme time sunset")
    if (availability.nighttimeScheme) group(SettingsSection.Theme, "Night schedule",
        "Timed range" to "schedule start end hours night time")
    group(SettingsSection.PaletteTint, "Behavior", "Avoid background color" to "tint contrast background colour")
    group(SettingsSection.PaletteTint, "Adjust",
        "Tint strength" to "palette intensity amount transparency alpha",
        "Colorfulness" to "saturation colour vivid muted vibrant",
        "Brightness" to "lightness tone darker lighter")
    group(SettingsSection.StoriesHeader, "Layout",
        "Frontpage selector" to "tabs dropdown frontpages navigation",
        "Compact header" to "spacing above title below controls",
        "Header pill" to "rounded surface color primary secondary tertiary toolbar",
        "Pill elevation" to "header shadow raised depth")

    group(SettingsSection.Stories, "Layout",
        "Category tag location" to "article categorization labels title metadata domain tags placement",
        "Preview image" to "images pictures photos cover large small thumbnails",
        "Borderless image" to "edge to edge photo cover padding margins",
        "Text size" to "font bigger smaller accessibility zoom",
        "Display style" to "flat filled raised outlined cards borders elevation",
        "Tint" to "palette colors colours images",
        "Compact layout" to "dense small minimal list",
        "Summary" to "preview text description excerpt",
        "Thumbnails" to "favicon favicons website icon logo",
        "Points" to "score votes upvotes",
        "Compact points" to "score upvote button small",
        "Include top level domain" to "tld com org website url",
        "Comment count" to "replies number discussion",
        "Show story indices" to "rank ranking numbered numbers order",
        "Left align comments button" to "left handed comments position thumb reach",
        "Highlight hot stories" to "popular trending points comments",
    )
    group(SettingsSection.Stories, "Behavior",
        "Always open comments" to "tap click story discussion default",
        "Use pagination" to "pages infinite scrolling load 30 stories",
        "Swipe to hide" to "gesture dismiss posts left right",
        "Hide clicked posts" to "read stories viewed history hide read",
        "Gray out clicked posts" to "grey dim fade read stories viewed",
        "Favicon provider" to "thumbnail logo website icon source google duckduckgo")

    group(SettingsSection.Comments, "Comments display",
        "Display style" to "flat filled raised outlined cards border",
        "Text size" to "font bigger smaller accessibility zoom",
        "Collect links" to "reference links collect references urls expand collapse",
        "Emphasize username and time" to "highlight metadata author date bold",
        "Mark new comments" to "unread new replies highlight dot")
    group(SettingsSection.Comments, "Interactions",
        "Hide text of collapsed comments" to "collapse parent minimize minimize hide",
        "Auto-collapse top level comments" to "automatically collapse root threads",
        "Hide comments" to "hidden flagged flag dead deleted killed delayed pending moderation show all hellbanned shadowbanned",
        "Preload comments from stories screen" to "prefetch download offline battery wifi background",
        "Comment tap action" to "long press touch click toggle visibility details gesture",
        "Comment sorting" to "sort order top newest oldest best chronological default",
        "Comments provider" to "api hacker news algolia source")
    group(SettingsSection.Comments, "Navigation",
        "Show navigation buttons" to "next previous top level threads jump",
        "Show scrollbar" to "scroll bar position indicator",
        "Peek story header" to "title scroll up top compact header",
        "Smooth scroll comments" to "scrolling animation navigation",
        "Animate comment expand/collapse" to "motion animations transitions reduce motion")
    if (capabilities.volumeNavigation) group(SettingsSection.Comments, "Navigation",
        "Volume buttons for navigation" to "volume keys rocker hardware up down jump threads")
    if (capabilities.commentsSwipeBack) group(SettingsSection.Comments, "Navigation",
        "Swipe back from comments" to "gesture right return previous screen disable swipeback")
    group(SettingsSection.ThreadDepth, "Indicator shape",
        "Thickness" to "width thread lines thin thick",
        "Rounded corners" to "round radius thread lines")
    group(SettingsSection.ThreadDepth, "Thread lines",
        "Continuous thread lines" to "connected branches nesting indentation",
        "Show top level indicators" to "root thread depth indentation")
    group(SettingsSection.QuoteHighlighting, "Appearance",
        "Padding" to "quote inset text left top bottom space",
        "Color" to "colour quotes palette material",
        "Style" to "background stripe both blockquote",
        "Quote spacing" to "compact comfortable padding margins")
    group(SettingsSection.UserAvatars, "Customize",
        "Frame shape" to "avatar circle rounded square squircle hexagon",
        "Colors" to "colours profile images material original")
    group(SettingsSection.HeaderDisplay, "Layout",
        "Preview image" to "header photo cover picture",
        "Post details" to "metadata title author points time score",
        "Domain" to "website url host",
        "Article category tags" to "categorization labels post header",
        "Thumbnail" to "favicon website logo icon",
        "Points" to "score votes post header indicator")
    group(SettingsSection.HeaderDisplay, "Appearance",
        "Background tint" to "header palette color colour",
        "Bottom edge" to "header shape rounded corners")

    if (capabilities.webLinks.integratedWebView) group(SettingsSection.WebLinks, "In-app browser",
        "In-app browser" to "embedded webview websites internal browser")
    if (capabilities.webLinks.closeWebViewOnBack) group(SettingsSection.WebLinks, "In-app browser",
        "Hide browser on back" to "back button close dismiss webview")
    if (capabilities.webLinks.preloadWebsites) group(SettingsSection.WebLinks, "In-app browser",
        "Preload websites" to "prefetch offline cache battery wifi")
    group(SettingsSection.WebLinks, "In-app browser",
        "Match in-app browser dark mode to theme" to "dark light websites night")
    if (capabilities.webLinks.adBlocking) group(SettingsSection.WebLinks, "In-app browser",
        "Block in-app browser ads" to "adblock ad blocker advertisements advertising tracking")
    group(SettingsSection.WebLinks, "Browser and links",
        "Use external browser" to "chrome firefox default browser custom tabs",
        "Redirect Twitter/X to Nitter" to "twitter x social alternative privacy",
        "Nitter instance URL" to "twitter server host address",
        "Redirect to archive version" to "archive is archive ph paywall archived domains",
        "Link previews" to "youtube video wikipedia twitter x thumbnails popups")
    if (capabilities.webLinks.readerMode) group(SettingsSection.ReaderMode, "Reading",
        "Use reader mode" to "enable reading readability",
        "Activate automatically" to "default automatic reader articles",
        "Font" to "typeface typography serif sans reader",
        "Text size" to "font bigger smaller accessibility zoom",
        "Line height" to "line spacing leading readability")
    group(SettingsSection.FiltersTags, "Filters",
        "Filter by story title" to "keyword block mute hide words exclude blacklist",
        "Filter by domain" to "website block mute hide url exclude blacklist",
        "Blocked users" to "mute ignore hide authors blacklist",
        "Hide job posts" to "jobs hiring employment recruiting",
        "Hide AI posts" to "artificial intelligence unslop news filter")
    group(SettingsSection.AiSummary, "",
        "Enable AI features" to "ask summarize summarise artificial intelligence toggle")
    if (availability.cloudAiSupported && availability.cloudAi) group(SettingsSection.AiSummary, "Large language model",
        "Base URL" to "api endpoint server openai compatible cloud provider",
        "API Key" to "token credential secret authentication cloud openai",
        "Model" to "name cloud llm gpt gemini claude")
    if (availability.geminiNano) group(SettingsSection.AiSummary, "Summarization",
        "Gemini Nano summarizer" to "on device local three bullets prompt summary")
    if (availability.localAi) group(SettingsSection.AiSummary, "Large language model",
        "Downloadable models" to "install download gemma qwen llama phi local offline storage")
    if (!availability.geminiNano) group(SettingsSection.AiSummary, "Summarization",
        "System prompt" to "instructions ai assistant behavior customise customize")
    group(SettingsSection.AiSummary, "Summarization",
        "Stream responses" to "streaming tokens live incremental",
        "Automatically summarize articles" to "auto summary summarise reading",
        "Enable bold formatting" to "markdown emphasis text",
        "Show generation details" to "tokens speed performance model statistics")

    page(SettingsSection.Account, "login log in logout log out notifications replies activity inbox alerts")
    val hasAccount = availability.loggedIn || availability.accountPreview
    if (!hasAccount) group(SettingsSection.Account, "", "Log in" to "account sign in hacker news")
    if (availability.accountPreview) group(SettingsSection.Account, "", "Use real account" to "debug preview account clear")
    else if (availability.loggedIn) group(SettingsSection.Account, "", "Log out" to "account sign out hacker news")
    if (hasAccount) group(SettingsSection.Account, "Recent activity",
        "Check now" to "refresh replies sync notifications",
        "Recent activity" to "inbox unread history replies")
    if (hasAccount) group(SettingsSection.Account, "Notifications", "Muted discussions" to "mute unmute threads alerts")
    if (capabilities.notifications && hasAccount) {
        group(SettingsSection.Account, "Notifications",
            "Replies to your comments" to "alerts replies notify",
            "Comments on your submissions" to "stories posts alerts notify")
        group(SettingsSection.Account, "Background checking",
            "Check automatically" to "background enable disable sync",
            "Background check frequency" to "poll interval minutes battery sync",
            "Wi-Fi only" to "wifi network mobile data",
            "Limit checking hours" to "schedule time night quiet hours")
        if (capabilities.systemSettings) group(SettingsSection.Account, "Notifications",
            "System notification settings" to "sound vibration permissions android ios alerts")
    }
    group(SettingsSection.Data, "Bookmarks",
        "Enable bookmarks" to "saved save offline favorites favourites",
        "Export bookmarks" to "backup save file transfer",
        "Import bookmarks" to "restore backup transfer file")
    if (availability.loggedIn) group(SettingsSection.Data, "Bookmarks",
        "Add all bookmarks to HN favorites" to "sync synchronize favourites account saved")
    group(SettingsSection.Data, "Storage",
        "Clear reading history" to "delete remove viewed read stories storage",
        "Clear hidden posts" to "unhide reset restore hidden stories",
        "Clear post cache" to "delete cached articles comments storage space",
        "Clear tint cache" to "delete palette color colour storage space")
    group(SettingsSection.Data, "Settings",
        "Export settings" to "backup preferences save file transfer",
        "Import settings" to "restore backup preferences transfer file",
        "Reset all settings" to "default factory preferences reset")
    group(SettingsSection.Data, "Other", "Show update changelogs" to "release notes whats new version")
    if (capabilities.systemSettings) group(SettingsSection.Data, "Other",
        "Open Hacker News links in Harmonic" to "default app supported links deep links android")
    if (availability.debug) {
        page(SettingsSection.Debug, "developer tools fixtures sample content test")
        page(SettingsSection.Glass, "blur haze transparency refraction surface effects")
        page(SettingsSection.DebugLinkPreviews, "debug examples video youtube wikipedia twitter previews")
        group(SettingsSection.Debug, "Debug tools",
            "Fail story loads" to "network error testing",
            "Always show tap to refresh" to "debug stories refresh",
            "Show widget debug info" to "home screen errors refresh",
            "Mock AI answers" to "sample llm replies summaries fixture",
            "Open without cache" to "debug story fresh network")
        group(SettingsSection.Debug, "Account",
            "Account preview" to "debug logged in username impersonate public activity identity",
            "Debug notifications" to "test notification replies alerts",
            "Open Account settings" to "debug account inbox activity")
    }
}
