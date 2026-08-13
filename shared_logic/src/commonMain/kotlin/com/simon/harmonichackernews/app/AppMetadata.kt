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
)

/** Dependencies linked by every Harmonic KMP host. */
object CommonLicenseCatalog {
    val entries = listOf(
        LicenseEntry("Kotlin standard library", "JetBrains", "Apache License 2.0", "https://kotlinlang.org/"),
        LicenseEntry("kotlinx.coroutines", "JetBrains", "Apache License 2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
        LicenseEntry("Ktor", "JetBrains", "Apache License 2.0", "https://ktor.io/"),
        LicenseEntry("Compose Multiplatform", "JetBrains", "Apache License 2.0", "https://www.jetbrains.com/compose-multiplatform/"),
        LicenseEntry("Coil", "Coil contributors", "Apache License 2.0", "https://coil-kt.github.io/coil/"),
        LicenseEntry("Ksoup", "FleekSoft", "MIT License", "https://github.com/fleeksoft/ksoup"),
        LicenseEntry("KMPalette", "Jordan Dixon", "Apache License 2.0", "https://github.com/jordond/KMPPalette"),
        LicenseEntry("pdf.js", "Mozilla", "Apache License 2.0", "https://mozilla.github.io/pdf.js/"),
        LicenseEntry("Readability", "Mozilla", "Apache License 2.0", "https://github.com/mozilla/readability"),
        LicenseEntry("Materialistic", "Hidroh", "Apache License 2.0", "https://github.com/hidroh/materialistic"),
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
        ),
        LicenseEntry(
            "LiteRT-LM",
            "Google",
            "Apache License 2.0",
            "https://github.com/google-ai-edge/LiteRT-LM",
        ),
        LicenseEntry(
            "llama.cpp",
            "ggml-org",
            "MIT License",
            "https://github.com/ggml-org/llama.cpp",
        ),
        LicenseEntry("ggml", "ggml-org", "MIT License", "https://github.com/ggml-org/ggml"),
    )
}
