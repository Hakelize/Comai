# Comai Project Record: Gemma 4 E4B, Qualcomm NPU Audit & Build Integration

**Date**: September 12, 2026  
**Project**: Comai / LIFELOOP (`D:\hakelize\Comai`)  
**Target Hardware**: iQOO (Snapdragon 8 Elite / SM8850, Hexagon v81 NPU, 16 GB RAM)  
**ADB Serial**: `10BFAT1STC000Z9`  

---

## 1. Summary of Changes & Fixes

### A. Build Failure & Merge Conflict Resolutions
1. **`VoiceInteractionManager.kt`**: Restored missing `isOnDeviceAvailable()` reference after branch merge.
2. **`OnboardingTest.kt`**: Implemented `getUserProfileFlow()` in `FakeUserProfileDao` to satisfy updated interface contract; fixed all unit test compilation errors.
3. **`AndroidManifest.xml`**: Merged conflicts between `origin/main` and `arch`:
   - Kept `com.comai.scheduling.ScheduleAlarmReceiver` and exact alarm permissions (`SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`).
   - Kept `.engine.gemma.GemmaTestReceiver` for isolated diagnostic testing.
4. **`NavGraph.kt`**: Removed extraneous `onLanguageChanged = onLanguageChanged` parameter passed to `VoiceHomeScreen` pulled from PR #9.
5. **Build Verification**:
   - Both `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` pass 100% cleanly (0 errors).

---

## 2. Gemma 4 E4B Controlled GPU Test Results

* **Model**: `gemma-4-E4B-it.litertlm` (3,659,530,240 bytes)
* **Runtime**: `com.google.mediapipe:tasks-genai:0.10.35`
* **Test Path**: `/sdcard/Android/data/com.comai/files/models/gemma-4-E4B-it.litertlm`
* **Baseline System Memory**: MemAvailable ~7.38 GB (out of 15.58 GB)
* **Findings**:
  - Model parsed 11 sections successfully.
  - 2,712 out of 2,712 nodes delegated to OpenCL (`ML_DRIFT_CL`).
  - Peak Graphics memory allocated: **~3.03 GB**.
  - Peak Native heap reached: **~4.97 GB**.
  - Peak Process PSS reached: **~3.39 GB**.
  - **Outcome**: Terminated by Android Low Memory Killer:
    ```
    am_kill : [0, 17313, com.comai, 707, low memory, 3171836]
    ```
  - **Conclusion**: GPU execution of Gemma 4 E4B on this device is not viable due to system-level RAM thresholds.

---

## 3. Qualcomm NPU (HTP / SM8850) Technical Feasibility

### Phase 1 Audit Summary:
1. **Model Incompatibility**: The existing `.litertlm` artifact contains generic TFLite flatbuffers for OpenCL GPU dynamic compilation and CPU. It contains **no Qualcomm QNN context binaries (`.bin`), no serialized HTP graphs, and no Hexagon delegate bytecode**.
2. **API Limitation**: `tasks-genai:0.10.35` public API exposes only `DEFAULT`, `CPU`, and `GPU`. There is no NPU or HTP backend selector in the public Java/Kotlin bindings.
3. **Qualcomm Dispatch**: While the device has `/vendor/lib64/hw/libQnnHtp.so`, third-party applications are blocked by Android SELinux `classloader-namespace` restrictions from directly loading it. Required libraries must be packaged in the APK.
4. **AOT Requirement**: Compiling an LLM graph for Hexagon NPU requires host-based ahead-of-time (AOT) toolchains (QAIRT SDK v2.28+, Bazel, Hexagon SDK). On-device JIT compilation of 3.4 GB models is impossible and exhausts device memory.
5. **No Official Path for E4B**: Neither Google AI Edge nor Qualcomm AI Hub provides an NPU deployment path or precompiled binary for Gemma 4 E4B. Official samples target GPU (`--backend=gpu`).

---

## 4. Teammate Model Configuration & Development Workflow

### A. Zero-Friction UI & Feature Development
Teammates working on UI, Scheduling, Voice, or Database do **not** need the 3.4 GB model file.
* The app automatically defaults to **`MockEngine`**.
* Covers all 7 MVP scenarios (morning greeting, commute traffic, tribe events, lunch, overtime check-in, night reminder, memory recall).
* Can be built and run on any emulator or physical device.

### B. Auto-Fallback Engine Pattern
To ensure the app never crashes when model files are missing:
```kotlin
val modelFile = File(context.getExternalFilesDir("models"), "gemma-4-E4B-it.litertlm")

val aiEngine: AIEngine = if (modelFile.exists() && modelFile.length() > 0) {
    // Instantiate real on-device engine
    RealLlmEngine(context, modelFile.absolutePath)
} else {
    // Fallback to MockEngine for instant development
    MockEngine()
}
```

### C. Device Push Command (For Testing Real Model)
```bash
adb shell mkdir -p /sdcard/Android/data/com.comai/files/models
adb push gemma-4-E4B-it.litertlm /sdcard/Android/data/com.comai/files/models/
```
*(Note: Use Gemma 2 2B-IT or Llama 3.2 1B-IT for devices with 8 GB or 12 GB RAM to remain well within safe memory limits).*
