package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.HuggingFaceModelInfo
import com.simon.harmonichackernews.data.LinkPreviewInfo
import com.simon.harmonichackernews.data.LinkPreviewType
import com.simon.harmonichackernews.serialization.JsonArray
import com.simon.harmonichackernews.serialization.JsonObject
import io.ktor.client.HttpClient
import io.ktor.http.encodeURLPathPart

data class HuggingFaceModel(val owner: String, val name: String)

internal data class HuggingFacePreviewTarget(
    val type: LinkPreviewType,
    val owner: String? = null,
    val name: String,
)

internal object HuggingFaceLinkPreview {
    private val quantizationTagPattern = Regex("\\d+-bit")

    fun isHuggingFaceUrl(url: String?): Boolean = huggingFaceModel(url) != null

    fun huggingFaceModel(url: String?): HuggingFaceModel? {
        val parsed = url?.toNetworkUrlOrNull() ?: return null
        if (parsed.host.lowercase().removePrefix("www.") != "huggingface.co") return null
        val segments = parsed.pathSegments.filter(String::isNotEmpty)
        if (segments.size < 2 || segments.first().lowercase() in huggingFaceReservedPaths) return null
        return HuggingFaceModel(segments[0], segments[1])
    }

    private val huggingFaceReservedPaths = setOf(
        "blog", "chat", "collections", "datasets", "docs", "enterprise", "join", "learn",
        "login", "models", "organizations", "papers", "pricing", "settings", "spaces", "tasks",
    )

    internal fun huggingFaceTarget(url: String?): HuggingFacePreviewTarget? {
        val parsed = url?.toNetworkUrlOrNull() ?: return null
        if (parsed.host.lowercase().removePrefix("www.") != "huggingface.co") return null
        val segments = parsed.pathSegments.filter(String::isNotEmpty)
        return when {
            segments.size >= 3 && segments[0] == "datasets" -> HuggingFacePreviewTarget(
                LinkPreviewType.HUGGING_FACE_DATASET,
                segments[1],
                segments[2],
            )
            segments.size >= 3 && segments[0] == "spaces" -> HuggingFacePreviewTarget(
                LinkPreviewType.HUGGING_FACE_SPACE,
                segments[1],
                segments[2],
            )
            segments.size >= 2 && segments[0] == "papers" -> HuggingFacePreviewTarget(
                LinkPreviewType.HUGGING_FACE_PAPER,
                name = segments[1],
            )
            segments.size >= 3 && segments[0] == "collections" -> HuggingFacePreviewTarget(
                LinkPreviewType.HUGGING_FACE_COLLECTION,
                segments[1],
                segments[2],
            )
            HuggingFaceLinkPreview.huggingFaceModel(url) != null -> HuggingFacePreviewTarget(
                LinkPreviewType.HUGGING_FACE_MODEL,
                segments[0],
                segments[1],
            )
            else -> null
        }
    }

    fun parseHuggingFace(response: String): HuggingFaceModelInfo {
        val json = JsonObject(response)
        val id = json.optString("id")
        val idParts = id.split('/')
        if (idParts.size != 2 || idParts.any(String::isBlank)) {
            throw LinkPreviewException("Hugging Face model data not found")
        }
        val tags = json.optJSONArray("tags")?.let { values ->
            (0..<values.length()).mapNotNull { index ->
                values.optString(index).takeUnless(String::isBlank)
            }
        }.orEmpty()
        val cardData = json.optJSONObject("cardData")
        val logoPath = selectHuggingFaceLogoPath(json)
        return HuggingFaceModelInfo(
            author = json.optString("author", idParts[0]),
            name = idParts[1],
            website = "https://huggingface.co/$id",
            logoUrl = logoPath?.let { path ->
                "https://huggingface.co/" +
                    idParts.joinToString("/") { it.encodeURLPathPart() } +
                    "/resolve/main/" +
                    path.split('/').joinToString("/") { it.encodeURLPathPart() }
            },
            pipelineTag = json.optString<String?>("pipeline_tag", null),
            libraryName = json.optString<String?>("library_name", null),
            quantization = tags.firstOrNull { quantizationTagPattern.matches(it) },
            licenseName = cardData?.optString<String?>("license_name", null)
                ?: cardData?.optString<String?>("license", null),
            lastModified = json.optString<String?>("lastModified", null),
            likes = json.optLong("likes"),
            downloads = json.optLong("downloads"),
            parameterCount = json.optJSONObject("safetensors")?.optLong("total") ?: 0,
        )
    }

    private fun selectHuggingFaceLogoPath(json: JsonObject): String? {
        val candidates = json.optJSONArray("siblings")?.let { siblings ->
            (0..<siblings.length()).mapNotNull { index ->
                siblings.optJSONObject(index)
                    ?.optString("rfilename")
                    ?.takeUnless(String::isBlank)
                    ?.takeIf(::isSupportedPreviewImage)
            }
        }.orEmpty()
        return candidates.minWithOrNull(
            compareBy<String>({ huggingFaceImagePriority(it) }, String::length),
        )
    }

    private fun isSupportedPreviewImage(path: String): Boolean =
        path.substringAfterLast('.', missingDelimiterValue = "").lowercase() in
            setOf("png", "webp", "jpg", "jpeg")

    private fun huggingFaceImagePriority(path: String): Int {
        val normalized = path.lowercase()
        val filename = normalized.substringAfterLast('/')
        return when {
            "logo" in filename -> 0
            "icon" in filename || "avatar" in filename -> 1
            normalized.startsWith("assets/") || normalized.startsWith("images/") -> 2
            else -> 3
        }
    }

