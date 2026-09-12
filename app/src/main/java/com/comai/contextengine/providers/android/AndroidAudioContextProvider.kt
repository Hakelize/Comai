package com.comai.contextengine.providers.android

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.comai.contextengine.providers.interfaces.AudioContextProvider
import com.comai.contextengine.providers.models.AudioContextData
import com.comai.contextengine.providers.models.AudioOutputDeviceType
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Version-aware Android implementation for Audio & Headphones Context (Android 10..14+, API 29..34+).
 * Dynamically tracks wired headset, Bluetooth audio, built-in speaker, and other audio outputs.
 * Gracefully handles permission restrictions, missing hardware devices, and system state transitions.
 */
class AndroidAudioContextProvider(
    private val context: Context
) : AudioContextProvider {

    override val isAvailable: Boolean = true

    private val audioManager: AudioManager? by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    private val listeners = CopyOnWriteArrayList<(AudioContextData) -> Unit>()
    private var isListening = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            Log.d(TAG, "Audio devices added callback received")
            notifyListeners()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            Log.d(TAG, "Audio devices removed callback received")
            notifyListeners()
        }
    }

    private val audioStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Audio state broadcast received: ${intent?.action}")
            notifyListeners()
        }
    }

    /**
     * Starts active listening for real-time audio output hardware changes.
     */
    fun startListening() {
        if (isListening) return
        try {
            audioManager?.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_HEADSET_PLUG)
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED")
                addAction("android.bluetooth.adapter.action.STATE_CHANGED")
            }
            context.registerReceiver(audioStateReceiver, filter)
            isListening = true
            Log.i(TAG, "AudioContextProvider started active device & broadcast listening.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register audio device callbacks or broadcast receivers", e)
        }
    }

    /**
     * Stops active listening and unregisters callbacks/receivers.
     */
    fun stopListening() {
        if (!isListening) return
        try {
            audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
            context.unregisterReceiver(audioStateReceiver)
            isListening = false
            Log.i(TAG, "AudioContextProvider stopped listening.")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering audio device callbacks/receivers", e)
        }
    }

    fun addStateChangeListener(listener: (AudioContextData) -> Unit) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
            if (!isListening) startListening()
        }
    }

    fun removeStateChangeListener(listener: (AudioContextData) -> Unit) {
        listeners.remove(listener)
        if (listeners.isEmpty() && isListening) {
            stopListening()
        }
    }

    private fun notifyListeners() {
        val currentData = getContextData()
        for (listener in listeners) {
            try {
                listener.invoke(currentData)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying AudioStateChangeListener", e)
            }
        }
    }

    override fun getContextData(): AudioContextData {
        val am = audioManager ?: return AudioContextData(
            isOutputAvailable = false,
            primaryOutputDevice = AudioOutputDeviceType.UNKNOWN,
            isSpeakerActive = false,
            activeOutputDeviceName = "No AudioManager"
        )

        var hasWired = false
        var hasBluetooth = false
        var hasSpeaker = false
        var hasOther = false
        var isOutputAvailable = false
        var primaryDeviceName = "Speaker"

        try {
            val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            if (outputs.isNotEmpty()) {
                isOutputAvailable = true
                val canQueryBluetooth = canAccessBluetoothAudio()

                for (device in outputs) {
                    when (device.type) {
                        AudioDeviceInfo.TYPE_WIRED_HEADSET,
                        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                        AudioDeviceInfo.TYPE_USB_HEADSET,
                        AudioDeviceInfo.TYPE_USB_DEVICE,
                        AudioDeviceInfo.TYPE_USB_ACCESSORY,
                        AudioDeviceInfo.TYPE_LINE_ANALOG,
                        AudioDeviceInfo.TYPE_LINE_DIGITAL -> {
                            hasWired = true
                            primaryDeviceName = device.productName.toString().ifBlank { "Wired Headphones" }
                        }

                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                        AudioDeviceInfo.TYPE_BLE_HEADSET,
                        AudioDeviceInfo.TYPE_BLE_SPEAKER,
                        AudioDeviceInfo.TYPE_BLE_BROADCAST,
                        AudioDeviceInfo.TYPE_HEARING_AID -> {
                            if (canQueryBluetooth) {
                                hasBluetooth = true
                                primaryDeviceName = device.productName.toString().ifBlank { "Bluetooth Audio" }
                            }
                        }

                        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
                        AudioDeviceInfo.TYPE_TELEPHONY -> {
                            hasSpeaker = true
                        }

                        AudioDeviceInfo.TYPE_HDMI,
                        AudioDeviceInfo.TYPE_HDMI_ARC,
                        AudioDeviceInfo.TYPE_HDMI_EARC,
                        AudioDeviceInfo.TYPE_DOCK,
                        AudioDeviceInfo.TYPE_FM_TUNER,
                        AudioDeviceInfo.TYPE_TV_TUNER,
                        AudioDeviceInfo.TYPE_REMOTE_SUBMIX,
                        AudioDeviceInfo.TYPE_IP -> {
                            hasOther = true
                            primaryDeviceName = device.productName.toString().ifBlank { "External Output" }
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException querying audio devices: ${e.message}")
            isOutputAvailable = true
            hasSpeaker = true
        } catch (e: Exception) {
            Log.e(TAG, "Error querying AudioDeviceInfo outputs", e)
            isOutputAvailable = true
            hasSpeaker = true
        }

        // Determine primary output destination priority: Bluetooth > Wired > Other > Speaker
        val primaryDevice = when {
            hasBluetooth -> AudioOutputDeviceType.BLUETOOTH_AUDIO
            hasWired -> AudioOutputDeviceType.WIRED_HEADPHONES
            hasOther -> AudioOutputDeviceType.OTHER
            hasSpeaker -> AudioOutputDeviceType.SPEAKER
            isOutputAvailable -> AudioOutputDeviceType.SPEAKER
            else -> AudioOutputDeviceType.UNKNOWN
        }

        val ringerModeStr = try {
            when (am.ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> "SILENT"
                AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
                AudioManager.RINGER_MODE_NORMAL -> "NORMAL"
                else -> "NORMAL"
            }
        } catch (e: Exception) {
            "NORMAL"
        }

        val isMusicActive = try {
            am.isMusicActive
        } catch (e: Exception) {
            false
        }

        val isHeadphonesPlugged = hasWired || hasBluetooth

        return AudioContextData(
            isOutputAvailable = isOutputAvailable,
            primaryOutputDevice = primaryDevice,
            isSpeakerActive = primaryDevice == AudioOutputDeviceType.SPEAKER || hasSpeaker,
            isWiredHeadphonesConnected = hasWired,
            isBluetoothAudioConnected = hasBluetooth,
            isOtherOutputConnected = hasOther,
            isHeadphonesPlugged = isHeadphonesPlugged,
            ringerMode = ringerModeStr,
            isMusicActive = isMusicActive,
            activeOutputDeviceName = primaryDeviceName
        )
    }

    private fun canAccessBluetoothAudio(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+ (API 31+)
            val perm = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            return perm == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    companion object {
        private const val TAG = "AndroidAudioProvider"
    }
}
