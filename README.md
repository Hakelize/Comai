# Comai — AI Companion App

> Privacy-first, voice-driven AI companion built with **Native Android**, **Kotlin**, and **Jetpack Compose**.

---

## 📱 Project Overview & Evolution

Comai understands permitted ambient context (time, location, device state, routine), maintains **privacy-first, local-first personal memory**, audits real device capabilities, and interacts through single-tap **push-to-talk voice** backed by Android's native on-device SpeechRecognizer and Text-to-Speech (TTS).

The repository has progressed through three verified, on-device production phases:
- **Phase 1 (Context Engine Vertical Slice)**: Background context sensing (`ContextForegroundService`) → `SharedContextContract` → `ContextInput` → `AIEngine` / `MockEngine` → UI Chat Bubbles → `TTSManager` speech readout.
- **Phase 2 (Local Personal Memory)**: Local-first personal memory in Room SQLite (`MemoryEntity`, `MemoryDao`, `MemoryRepository`). Conservative extraction of explicit user statements, relevance-based retrieval injected into `ContextInput.retrievedData`, and strict user control (inspection and one-tap deletion).
- **Phase 3 (Device Capability & Voice Foundation)**:
  - **Capability Dashboard**: Audits 20 distinct system capabilities categorizing them strictly into Platform/Hardware, Runtime Permission, Special Access, and Restricted Behavior without non-SDK bypasses or intrusive accessibility services.
  - **Push-to-Talk Voice Pipeline**: Direct physical pipeline: Single-tap Mic → Android `SpeechRecognizer` (prefers on-device API 31+) → `ChatViewModel.sendMessage()` → `MockEngine` (manual user intent strictly prioritized over ambient context) → `TTSManager` (`UtteranceProgressListener`). Real-time RMS audio streaming connects to the animated Voice Canvas orb.

---

## 🏗️ Architecture & Package Structure

```
Comai/
├── app/src/main/
│   ├── AndroidManifest.xml                     # Foreground service, permissions, package visibility <queries>
│   ├── java/com/comai/
│   │   ├── ComaiApplication.kt                 # Application-level singletons (AIEngine, TTS, Room, Context, Voice)
│   │   ├── MainActivity.kt                     # ComponentActivity, Edge-to-Edge, permission launchers, Navigation
│   │   ├── capability/                         # Phase 3: Device Capability Audit
│   │   │   ├── DeviceCapability.kt             # Capability data models & 4-tier category classification
│   │   │   └── CapabilityAuditor.kt            # Safe system API checks for 20 device/platform capabilities
│   │   ├── contextengine/                      # Phase 1: Native Context Sensing & Rule Escalation
│   │   │   ├── context/                        # UserState, WakeSleepDetector, ActivityRecognitionManager
│   │   │   ├── contract/                       # SharedContextContract, ContractAssembler, ContractValidator
│   │   │   ├── db/                             # Room DB: UserProfile, DailyLog, CommunityEventDatabase
│   │   │   ├── rules/                          # RuleEngine & RuleDefinition (deterministic decision matrix)
│   │   │   ├── service/                        # ContextForegroundService & fallback workers
│   │   │   └── ContextRepository.kt            # Application-scoped bridge between Context Engine & UI/AI
│   │   ├── data/
│   │   │   ├── db/                             # Phase 2: Comai App Room Database
│   │   │   │   ├── AppDatabase.kt              # Room database definition with schema migrations
│   │   │   │   └── MemoryDao.kt                # Type-safe Room DAO for local memory persistence
│   │   │   ├── memory/                         # Phase 2: Local Personal Memory Engine
│   │   │   │   ├── MemoryEntity.kt             # Schema: key, category, value, confidence, timestamps
│   │   │   │   ├── MemoryRepository.kt         # Room-backed memory store with relevance query & purge
│   │   │   │   └── MemoryExtractor.kt          # Conservative parser for explicit user facts & preferences
│   │   │   └── models/
│   │   │       └── ChatMessage.kt              # Chat bubble entity with timestamps and role
│   │   ├── engine/                             # Core AI Engine Abstractions
│   │   │   ├── AIEngine.kt                     # Shared Kotlin interface for AI inference
│   │   │   ├── MockEngine.kt                   # Deterministic engine: prioritizes user intent & memory over ambient rules
│   │   │   └── models/
│   │   │       ├── ContextInput.kt             # Schema matching Context Engine JSON + retrieved memories
│   │   │       └── AIResponse.kt               # Structured typed response schema
│   │   ├── tts/
│   │   │   └── TTSManager.kt                   # Android TextToSpeech wrapper with UtteranceProgressListener
│   │   ├── voice/                              # Phase 3: Push-to-Talk Voice Foundation
│   │   │   └── VoiceInteractionManager.kt      # Single-tap push-to-talk, on-device recognition, RMS streaming
│   │   └── ui/
│   │       ├── navigation/NavGraph.kt          # Compose navigation (Chat, Audio, Dashboard, Capability)
│   │       ├── screens/
│   │       │   ├── audio/AudioCanvasScreen.kt  # Audio Canvas with dynamic RMS-driven pulsing orb
│   │       │   ├── capability/                 # Developer Capability Audit Dashboard (20 capabilities)
│   │       │   ├── chat/                       # Comai Chat screen, speech state banner, push-to-talk mic bar
│   │       │   └── dashboard/                  # Developer Override Console (time & scenario simulator)
│   │       └── theme/                          # Material 3 dark palette, typography, system bars
│   └── res/
│       ├── drawable/comai_avatar.png           # Comai robot avatar
│       ├── values/                             # Strings, themes, colors
│       └── mipmap/                             # Launcher icons
├── build.gradle.kts                            # AGP 8.5.0, Kotlin 1.9.24, Room, Coroutines, Compose BOM
└── gradle.properties                           # AndroidX, JVM configuration
```

