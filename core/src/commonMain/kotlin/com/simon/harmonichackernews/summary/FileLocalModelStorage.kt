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
    private val models: List<LocalModelDefinition> = LocalModelCatalog.models,
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

    override fun storedBytes(): Long = runCatching {
        modelDirectories().sumOf(::treeBytes) + inferenceCacheFiles().sumOf(::treeBytes)
    }.getOrDefault(0L)

    override fun clearStoredModels(): Boolean = runCatching {
        modelDirectories().forEach(::deleteTree)
        inferenceCacheFiles().forEach(::deleteTree)
        true
    }.getOrDefault(false)

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

    private fun modelDirectories(): List<Path> = models
        .asSequence()
        .filter(LocalModelDefinition::downloadable)
        .map { LocalModelFilePolicy.modelDirectory(root, it.id) }
        .distinct()
        .toList()

    private fun inferenceCacheFiles(): List<Path> {
        val cacheRoot = inferenceCacheRoot ?: return emptyList()
        if (fileSystem.metadataOrNull(cacheRoot)?.isDirectory != true) return emptyList()
        val prefixes = models
            .filter(LocalModelDefinition::downloadable)
            .flatMap(LocalModelFilePolicy::inferenceCachePrefixes)
            .distinct()
        return fileSystem.list(cacheRoot).filter { path ->
            fileSystem.metadataOrNull(path)?.isRegularFile == true &&
                prefixes.any(path.name::startsWith)
        }
    }

    private fun treeBytes(path: Path): Long {
        val metadata = fileSystem.metadataOrNull(path) ?: return 0L
        return when {
            metadata.isRegularFile -> metadata.size
            metadata.isDirectory -> fileSystem.list(path).sumOf(::treeBytes)
            else -> 0L
        }
    }

    private fun deleteTree(path: Path) {
        val metadata = fileSystem.metadataOrNull(path) ?: return
        if (metadata.isDirectory) fileSystem.list(path).forEach(::deleteTree)
        fileSystem.delete(path, mustExist = false)
    }
}
