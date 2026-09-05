# COMAI Context Engine — Testing Guide (`here.md`)

This guide explains step-by-step how to test and verify the **COMAI Context Engine (Person 1)** module.

---

## 🎯 Quick Start: 3 Ways to Test the Module

### Method 1: Programmatically via `ContextTestHarness` (Fastest)

Run the full simulation sequence directly in Kotlin code. This executes all **4 MVP demo scenarios** and generates validated Shared JSON Contracts:

```kotlin
import com.comai.contextengine.testharness.ContextTestHarness

fun main() {
    val harness = ContextTestHarness()
    val jsonOutputs: List<String> = harness.runFullSimulationSequence()
    
    // Prints and returns 4 validated JSON contract strings formatted for Person 2
}
```

---

### Method 2: Running Unit Tests in Android Studio / IntelliJ

1. Open `d:\comai` in Android Studio or IntelliJ IDEA.
2. Open [`ContextEngineTest.kt`](file:///d:/comai/app/src/test/java/com/comai/contextengine/ContextEngineTest.kt).
3. Right-click anywhere in the file and select **Run 'ContextEngineTest'**.

#### Tests Included:
- `testWakeTimeDeviationTrigger()`: Verifies rule escalation when wake time deviates by >30 mins.
- `testTribeFinderNonWorkDayTrigger()`: Verifies non-workday commute window triggers Tribe Finder database lookup.
- `testContractValidationSuccess()`: Validates schema formatting against strict constraints.
- `testContractValidationFailureOnBlankField()`: Ensures `InvalidContractException` is thrown if mandatory JSON fields are missing.
- `testFullTestHarnessSimulationSequence()`: Validates all 4 scenario pipelines end-to-end.

---

### Method 3: Simulating Live Sensor Events

To simulate custom sensor events dynamically (e.g. wake, sleep, still duration, walking transitions):

```kotlin
import com.comai.contextengine.context.ActivityRecognitionManager
import com.comai.contextengine.context.DetectedContextEvent
import com.comai.contextengine.context.DetectedEventType

val manager = ActivityRecognitionManager(context)
manager.startMonitoring()

// Inject a simulated wake event
manager.injectSimulatedEvent(
    DetectedContextEvent(
        timestampMs = System.currentTimeMillis(),
        type = DetectedEventType.WAKE,
        confidence = 100,
        details = "Simulated alarm dismissal"
    )
)

// Fetch formatted user state for JSON contract
val userState = manager.getCurrentUserState()
println(userState.toContractSummary())
// Output: "awake since 07:00 (0h 1m), current activity: STILL"
```

---

## 🔌 Person 2 (LLM Layer) Listener Integration Test

Person 2 connects their LLM engine to receive escalated context events directly from the Foreground Service:

```kotlin
import com.comai.contextengine.service.ContextForegroundService
import com.comai.contextengine.service.ContextOutputListener
import com.comai.contextengine.contract.SharedContextContract

service.registerOutputListener(object : ContextOutputListener {
    override fun onContextEscalated(jsonContract: String, contractObject: SharedContextContract) {
        println("Received escalated signal for task: ${contractObject.task}")
        // Person 2 LLM Prompt invocation goes here
    }
})
```

---

## 📋 Expected Output Benchmarks (4 MVP Scenarios)

### Scenario 1: Morning Wake Time Deviation
```json
{
  "system_role": "Comai Proactive Context Engine",
  "user_state": "awake since 08:45 (0h 15m), inactive 0.0h, current activity: STILL",
  "context_signals": {
    "time": "09:00",
    "location": "Home",
    "routine_deviation": true
  },
  "retrieved_data": null,
  "task": "MORNING_ROUTINE_REMINDER",
  "constraints": "Keep response empathetic, concise (max 2 sentences), and suggest an adjusted morning wake-up routine focus."
}
```

### Scenario 2: Lunch Window Gap (No Logged Lunch)
```json
{
  "system_role": "Comai Proactive Context Engine",
  "user_state": "awake since 07:00 (6h 0m), inactive 4.0h, current activity: STILL",
  "context_signals": {
    "time": "13:00",
    "location": "Office",
    "routine_deviation": false
  },
  "retrieved_data": null,
  "task": "LUNCH_CHECKIN",
  "constraints": "Remind user to take a lunch break and log their meal, keep under 15 words."
}
```

### Scenario 3: Non-Workday Free Evening Slot (Tribe Finder Lookup)
```json
{
  "system_role": "Comai Proactive Context Engine",
  "user_state": "awake since 09:00 (9h 30m), inactive 1.5h, current activity: WALKING",
  "context_signals": {
    "time": "18:30",
    "location": "Downtown",
    "routine_deviation": true
  },
  "retrieved_data": "[{name: 'Sunset Outdoor Calisthenics Circle', category: 'FITNESS', area: 'Home Area'}, {name: 'Weekend Board Games & Specialty Coffee', category: 'COMMUNITY', area: 'Downtown'}]",
  "task": "TRIBE_FINDER_RECOMMENDATION",
  "constraints": "Proactively suggest 1-2 local events matching non-workday free evening slot without being pushy."
}
```

### Scenario 4: Night Window Medication Reminder
```json
{
  "system_role": "Comai Proactive Context Engine",
  "user_state": "awake since 07:15 (14h 15m), inactive 3.0h, current activity: STILL",
  "context_signals": {
    "time": "21:30",
    "location": "Home",
    "routine_deviation": false
  },
  "retrieved_data": "Medication profile flag active: Evening Supplement",
  "task": "NIGHT_MEDICATION_REMINDER",
  "constraints": "Warm and gentle reminder to take evening medication before wind-down."
}
```
