package com.simon.harmonichackernews.app

import com.simon.harmonichackernews.platform.CredentialStore
import com.simon.harmonichackernews.summary.DesktopLiteRtInference
import com.simon.harmonichackernews.summary.LocalModelCatalog
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue

class DesktopCategoryEmbeddingsTest {
    @Test
    fun productionAppWiresCategoryRuntimeAndDownloadableEmbeddingModel() {
        assumeTrue(DesktopLiteRtInference.supported)
        val root = Files.createTempDirectory("harmonic-desktop-categories")
        val app = DesktopHarmonicAppBootstrap.production(
            userAgent = "HarmonicTest",
            metadata = AppMetadata(),
            paths = DesktopAppPaths(root.resolve("config"), root.resolve("files"), root.resolve("cache")),
            credentials = object : CredentialStore {
                override fun read(id: String): String? = null
                override fun write(id: String, value: String): Boolean = true
                override fun remove(id: String): Boolean = true
            },
        )
        try {
            assertTrue(app.app.articleCategories.supported)
            val models = assertNotNull(app.app.localModels)
            assertTrue(models.catalog.any { it.id == LocalModelCatalog.MODEL_EMBEDDING_GEMMA_2 })
            assertTrue(models.isSupported(LocalModelCatalog.embeddingModel))
            assertTrue(models.isRuntimeInstalled(LocalModelCatalog.embeddingModel.runtime))
        } finally {
            app.close()
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }
}
