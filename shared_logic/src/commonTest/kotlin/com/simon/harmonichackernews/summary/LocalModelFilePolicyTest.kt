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
            LocalModelCatalog.models.firstOrNull { it.id == LocalModelCatalog.MODEL_QWEN_08B },
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
        val qwen = checkNotNull(
            LocalModelCatalog.models.firstOrNull { it.id == LocalModelCatalog.MODEL_QWEN_08B },
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
