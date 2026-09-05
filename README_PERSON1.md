# LIFELOOP — Person 1 Module Summary (Context Engine)

## 📌 Module Scope & Deliverables
This module (`com.comai.contextengine`) provides the native Android background architecture, context sensing, Room databases, rule escalation engine, and shared contract serializer for Person 1 in Sprint 1.

---

## 🏗️ Architecture Overview

1. **Foreground Service (`service/ContextForegroundService.kt`)**
   - Headless background service featuring persistent low-priority notification, `START_STICKY` restart, and `WorkManager` fallback (`service/ContextFallbackWorker.kt`) for aggressive OS behavior (e.g. OriginOS).
   - Provides `ContextOutputListener` callback interface for Person 2's LLM engine to register for escalated events.

2. **Wake/Sleep & Activity Sensing (`context/`)**
   - `WakeSleepDetector.kt` & `ActivityRecognitionManager.kt`: Monitors wake/sleep events, transition states (`STILL`, `WALKING`, `IN_VEHICLE`), and inactive durations.
   - `UserState.kt`: Provides `getCurrentUserState()` for JSON contract serialization.

3. **Deterministic Rule Engine (`rules/`)**
   - `RuleEngine.kt` & `RuleDefinition.kt`: Configurable non-LLM decision matrix prioritizing high-signal events for escalation.
   - Implements MVP triggers: Morning wake deviation, Non-workday commute / Tribe Finder, Lunch break gap, Late office departure, and Evening medication.

4. **Room Databases (`db/`)**
   - `ComaiDatabase.kt`: Manages `UserProfile`, `DailyLog`, `Program` baseline, and `PersonalizationState`.
   - `CommunityEventDatabase.kt`: Prepackaged SQLite database (`createFromAsset` support) storing offline Tribe Finder local events, queryable by `(is_work_day, current_time, location_area)`.

5. **Shared Contract & Assembly (`contract/`)**
   - `SharedContextContract.kt`: Strictly structured data representation matching Person 2's target JSON schema.
   - `ContractValidator.kt`: Enforces strict validation and throws `InvalidContractException` if fields are missing/empty.
   - `ContractAssembler.kt` & `ContractFixtures.kt`: Serializes contract instances and provides pre-built fixtures for testing.

6. **Test Harness (`testharness/`)**
   - `ContextTestHarness.kt`: Standalone simulation engine that executes all 4 MVP scenarios end-to-end without requiring live sensors or an LLM call.

---

## 🔒 Shared JSON Contract Schema Output

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

---

## ❓ Open Questions for Checkpoint A

1. **OriginOS Auto-Start Permissions**: Does the target test device require manually whitelisting Autostart in settings to maintain `START_STICKY` background persistence?
2. **Community Event Database Seeding**: Are there specific local categories or geographic tagging formats preferred for Person 2's RAG/retrieved data prompt formatting?
