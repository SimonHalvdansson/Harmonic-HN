package com.simon.harmonichackernews.summary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalModelPresentationTest {
    private val model = LocalModelCatalog.models.first { it.downloadable }

    @Test
    fun nanoStaysDisabledUntilAvailabilityIsResolved() {
        assertEquals("Gemini Nano", LocalModelCatalog.models.first().displayName)
        val unresolved = presentNano(resolved = false, available = false)
        val unavailable = presentNano(resolved = true, available = false)
        val available = presentNano(resolved = true, available = true)

        assertEquals("Checking availability…", unresolved.summary)
        assertFalse(unresolved.enabled)
        assertFalse(unresolved.selectable)
        assertEquals("Not available", unavailable.summary)
        assertFalse(unavailable.enabled)
        assertFalse(unavailable.selectable)
        assertEquals("Available · system managed", available.summary)
        assertTrue(available.enabled)
        assertTrue(available.selectable)
        assertNull(available.action)
    }

    @Test
    fun completedModelCanBeSelectedAndDeleted() {
        val result = present(
            transfer = LocalModelTransferStatus(LocalModelTransferState.DOWNLOADED),
            runtimeInstalled = true,
            selected = true,
        )

        assertTrue(result.selectable)
        assertTrue(result.selected)
        assertEquals(LocalModelPresentationAction.DELETE_MODEL, result.action)
        assertTrue(result.summary.endsWith("\nDownloaded"))
    }

    @Test
    fun activeRuntimeInstallWinsOverTheModelTransferState() {
        val result = present(
            transfer = LocalModelTransferStatus(LocalModelTransferState.NOT_DOWNLOADED),
            runtime = LocalRuntimeInstallStatus(
                state = LocalRuntimeInstallState.DOWNLOADING,
                pendingModelId = model.id,
                downloadedBytes = 25L,
                totalBytes = 100L,
            ),
        )

        assertEquals(LocalModelPresentationAction.CANCEL_DOWNLOAD, result.action)
        assertEquals(0.25f, result.progress)
        assertTrue(result.summary.endsWith("Installing runtime · 25%"))
    }

    @Test
    fun transferProgressAndPartialResumeTextArePortable() {
        val downloading = present(
            LocalModelTransferStatus(
                LocalModelTransferState.DOWNLOADING,
                receivedBytes = model.sizeBytes / 2,
            ),
        )
        val partial = present(
            LocalModelTransferStatus(
                LocalModelTransferState.PARTIALLY_DOWNLOADED,
                receivedBytes = 1_500_000L,
            ),
        )

        assertEquals(LocalModelPresentationAction.CANCEL_DOWNLOAD, downloading.action)
        assertEquals(0.5f, downloading.progress)
        assertTrue(partial.summary.endsWith("1.5 MB downloaded · tap to resume"))
    }

    @Test
    fun unsupportedModelExposesReasonWithoutBeingSelectable() {
        val result = present(
            transfer = LocalModelTransferStatus(LocalModelTransferState.NOT_DOWNLOADED),
            supported = false,
            unsupportedReason = "Requires a 64-bit device",
        )

        assertEquals("Requires a 64-bit device", result.summary)
        assertFalse(result.enabled)
        assertFalse(result.selectable)
    }

    private fun presentNano(resolved: Boolean, available: Boolean): LocalModelPresentation =
        LocalModelPresentationPolicy.present(
            LocalModelPresentationInput(
                model = LocalModelCatalog.models.first(),
                supported = true,
                selected = true,
                nanoAvailabilityResolved = resolved,
                nanoAvailable = available,
                transferStatus = LocalModelTransferStatus(
                    LocalModelTransferState.NOT_DOWNLOADED,
                ),
                runtimeStatus = LocalRuntimeInstallStatus(
                    LocalRuntimeInstallState.INSTALLED,
                ),
                runtimeInstalled = true,
            ),
        )

    private fun present(
        transfer: LocalModelTransferStatus,
        runtime: LocalRuntimeInstallStatus = LocalRuntimeInstallStatus(
            LocalRuntimeInstallState.INSTALLED,
        ),
        runtimeInstalled: Boolean = false,
        selected: Boolean = false,
        supported: Boolean = true,
        unsupportedReason: String = "",
    ): LocalModelPresentation = LocalModelPresentationPolicy.present(
        LocalModelPresentationInput(
            model = model,
            supported = supported,
            unsupportedReason = unsupportedReason,
            selected = selected,
            nanoAvailabilityResolved = true,
            nanoAvailable = false,
            transferStatus = transfer,
            runtimeStatus = runtime,
            runtimeInstalled = runtimeInstalled,
        ),
    )
}
