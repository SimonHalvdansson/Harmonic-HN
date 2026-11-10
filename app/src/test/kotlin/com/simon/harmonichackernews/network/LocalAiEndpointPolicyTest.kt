package com.simon.harmonichackernews.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException

class LocalAiEndpointPolicyTest {
    @Test
    fun recognizesMdnsWithoutAttemptingPermissionGatedResolution() {
        assertTrue(LocalAiEndpointPolicy.requiresLocalAccess("AI.Local.", resolve = {
            error("mDNS must not run before permission is granted")
        }))
    }

    @Test
    fun recognizesPrivateLinkLocalAndUniqueLocalAddresses() {
        for (address in listOf("10.0.2.2", "172.16.0.1", "192.168.1.2", "169.254.1.2",
            "fe80::1", "fc00::1", "fd12::1", "fec0::1")) {
            assertTrue(address, LocalAiEndpointPolicy.requiresLocalAccess("ai.example", resolve = {
                listOf(InetAddress.getByName(address))
            }))
        }
    }

    @Test
    fun publicAndLoopbackServersDoNotNeedLanAccess() {
        for (address in listOf("8.8.8.8", "172.32.0.1", "2001:4860:4860::8888", "127.0.0.1", "::1")) {
            assertFalse(address, LocalAiEndpointPolicy.requiresLocalAccess("ai.example", resolve = {
                listOf(InetAddress.getByName(address))
            }))
        }
        assertFalse(LocalAiEndpointPolicy.requiresLocalAccess("missing.example", resolve = {
            throw UnknownHostException()
        }))
    }

    @Test
    fun checksAllDnsAnswersAndPublicAddressesOnTheLocalLink() {
        assertTrue(LocalAiEndpointPolicy.requiresLocalAccess("ai.example", resolve = {
            listOf(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("192.168.1.2"))
        }))
        assertTrue(LocalAiEndpointPolicy.requiresLocalAccess("ai.example", resolve = {
            listOf(InetAddress.getByName("2001:db8::1"))
        }, isOnLink = { true }))
    }
}
