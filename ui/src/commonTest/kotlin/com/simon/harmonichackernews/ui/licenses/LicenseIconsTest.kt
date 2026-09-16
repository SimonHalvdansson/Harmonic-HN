package com.simon.harmonichackernews.ui.licenses

import com.simon.harmonichackernews.app.CommonLicenseCatalog
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LicenseIconsTest {
    @Test
    fun everyBundledDependencyHasAnImage() {
        CommonLicenseCatalog.complete(emptyList(), includeLocalAi = true).forEach { entry ->
            assertNotNull(licenseIconResource(entry.name), "Missing image for ${entry.name}")
        }
    }

    @Test
    fun unknownDependencyUsesFallbackInsteadOfThrowing() {
        assertNull(licenseIconResource("Future dependency"))
    }
}