---

## 🛠️ Prerequisites & Environment

1. **Android Studio**: Android Studio Ladybug (2024.2+) or later.
2. **JDK**: **Java 17** (ensure `JAVA_HOME` points to JDK 17).
3. **Android SDK**: Min SDK `29` (Android 10+), Compile / Target SDK `34` (Android 14+).
4. **Tested Hardware**: Verified on physical **Samsung Galaxy A15 5G** (`SM-A156E`, MediaTek Dimensity 6100+, 8GB RAM, Android 14/16 Preview API 36).

---

## 🚀 How to Build, Test, and Run

### 1. Build and Run Automated Tests
Run the comprehensive unit test suite (Context Engine, Local Memory, Voice Foundation, and AI Engine pipeline):
```powershell
$env:JAVA_HOME = "C:\Path\To\Java17"
.\gradlew.bat test
```
*Current test status: 36/36 unit tests passing across all suites.*

### 2. Assemble Debug APK
```powershell
.\gradlew.bat assembleDebug
```
The compiled APK is generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### 3. Install on Connected Device
```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.comai/.MainActivity
```

---

## 🌟 Key Feature Modules

### 1. Push-to-Talk Voice Pipeline (Phase 3)
- **Single-Tap Mic Button**: Positioned in the chat input bar. One tap activates speech recognition; automatically switches off when speech finishes.
- **Audio Feedback Loop Protection**: Automatically halts active TTS speech when the mic is engaged.
- **On-Device Speech Recognition**: Utilizes `SpeechRecognizer.createOnDeviceSpeechRecognizer(context)` when available on API 31+ for privacy and low latency.
- **Boundary Logging**: Emits structured logcat events (`MIC_REQUESTED`, `MIC_PERMISSION_GRANTED`, `SPEECH_RECOGNIZER_CREATED`, `MIC_STARTED`, `LISTENING_STARTED`, `AUDIO_CAPTURE_STARTED`, `TEXT_SUBMITTED`, `TTS_STARTED`, `TTS_COMPLETED`).
- **Dynamic Voice Orb**: The Voice Canvas screen (`AudioCanvasScreen`) animates in real-time driven by RMS sound pressure levels (`onRmsChanged`).

### 2. Privacy-First Local Memory (Phase 2)
- **Zero Cloud Transmission**: All memories reside strictly in the app's encrypted local SQLite database via Room (`AppDatabase`).
- **Conservative Fact Extraction**: Stored facts require explicit, persistent intent (e.g. *"I work at Tech Hub Office"*, *"I usually leave work around 5:30"*). Transitory or uncertain statements are discarded.
- **Relevance Retrieval**: Relevant memories are queried using token relevance and injected into `ContextInput.retrievedData` before the AI engine evaluates the prompt.
- **User Transparency & Deletion**:
  - Asking *"what do you know about me"* or *"what do you recall"* prints stored facts.
  - Sending *"forget my memory"* or *"clear memory"* permanently wipes local memory records.

### 3. Device Capability Audit Dashboard (Phase 3)
- Accessible via the top bar menu or route `Routes.CAPABILITY`.
- Categorizes 20 system capabilities across 4 transparent tiers:
  1. **Platform / Hardware**: Microphone, SpeechRecognizer, On-Device Recognizer, TTS, Battery, Network, Headphones, Navigation intent.
  2. **Runtime Permission**: Fine/Coarse Location, Background Location, Activity Recognition, Bluetooth, Calendar, Contacts.
  3. **Special User Access**: Usage Stats (`PACKAGE_USAGE_STATS`), Notification Listener (`BIND_NOTIFICATION_LISTENER_SERVICE`).
  4. **System Restrictions**: Background execution boundaries and non-intrusive Accessibility audit.
- Zero non-SDK / hidden API bypasses; no unauthorized background services.

### 4. Deterministic Context Engine (Phase 1)
- Runs a low-overhead foreground service (`ContextForegroundService`).
- Senses user activity and time/location state, producing a strictly validated `SharedContextContract`.
- Directly escalates contextual events into the chat timeline accompanied by TTS voice notifications.

---

## 🔒 Privacy & Android Restriction Guarantees

- **No Always-On Microphone**: Comai operates strictly on explicit user push-to-talk. It does not record audio in the background or maintain passive wake-word listeners.
- **No Intrusive Accessibility Services**: Accessibility capabilities are audited for system state only; no service is registered or active.
- **No Cloud AI Transmission**: In-flight queries and local memory never leave the physical device.