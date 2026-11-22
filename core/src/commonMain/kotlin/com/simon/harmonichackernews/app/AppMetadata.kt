package com.simon.harmonichackernews.app

data class AppMetadata(
    val name: String = "Harmonic",
    val versionName: String = "",
    val versionCode: Int = 0,
    val buildNumber: String = "",
    val buildType: String = "",
    val debug: Boolean = false,
    val debugSettingsEnabled: Boolean = false,
    val projectUrl: String = "https://github.com/SimonHalvdansson/Harmonic-HN",
    val privacyUrl: String = "https://simonhalvdansson.github.io/harmonic_privacy.html",
) {
    val versionLabel: String
        get() = when {
            versionName.isBlank() -> ""
            debug && buildType.isNotBlank() -> "Version $versionName ($buildType)"
            else -> "Version $versionName"
        }
}

data class LicenseEntry(
    val name: String,
    val creator: String,
    val licenseType: String,
    val url: String,
    val noticeFile: String,
    val attribution: String? = null,
    val termsUrl: String? = null,
) {
    companion object {
        fun apache2(name: String, creator: String, url: String, attribution: String? = null) =
            LicenseEntry(name, creator, "Apache License 2.0", url, "apache_2.txt", attribution)
    }
}

/** Dependencies and adapted source code shared by every Harmonic KMP host. */
object CommonLicenseCatalog {
    val entries = listOf(
        LicenseEntry.apache2("Kotlin standard library", "JetBrains", "https://kotlinlang.org/"),
        LicenseEntry.apache2(
            "kotlinx.coroutines",
            "JetBrains",
            "https://github.com/Kotlin/kotlinx.coroutines",
            "Copyright 2000-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.",
        ),
        LicenseEntry.apache2("kotlinx.serialization", "JetBrains", "https://github.com/Kotlin/kotlinx.serialization"),
        LicenseEntry.apache2(
            "FlatBuffers",
            "Google",
            "https://github.com/google/flatbuffers",
            "Copyright 2021 Google Inc. All rights reserved.",
        ),
        LicenseEntry.apache2("kotlinx.io", "JetBrains", "https://github.com/Kotlin/kotlinx-io"),
        LicenseEntry.apache2(
            "kotlinx.collections.immutable",
            "JetBrains",
            "https://github.com/Kotlin/kotlinx.collections.immutable",
        ),
        LicenseEntry.apache2("Ktor", "JetBrains", "https://ktor.io/", "Copyright 2000-2023 JetBrains s.r.o."),
        LicenseEntry("kuri", "dexpace and Omar Aljarrah", "MIT License", "https://github.com/dexpace/kuri", "kuri.txt"),
        LicenseEntry(
            "Unicode character data",
            "Unicode, Inc.",
            "Unicode License v3",
            "https://www.unicode.org/ucd/",
            "unicode_3.txt",
        ),
        LicenseEntry.apache2(
            "Compose Multiplatform",
            "JetBrains",
            "https://www.jetbrains.com/compose-multiplatform/",
            "Copyright 2020-2021 JetBrains s.r.o. and and respective authors and developers.",
        ),
        LicenseEntry.apache2("Haze", "Chris Banes", "https://github.com/chrisbanes/haze", "Copyright 2024 Chris Banes"),
        LicenseEntry.apache2(
            "Coil",
            "Coil contributors",
            "https://coil-kt.github.io/coil/",
            "Copyright 2026 Coil Contributors",
        ),
        LicenseEntry("Ksoup", "FleekSoft", "MIT License", "https://github.com/fleeksoft/ksoup", "ksoup.txt"),
        LicenseEntry(
            "Compose LaTeX",
            "huarangmeng",
            "MIT License",
            "https://github.com/huarangmeng/latex",
            "compose_latex.txt",
        ),
        LicenseEntry(
            "KaTeX math fonts",
            "Khan Academy and contributors",
            "MIT License",
            "https://github.com/KaTeX/katex-fonts",
            "katex_fonts.txt",
            "Copyright (c) 2013-2020 Khan Academy and other contributors.",
        ),
        LicenseEntry.apache2(
            "Palette algorithms",
            "Android Open Source Project and KMPalette contributors",
            "https://github.com/jordond/kmpalette",
            "Copyright 2018 The Android Open Source Project. Adapted from the androidx-palette module in KMPalette.",
        ),
        LicenseEntry.apache2("pdf.js", "Mozilla", "https://mozilla.github.io/pdf.js/", "Copyright 2024 Mozilla Foundation"),
        LicenseEntry.apache2(
            "Readability",
            "Mozilla",
            "https://github.com/mozilla/readability",
            "Copyright (c) 2010 Arc90 Inc",
        ),
        LicenseEntry.apache2(
            "Materialistic",
            "Hidroh",
            "https://github.com/hidroh/materialistic",
            "Copyright 2015 Ha Duy Trung",
        ),
    )

    fun withPlatform(platformEntries: List<LicenseEntry>): List<LicenseEntry> =
        (platformEntries + entries).distinctBy { it.name to it.url }

    fun complete(
        platformEntries: List<LicenseEntry>,
        includeLocalAi: Boolean,
    ): List<LicenseEntry> = withPlatform(
        platformEntries + if (includeLocalAi) LocalAiLicenseCatalog.entries else emptyList(),
    )
}

/** Optional local-inference dependencies shared by hosts that bundle the local AI runtime. */
object LocalAiLicenseCatalog {
    val entries = listOf(
        LicenseEntry(
            "ML Kit GenAI APIs",
            "Google",
            "ML Kit Terms of Service",
            "https://developers.google.com/ml-kit/genai",
            "ml_kit_genai.txt",
            termsUrl = "https://developers.google.com/ml-kit/terms",
        ),
        LicenseEntry.apache2(
            "LiteRT-LM",
            "Google",
            "https://github.com/google-ai-edge/LiteRT-LM",
        ),
    )
}
