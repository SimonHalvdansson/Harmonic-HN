package com.simon.harmonichackernews.app

import kotlin.test.Test
import kotlin.test.assertFalse

class DesktopPlatformStateTest {
    @Test
    fun unknownConnectionCostIsNotReportedAsUnmetered() {
        assertFalse(DesktopConnectivity.isUnmetered())
    }
}
