package com.simon.harmonichackernews.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HuggingFaceLinkPreviewTest {
    @Test
    fun recognizesModelPagesAndIgnoresOtherHuggingFaceProducts() {
        assertEquals(
            HuggingFaceModel("moonshotai", "Kimi-K3"),
            LinkPreviewUrls.huggingFaceModel("https://huggingface.co/moonshotai/Kimi-K3"),
        )
        assertEquals(
            HuggingFaceModel("moonshotai", "Kimi-K3"),
            LinkPreviewUrls.huggingFaceModel(
                "https://huggingface.co/moonshotai/Kimi-K3/blob/main/README.md",
            ),
        )
        assertNull(LinkPreviewUrls.huggingFaceModel("https://huggingface.co/datasets/example/data"))
        assertFalse(LinkPreviewUrls.isHuggingFaceUrl("https://example.com/moonshotai/Kimi-K3"))
    }

    @Test
    fun parsesAndFormatsModelApiFields() {
        val info = LinkPreviewParsers.parseHuggingFace(
            """
            {
              "id":"moonshotai/Kimi-K3",
              "author":"moonshotai",
              "downloads":2163953,
              "likes":10810,
              "pipeline_tag":"image-text-to-text",
              "library_name":"transformers",
              "tags":["transformers","8-bit","license:other"],
              "siblings":[
                {"rfilename":"README.md"},
                {"rfilename":"assets/example.png"},
                {"rfilename":"assets/kimi-logo.png"}
              ],
              "lastModified":"2026-07-27T16:29:18.000Z",
              "cardData":{"license":"other","license_name":"kimi-k3"},
              "safetensors":{"total":2779931837184}
            }
            """.trimIndent(),
        )

        assertEquals("moonshotai", info.author)
        assertEquals("Kimi-K3", info.name)
        assertEquals(
            "https://huggingface.co/moonshotai/Kimi-K3/resolve/main/assets/kimi-logo.png",
            info.logoUrl,
        )
        assertEquals("Image-text-to-text · Transformers · 8-bit", info.formatCapability())
        assertEquals("10.8K likes", info.formatLikes())
        assertEquals("2.16M downloads", info.formatDownloads())
        assertEquals("2.78T parameters", info.formatParameters())
        assertEquals("Kimi-K3 license", info.formatLicense())
        assertEquals("Updated Jul 27", info.formatUpdated())
        assertEquals("huggingface.co/moonshotai/Kimi-K3", info.shortenedUrl)
        assertTrue(LinkPreviewUrls.isHuggingFaceUrl(info.website))
    }

    @Test
    fun fallsBackToAnotherRepositoryImageWhenNoLogoExists() {
        val info = LinkPreviewParsers.parseHuggingFace(
            """
            {
              "id":"example/model",
              "siblings":[
                {"rfilename":"README.md"},
                {"rfilename":"samples/output.jpg"},
                {"rfilename":"assets/preview.webp"}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(
            "https://huggingface.co/example/model/resolve/main/assets/preview.webp",
            info.logoUrl,
        )
    }
}
