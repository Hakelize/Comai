package com.comai.contextengine.rules

import android.util.Log
import com.comai.contextengine.contract.SharedContextContract
import com.comai.contextengine.service.ContextOutputListener
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Manages event listener registrations and dispatches escalated context triggers.
 */
class EventTriggerManager {

    private val listeners = CopyOnWriteArrayList<ContextOutputListener>()

    fun addListener(listener: ContextOutputListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
            Log.d(TAG, "Registered ContextOutputListener: ${listener.javaClass.simpleName}")
        }
    }

    fun removeListener(listener: ContextOutputListener) {
        listeners.remove(listener)
    }

    fun triggerEvent(jsonContract: String, contract: SharedContextContract) {
        Log.i(TAG, "Dispatching event trigger: task=${contract.task} to ${listeners.size} listeners")
        for (listener in listeners) {
            try {
                listener.onContextEscalated(jsonContract, contract)
            } catch (e: Exception) {
                Log.e(TAG, "Error in ContextOutputListener callback", e)
            }
        }
    }

    companion object {
        private const val TAG = "EventTriggerManager"
    }
}
