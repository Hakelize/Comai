package com.comai.ui.screens.ramdashboard

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.comai.contextengine.ContextEngineFacade
import com.comai.contextengine.models.ContextProcessingResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DeviceCapabilityItem(
    val name: String,
    val status: String, // "AVAILABLE", "UNAVAILABLE", "PERMISSION REQUIRED", "OPTIONAL"
    val details: String
)

data class PipelineStageItem(
    val stageName: String,
    val status: String,
    val summary: String,
    val isCompleted: Boolean = true
)

class RamContextDashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext
    private val facade by lazy { ContextEngineFacade(context) }

    private val _contextResult = MutableStateFlow<ContextProcessingResult?>(null)
    val contextResult: StateFlow<ContextProcessingResult?> = _contextResult

    private val _capabilities = MutableStateFlow<List<DeviceCapabilityItem>>(emptyList())
    val capabilities: StateFlow<List<DeviceCapabilityItem>> = _capabilities

    private val _pipelineStages = MutableStateFlow<List<PipelineStageItem>>(emptyList())
    val pipelineStages: StateFlow<List<PipelineStageItem>> = _pipelineStages

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val result = facade.processContext()
                _contextResult.value = result
                _capabilities.value = inspectRealCapabilities(context)
                _pipelineStages.value = buildPipelineStages(result)
            } catch (e: Exception) {
                _capabilities.value = inspectRealCapabilities(context)
            }
        }
    }

    private fun inspectRealCapabilities(context: Context): List<DeviceCapabilityItem> {
        val list = mutableListOf<DeviceCapabilityItem>()

        // 1. Microphone
        val hasMicPerm = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        list.add(
            DeviceCapabilityItem(
                name = "Microphone",
                status = if (hasMicPerm) "AVAILABLE" else "PERMISSION REQUIRED",
                details = if (hasMicPerm) "RECORD_AUDIO granted" else "RECORD_AUDIO permission required"
            )
        )

        // 2. Location
        val hasLocPerm = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        list.add(
            DeviceCapabilityItem(
                name = "Location",
                status = if (hasLocPerm) "AVAILABLE" else "PERMISSION REQUIRED",
                details = if (hasLocPerm) "ACCESS_FINE_LOCATION granted" else "ACCESS_FINE_LOCATION required"
            )
        )

        // 3. GPS
        val locManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val isGpsOn = locManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        list.add(
            DeviceCapabilityItem(
                name = "GPS",
                status = if (isGpsOn) "AVAILABLE" else "UNAVAILABLE",
                details = if (isGpsOn) "GPS hardware provider active" else "GPS disabled in system settings"
            )
        )

        // 4. Battery
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 85
        list.add(
            DeviceCapabilityItem(
                name = "Battery",
                status = "AVAILABLE",
                details = "Battery Level: $batteryPct%"
            )
        )

        // 5. Network
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val isOnline = cm?.activeNetwork != null
        list.add(
            DeviceCapabilityItem(
                name = "Network",
                status = if (isOnline) "AVAILABLE" else "UNAVAILABLE",
                details = if (isOnline) "Active Network Connection" else "Offline"
            )
        )

        // 6. Activity
        val hasActPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        } else true

        list.add(
            DeviceCapabilityItem(
                name = "Activity",
                status = if (hasActPerm) "AVAILABLE" else "PERMISSION REQUIRED",
                details = if (hasActPerm) "Activity Recognition Active" else "ACTIVITY_RECOGNITION permission required"
            )
        )

        // 7. Usage Stats
        list.add(
            DeviceCapabilityItem(
                name = "Usage Stats",
                status = "OPTIONAL",
                details = "PACKAGE_USAGE_STATS extension point"
            )
        )

        // 8. Notifications
        val notifManager = androidx.core.app.NotificationManagerCompat.from(context)
        val hasNotifPerm = notifManager.areNotificationsEnabled()
        list.add(
            DeviceCapabilityItem(
                name = "Notifications",
                status = if (hasNotifPerm) "AVAILABLE" else "PERMISSION REQUIRED",
                details = if (hasNotifPerm) "Notifications enabled" else "Notification permission required"
            )
        )

        // 9. Accessibility
        list.add(
            DeviceCapabilityItem(
                name = "Accessibility",
                status = "OPTIONAL",
                details = "Not implemented / Not required for MVP"
            )
        )

        return list
    }

    private fun buildPipelineStages(result: ContextProcessingResult): List<PipelineStageItem> {
        val devCtx = result.deviceContext
        val loc = devCtx.location
        val routine = result.routineContext
        val confidence = result.confidence
        val contract = result.contract

        return listOf(
            PipelineStageItem(
                stageName = "GPS",
                status = if (loc.latitude != null) "AVAILABLE" else "LOCATION UNSET",
                summary = "Lat: ${loc.latitude ?: 37.7749}, Long: ${loc.longitude ?: -122.4194} (Acc: ${loc.accuracyMeters}m)"
            ),
            PipelineStageItem(
                stageName = "Location",
                status = "RESOLVED",
                summary = "Category: ${loc.locationCategory} | Place: ${loc.currentPlace?.name ?: loc.locationCategory}"
            ),
            PipelineStageItem(
                stageName = "Routine",
                status = if (routine.isRoutineDeviation) "DEVIATION DETECTED" else "NORMAL",
                summary = "State: ${routine.routineState} | Delta: +${routine.deviationMinutes}m | Reason: ${routine.deviationReason}"
            ),
            PipelineStageItem(
                stageName = "Rule",
                status = "EVALUATED",
                summary = "Triggered Task: ${contract.task}"
            ),
            PipelineStageItem(
                stageName = "Context",
                status = "COMPRESSED",
                summary = "Confidence: ${confidence.confidenceScore} (${confidence.confidenceLevel}) | BroadTag: ${result.userContext.broadContextTag}"
            ),
            PipelineStageItem(
                stageName = "AI",
                status = "READY",
                summary = "Mapped SharedContextContract -> ContextInput boundary"
            ),
            PipelineStageItem(
                stageName = "Action",
                status = if (contract.task != "GENERAL_CHECKIN") "ESCALATED" else "LOGGED",
                summary = "Dispatched task '${contract.task}' to Person 2 AIEngine"
            )
        )
    }
}
