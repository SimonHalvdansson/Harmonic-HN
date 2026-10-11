package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.LinkPreviewType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProviderMetadataRegressionTest {
    @Test fun gatedDatasetMustNotBeLabelledPublic() {
        for (mode in listOf("auto", "manual")) {
            val result = HuggingFaceLinkPreview.parseHuggingFace(
                LinkPreviewType.HUGGING_FACE_DATASET,
                """{"id":"ILSVRC/imagenet-1k","gated":"$mode","private":false}""",
                HuggingFacePreviewTarget(LinkPreviewType.HUGGING_FACE_DATASET, "ILSVRC", "imagenet-1k"),
                "https://huggingface.co/datasets/ILSVRC/imagenet-1k",
            )
            assertEquals("Gated", result.details.single { it.label == "Access" }.value, mode)
        }
    }
    @Test fun homebrewCaskUsesItsDisplayName() {
        val result = PackageLinkPreview.parsePackage(
            LinkPreviewType.HOMEBREW_PACKAGE,
            """{"token":"google-chrome","name":["Google Chrome"],"desc":"Web browser","version":"155.0.8059.40"}""",
            PackagePreviewTarget(LinkPreviewType.HOMEBREW_PACKAGE, "google-chrome", "cask"),
            "https://formulae.brew.sh/cask/google-chrome",
        )
        assertEquals("Google Chrome", result.title)
    }
    @Test fun usgsNonEventPagesMustNotBecomeEarthquakePreviews() {
        for (path in listOf("learn/", "earthquakes/map/", "earthquakes/eventpage/", "")) {
            assertNull(RichLinkPreviewUrls.type("https://earthquake.usgs.gov/$path"), path)
        }
        assertEquals(LinkPreviewType.USGS_EARTHQUAKE, RichLinkPreviewUrls.type(
            "https://earthquake.usgs.gov/earthquakes/eventpage/us6000tkt2/executive"))
    }
}
