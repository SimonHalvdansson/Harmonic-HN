package com.simon.harmonichackernews.ui.content

import androidx.compose.ui.graphics.ImageBitmap
import com.simon.harmonichackernews.palette.HarmonicPalette
import com.simon.harmonichackernews.palette.HarmonicPaletteExtractor
import kotlinx.coroutines.channels.Channel

/** Exclusive, lazily allocated workspaces; results never retain the reusable pixel buffer. */
internal class BitmapPaletteExtractor(maxConcurrentExtractions: Int) {
    init { require(maxConcurrentExtractions > 0) }

    private val workspaces = Channel<Lazy<Workspace>>(
        capacity = maxConcurrentExtractions,
        // A suspended receiver can be cancelled after delivery but before its block resumes.
        onUndeliveredElement = ::releaseWorkspace,
    ).apply {
        repeat(maxConcurrentExtractions) { check(trySend(lazy { Workspace() }).isSuccess) }
    }

    suspend fun extract(sample: ImageBitmap): HarmonicPalette {
        require(sample.width in 1..96 && sample.height in 1..96)
        val slot = workspaces.receive()
        try {
            val workspace = slot.value
            sample.readPixels(workspace.pixels)
            return workspace.extractor.extract(workspace.pixels, sample.width * sample.height)
        } finally {
            // Non-suspending release also works when the caller's coroutine has been cancelled.
            releaseWorkspace(slot)
        }
    }

    private fun releaseWorkspace(slot: Lazy<Workspace>) {
        check(workspaces.trySend(slot).isSuccess)
    }

    private class Workspace {
        val pixels = IntArray(96 * 96)
        val extractor = HarmonicPaletteExtractor(pixels.size)
    }
}
