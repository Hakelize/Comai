package com.comai.engine.gemma

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import org.json.JSONObject
import java.io.File

/**
 * Controlled GPU Initialization & Single-Inference Tester for Gemma 4 E4B LiteRT-LM.
 */
object GemmaGpuTester {

    private const val TAG = "GEMMA_GPU_TEST"
    private const val DEFAULT_MODEL_PATH = "/sdcard/Android/data/com.comai/files/models/gemma-4-E4B-it.litertlm"

    data class Report(
        val modelPath: String,
        val modelExists: Boolean,
        val modelSizeBytes: Long,
        val ramAvailableBeforeKb: Long,
        val ramTotalKb: Long,
        val initStartTimestamp: Long,
        val initEndTimestamp: Long = 0,
        val initDurationMs: Long = 0,
        val backend: String = "GPU",
        val initSuccess: Boolean = false,
        val initError: String? = null,
        val ramAvailableAfterKb: Long = 0,
        val inferenceLatencyMs: Long = 0,
        val inferenceResponse: String? = null,
        val inferenceSuccess: Boolean = false,
        val inferenceError: String? = null
    ) {
        fun toJson(): String {
            return JSONObject().apply {
                put("modelPath", modelPath)
                put("modelExists", modelExists)
                put("modelSizeBytes", modelSizeBytes)
                put("ramAvailableBeforeKb", ramAvailableBeforeKb)
                put("ramTotalKb", ramTotalKb)
                put("initStartTimestamp", initStartTimestamp)
                put("initEndTimestamp", initEndTimestamp)
                put("initDurationMs", initDurationMs)
                put("backend", backend)
                put("initSuccess", initSuccess)
                put("initError", initError ?: JSONObject.NULL)
                put("ramAvailableAfterKb", ramAvailableAfterKb)
                put("inferenceLatencyMs", inferenceLatencyMs)
                put("inferenceResponse", inferenceResponse ?: JSONObject.NULL)
                put("inferenceSuccess", inferenceSuccess)
                put("inferenceError", inferenceError ?: JSONObject.NULL)
            }.toString(2)
        }
    }

    fun runTest(context: Context, customPath: String? = null): Report {
        val targetPath = customPath ?: resolveModelPath(context)
        val modelFile = File(targetPath)

        val ramTotal = readRamValueKb("MemTotal:")
        val ramBefore = readRamValueKb("MemAvailable:")

        Log.i(TAG, "==================================================")
        Log.i(TAG, "STEP 1: PRE-INITIALIZATION LOGGING")
        Log.i(TAG, "Model Path: ${modelFile.absolutePath}")
        Log.i(TAG, "Model File Existence: ${modelFile.exists()}")
        Log.i(TAG, "Model File Size: ${modelFile.length()} bytes (~${modelFile.length() / (1024 * 1024)} MB)")
        Log.i(TAG, "Available RAM Before: $ramBefore kB (~${ramBefore / 1024} MB)")
        Log.i(TAG, "Total RAM: $ramTotal kB (~${ramTotal / 1024} MB)")
        Log.i(TAG, "==================================================")

        if (!modelFile.exists()) {
            val err = "Model file does not exist at: ${modelFile.absolutePath}"
            Log.e(TAG, "ERROR: $err")
            return Report(
                modelPath = modelFile.absolutePath,
                modelExists = false,
                modelSizeBytes = 0,
                ramAvailableBeforeKb = ramBefore,
                ramTotalKb = ramTotal,
                initStartTimestamp = System.currentTimeMillis(),
                initSuccess = false,
                initError = err
            )
        }

        val initStart = System.currentTimeMillis()
        val initStartUptime = SystemClock.uptimeMillis()
        Log.i(TAG, "STEP 2: CONTROLLED GPU INITIALIZATION")
        Log.i(TAG, "Initialization Start Timestamp: $initStart")
        Log.i(TAG, "Backend Selected: GPU")

        var inference: LlmInference? = null
        var initEnd = 0L
        var initDuration = 0L
        var initSuccess = false
        var initErrorMsg: String? = null
        var ramAfter = 0L

        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(512)
                .setPreferredBackend(LlmInference.Backend.GPU)
                .build()

            inference = LlmInference.createFromOptions(context, options)
            initEnd = System.currentTimeMillis()
            initDuration = SystemClock.uptimeMillis() - initStartUptime
            initSuccess = true

            Log.i(TAG, "Initialization Completion Timestamp: $initEnd")
            Log.i(TAG, "Initialization Duration: ${initDuration} ms")
            Log.i(TAG, "INIT_SUCCESS: LlmInference instance created on GPU")

            ramAfter = readRamValueKb("MemAvailable:")
            Log.i(TAG, "Available RAM After Initialization: $ramAfter kB (~${ramAfter / 1024} MB)")
            Log.i(TAG, "RAM Consumed (Delta): ${ramBefore - ramAfter} kB (~${(ramBefore - ramAfter) / 1024} MB)")
        } catch (t: Throwable) {
            initEnd = System.currentTimeMillis()
            initDuration = SystemClock.uptimeMillis() - initStartUptime
            initErrorMsg = "${t.javaClass.name}: ${t.message}"
            Log.e(TAG, "INIT_FAILED: $initErrorMsg", t)
            ramAfter = readRamValueKb("MemAvailable:")

            val report = Report(
                modelPath = modelFile.absolutePath,
                modelExists = true,
                modelSizeBytes = modelFile.length(),
                ramAvailableBeforeKb = ramBefore,
                ramTotalKb = ramTotal,
                initStartTimestamp = initStart,
                initEndTimestamp = initEnd,
                initDurationMs = initDuration,
                backend = "GPU",
                initSuccess = false,
                initError = initErrorMsg,
                ramAvailableAfterKb = ramAfter
            )
            saveReport(context, report)
            return report
        }

