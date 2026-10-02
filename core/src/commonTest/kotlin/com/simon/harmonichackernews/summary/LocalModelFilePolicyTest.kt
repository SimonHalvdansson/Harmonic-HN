package com.simon.harmonichackernews.summary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.io.files.Path

class LocalModelFilePolicyTest {
    @Test
    fun completedAndPartialFilesShareTheCanonicalModelDirectory() {
        val root = Path("models")
        val model = checkNotNull(
            LocalModelCatalog.models.firstOrNull { it.id == LocalModelCatalog.MODEL_BONSAI_17B },
        )

        assertEquals(model.fileName, LocalModelFilePolicy.completedPath(root, model).name)
        assertEquals(
            model.fileName + LocalModelFilePolicy.PARTIAL_FILE_SUFFIX,
            LocalModelFilePolicy.partialPath(root, model).name,
        )
        assertEquals(
            model.id,
            checkNotNull(LocalModelFilePolicy.completedPath(root, model).parent).name,
        )
    }

    @Test
    fun inferenceCachePrefixesCoverCurrentAndLegacyRuntimeNames() {
        val gemma = checkNotNull(
            LocalModelCatalog.models.firstOrNull { it.id == LocalModelCatalog.MODEL_E2B },
        )
        // Retired downloads can still leave inference caches behind.
        val qwen = gemma.copy(
            id = LocalModelCatalog.MODEL_QWEN_08B,
            fileName = "Qwen3.5-0.8B-Q4_K_M.gguf",
        )

        assertTrue(
            LocalModelFilePolicy.inferenceCachePrefixes(gemma)
                .contains("gemma-4-E2B-it.litertlm.xnnpack_cache_"),
        )
        assertTrue(
            LocalModelFilePolicy.inferenceCachePrefixes(qwen)
                .contains("Qwen3.5-0.8B-hybrid-exact-c2048.litertlm.xnnpack_cache_"),
        )
        assertTrue(
            LocalModelFilePolicy.inferenceCachePrefixes(qwen)
                .contains("${qwen.fileName}.xnnpack_cache_"),
        )
    }
}
