package com.simon.harmonichackernews.summary

import kotlinx.io.Source
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable

@Serializable
data class ExternalModelFile(
    val location: String,
    val name: String,
    val sizeBytes: Long,
    val displayPath: String = location,
) {
    fun definition() = LocalModelDefinition(
        id = ID, displayName = name, parameterSize = "", quantization = "",
        brand = LocalModelBrand.SYSTEM, fileName = name, url = "", sizeBytes = sizeBytes,
        downloadable = false, runtime = LocalModelRuntime.LITERT_LM, contextTokens = 2048,
        externalFile = this,
    )

    companion object {
        const val ID = "external-litert-lm"

        fun inspect(location: String, name: String, sizeBytes: Long, source: Source): ExternalModelFile {
            require(name.endsWith(".litertlm", ignoreCase = true)) { "Choose a .litertlm model file." }
            val valid = sizeBytes > 8 && source.request(8) && source.readByteArray(8).decodeToString() == "LITERTLM"
            require(valid) { "This file does not contain a LiteRT-LM model." }
            return ExternalModelFile(location, name, sizeBytes)
        }
    }
}

/** User-owned files are opened read-only and never enter the download or storage cleanup paths. */
interface ExternalModelAccess {
    fun inspect(location: String): ExternalModelFile
    fun retain(location: String) = Unit
    fun release(location: String) = Unit
    suspend fun <T> withPath(file: ExternalModelFile, use: suspend (String) -> T): T
}
