package com.comai.contextengine.providers.android

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.comai.contextengine.providers.interfaces.BatteryContextProvider
import com.comai.contextengine.providers.models.BatteryContextData

/**
 * Android implementation for Battery & Power Context. Works completely locally without cloud services or crashes.
 */
class AndroidBatteryContextProvider(private val context: Context) : BatteryContextProvider {

    override val isAvailable: Boolean = true

    override fun getContextData(): BatteryContextData {
        return try {
            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
                context.registerReceiver(null, filter)
            }

            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else 100

            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            val chargePlug = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            val plugType = when (chargePlug) {
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
                else -> if (isCharging) "CHARGING" else "NONE"
            }

            val health = batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
            val healthStr = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "GOOD"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
                BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER_VOLTAGE"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "FAILURE"
                else -> "UNKNOWN"
            }

            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isPowerSaverMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                pm?.isPowerSaveMode ?: false
            } else {
                false
            }

            BatteryContextData(
                batteryLevel = batteryPct,
                isCharging = isCharging,
                plugType = plugType,
                batteryHealth = healthStr,
                isLowBattery = batteryPct in 0..15 && !isCharging,
                isPowerSaverMode = isPowerSaverMode
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring battery context", e)
            BatteryContextData(
                batteryLevel = 100,
                isCharging = false,
                plugType = "NONE",
                batteryHealth = "UNKNOWN",
                isLowBattery = false,
                isPowerSaverMode = false
            )
        }
    }

    companion object {
        private const val TAG = "AndroidBatteryProvider"
    }
}
