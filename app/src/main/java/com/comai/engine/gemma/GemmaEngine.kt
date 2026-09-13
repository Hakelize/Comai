package com.comai.engine.gemma

import android.content.Context
import android.util.Log
import com.comai.engine.AIEngine
import com.comai.engine.MockEngine
import com.comai.engine.models.AIResponse
import com.comai.engine.models.ContextInput
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Production on-device LLM engine powered by Google MediaPipe / LiteRT-LM
 * running Gemma 4 E4B instruction-tuned model (`gemma-4-E4B-it.litertlm`).
 *
 * Provides accelerated GPU inference with automatic CPU fallback and
 * non-blocking background initialization.
 */
class GemmaEngine(
    private val context: Context,
    private val fallbackEngine: AIEngine = MockEngine()
) : AIEngine {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var inference: LlmInference? = null

    @Volatile
    private var isInitializing = false

    @Volatile
    private var initializationError: String? = null

    @Volatile
    private var activeBackend: String = "NOT_INITIALIZED"

    @Volatile
    private var resolvedModelPath: String? = null

    init {
        initializeAsync()
    }

    /**
     * Resolves all candidate storage paths on the device where the model may reside.
     */
    fun getCandidateModelPaths(): List<String> {
        val paths = mutableListOf<String>()

        // 1. App-specific external models directory (Preferred MediaPipe path)
        context.getExternalFilesDir("models")?.let { dir ->
            paths.add(File(dir, MODEL_FILENAME).absolutePath)
        }

        // 2. Standard device storage paths
        paths.add("/storage/emulated/0/Android/data/com.comai/files/models/$MODEL_FILENAME")
        paths.add("/sdcard/Android/data/com.comai/files/models/$MODEL_FILENAME")
        paths.add("/storage/emulated/0/models/$MODEL_FILENAME")
        paths.add("/sdcard/models/$MODEL_FILENAME")

        // 3. Internal app files directory
        paths.add(File(context.filesDir, "models/$MODEL_FILENAME").absolutePath)

        return paths.distinct()
    }

    /**
     * Asynchronously loads and initializes LiteRT-LM LlmInference.
     */
    fun initializeAsync() {
        if (inference != null || isInitializing) return
        isInitializing = true

        scope.launch {
            try {
                val candidatePaths = getCandidateModelPaths()
                var modelFile: File? = null

                for (path in candidatePaths) {
                    val f = File(path)
                    if (f.exists() && f.length() > 0) {
                        modelFile = f
                        Log.i(TAG, "Located model file at: ${f.absolutePath} (${f.length()} bytes)")
                        break
                    }
                }

                if (modelFile == null) {
                    // Check if model is bundled inside APK assets
                    val internalDest = File(context.filesDir, "models/$MODEL_FILENAME")
                    if (isAssetPresent("models/$MODEL_FILENAME")) {
                        Log.i(TAG, "Model found in APK assets! Extracting to ${internalDest.absolutePath}...")
                        internalDest.parentFile?.mkdirs()
                        context.assets.open("models/$MODEL_FILENAME").use { input ->
                            internalDest.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (internalDest.exists() && internalDest.length() > 0) {
                            modelFile = internalDest
                            Log.i(TAG, "Successfully extracted model from APK assets: ${internalDest.length()} bytes")
                        }
                    }
                }

                if (modelFile == null) {
                    val err = "Gemma 4 model file ($MODEL_FILENAME) not found in candidate paths ($candidatePaths) or APK assets."
                    Log.w(TAG, err)
                    initializationError = err
                    isInitializing = false
                    return@launch
                }

                resolvedModelPath = modelFile.absolutePath
                Log.i(TAG, "Starting LiteRT-LM initialization with: ${modelFile.absolutePath}")

                // Attempt GPU initialization first
                try {
                    Log.i(TAG, "Configuring LlmInference with GPU backend...")
                    val gpuOptions = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelFile.absolutePath)
                        .setMaxTokens(512)
                        .setPreferredBackend(LlmInference.Backend.GPU)
                        .build()

                    val instance = LlmInference.createFromOptions(context, gpuOptions)
                    inference = instance
                    activeBackend = "GPU"
                    initializationError = null
                    Log.i(TAG, "SUCCESS: Gemma 4 LiteRT-LM initialized on GPU backend!")
                } catch (gpuEx: Throwable) {
                    Log.w(TAG, "GPU backend failed (${gpuEx.message}). Falling back to CPU backend...")
                    try {
                        val cpuOptions = LlmInference.LlmInferenceOptions.builder()
                            .setModelPath(modelFile.absolutePath)
                            .setMaxTokens(512)
                            .setPreferredBackend(LlmInference.Backend.CPU)
                            .build()

                        val instance = LlmInference.createFromOptions(context, cpuOptions)
                        inference = instance
                        activeBackend = "CPU"
                        initializationError = null
                        Log.i(TAG, "SUCCESS: Gemma 4 LiteRT-LM initialized on CPU backend!")
                    } catch (cpuEx: Throwable) {
                        Log.e(TAG, "CPU backend also failed: ${cpuEx.message}", cpuEx)
                        initializationError = "GPU failed: ${gpuEx.message}; CPU failed: ${cpuEx.message}"
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Unexpected error initializing Gemma model: ${t.message}", t)
                initializationError = t.message
            } finally {
                isInitializing = false
            }
        }
    }

    override suspend fun process(context: ContextInput): AIResponse {
        val currentInference = inference
        if (currentInference == null) {
            Log.d(TAG, "Gemma engine not ready (backend=$activeBackend, error=$initializationError). Delegating to fallback engine.")
            return fallbackEngine.process(context)
        }

        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildGemmaPrompt(context)
                Log.d(TAG, "Executing Gemma 4 on-device inference ($activeBackend)...")
                val startTime = System.currentTimeMillis()
                val rawOutput = currentInference.generateResponse(prompt)
                val latency = System.currentTimeMillis() - startTime
                Log.i(TAG, "Gemma 4 generation finished in ${latency}ms")

                val cleaned = sanitizeResponse(rawOutput)
                val detectedAction = determineAction(context.task, cleaned)
                AIResponse(
                    action = detectedAction,
                    speech = cleanForTts(cleaned),
                    displayText = cleaned,
                    metadata = mapOf(
                        "engine" to "gemma-4-E4B-it",
                        "backend" to activeBackend,
                        "scenario" to detectedAction,
                        "latency_ms" to latency.toString(),
                        "model_path" to (resolvedModelPath ?: "")
                    )
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Error executing Gemma inference: ${t.message}. Falling back.", t)
                fallbackEngine.process(context)
            }
        }
    }

    private fun buildGemmaPrompt(input: ContextInput): String {
        val sb = StringBuilder()
        sb.append("<start_of_turn>user\n")
        sb.append(BASE_SYSTEM_PROMPT.trim())
        sb.append("\n\n--- BASE KNOWLEDGE & SCENARIOS ---\n")
        sb.append(BASE_SCENARIOS.trim())
        sb.append("\n\n--- CURRENT CONTEXT ---\n")
        if (input.contextSignals.time.isNotBlank()) {
            sb.append("Time of day: ${input.contextSignals.time}\n")
        }
        if (input.contextSignals.location.isNotBlank()) {
            sb.append("Current Location: ${input.contextSignals.location}\n")
        }
        if (input.contextSignals.routineDeviation) {
            sb.append("Routine Status: Deviation detected from usual schedule\n")
        }
        val retrieved = input.retrievedData?.trim()
        val memContextList = input.memoryContext
        if (!retrieved.isNullOrBlank()) {
            sb.append("Stored Personal Memories:\n$retrieved\n")
        } else if (!memContextList.isNullOrEmpty()) {
            val formatted = memContextList.joinToString("\n") { "- $it" }
            sb.append("Stored Personal Memories:\n$formatted\n")
        } else {
            sb.append("Stored Personal Memories: None recorded yet\n")
        }
        sb.append("\nUser message: ${input.task}\n")
        sb.append("Respond as Comai, adopting the companion persona and base knowledge above. Keep the response warm, natural, and concise (under 40 words).<end_of_turn>\n")
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    private fun sanitizeResponse(raw: String): String {
        var text = raw.trim()
        text = text.replace("<start_of_turn>model", "")
            .replace("<start_of_turn>user", "")
            .replace("<end_of_turn>", "")
            .trim()
        return text.ifBlank { "I'm right here with you. How can I help?" }
    }

    private fun cleanForTts(text: String): String {
        return text.replace(Regex("[*#_`~]"), "")
            .replace(Regex("[\\p{So}\\p{Cn}]"), "")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private fun determineAction(task: String, responseText: String = ""): String {
        val combined = "$task $responseText".lowercase()
        return when {
            combined.contains("memory") || combined.contains("remember") || combined.contains("know about me") || combined.contains("recall") -> "memory_recall"
            combined.contains("morning") || combined.contains("wake") || combined.contains("good morning") -> "greeting"
            combined.contains("commute") || combined.contains("traffic") || combined.contains("route") || combined.contains("delay") -> "commute"
            combined.contains("tribe") || combined.contains("club") || combined.contains("meetup") || combined.contains("running") || combined.contains("event") -> "tribe_event"
            combined.contains("lunch") || combined.contains("food") || combined.contains("eat") || combined.contains("hungry") -> "reminder"
            combined.contains("medication") || combined.contains("medicine") || combined.contains("wind down") || combined.contains("sleep well") || combined.contains("good night") -> "medication"
            combined.contains("check-in") || combined.contains("leaving later") || combined.contains("overtime") || combined.contains("still at the office") -> "check_in"
            combined.contains("reminder") || combined.contains("notify") || combined.contains("set a reminder") -> "reminder"
            else -> "general"
        }
    }

    private fun isAssetPresent(modelName: String = MODEL_FILENAME): Boolean {
        return try {
            val list = context.assets.list("models") ?: emptyArray()
            list.contains(modelName)
        } catch (e: Exception) {
            false
        }
    }

    override fun isReady(): Boolean = inference != null

    override fun engineName(): String {
        return if (inference != null) {
            "Gemma 4 E4B ($activeBackend)"
        } else if (isInitializing) {
            "Gemma 4 E4B (Initializing...)"
        } else {
            "Mock Fallback (Model Missing)"
        }
    }

    fun getEngineStatus(): String = engineName()

    fun close() {
        try {
            inference?.close()
            inference = null
        } catch (e: Exception) {
            Log.w(TAG, "Error closing LlmInference: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "GemmaEngine"
        const val MODEL_FILENAME = "gemma-4-E4B-it.litertlm"

        private const val BASE_SYSTEM_PROMPT = """
You are Comai, a warm, caring, proactive on-device personal AI life companion.
All personal data and memories stay strictly private and local to the user's phone.
Your responses should be conversational, supportive, concise (under 40 words), and tailored to the user's daily life.
"""

        private const val BASE_SCENARIOS = """
Base knowledge and scenario guidelines:
1. Memory & Privacy: When user asks what you remember or know about them, summarize the saved memories clearly, and reassure them that all memories stay on their device and can be reviewed or deleted anytime. If there are no saved memories, tell them what kinds of things you can remember (commute habits, office location, music preferences).
2. Morning Routine: If morning or user wakes early, greet them warmly ("Good morning! You're up a bit earlier than usual today. Want me to adjust your morning routine?").
3. Commute & Traffic: For commute/traffic queries, suggest optimal departure times (e.g. 8:15) and advise on traffic delays near highway exits.
4. Tribe Finder: Suggest local community meetups (e.g., Evening Run Club meetup near the office at 6 PM with 14 attendees) and offer to set reminders.
5. Lunch & Breaks: Around lunchtime, offer quick nearby food options or help order from regular favorite spots.
6. Evening & Overtime: If working late or departing later than usual, ask thoughtful check-ins ("You're leaving later than usual today. How was work?").
7. Wind-down & Night: At bedtime, remind about evening medication, dimming down, and getting restful sleep ("Time to wind down. Don't forget your evening medication. Sleep well!").
8. Reminders: Confirm reminders clearly with live traffic departure alerts.
"""
    }
}
