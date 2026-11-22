package com.simon.harmonichackernews.ui.licenses

import com.simon.harmonichackernews.app.CommonLicenseCatalog
import com.simon.harmonichackernews.resources.Res
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class LicenseNoticesTest {
    @Test fun eachProjectShipsItsNotice() = runBlocking {
        val entries = CommonLicenseCatalog.complete(emptyList(), includeLocalAi = true)
        for (entry in entries) {
            val text = Res.readBytes("files/licenses/${entry.noticeFile}").decodeToString()
            val expected = when (entry.licenseType) {
                "Apache License 2.0" -> "END OF TERMS AND CONDITIONS"
                "MIT License" -> "Permission is hereby granted"
                else -> entry.licenseType
            }
            assertTrue(text.contains(expected, ignoreCase = true), entry.name)
        }
    }
}
