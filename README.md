# Comai

Comai is a privacy-first, voice-driven personal AI companion app built natively for Android. It understands a user's daily context—including routines, personal schedules, and device activity—to provide helpful assistance without needing repetitive instructions. All core intelligence, personal memories, and schedules run locally on the physical device without relying on cloud servers.

---

## Features

- 🎙️ **Voice Interaction** – Push-to-talk voice capture using Android's native `SpeechRecognizer`, featuring real-time audio wave visualization (animated voice orb) and dynamic spoken greetings.
- 💬 **Chat** – Full-screen conversational text interface with message bubbles, typing indicators, quick suggestion chips, and edge-to-edge keyboard support.
- 👤 **Personal Profile** – Store and edit personal details (name, gender, occupation, routine timings) and app preferences safely on the device.
- 📅 **Personal Schedule** – Add, edit, and toggle daily tasks and reminders with custom timing.
- 🔔 **Notifications & Alarms** – Choose between standard notifications or high-priority alarms with sound using Android `AlarmManager`.
- 🩸 **Menstrual Cycle Tracking** – Private, on-device cycle tracking and period estimation inside Personal Schedule, dynamically displayed for female profiles.
- 🌐 **Multilingual Voice** – Built-in support for 6 language profiles: English (India), Tamil, Telugu, Hindi, Malayalam, and Tanglish (Tamil-English code switching).
- 🧠 **Context Awareness** – Background context engine tracks routine baselines (wake, commute, return, sleep), detects schedule deviations, and calculates confidence scores.
- 📱 **Digital Activity** – Tracks daily screen time, app usage categories, top-used apps, and awake/inactive device periods using Android `UsageStatsManager`.
- 🧠 **AI Assistant** – Intelligent assistant that prioritizes user intent and retrieves saved personal facts to generate structured responses.
- 🔒 **Local-first Privacy** – User profile data, memories, schedules, and activity statistics remain strictly on the phone in local SQLite storage.

---

## Main Screens

- **Home (Voice Home)** – Main hub with the animated voice orb, contextual spoken greeting, live speech subtitles, and quick mode navigation.
- **Chat** – Messaging screen with conversation history, push-to-talk voice input bar, and clear memory shortcuts.
- **Profile** – User profile management for name, gender, routine timings, voice language selection, and permission status.
- **Personal Schedule** – Daily routine timeline, custom tasks, reminder toggles (`[ Notification ]` vs `[ Alarm ]`), and cycle tracking.
- **Digital Activity** – Dashboard showing total screen time, app category breakdown, top apps, and device inactivity intervals.
- **Onboarding** – Multi-step setup wizard covering feature introduction, language choice, voice setup, routine input, and privacy pledges.
- **Developer & RAM Dashboard** – Diagnostic console displaying real-time sensor context, rule triggers, confidence ratings, and test scenario overrides.
- **Device Capability Audit** – System audit screen categorizing 20 Android platform capabilities across hardware, runtime permissions, and system boundaries.
- **Audio Canvas** – Dedicated voice screen with a pulsing visual orb driven by real-time speech sound levels.

---

## How Comai Works

```text
User (Voice or Text)
        │
        ▼
Input Processing (SpeechRecognizer / Text Input)
        │
        ▼
Context & Memory Synthesis (Local Routine + Screen Activity + Saved Facts)
        │
        ▼
AI Engine Processing (Rule Evaluation & Structured Response Generation)
        │
        ▼
Output Action (Android TTS Voice Readout / Chat UI / Alarm & Notification)
```

---

## Technology Used

- **Language & Platform**: Kotlin, Android (Min SDK 29, Target SDK 34)
- **UI Framework**: Jetpack Compose, Material 3, Material Icons Extended
- **Architecture**: Android Architecture Components, ViewModel, Navigation Compose, Coroutines & Flow
- **Local Storage**: SQLite via Room Database 2.6.1 (with KSP), Android SharedPreferences
- **Speech & Audio**: Android `SpeechRecognizer` (on-device preferred API 31+), Android `TextToSpeech` (TTS)
- **Scheduling & Alarms**: Android `AlarmManager` (`SCHEDULE_EXACT_ALARM`), `NotificationManager` (Standard and Alarm audio channels)
- **Background & Monitoring**: Android Foreground Service (`specialUse|dataSync`), `WorkManager`, `UsageStatsManager`, `AppOpsManager`
- **Data Serialization**: Google Gson, Kotlinx Serialization
- **Edge AI Integration**: Google MediaPipe GenAI LLM Inference SDK (`tasks-genai:0.10.35`) for LiteRT-LM models

---

## Privacy

- **Push-to-Talk Only**: Microphone capture activates only when the user explicitly taps the mic or orb. There is no background audio recording or always-on listening.
- **On-Device Storage**: Personal profiles, chat messages, memories, schedules, and health data are stored locally in Room SQLite database and SharedPreferences.
- **Zero Cloud Dependency**: Critical-path operations, context detection, and voice interactions run directly on the physical device without sending personal data to external servers.
- **Safe System APIs**: Device activity is read using standard Android `UsageStatsManager` permissions without using intrusive accessibility services or non-SDK workarounds.

---

## Project Status

### Implemented:
- Push-to-talk voice pipeline with audio wave animations, dynamic greetings, and feedback loop prevention.
- Conversational chat UI with keyboard IME handling and message bubble history.
- Multi-step onboarding flow and editable user profile management.
- Personal Schedule management with exact `AlarmManager` integration and notification/alarm selection.
- Female-specific Menstrual Cycle tracking card and local calculator.
- Multilingual voice support across 6 Indian and English language profiles.
- Digital activity intelligence (screen time, top apps, awake/inactive state detection).
- Unified Room SQLite database (`comai_core_db`) for profiles, daily logs, personal memories, and proactive events.
- Device capability audit dashboard covering 20 platform features.

### Partially Implemented:
- **On-Device LLM (MediaPipe / Gemma LiteRT-LM)**: MediaPipe GenAI library is integrated and the GPU initialization/benchmarking tester (`GemmaGpuTester`) is implemented. The active production app currently runs the deterministic `MockEngine` for stability and offline testing.
- **Community Event Database**: SQLite database schema and DAO exist for offline community events, but live event data ingestion is pending.

---

## Setup

### Prerequisites
- Android Studio Ladybug (2024.2+) or later
- JDK 17 (Java 17)
- Android device or emulator running Android 10 (API 29) or higher

### Build & Run
1. Open the project root in Android Studio or terminal.
2. Build the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
   *(On Windows PowerShell: `.\gradlew.bat assembleDebug`)*
3. Install onto a connected Android device:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## Team

- **Rakesh** – Frontend Lead (UI, Jetpack Compose, Voice Interaction, Screens)
- **Ram** – Data & Logic Lead (Context Engine, Background Sensing, Rule Matrix, Room Database)
- **Harish** – Edge AI Lead (MediaPipe Setup, On-Device Model Compilation & GPU Benchmarking)