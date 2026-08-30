package com.simon.harmonichackernews.platform

import kotlin.math.roundToInt
import platform.UIKit.UIDevice

/** Reads the native battery level without leaving monitoring enabled after the policy check. */
class IosBatteryStatusService : BatteryStatusService {
    override fun batteryPercent(): Int? {
        val device = UIDevice.currentDevice
        val monitoringWasEnabled = device.batteryMonitoringEnabled
        if (!monitoringWasEnabled) device.batteryMonitoringEnabled = true
        val level = device.batteryLevel
        if (!monitoringWasEnabled) device.batteryMonitoringEnabled = false
        return level
            .takeIf { it >= 0f }
            ?.times(100f)
            ?.roundToInt()
            ?.coerceIn(0, 100)
    }
}
