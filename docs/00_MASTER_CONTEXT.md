# COMAI — Master Project Context (v2)
_Updated with refined tech stack and team assignments._

## Project Constraints
- Target: Native Android (Kotlin), Min SDK 29 (OriginOS 6 / Android 14+)
- Hardware: Snapdragon NPU via MediaPipe LLM Inference API
- Model: Quantized Gemma 2B (INT4) on-device
- No backend, no cloud dependency for the critical path — everything resolves on-device
- All LLM outputs must be strict JSON schemas — never free-text parsing
- Architecture: background logic lives in Android Foreground Services (START_STICKY), decoupled from UI
- Storage: SQLite (Room) for Tribe Finder events and rolling user memory
- Device: iQOO/vivo phone, vivo Office Kit paired
- Voice output: Android TTS

## Product One-Liner
Comai is a privacy-first, voice-driven AI companion that understands a user's daily context
(time, location, routine) and proactively assists — without needing the user to explain themselves
every time.

## Tech Stack Architecture
- **Frontend:** Native Android with Kotlin + Jetpack Compose
  - Multimodal Chat UI, Audio Canvas, hidden Developer Dashboard
- **Local Backend:** Android Foreground Service (START_STICKY)
  - SQLite database for Tribe Finder events and rolling user memory
- **Hardware Inference:** MediaPipe LLM Inference API
  - Quantized Gemma 2B (INT4) on Snapdragon NPU
  - Android TTS for voice output

## The Integration Contract

### AIEngine Interface
A rigid Kotlin interface that accepts a context data class and returns structured JSON.

### MockEngine
Returns hardcoded JSON instantly (e.g., `{ "action": "tribe_event", "speech": "There is a run club nearby." }`)
to unblock UI testing without battery drain.

### HardwareEngine
Live implementation wrapping MediaPipe API, injected only during final hardware integration.

## Shared JSON Contract (Context Engine → LLM)
```json
{
  "system_role": "string — assistant persona for this turn",
  "user_state": "string — e.g. 'inactive 3h, isolated, afternoon'",
  "context_signals": {
    "time": "string",
    "location": "string",
    "routine_deviation": "boolean"
  },
  "retrieved_data": "string or null — e.g. relevant memory/event",
  "task": "string — what the LLM must produce",
  "constraints": "string — e.g. 'under 20 words, warm tone'"
}
```

## GitHub Branch Strategy
| Branch | Owner | Scope |
|---|---|---|
| `ui-compose` | Rakesh | Jetpack Compose screens, Android TTS routing, MockEngine bindings |
| `context-daemon` | Ram | Background services, Sleep API wake logic, SQLite queries |
| `npu-inference` | Harish | MediaPipe setup, NPU memory management, Gemma 2B prompt synthesis |

## Three-Person Execution Plan
| Person | Role | Sprint 1 Focus |
|---|---|---|
| **Rakesh** (Frontend Lead) | Chat interface, developer override menu, visual polish | Mock data flow, fully interactive UI |
| **Ram** (Data & Logic Lead) | Native rule engine, pre-populated SQLite community DB | Sensor event filtering, proactive event matching |
| **Harish** (Edge AI Lead) | On-device compilation, quantized model loading | NPU memory, thermal throttling avoidance |

## Build Sequence
1. **Sprint 1 (solo build):** each person scaffolds against mock data/JSON
2. **Checkpoint A:** Context Engine output → LLM input — verify JSON contract
3. **Checkpoint B:** Voice/Chat UI consumes LLM output — verify round trip
4. **Checkpoint C (Office Kit Red Light):** full loop live on phone
5. **Final:** Hybrid model experiment — only after Checkpoint C passes clean

## Non-Negotiables
- Working transformer-based path is the demo guarantee — never break it to chase an experiment
- No feature ships without hitting the JSON contract above
- If something isn't stable by the last integration checkpoint, cut it — don't demo broken
