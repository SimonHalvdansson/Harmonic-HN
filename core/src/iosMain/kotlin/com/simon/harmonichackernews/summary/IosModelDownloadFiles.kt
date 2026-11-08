package com.simon.harmonichackernews.summary

import com.simon.harmonichackernews.network.FileResumableDownloadDestination
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/** Swift's background-session delegate calls this only on its serial file/transfer worker. */
class IosModelDownloadFiles(rootDirectory: String) {
    private val root = Path(rootDirectory)

    fun resumeDataPath(model: LocalModelDefinition): String =
        LocalModelFilePolicy.resumeDataPath(root, model).toString()

    fun partialBytes(model: LocalModelDefinition): Long = runCatching {
        SystemFileSystem.metadataOrNull(LocalModelFilePolicy.partialPath(root, model))?.size ?: 0L
    }.getOrDefault(0L)

    fun discardCompleted(model: LocalModelDefinition): Boolean = runCatching {
        SystemFileSystem.delete(LocalModelFilePolicy.completedPath(root, model), mustExist = false)
    }.isSuccess

    fun install(model: LocalModelDefinition, temporaryPath: String, appendOffset: Long): String? =
        runCatching {
            runBlocking {
                FileResumableDownloadDestination(
                    LocalModelFilePolicy.completedPath(root, model),
                    LocalModelFilePolicy.partialPath(root, model),
                ).importDownloadedFile(Path(temporaryPath), model.sizeBytes, appendOffset)
            }
        }.exceptionOrNull()?.let { it.message ?: "Could not install the downloaded model" }
}