    fun parseHuggingFace(
        type: LinkPreviewType,
        response: String,
        target: HuggingFacePreviewTarget,
        url: String,
    ): LinkPreviewInfo {
        val json = JsonObject(response)
        val id = json.nonBlankString("id") ?: listOfNotNull(target.owner, target.name).joinToString("/")
        return when (type) {
            LinkPreviewType.HUGGING_FACE_DATASET -> LinkPreviewInfo(
                type,
                id.substringAfterLast('/'),
                id.substringBeforeLast('/', missingDelimiterValue = target.owner.orEmpty()),
                json.nonBlankString("description"),
                null,
                url,
                details(
                    "Downloads" to json.optLong("downloads").toString(),
                    "Likes" to json.optLong("likes").toString(),
                    "Updated" to json.nonBlankString("lastModified")?.dateOnly(),
                    "Format" to tagValue(json, "format:"),
                    "Size" to tagValue(json, "size_categories:"),
                    "Access" to if (json.optBoolean("gated")) "Gated" else "Public",
                ),
            )
            LinkPreviewType.HUGGING_FACE_SPACE -> {
                val card = json.optJSONObject("cardData")
                val runtime = json.optJSONObject("runtime")
                LinkPreviewInfo(
                    type,
                    card?.nonBlankString("title") ?: id.substringAfterLast('/'),
                    id,
                    card?.nonBlankString("short_description"),
                    null,
                    url,
                    details(
                        "SDK" to (json.nonBlankString("sdk") ?: card?.nonBlankString("sdk")),
                        "Status" to runtime?.nonBlankString("stage")?.titleCase(),
                        "Hardware" to runtime?.optJSONObject("hardware")?.nonBlankString("current"),
                        "Likes" to json.optLong("likes").toString(),
                        "License" to card?.nonBlankString("license"),
                        "Updated" to json.nonBlankString("lastModified")?.dateOnly(),
                    ),
                )
            }
            LinkPreviewType.HUGGING_FACE_PAPER -> {
                val authors = json.optJSONArray("authors").objectStrings("name")
                LinkPreviewInfo(
                    type,
                    json.optString("title").requiredPreviewTitle(type),
                    "Paper ${json.optString("id", target.name)}",
                    json.nonBlankString("summary"),
                    null,
                    url,
                    details(
                        "Authors" to authors.take(3).joinToString(", ").takeIf(String::isNotEmpty),
                        "Upvotes" to json.optLong("upvotes").toString(),
                        "GitHub stars" to json.optLong("githubStars").takeIf { it > 0 }?.toString(),
                        "Published" to json.nonBlankString("publishedAt")?.dateOnly(),
                    ),
                )
            }
            LinkPreviewType.HUGGING_FACE_COLLECTION -> {
                val owner = json.optJSONObject("owner")
                LinkPreviewInfo(
                    type,
                    json.optString("title").requiredPreviewTitle(type),
                    owner?.nonBlankString("fullname") ?: target.owner,
                    json.nonBlankString("description"),
                    owner?.nonBlankString("avatarUrl"),
                    url,
                    details(
                        "Items" to json.optJSONArray("items")?.length()?.toString(),
                        "Followers" to owner?.optLong("followerCount")?.toString(),
                        "Updated" to json.nonBlankString("lastUpdated")?.dateOnly(),
                    ),
                )
            }
            else -> throw LinkPreviewException("Unsupported Hugging Face preview response")
        }
    }

    private fun tagValue(json: JsonObject, prefix: String): String? = json.optJSONArray("tags")
        ?.let { tags ->
            (0..<tags.length()).map(tags::optString).firstOrNull { it.startsWith(prefix) }
        }
        ?.removePrefix(prefix)

    private fun JsonArray?.objectStrings(key: String): List<String> = this?.let { values ->
        (0..<values.length()).mapNotNull { values.optJSONObject(it)?.nonBlankString(key) }
    }.orEmpty()

    private fun JsonObject.nullableString(key: String): String? =
        (opt(key) as? String)?.takeUnless(String::isEmpty)
}

internal suspend fun HttpClient.loadHuggingFaceInfo(url: String): HuggingFaceModelInfo {
    val model = HuggingFaceLinkPreview.huggingFaceModel(url)
        ?: throw LinkPreviewException("Invalid Hugging Face model URL")
    val endpoint = "https://huggingface.co/api/models/" +
        model.owner.encodeURLPathPart() + "/" + model.name.encodeURLPathPart()
    return HuggingFaceLinkPreview.parseHuggingFace(getTextOrThrow(endpoint))
}

internal suspend fun HttpClient.loadHuggingFacePreview(
    type: LinkPreviewType,
    url: String,
): LinkPreviewInfo {
    val target = HuggingFaceLinkPreview.huggingFaceTarget(url)?.takeIf { it.type == type }
        ?: throw LinkPreviewException("Invalid ${type.title} URL")
    val endpoint = when (type) {
        LinkPreviewType.HUGGING_FACE_DATASET ->
            "https://huggingface.co/api/datasets/${apiPath(target.owner.orEmpty(), target.name)}"
        LinkPreviewType.HUGGING_FACE_SPACE ->
            "https://huggingface.co/api/spaces/${apiPath(target.owner.orEmpty(), target.name)}"
        LinkPreviewType.HUGGING_FACE_PAPER ->
            "https://huggingface.co/api/papers/${target.name.encodeURLPathPart()}"
        LinkPreviewType.HUGGING_FACE_COLLECTION ->
            "https://huggingface.co/api/collections/${apiPath(target.owner.orEmpty(), target.name)}"
        else -> error("Unexpected Hugging Face preview type")
    }
    return HuggingFaceLinkPreview.parseHuggingFace(type, getTextOrThrow(endpoint), target, url)
}
