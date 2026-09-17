package com.simon.harmonichackernews.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidConnectivityPolicyTest {
    @Test
    fun internetCapabilityRequiresValidation() {
        assertEquals(
            AndroidConnectivityStatus.Offline,
            evaluateAndroidConnectivity(
                hasInternetCapability = true,
                hasValidatedCapability = false,
                hasUnmeteredCapability = true,
            ),
        )
    }

    @Test
    fun validatedMeteredNetworkIsOnlineButNotUnmetered() {
        assertEquals(
            AndroidConnectivityStatus(online = true, unmetered = false),
            evaluateAndroidConnectivity(
                hasInternetCapability = true,
                hasValidatedCapability = true,
                hasUnmeteredCapability = false,
            ),
        )
    }

    @Test
    fun validatedNotMeteredNetworkSatisfiesBothPredicates() {
        assertEquals(
            AndroidConnectivityStatus(online = true, unmetered = true),
            evaluateAndroidConnectivity(
                hasInternetCapability = true,
                hasValidatedCapability = true,
                hasUnmeteredCapability = true,
            ),
        )
    }
}
