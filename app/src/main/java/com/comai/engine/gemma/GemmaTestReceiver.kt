package com.comai.engine.gemma

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlin.concurrent.thread

/**
 * BroadcastReceiver triggered via ADB to run the controlled Gemma GPU test.
 *
 * Command:
 * adb shell am broadcast -a com.comai.action.TEST_GEMMA_GPU
 */
class GemmaTestReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(TAG, "GemmaTestReceiver received action: ${intent?.action}")
        if (intent?.action == ACTION_TEST_GPU) {
            val customPath = intent.getStringExtra(EXTRA_MODEL_PATH)
            val pendingResult = goAsync()

            thread(name = "GemmaGpuTestThread") {
                try {
                    Log.i(TAG, "Starting Gemma GPU Initialization Test in background thread...")
                    val report = GemmaGpuTester.runTest(context.applicationContext, customPath)
                    Log.i(TAG, "Gemma GPU Test Completed. Success=${report.initSuccess && report.inferenceSuccess}")
                } catch (t: Throwable) {
                    Log.e(TAG, "Unexpected error in GemmaTestReceiver: ${t.message}", t)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        private const val TAG = "GEMMA_GPU_TEST"
        const val ACTION_TEST_GPU = "com.comai.action.TEST_GEMMA_GPU"
        const val EXTRA_MODEL_PATH = "model_path"
    }
}
