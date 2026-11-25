package com.simon.harmonichackernews.summary

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.io.asSource
import kotlinx.io.buffered

internal object DesktopExternalModelAccess : ExternalModelAccess {
    override fun inspect(location: String): ExternalModelFile {
        val path = Path.of(location)
        require(path.isAbsolute && Files.isRegularFile(path) && Files.isReadable(path)) {
            "Could not read the model file. Choose it again."
        }
        return Files.newInputStream(path).asSource().buffered().use { source ->
            ExternalModelFile.inspect(location, path.fileName.toString(), Files.size(path), source)
        }
    }

    override suspend fun <T> withPath(file: ExternalModelFile, use: suspend (String) -> T): T {
        inspect(file.location)
        return use(file.location)
    }
}
