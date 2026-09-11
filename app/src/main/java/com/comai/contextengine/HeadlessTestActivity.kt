package com.comai.contextengine

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.comai.contextengine.service.ContextForegroundService

/**
 * HeadlessTestActivity - Minimal launcher activity strictly for testing/starting the headless Context Engine background process.
 */
class HeadlessTestActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Start Foreground Service
        val serviceIntent = Intent(this, ContextForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        // Person 3 Integration Point: UI layer attaches to service/repository from here.
        finish()
    }
}
