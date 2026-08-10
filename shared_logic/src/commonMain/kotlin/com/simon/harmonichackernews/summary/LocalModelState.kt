package com.simon.harmonichackernews.summary

import com.simon.harmonichackernews.settings.KeyValueStore
import kotlin.math.roundToLong

enum class LocalModelTransferState {
    NOT_DOWNLOADED,
    PARTIALLY_DOWNLOADED,
    WAITING,
    DOWNLOADING,
    DOWNLOADED,
    FAILED,
}

enum class LocalModelWorkState {
    WAITING,
    RUNNING,
    FAILED,
    FINISHED,
}

data class LocalModelWorkSnapshot(
    val state: LocalModelWorkState,
    val receivedBytes: Long = 0L,
    val error: String = "",
)

data class LocalModelTransferStatus(
    val state: LocalModelTransferState,
    val receivedBytes: Long = 0L,
    val error: String = "",
)

/** Owns persisted model selection and portable download-state interpretation. */
class LocalModelStateStore(
    private val models: List<LocalModelDefinition>,
    private val preferences: KeyValueStore,
    private val selectionKey: String,
    private val defaultModelId: String,
) {
    val selectedModelId: String
        get() {
            val stored = preferences.getString(selectionKey, defaultModelId)
            return models.firstOrNull { it.id == stored }?.id ?: defaultModelId
        }

    fun select(modelId: String?, supported: Boolean, downloaded: Boolean): Boolean {
        val model = models.firstOrNull { it.id == modelId }
            ?: models.firstOrNull { it.id == defaultModelId }
            ?: return false
        if (!supported || (model.downloadable && !downloaded)) return false
        preferences.putString(selectionKey, model.id)
        return true
    }

    fun clearSelection() {
        preferences.putString(selectionKey, null)
    }

    fun resolveStatus(
        modelId: String,
        finalFileBytes: Long?,
        partialFileBytes: Long,
        work: LocalModelWorkSnapshot?,
    ): LocalModelTransferStatus {
        val model = models.firstOrNull { it.id == modelId }
            ?: return LocalModelTransferStatus(LocalModelTransferState.NOT_DOWNLOADED)
        if (!model.downloadable) {
            return LocalModelTransferStatus(LocalModelTransferState.NOT_DOWNLOADED)
        }
        if (finalFileBytes == model.sizeBytes) {
            return LocalModelTransferStatus(
                LocalModelTransferState.DOWNLOADED,
                model.sizeBytes,
            )
        }
        if (work != null) {
            when (work.state) {
                LocalModelWorkState.RUNNING -> return LocalModelTransferStatus(
                    LocalModelTransferState.DOWNLOADING,
                    work.receivedBytes,
                )
                LocalModelWorkState.WAITING -> return LocalModelTransferStatus(
                    LocalModelTransferState.WAITING,
                    work.receivedBytes,
                )
                LocalModelWorkState.FAILED -> return LocalModelTransferStatus(
                    LocalModelTransferState.FAILED,
                    work.receivedBytes,
                    work.error.ifEmpty { "Model download failed" },
                )
                LocalModelWorkState.FINISHED -> Unit
            }
        }
        if (partialFileBytes > 0L) {
            return LocalModelTransferStatus(
                LocalModelTransferState.PARTIALLY_DOWNLOADED,
                partialFileBytes,
            )
        }
        return LocalModelTransferStatus(LocalModelTransferState.NOT_DOWNLOADED)
    }
}

fun localModelProgressPercent(receivedBytes: Long, expectedBytes: Long): Int {
    if (expectedBytes <= 0L) return 0
    return ((receivedBytes.toDouble() / expectedBytes.toDouble()) * 100.0)
        .toInt()
        .coerceIn(0, 100)
}

fun formatDecimalBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> formatFixed(bytes / 1_000_000_000.0, 2) + " GB"
    bytes >= 1_000_000L -> formatFixed(bytes / 1_000_000.0, 1) + " MB"
    else -> formatFixed(bytes / 1_000.0, 1) + " kB"
}

private fun formatFixed(value: Double, decimals: Int): String {
    val factor = when (decimals) {
        2 -> 100L
        1 -> 10L
        else -> 1L
    }
    val rounded = (value * factor).roundToLong()
    if (decimals == 0) return rounded.toString()
    val whole = rounded / factor
    val fraction = (rounded % factor).toString().padStart(decimals, '0')
    return "$whole.$fraction"
}
