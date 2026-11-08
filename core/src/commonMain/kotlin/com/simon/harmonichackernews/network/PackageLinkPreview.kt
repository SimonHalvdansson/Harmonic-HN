package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.LinkPreviewInfo
import com.simon.harmonichackernews.data.LinkPreviewType
import com.simon.harmonichackernews.serialization.JsonObject
import io.ktor.client.HttpClient
import io.ktor.http.encodeURLPathPart

internal data class PackagePreviewTarget(
    val type: LinkPreviewType,
    val name: String,
    val variant: String? = null,
)


internal object PackageLinkPreview {
    internal fun packageTarget(url: String?): PackagePreviewTarget? {
        return packageTarget(url?.toNetworkUrlOrNull() ?: return null)
    }

    internal fun packageTarget(parsed: NetworkUrl): PackagePreviewTarget? {
        val host = parsed.host.lowercase().removePrefix("www.")
        val segments = parsed.pathSegments.filter(String::isNotEmpty)
        return when {
            host == "npmjs.com" && segments.firstOrNull() == "package" && segments.size >= 2 ->
                PackagePreviewTarget(
                    LinkPreviewType.NPM_PACKAGE,
                    if (segments[1].startsWith('@')) {
                        if (segments.size < 3) return null
                        segments.subList(1, 3).joinToString("/")
                    } else {
                        segments[1]
                    },
                )
            host == "pypi.org" && segments.firstOrNull() == "project" && segments.size >= 2 ->
                PackagePreviewTarget(LinkPreviewType.PYPI_PACKAGE, segments[1])
            host == "crates.io" && segments.firstOrNull() == "crates" && segments.size >= 2 ->
                PackagePreviewTarget(LinkPreviewType.CRATES_PACKAGE, segments[1])
            host == "pkg.go.dev" && segments.isNotEmpty() && segments.first() != "vuln" ->
                PackagePreviewTarget(LinkPreviewType.GO_PACKAGE, segments.joinToString("/"))
            host == "formulae.brew.sh" && segments.size >= 2 &&
                segments[0] in setOf("formula", "cask") -> PackagePreviewTarget(
                    LinkPreviewType.HOMEBREW_PACKAGE,
                    segments[1],
                    variant = segments[0],
                )
            else -> null
        }
    }

    fun parsePackage(
        type: LinkPreviewType,
        response: String,
        target: PackagePreviewTarget,
        url: String,
    ): LinkPreviewInfo = when (type) {
        LinkPreviewType.NPM_PACKAGE -> {
            val json = JsonObject(response)
            val author = json.optJSONObject("author")?.nonBlankString("name")
                ?: json.nonBlankString("author")
            LinkPreviewInfo(
                type,
                json.optString("name", target.name),
                packageSubtitle("npm", json.nonBlankString("version")),
                json.nonBlankString("description"),
                null,
                url,
                details(
                    "License" to json.nonBlankString("license"),
                    "Author" to author,
                    "Dependencies" to json.optJSONObject("dependencies")?.length()?.toString(),
                    "Node" to json.optJSONObject("engines")?.nonBlankString("node"),
                    "Project URL" to (
                        (json.optJSONObject("repository")?.nonBlankString("url")
                            ?: json.nonBlankString("repository"))?.removePrefix("git+")
                            ?.removeSuffix(".git")?.webProjectUrl()
                            ?: json.nonBlankString("homepage")?.webProjectUrl()
                    ),
                ),
            )
        }
        LinkPreviewType.PYPI_PACKAGE -> {
            val info = JsonObject(response).getJSONObject("info")
            LinkPreviewInfo(
                type,
                info.optString("name", target.name),
                packageSubtitle("PyPI", info.nonBlankString("version")),
                info.nonBlankString("summary"),
                null,
                url,
                details(
                    "License" to (
                        info.nonBlankString("license_expression")
                            ?: info.nonBlankString("license")?.take(48)
                    ),
                    "Author" to info.nonBlankString("author"),
                    "Python" to info.nonBlankString("requires_python"),
                    "Project URL" to pypiProjectUrl(info),
                ),
            )
        }
        LinkPreviewType.CRATES_PACKAGE -> {
            val crate = JsonObject(response).getJSONObject("crate")
            LinkPreviewInfo(
                type,
                crate.optString("name", target.name),
                packageSubtitle("crates.io", crate.nonBlankString("newest_version")),
                crate.nonBlankString("description"),
                null,
                url,
                details(
                    "Downloads" to crate.optLong("downloads").toString(),
                    "Recent downloads" to crate.optLong("recent_downloads").toString(),
                    "Updated" to crate.nonBlankString("updated_at")?.dateOnly(),
                    "Project URL" to (crate.nonBlankString("repository")?.webProjectUrl()
                        ?: crate.nonBlankString("homepage")?.webProjectUrl()),
                ),
            )
        }
        LinkPreviewType.GO_PACKAGE -> {
            val json = JsonObject(response)
            LinkPreviewInfo(
                type,
                json.optString("name").requiredPreviewTitle(type),
                json.optString("path", target.name),
                json.nonBlankString("synopsis"),
                null,
                url,
                details(
                    "Version" to json.nonBlankString("version"),
                    "Module" to json.nonBlankString("modulePath"),
                    "Latest" to if (json.optBoolean("isLatest")) "Yes" else "No",
                    "Standard library" to if (json.optBoolean("isStandardLibrary")) "Yes" else "No",
                    "Redistributable" to if (json.optBoolean("isRedistributable")) "Yes" else "No",
                ),
            )
        }
        LinkPreviewType.HOMEBREW_PACKAGE -> {
            val json = JsonObject(response)
            val versions = json.optJSONObject("versions")
            val analytics = json.optJSONObject("analytics")
                ?.optJSONObject("install")?.optJSONObject("30d")
            val names = json.optJSONArray("name")
            val name = json.nonBlankString("name")
                ?: names?.let { (0 until it.length()).firstNotNullOfOrNull { index ->
                    names.optString(index).takeIf(String::isNotBlank)
                } }
                ?: target.name
            LinkPreviewInfo(
                type,
                name,
                if (target.variant == "cask") "Homebrew cask" else "Homebrew formula",
                json.nonBlankString("desc"),
                null,
                url,
                details(
                    "Version" to (versions?.nonBlankString("stable") ?: json.nonBlankString("version")),
                    "License" to json.nonBlankString("license"),
                    "Installs (30d)" to analytics?.optLong(target.name)?.toString(),
                    "Dependencies" to json.optJSONArray("dependencies")?.length()?.toString(),
                    "Project URL" to json.nonBlankString("homepage")?.webProjectUrl(),
                ),
            )
        }
        else -> throw LinkPreviewException("Unsupported package response")
    }

