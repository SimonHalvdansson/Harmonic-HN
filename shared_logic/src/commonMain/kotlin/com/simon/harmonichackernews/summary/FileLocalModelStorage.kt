package com.simon.harmonichackernews.summary

import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/** Canonical model paths and runtime-cache cleanup rules used by every platform host. */
object LocalModelFilePolicy {
    const val PARTIAL_FILE_SUFFIX = ".download"

    fun modelDirectory(root: Path, modelId: String): Path = Path(root, modelId)

    fun completedPath(root: Path, modelId: String, fileName: String): Path =
        Path(modelDirectory(root, modelId), fileName)

    fun completedPath(root: Path, model: LocalModelDefinition): Path =
        completedPath(root, model.id, model.fileName)

    fun partialPath(root: Path, modelId: String, fileName: String): Path =
        Path(modelDirectory(root, modelId), fileName + PARTIAL_FILE_SUFFIX)

    fun partialPath(root: Path, model: LocalModelDefinition): Path =
        partialPath(root, model.id, model.fileName)

    fun inferenceCachePrefixes(model: LocalModelDefinition): List<String> = buildList {
        add("${model.fileName}.xnnpack_cache_")
        when (model.id) {
            LocalModelCatalog.MODEL_E2B -> add("gemma-4-E2B-it.litertlm.xnnpack_cache_")
            LocalModelCatalog.MODEL_QWEN_08B ->
                add("Qwen3.5-0.8B-hybrid-exact-c2048.litertlm.xnnpack_cache_")
        }
    }
}

/**
 * Multiplatform filesystem implementation for downloaded local models.
 *
 * Hosts supply an app-owned root and their native free-space reading. Model placement, resumable
 * partials, obsolete-file cleanup and inference-cache cleanup remain shared.
 */
class FileLocalModelStorage(
    private val root: Path,
    private val usableSpaceBytes: () -> Long,
    private val inferenceCacheRoot: Path? = null,
    private val fileSystem: FileSystem = SystemFileSystem,
) : LocalModelStorage {
    override fun snapshot(model: LocalModelDefinition): LocalModelStorageSnapshot {
        val finalMetadata = fileSystem.metadataOrNull(LocalModelFilePolicy.completedPath(root, model))
        val partialMetadata = fileSystem.metadataOrNull(LocalModelFilePolicy.partialPath(root, model))
        return LocalModelStorageSnapshot(
            finalFileBytes = finalMetadata?.takeIf { it.isRegularFile }?.size,
            partialFileBytes = partialMetadata?.takeIf { it.isRegularFile }?.size ?: 0L,
            usableSpaceBytes = usableSpaceBytes(),
        )
    }

    override fun prepareDownload(model: LocalModelDefinition): LocalModelStoragePreparation =
        runCatching {
            deleteObsoleteModelFiles(model)
            deleteInferenceCacheFiles(model)
            fileSystem.delete(LocalModelFilePolicy.completedPath(root, model), mustExist = false)
            fileSystem.createDirectories(root)
            check(fileSystem.metadataOrNull(root)?.isDirectory == true)
            LocalModelStoragePreparation.Ready(snapshot(model))
        }.getOrElse {
            LocalModelStoragePreparation.Failed("Could not create or update local model storage.")
        }

    override fun remove(model: LocalModelDefinition, includeFinalFile: Boolean) {
        runCatching {
            val directory = LocalModelFilePolicy.modelDirectory(root, model.id)
            fileSystem.delete(LocalModelFilePolicy.partialPath(root, model), mustExist = false)
            if (includeFinalFile) {
                fileSystem.delete(LocalModelFilePolicy.completedPath(root, model), mustExist = false)
                deleteInferenceCacheFiles(model)
                if (fileSystem.metadataOrNull(directory)?.isDirectory == true) {
                    fileSystem.list(directory).forEach { path ->
                        if (fileSystem.metadataOrNull(path)?.isRegularFile == true) {
                            fileSystem.delete(path, mustExist = false)
                        }
                    }
                }
            }
            if (fileSystem.metadataOrNull(directory)?.isDirectory == true &&
                fileSystem.list(directory).isEmpty()
            ) {
                fileSystem.delete(directory, mustExist = false)
            }
        }
    }

    override fun installedPath(model: LocalModelDefinition): String =
        LocalModelFilePolicy.completedPath(root, model).toString()

    private fun deleteObsoleteModelFiles(model: LocalModelDefinition) {
        val directory = LocalModelFilePolicy.modelDirectory(root, model.id)
        if (fileSystem.metadataOrNull(directory)?.isDirectory != true) return
        val completed = LocalModelFilePolicy.completedPath(root, model)
        val partial = LocalModelFilePolicy.partialPath(root, model)
        fileSystem.list(directory).forEach { path ->
            if (path != completed && path != partial &&
                fileSystem.metadataOrNull(path)?.isRegularFile == true
            ) {
                fileSystem.delete(path, mustExist = false)
            }
        }
    }

    private fun deleteInferenceCacheFiles(model: LocalModelDefinition) {
        val cacheRoot = inferenceCacheRoot ?: return
        if (fileSystem.metadataOrNull(cacheRoot)?.isDirectory != true) return
        val prefixes = LocalModelFilePolicy.inferenceCachePrefixes(model)
        fileSystem.list(cacheRoot).forEach { path ->
            if (fileSystem.metadataOrNull(path)?.isRegularFile == true &&
                prefixes.any(path.name::startsWith)
            ) {
                fileSystem.delete(path, mustExist = false)
            }
        }
    }
}
