package com.comai.contextengine

import android.app.Application
import android.util.Log

/**
 * ComaiApplication - Application class for initializing Context Engine services.
 */
class ComaiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Comai Application initialized.")
    }

    companion object {
        private const val TAG = "ComaiApp"
    }
}