    private fun packageSubtitle(provider: String, version: String?): String =
        listOfNotNull(provider, version).joinToString(" · ")

    private fun String.webProjectUrl(): String? = trim().takeIf { value ->
        (value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true)) &&
            value.toNetworkUrlOrNull()?.host?.isNotBlank() == true
    }

    private fun pypiProjectUrl(info: JsonObject): String? {
        val links = info.optJSONObject("project_urls")
        // PyPI's project_url is the registry page itself. Use the author's project links instead.
        val normalizedLinks = links?.keys()?.asSequence()?.associate { key ->
            key.lowercase().filter(Char::isLetterOrDigit) to links.nonBlankString(key)
        }.orEmpty()
        return sequenceOf("homepage", "source", "sourcecode", "repository", "github", "documentation")
            .mapNotNull { normalizedLinks[it]?.webProjectUrl() }.firstOrNull()
            ?: info.nonBlankString("home_page")?.webProjectUrl()
    }
}

internal suspend fun HttpClient.loadPackagePreview(
    type: LinkPreviewType,
    url: String,
): LinkPreviewInfo {
    val target = PackageLinkPreview.packageTarget(url)?.takeIf { it.type == type }
        ?: throw LinkPreviewException("Invalid ${type.title} URL")
    val response = when (type) {
        LinkPreviewType.NPM_PACKAGE ->
            getTextOrThrow("https://registry.npmjs.org/${target.name.encodeURLPathPart()}/latest")
        LinkPreviewType.PYPI_PACKAGE ->
            getTextOrThrow("https://pypi.org/pypi/${target.name.encodeURLPathPart()}/json")
        LinkPreviewType.CRATES_PACKAGE ->
            getTextOrThrow("https://crates.io/api/v1/crates/${target.name.encodeURLPathPart()}")
        LinkPreviewType.GO_PACKAGE -> getTextOrThrow(
            "https://pkg.go.dev/v1beta/package/${target.name.split('/').joinToString("/") { it.encodeURLPathPart() }}",
        )
        LinkPreviewType.HOMEBREW_PACKAGE -> getTextOrThrow(
            "https://formulae.brew.sh/api/${target.variant}/${target.name.encodeURLPathPart()}.json",
        )
        else -> error("Unexpected package preview type")
    }
    return PackageLinkPreview.parsePackage(type, response, target, url)
}
