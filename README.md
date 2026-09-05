# Camoi (Comai) — AI Companion App

> **LIFELOOP Frontend Module** — Privacy-first, voice-driven AI companion built with **Native Android**, **Kotlin**, and **Jetpack Compose**.

---

## 📱 Codebase Overview & Architecture

The app is decoupled into clear layers adhering to the project's shared integration contract:

```
Comai/
├── app/src/main/
│   ├── AndroidManifest.xml                  # Permissions (Audio, Location, Foreground) & App Entry
│   ├── java/com/lifeloop/comai/
│   │   ├── CamoiApplication.kt              # App-level singleton (AIEngine & TTSManager lifecycle)
│   │   ├── MainActivity.kt                  # ComponentActivity, Edge-to-Edge & ViewModel factory
│   │   ├── engine/
│   │   │   ├── AIEngine.kt                  # Shared Kotlin interface for AI inference
│   │   │   ├── MockEngine.kt                # Scenario-aware hardcoded engine (Sprint 1)
│   │   │   └── models/
│   │   │       ├── ContextInput.kt          # Input schema matching Context Engine JSON
│   │   │       └── AIResponse.kt            # Structured typed response schema
│   │   ├── tts/
│   │   │   └── TTSManager.kt                # Android TextToSpeech wrapper with StateFlow
│   │   ├── data/models/
│   │   │   └── ChatMessage.kt               # Chat bubble entity
│   │   └── ui/
│   │       ├── theme/                       # Material 3 dark palette, typography, system bars
│   │       ├── navigation/NavGraph.kt       # Navigation routes (chat, audio, dashboard)
│   │       └── screens/
│   │           ├── chat/                    # Camoi Chat screen, bubbles, input bar
│   │           ├── audio/                   # Audio Canvas with pulsing glowing orb & voice state
│   │           └── dashboard/               # Hidden Developer Override Console
│   └── res/
│       ├── drawable/camoi_avatar.png        # Camoi AI robot avatar
│       ├── values/ (strings.xml, themes.xml)# Theme & string resources
│       └── mipmap/                          # Adaptive app launcher icons
├── build.gradle.kts                         # AGP 8.5.0, Kotlin 1.9.24
├── settings.gradle.kts                      # Repositories & project include
└── gradle.properties                        # AndroidX, JVM memory & parallel build flags
```

---

## 🛠️ Prerequisites

1. **Android Studio**: Install **Android Studio Ladybug (2024.2+)** or **Jellyfish / Iguana**.
2. **JDK**: **Java 17** (bundled automatically inside Android Studio).
3. **Android SDK**: Min SDK `29` (Android 10+), Target SDK `34` (Android 14+).
4. **Device/Target**:
   - Android Virtual Device (AVD Emulator) running **API 29+**, OR
   - A physical Android phone (e.g., iQOO / vivo running OriginOS or any Android 10+ device).

---

## 🚀 How to Execute the Code

### Method 1: Running with Android Studio (Recommended)

1. **Open the Project:**
   - Launch **Android Studio**.
   - Click **File** > **Open...**
   - Select the `Comai` folder:
     ```
     c:\Users\moker\Desktop\RAKI\Project-Comai\Comai
     ```
2. **Sync Gradle:**
   - Android Studio will automatically recognize the Gradle build files and sync the dependencies (Compose BOM `2024.06.00`, Material 3, Navigation, Gson).
   - Wait for the status bar at the bottom to state **"BUILD SUCCESSFUL"**.
3. **Select a Run Target:**
   - **Emulator**: Open **Device Manager** (`Tools` > `Device Manager`) and launch an emulator (Pixel 7/8 with API 29 or higher).
   - **Physical Device**:
     1. On your phone, enable Developer Mode: **Settings** > **About Phone** > tap **Build Number** 7 times.
     2. Enable **USB Debugging** under **Developer Options**.
     3. Connect phone via USB cable and authorize the prompt on the screen.
4. **Build & Run:**
   - Click the green **Run (▶)** button in the top toolbar (or press `Shift + F10`).
   - The app will compile, install, and launch **Camoi** on your screen.

---

### Method 2: Running via Command Line (CLI)

If you have the Android SDK and Java 17 configured in your system environment (`ANDROID_HOME` & `JAVA_HOME`):

1. Open PowerShell / Terminal in the project directory:
   ```powershell
   cd c:\Users\moker\Desktop\RAKI\Project-Comai\Comai
   ```
2. Set your local Android SDK location in `local.properties`:
   ```properties
   sdk.dir=C\:\\Users\\<YourUsername>\\AppData\\Local\\Android\\Sdk
   ```
3. Build the debug APK:
   ```powershell
   # If using system gradle:
   gradle assembleDebug
   ```
4. Install and start on connected device/emulator:
   ```powershell
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   adb shell am start -n com.lifeloop.comai/.MainActivity
   ```

---

## 🧪 Interactive Features to Test

### 1. Camoi Multimodal Chat
- **Appearance**: Dark theme (`#0D0F14`), custom robot profile picture, top bar with "Online" status dot, electric blue user bubbles (`#2979FF`), charcoal AI bubbles (`#1E2028`), and message timestamps.
- **Interactions**:
  - Send messages by typing in the message box.
  - Test scenario keywords:
    - `"morning"` or `"wake up"` → Morning routine suggestion
    - `"commute"` or `"traffic"` → Traffic delay alert & route recommendations
    - `"tribe"` or `"running club"` → Tribe Finder community event suggestion
    - `"lunch"` → Lunch routine assistance
    - `"evening"` or `"leaving late"` → Office departure check-in
    - `"night"` or `"medication"` → Wind-down & medicine reminder
    - `"what do you remember about me"` → Privacy memory inspection & deletion option
- **Voice Response**: Whenever Camoi replies, Android's **TextToSpeech (TTS)** engine reads the message aloud.

### 2. Audio Canvas (Voice Screen)
- Tap the **Phone Call icon** in the top bar or the **Mic icon** in the input bar.
- Shows a central **pulsing, glowing orb** with animated state transitions:
  - `IDLE` (Cyan glow)
  - `LISTENING` (Green glow)
  - `PROCESSING` (Amber glow)
  - `SPEAKING` (Purple glow + TTS voice readout)

### 3. Developer Override Console (Hidden Menu)
- Access it by **long-pressing the "Camoi" top title** or tapping the **three dots (⋮)** in the top right.
- Features:
  - **Context Time Overrides**: Toggle between `morning`, `noon`, `evening`, and `night`.
  - **Routine Deviation Switch**: Simulate the user waking early or staying late at work.
  - **One-Tap Demo Story Triggers**: Instantly fire all 7 MVP demo story beats during presentations.

---

## 🛡️ OriginOS / vivo Device Optimization Checklist
When running on physical vivo / iQOO devices:
1. **iManager** > **Autostart** > Enable for **Camoi**.
2. **Settings** > **Battery** > **Background power consumption** > Set to **"Allow high background power consumption"**.
3. **Recent Apps Screen** > Swipe down on the Camoi app card > Tap the **Lock icon** to prevent OS cleanup.