package com.comai.ui.screens.capability

import androidx.lifecycle.ViewModel
import com.comai.capability.CapabilityAuditor
import com.comai.capability.DeviceCapability
import com.comai.capability.DeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CapabilityViewModel(
    private val auditor: CapabilityAuditor
) : ViewModel() {

    private val _deviceInfo = MutableStateFlow(auditor.getDeviceInfo())
    val deviceInfo: StateFlow<DeviceInfo> = _deviceInfo.asStateFlow()

    private val _capabilities = MutableStateFlow(auditor.auditAll())
    val capabilities: StateFlow<List<DeviceCapability>> = _capabilities.asStateFlow()

    fun refresh() {
        _deviceInfo.value = auditor.getDeviceInfo()
        _capabilities.value = auditor.auditAll()
    }
}