        // STEP 3: PERFORM EXACTLY ONE INFERENCE
        val prompt = "Hello. Respond with exactly: Comai Gemma 4 is working."
        Log.i(TAG, "==================================================")
        Log.i(TAG, "STEP 3: PERFORMING SINGLE INFERENCE TEST")
        Log.i(TAG, "Prompt: \"$prompt\"")

        var inferenceLatency = 0L
        var responseText: String? = null
        var inferenceSuccess = false
        var inferenceErrorMsg: String? = null

        try {
            val inferStart = SystemClock.uptimeMillis()
            responseText = inference.generateResponse(prompt)
            inferenceLatency = SystemClock.uptimeMillis() - inferStart
            inferenceSuccess = true

            Log.i(TAG, "Inference Latency: ${inferenceLatency} ms")
            Log.i(TAG, "Generated Response: \"$responseText\"")
            Log.i(TAG, "INFERENCE_SUCCESS")
        } catch (t: Throwable) {
            inferenceErrorMsg = "${t.javaClass.name}: ${t.message}"
            Log.e(TAG, "INFERENCE_FAILED: $inferenceErrorMsg", t)
        } finally {
            try {
                inference.close()
                Log.i(TAG, "LlmInference closed cleanly.")
            } catch (e: Exception) {
                Log.w(TAG, "Error closing LlmInference: ${e.message}")
            }
        }

        val report = Report(
            modelPath = modelFile.absolutePath,
            modelExists = true,
            modelSizeBytes = modelFile.length(),
            ramAvailableBeforeKb = ramBefore,
            ramTotalKb = ramTotal,
            initStartTimestamp = initStart,
            initEndTimestamp = initEnd,
            initDurationMs = initDuration,
            backend = "GPU",
            initSuccess = initSuccess,
            initError = initErrorMsg,
            ramAvailableAfterKb = ramAfter,
            inferenceLatencyMs = inferenceLatency,
            inferenceResponse = responseText,
            inferenceSuccess = inferenceSuccess,
            inferenceError = inferenceErrorMsg
        )

        saveReport(context, report)
        Log.i(TAG, "==================================================")
        Log.i(TAG, "REPORT_SUMMARY:\n${report.toJson()}")
        Log.i(TAG, "==================================================")
        return report
    }

    private fun resolveModelPath(context: Context): String {
        val appSpecificDir = context.getExternalFilesDir("models")
        val appSpecificFile = File(appSpecificDir, "gemma-4-E4B-it.litertlm")
        if (appSpecificFile.exists()) return appSpecificFile.absolutePath

        val emulatedStorage = File("/storage/emulated/0/Android/data/com.comai/files/models/gemma-4-E4B-it.litertlm")
        if (emulatedStorage.exists()) return emulatedStorage.absolutePath

        val directFile = File(DEFAULT_MODEL_PATH)
        if (directFile.exists()) return directFile.absolutePath

        return DEFAULT_MODEL_PATH
    }

    private fun readRamValueKb(prefix: String): Long {
        return try {
            File("/proc/meminfo").bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line.startsWith(prefix)) {
                        val parts = line.split("\\s+".toRegex())
                        if (parts.size >= 2) {
                            return@useLines parts[1].toLongOrNull() ?: -1L
                        }
                    }
                }
                -1L
            }
        } catch (e: Exception) {
            -1L
        }
    }

    private fun saveReport(context: Context, report: Report) {
        try {
            val reportFile = File(context.getExternalFilesDir(null), "gemma_gpu_test_report.json")
            reportFile.writeText(report.toJson())
            Log.i(TAG, "Saved test report to: ${reportFile.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save test report file: ${e.message}")
        }
    }
}
