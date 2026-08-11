package com.simon.harmonichackernews.summary

enum class LocalRuntimeInstallState {
    NOT_INSTALLED,
    PENDING,
    DOWNLOADING,
    INSTALLING,
    INSTALLED,
    FAILED,
    CANCELED,
}

data class LocalRuntimeInstallStatus(
    val state: LocalRuntimeInstallState,
    val pendingModelId: String = "",
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
) {
    val active: Boolean
        get() = state == LocalRuntimeInstallState.PENDING ||
            state == LocalRuntimeInstallState.DOWNLOADING ||
            state == LocalRuntimeInstallState.INSTALLING
}

enum class LocalModelPresentationAction {
    CANCEL_DOWNLOAD,
    DELETE_MODEL,
    DOWNLOAD_MODEL,
}

data class LocalModelPresentationInput(
    val model: LocalModelDefinition,
    val supported: Boolean,
    val unsupportedReason: String = "",
    val selected: Boolean,
    val nanoAvailabilityResolved: Boolean,
    val nanoAvailable: Boolean,
    val transferStatus: LocalModelTransferStatus,
    val runtimeStatus: LocalRuntimeInstallStatus,
    val runtimeInstalled: Boolean,
)

data class LocalModelPresentation(
    val summary: String,
    val enabled: Boolean,
    val selectable: Boolean,
    val selected: Boolean,
    val action: LocalModelPresentationAction?,
    val progress: Float?,
)

/** Portable labels, actions and progress for the local-model settings list. */
object LocalModelPresentationPolicy {
    fun present(input: LocalModelPresentationInput): LocalModelPresentation {
        val model = input.model
        if (model.id == LocalModelCatalog.MODEL_GEMINI_NANO) {
            val selectable = input.nanoAvailabilityResolved && input.nanoAvailable
            return LocalModelPresentation(
                summary = when {
                    !input.nanoAvailabilityResolved -> "Checking availability…"
                    input.nanoAvailable -> "Available · system managed"
                    else -> "Not available"
                },
                enabled = input.supported && input.nanoAvailabilityResolved,
                selectable = selectable,
                selected = selectable && input.selected,
                action = null,
                progress = null,
            )
        }

        if (!input.supported) {
            return LocalModelPresentation(
                summary = input.unsupportedReason,
                enabled = false,
                selectable = false,
                selected = false,
                action = LocalModelPresentationAction.DOWNLOAD_MODEL,
                progress = null,
            )
        }

        val runtimeActiveForModel = input.runtimeStatus.active &&
            input.runtimeStatus.pendingModelId == model.id
        val transferActive = input.transferStatus.state == LocalModelTransferState.DOWNLOADING ||
            input.transferStatus.state == LocalModelTransferState.WAITING
        val downloaded = input.transferStatus.state == LocalModelTransferState.DOWNLOADED
        val selectable = downloaded && input.runtimeInstalled

        return LocalModelPresentation(
            summary = "${modelDescription(model)}\n${modelStateSummary(input, runtimeActiveForModel)}",
            enabled = true,
            selectable = selectable,
            selected = selectable && input.selected,
            action = when {
                runtimeActiveForModel || transferActive ->
                    LocalModelPresentationAction.CANCEL_DOWNLOAD
                downloaded && input.runtimeInstalled ->
                    LocalModelPresentationAction.DELETE_MODEL
                else -> LocalModelPresentationAction.DOWNLOAD_MODEL
            },
            progress = when {
                runtimeActiveForModel && input.runtimeStatus.totalBytes > 0L ->
                    progressFraction(
                        input.runtimeStatus.downloadedBytes,
                        input.runtimeStatus.totalBytes,
                    )
                input.transferStatus.state == LocalModelTransferState.DOWNLOADING ->
                    progressFraction(input.transferStatus.receivedBytes, model.sizeBytes)
                else -> null
            },
        )
    }

    private fun modelDescription(model: LocalModelDefinition): String = listOf(
        model.parameterSize,
        model.quantization,
        formatDecimalBytes(model.sizeBytes),
        when (model.runtime) {
            LocalModelRuntime.LITERT_LM -> "LiteRT-LM"
            LocalModelRuntime.LLAMA_CPP -> "llama.cpp"
            LocalModelRuntime.GEMINI_NANO -> "Gemini Nano"
        },
    ).filter(String::isNotBlank).joinToString(" · ")

    private fun modelStateSummary(
        input: LocalModelPresentationInput,
        runtimeActiveForModel: Boolean,
    ): String = when {
        runtimeActiveForModel -> {
            if (
                input.runtimeStatus.state == LocalRuntimeInstallState.DOWNLOADING &&
                input.runtimeStatus.totalBytes > 0L
            ) {
                "Installing runtime · ${localModelProgressPercent(
                    input.runtimeStatus.downloadedBytes,
                    input.runtimeStatus.totalBytes,
                )}%"
            } else {
                "Preparing local AI runtime…"
            }
        }
        input.transferStatus.state == LocalModelTransferState.DOWNLOADING ->
            "${localModelProgressPercent(
                input.transferStatus.receivedBytes,
                input.model.sizeBytes,
            )}% downloaded"
        input.transferStatus.state == LocalModelTransferState.WAITING ->
            "Waiting for a network connection…"
        input.transferStatus.state == LocalModelTransferState.PARTIALLY_DOWNLOADED ->
            "${formatDecimalBytes(input.transferStatus.receivedBytes)} downloaded · tap to resume"
        input.transferStatus.state == LocalModelTransferState.FAILED ->
            input.transferStatus.error.ifBlank { "Download failed · tap to retry" }
        input.transferStatus.state == LocalModelTransferState.DOWNLOADED &&
            !input.runtimeInstalled -> "local AI runtime required"
        input.transferStatus.state == LocalModelTransferState.DOWNLOADED -> "Downloaded"
        else -> "Not downloaded"
    }

    private fun progressFraction(receivedBytes: Long, expectedBytes: Long): Float =
        localModelProgressPercent(receivedBytes, expectedBytes) / 100f
}
