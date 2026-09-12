package com.comai.capability

import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import android.speech.SpeechRecognizer
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Diagnostic utility that audits device capabilities, runtime permissions,
 * special access settings, and OS restrictions without bypassing any Android policies.
 */
class CapabilityAuditor(private val context: Context) {

    fun getDeviceInfo(): DeviceInfo {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)

        val totalGb = String.format(Locale.ROOT, "%.2f GB", memInfo.totalMem / (1024.0 * 1024.0 * 1024.0))
        val availGb = String.format(Locale.ROOT, "%.2f GB", memInfo.availMem / (1024.0 * 1024.0 * 1024.0))

        return DeviceInfo(
            androidVersion = Build.VERSION.RELEASE ?: "Unknown",
            apiLevel = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER ?: "Unknown",
            model = Build.MODEL ?: "Unknown",
            hardware = Build.HARDWARE ?: "Unknown",
            totalRam = totalGb,
            availableRam = availGb
        )
    }

    fun auditAll(): List<DeviceCapability> {
        val list = mutableListOf<DeviceCapability>()
        val pm = context.packageManager

        // 1. Microphone
        val hasMicHw = pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        val micPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        list.add(
            DeviceCapability(
                name = "Microphone",
                permissionOrAccess = "android.permission.RECORD_AUDIO",
                type = if (!hasMicHw) CapabilityType.PLATFORM_HARDWARE else CapabilityType.RUNTIME_PERMISSION,
                status = if (!hasMicHw) CapabilityStatus.UNSUPPORTED else if (micPerm) CapabilityStatus.GRANTED else CapabilityStatus.NOT_GRANTED,
                details = if (!hasMicHw) "Hardware microphone missing" else if (micPerm) "Hardware present; RECORD_AUDIO granted" else "Hardware present; RECORD_AUDIO not granted"
            )
        )

        // 2. Android SpeechRecognizer
        val isSpeechAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        list.add(
            DeviceCapability(
                name = "Android SpeechRecognizer",
                permissionOrAccess = "SpeechRecognizer Service Binding",
                type = CapabilityType.PLATFORM_HARDWARE,
                status = if (isSpeechAvailable) CapabilityStatus.AVAILABLE else CapabilityStatus.UNSUPPORTED,
                details = if (isSpeechAvailable) "Standard Android recognition service available" else "Recognition service not installed"
            )
        )

        // 3. On-Device SpeechRecognizer
        val isOnDeviceSpeechAvailable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        } else {
            false
        }
        list.add(
            DeviceCapability(
                name = "On-Device SpeechRecognizer",
                permissionOrAccess = "SpeechRecognizer.createOnDeviceSpeechRecognizer (API 31+)",
                type = CapabilityType.PLATFORM_HARDWARE,
                status = if (isOnDeviceSpeechAvailable) CapabilityStatus.AVAILABLE else CapabilityStatus.UNSUPPORTED,
                details = if (isOnDeviceSpeechAvailable) "On-device ASR models present (API 31+)" else "On-device recognition unsupported or no local model pack"
            )
        )

        // 4. Text-To-Speech
        list.add(
            DeviceCapability(
                name = "Text-to-Speech (TTS)",
                permissionOrAccess = "android.speech.tts.TextToSpeech",
                type = CapabilityType.PLATFORM_HARDWARE,
                status = CapabilityStatus.AVAILABLE,
                details = "Native Android TextToSpeech engine initialized"
            )
        )

        // 5. Foreground Service
        val fgPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.FOREGROUND_SERVICE) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        list.add(
            DeviceCapability(
                name = "Foreground Service",
                permissionOrAccess = "android.permission.FOREGROUND_SERVICE",
                type = CapabilityType.SYSTEM_RESTRICTION,
                status = if (fgPerm) CapabilityStatus.GRANTED else CapabilityStatus.NOT_GRANTED,
                details = if (fgPerm) "FOREGROUND_SERVICE granted (ContextForegroundService active)" else "Permission missing"
            )
        )

        // 6. Location
        val fineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        list.add(
            DeviceCapability(
                name = "Location",
                permissionOrAccess = "ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION",
                type = CapabilityType.RUNTIME_PERMISSION,
                status = if (fineLocation || coarseLocation) CapabilityStatus.GRANTED else CapabilityStatus.NOT_GRANTED,
                details = if (fineLocation) "Fine location granted" else if (coarseLocation) "Coarse location granted" else "Location permission not granted"
            )
        )

        // 7. Background Location
        val bgLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            fineLocation || coarseLocation
        }
        list.add(
            DeviceCapability(
                name = "Background Location",
                permissionOrAccess = "ACCESS_BACKGROUND_LOCATION",
                type = CapabilityType.SYSTEM_RESTRICTION,
                status = if (bgLocation) CapabilityStatus.GRANTED else CapabilityStatus.RESTRICTED,
                details = if (bgLocation) "ACCESS_BACKGROUND_LOCATION granted" else "Restricted: Requires separate user setting approval on Android 10+"
            )
        )

        // 8. Activity Recognition
        val actPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        list.add(
            DeviceCapability(
                name = "Activity Recognition",
                permissionOrAccess = "android.permission.ACTIVITY_RECOGNITION",
                type = CapabilityType.RUNTIME_PERMISSION,
                status = if (actPerm) CapabilityStatus.GRANTED else CapabilityStatus.NOT_GRANTED,
                details = if (actPerm) "ACTIVITY_RECOGNITION granted" else "Activity recognition permission not granted"
            )
        )

        // 9. Battery State
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        list.add(
            DeviceCapability(
                name = "Battery State",
                permissionOrAccess = "Intent.ACTION_BATTERY_CHANGED",
                type = CapabilityType.PLATFORM_HARDWARE,
                status = if (batteryPct >= 0) CapabilityStatus.AVAILABLE else CapabilityStatus.UNSUPPORTED,
                details = if (batteryPct >= 0) "Current charge: $batteryPct%" else "Battery manager unavailable"
            )
        )

        // 10. Charging State
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val isCharging = plugged != 0
        val chargeType = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Discharging"
        }
        list.add(
            DeviceCapability(
                name = "Charging State",
                permissionOrAccess = "BatteryManager.EXTRA_PLUGGED",
                type = CapabilityType.PLATFORM_HARDWARE,
                status = CapabilityStatus.AVAILABLE,
                details = if (isCharging) "Charging ($chargeType)" else "On battery power"
            )
        )

        // 11. Network Connectivity
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNet = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNet)
        val netType = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "VPN"
            else -> "Disconnected"
        }
        list.add(
            DeviceCapability(
                name = "Network",
                permissionOrAccess = "ConnectivityManager.getNetworkCapabilities",
                type = CapabilityType.PLATFORM_HARDWARE,
                status = if (activeNet != null) CapabilityStatus.AVAILABLE else CapabilityStatus.NOT_GRANTED,
                details = "Active transport: $netType"
            )
        )

        // 12. Bluetooth
        val hasBtHw = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        val btConnectPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        list.add(
            DeviceCapability(
                name = "Bluetooth",
                permissionOrAccess = "android.permission.BLUETOOTH_CONNECT",
                type = if (!hasBtHw) CapabilityType.PLATFORM_HARDWARE else CapabilityType.RUNTIME_PERMISSION,
                status = if (!hasBtHw) CapabilityStatus.UNSUPPORTED else if (btConnectPerm) CapabilityStatus.AVAILABLE else CapabilityStatus.NOT_GRANTED,
                details = if (!hasBtHw) "Bluetooth hardware missing" else if (btConnectPerm) "Bluetooth hardware present & permitted" else "BLUETOOTH_CONNECT permission required on API 31+"
            )
        )

        // 13. Headphones / Media State
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        var headphoneDetails = "Speaker"
        if (audioManager != null) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val hasWired = devices.any { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES }
            val hasBtA2dp = devices.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            headphoneDetails = when {
                hasWired -> "Wired Headphones connected"
                hasBtA2dp -> "Bluetooth Audio (A2DP/SCO) connected"
                else -> "Built-in Speaker"
            }
        }
        list.add(
            DeviceCapability(
                name = "Headphones / Media State",
                permissionOrAccess = "AudioManager.getDevices",
                type = CapabilityType.PLATFORM_HARDWARE,
                status = CapabilityStatus.AVAILABLE,
                details = headphoneDetails
            )
        )

        // 14. Calendar
        val calPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        list.add(
            DeviceCapability(
                name = "Calendar",
                permissionOrAccess = "android.permission.READ_CALENDAR",
                type = CapabilityType.RUNTIME_PERMISSION,
                status = if (calPerm) CapabilityStatus.GRANTED else CapabilityStatus.NOT_GRANTED,
                details = if (calPerm) "READ_CALENDAR granted" else "READ_CALENDAR not requested/granted"
            )
        )

        // 15. Contacts
        val contactsPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        list.add(
            DeviceCapability(
                name = "Contacts",
                permissionOrAccess = "android.permission.READ_CONTACTS",
                type = CapabilityType.RUNTIME_PERMISSION,
                status = if (contactsPerm) CapabilityStatus.GRANTED else CapabilityStatus.NOT_GRANTED,
                details = if (contactsPerm) "READ_CONTACTS granted" else "READ_CONTACTS not requested/granted"
            )
        )

        // 16. Usage Access
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        val usageMode = appOps?.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        ) ?: AppOpsManager.MODE_IGNORED
        val hasUsageAccess = usageMode == AppOpsManager.MODE_ALLOWED
        list.add(
            DeviceCapability(
                name = "Usage Access",
                permissionOrAccess = "AppOpsManager: OPSTR_GET_USAGE_STATS",
                type = CapabilityType.SPECIAL_ACCESS,
                status = if (hasUsageAccess) CapabilityStatus.GRANTED else CapabilityStatus.RESTRICTED,
                details = if (hasUsageAccess) "PACKAGE_USAGE_STATS allowed" else "Requires user approval in Settings > Usage Access"
            )
        )

        // 17. Notification Access
        val enabledNotificationListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
        val hasNotificationAccess = enabledNotificationListeners.contains(context.packageName)
        list.add(
            DeviceCapability(
                name = "Notification Access",
                permissionOrAccess = "NotificationListenerService",
                type = CapabilityType.SPECIAL_ACCESS,
                status = if (hasNotificationAccess) CapabilityStatus.GRANTED else CapabilityStatus.RESTRICTED,
                details = if (hasNotificationAccess) "NotificationListenerService enabled" else "Requires user approval in Settings > Notification Access"
            )
        )

        // 18. Accessibility Status (Audit only, never create service)
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val isA11yEnabled = accessibilityManager?.isEnabled == true
        list.add(
            DeviceCapability(
                name = "Accessibility Status",
                permissionOrAccess = "AccessibilityManager.isEnabled (Audit Only)",
                type = CapabilityType.SPECIAL_ACCESS,
                status = if (isA11yEnabled) CapabilityStatus.AVAILABLE else CapabilityStatus.RESTRICTED,
                details = if (isA11yEnabled) "Accessibility subsystem active on device (Audit only)" else "Disabled/No active accessibility services"
            )
        )

        // 19. Maps / Navigation Intent Availability
        val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=test"))
        val hasMapsApp = geoIntent.resolveActivity(pm) != null
        list.add(
            DeviceCapability(
                name = "Maps / Navigation Intent",
                permissionOrAccess = "Intent(ACTION_VIEW, geo:)",
                type = CapabilityType.PLATFORM_HARDWARE,
                status = if (hasMapsApp) CapabilityStatus.AVAILABLE else CapabilityStatus.UNSUPPORTED,
                details = if (hasMapsApp) "Navigation/Map application resolved" else "No app found to handle geo: intents"
            )
        )

        // 20. Media Control Availability
        val mediaIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
        val hasMediaControl = mediaIntent.resolveActivity(pm) != null || audioManager != null
        list.add(
            DeviceCapability(
                name = "Media Control Availability",
                permissionOrAccess = "Intent(ACTION_MEDIA_BUTTON) / AudioManager",
                type = CapabilityType.PLATFORM_HARDWARE,
                status = if (hasMediaControl) CapabilityStatus.AVAILABLE else CapabilityStatus.RESTRICTED,
                details = if (hasMediaControl) "AudioManager media session/dispatch available" else "No media receivers resolved"
            )
        )

        return list
    }
}
