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
        val parsed = url?.toNetworkUrlOrNull() ?: return null
        val host = parsed.host.lowercase().removePrefix("www.")
        val segments = parsed.pathSegments.filter(String::isNotEmpty)
        return when {
            host == "npmjs.com" && segments.firstOrNull() == "package" && segments.size >= 2 ->
                PackagePreviewTarget(
                    LinkPreviewType.NPM_PACKAGE,
                    segments.drop(1).take(2).joinToString("/"),
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
                "npm · ${json.optString("version")}",
                json.nonBlankString("description"),
                null,
                url,
                details(
                    "Version" to json.nonBlankString("version"),
                    "License" to json.nonBlankString("license"),
                    "Author" to author,
                    "Dependencies" to json.optJSONObject("dependencies")?.length()?.toString(),
                    "Node" to json.optJSONObject("engines")?.nonBlankString("node"),
                ),
            )
        }
        LinkPreviewType.PYPI_PACKAGE -> {
            val info = JsonObject(response).getJSONObject("info")
            LinkPreviewInfo(
                type,
                info.optString("name", target.name),
                "PyPI · ${info.optString("version")}",
                info.nonBlankString("summary"),
                null,
                url,
                details(
                    "Version" to info.nonBlankString("version"),
                    "License" to (
                        info.nonBlankString("license_expression")
                            ?: info.nonBlankString("license")?.take(48)
                    ),
                    "Author" to info.nonBlankString("author"),
                    "Python" to info.nonBlankString("requires_python"),
                    "Project URL" to info.nonBlankString("project_url"),
                ),
            )
        }
        LinkPreviewType.CRATES_PACKAGE -> {
            val crate = JsonObject(response).getJSONObject("crate")
            LinkPreviewInfo(
                type,
                crate.optString("name", target.name),
                "crates.io · ${crate.optString("newest_version")}",
                crate.nonBlankString("description"),
                null,
                url,
                details(
                    "Version" to crate.nonBlankString("newest_version"),
                    "Downloads" to crate.optLong("downloads").toString(),
                    "Recent downloads" to crate.optLong("recent_downloads").toString(),
                    "Updated" to crate.nonBlankString("updated_at")?.dateOnly(),
                    "Repository" to crate.nonBlankString("repository")?.shortHost(),
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
            val name = json.optString("name", target.name)
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
                    "Installs (30d)" to analytics?.optLong(name)?.toString(),
                    "Dependencies" to json.optJSONArray("dependencies")?.length()?.toString(),
                    "Homepage" to json.nonBlankString("homepage")?.shortHost(),
                ),
            )
        }
        else -> throw LinkPreviewException("Unsupported package response")
    }

    private fun String.shortHost(): String? = toNetworkUrlOrNull()?.host?.removePrefix("www.")
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
