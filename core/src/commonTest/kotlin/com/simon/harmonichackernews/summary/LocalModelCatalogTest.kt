package com.simon.harmonichackernews.summary

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalModelCatalogTest {
    @Test
    fun persistedDownloadsMustMatchTheCurrentArtifact() {
        val model = LocalModelCatalog.models.first { it.id == LocalModelCatalog.MODEL_LFM3_17B }
        assertTrue(LocalModelCatalog.isCurrentDownload(model.id, model.url, model.fileName, model.sizeBytes))
        assertFalse(LocalModelCatalog.isCurrentDownload(
            model.id, "https://example.com/old.gguf", "LFM2.5-1.2B-Instruct-Q4_K_M.gguf", 730_895_168,
        ))
        assertFalse(LocalModelCatalog.isCurrentDownload("bonsai-1.7b", model.url, model.fileName, model.sizeBytes))
        assertFalse(LocalModelCatalog.isCurrentDownload(
            "lfm-2.5-1.2b",
            "https://huggingface.co/litert-community/LFM2.5-1.2B-Instruct/resolve/" +
                "a7a19073d20333b24be0e9b0dad02a0735154376/LFM2.5-1.2B-Instruct_int4_gpu.litertlm?download=true",
            "LFM2.5-1.2B-Instruct_int4_gpu.litertlm",
            736_220_768L,
        ))
        assertFalse(LocalModelCatalog.isCurrentDownload(model.id, model.url, model.fileName, model.sizeBytes - 1))
        assertFalse(LocalModelCatalog.isCurrentDownload(model.id, model.url + "old", model.fileName, model.sizeBytes))
    }
}
