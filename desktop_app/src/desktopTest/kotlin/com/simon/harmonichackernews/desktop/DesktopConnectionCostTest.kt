package com.simon.harmonichackernews.desktop

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue

class DesktopConnectionCostTest {
    @Test fun windowsOnlyAllowsUnrestrictedConnectionsWithoutCostWarnings() {
        assertTrue(windowsCostIsUnmetered(1))
        listOf(0, 2, 4, 0x10001, 0x20001, 0x40001, -1).forEach {
            assertFalse(windowsCostIsUnmetered(it), "Cost $it must not permit Wi-Fi-only preloads")
        }
    }

    @Test fun networkManagerDistinguishesUnmeteredFromUnknownAndMeteredRoutes() {
        assertTrue(networkManagerCostIsUnmetered("u 2\n"))
        assertTrue(networkManagerCostIsUnmetered("u 4"))
        listOf("u 0", "u 1", "u 3", "", "Failed to connect to bus").forEach {
            assertFalse(networkManagerCostIsUnmetered(it))
        }
    }

    @Test fun macMonitorCanStartReadAndReleaseWithoutOpeningABrowser() {
        assumeTrue(System.getProperty("harmonic.testMacWebView") == "true")
        val api = MacWebViewLibrary.api
        val monitor = checkNotNull(api.harmonic_network_monitor_create())
        try {
            assertTrue(api.harmonic_network_monitor_online(monitor) in 0..1)
            assertTrue(api.harmonic_network_monitor_unmetered(monitor) in 0..1)
        } finally {
            api.harmonic_network_monitor_destroy(monitor)
        }
    }
}
