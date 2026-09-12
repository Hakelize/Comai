package com.comai.contextengine.rules

import android.util.Log
import com.comai.contextengine.contract.ContextSignals
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Deterministic, local Rule Engine for Person 1.
 * Converts normalized device context signals into proactive events.
 * Handles rule evaluation, threshold triggers, cooldown management, and duplicate event prevention.
 */
class RuleEngine(
    initialRules: List<Rule> = emptyList()
) {

    private val configurableRules = CopyOnWriteArrayList<Rule>()
    val legacyRules = mutableListOf<RuleDefinition>()

    init {
        if (initialRules.isNotEmpty()) {
            configurableRules.addAll(initialRules)
        } else {
            loadDefaultRules()
        }
    }

    /**
     * Loads standard default rules covering all core proactive event scenarios.
     */
    fun loadDefaultRules() {
        configurableRules.clear()
        configurableRules.addAll(
            listOf(
                // 1. Wake-time context → morning routine reminder
                Rule(
                    id = "rule_wake_routine",
                    name = "Wake Time Morning Routine Trigger",
                    description = "Fires when user wake time deviates significantly from baseline",
                    eventType = "MORNING_ROUTINE_REMINDER",
                    priority = EventPriority.MEDIUM,
                    condition = RuleCondition(
                        threshold = TriggerThreshold(deviationMinutesThreshold = 30),
                        evaluator = { input ->
                            val deviationMins = calculateTimeDifferenceMinutes(input.wakeTime, input.baselineWakeTime)
                            kotlin.math.abs(deviationMins) >= 30
                        }
                    ),
                    cooldown = Cooldown(cooldownDurationMs = 3_600_000L), // 1 hour cooldown
                    targetTask = "MORNING_ROUTINE_REMINDER",
                    targetConstraints = "Keep response empathetic, concise (max 2 sentences)."
                ),

                // 2. Normal office arrival time → recognize work routine
                Rule(
                    id = "rule_work_routine_arrival",
                    name = "Normal Office Arrival Trigger",
                    description = "Fires when user arrives at office during standard work arrival window",
                    eventType = "WORK_ROUTINE_RECOGNITION",
                    priority = EventPriority.LOW,
                    condition = RuleCondition(
                        threshold = TriggerThreshold(
                            timeWindowStart = "08:15",
                            timeWindowEnd = "09:30",
                            requiredLocation = "Office",
                            isWorkDayOnly = true
                        ),
                        evaluator = { input ->
                            input.isWorkDay &&
                                    input.currentLocation.equals("Office", ignoreCase = true) &&
                                    isTimeInWindow(input.currentTime, "08:15", "09:30")
                        }
                    ),
                    cooldown = Cooldown(cooldownDurationMs = 14_400_000L), // 4 hours cooldown
                    targetTask = "WORK_ROUTINE_RECOGNITION",
                    targetConstraints = "Acknowledge arrival concisely and confirm day focus."
                ),

                // 3. Leaving office later than normal → check-in trigger
                Rule(
                    id = "rule_overtime_leaving",
                    name = "Late Office Departure Overtime Trigger",
                    description = "Fires when user stays at office past historical departure time",
                    eventType = "OVERTIME_CHECKIN",
                    priority = EventPriority.HIGH,
                    condition = RuleCondition(
                        threshold = TriggerThreshold(
                            deviationMinutesThreshold = 45,
                            requiredLocation = "Office",
                            isWorkDayOnly = true
                        ),
                        evaluator = { input ->
                            if (input.isWorkDay && input.currentLocation.equals("Office", ignoreCase = true)) {
                                val overtimeMins = calculateTimeDifferenceMinutes(input.currentTime, input.historicalOfficeDepartureTime)
                                overtimeMins >= 45
                            } else false
                        }
                    ),
                    cooldown = Cooldown(cooldownDurationMs = 7_200_000L), // 2 hours cooldown
                    targetTask = "OVERTIME_CHECKIN",
                    targetConstraints = "Offer empathetic overtime checkin and commute advice."
                ),

                // 4. Lunch period → lunch reminder candidate
                Rule(
                    id = "rule_lunch_reminder",
                    name = "Lunch Window Gap Trigger",
                    description = "Fires when lunch time window is reached with no logged meal",
                    eventType = "LUNCH_REMINDER",
                    priority = EventPriority.MEDIUM,
                    condition = RuleCondition(
                        threshold = TriggerThreshold(
                            timeWindowStart = "12:30",
                            timeWindowEnd = "14:00"
                        ),
                        evaluator = { input ->
                            isTimeInWindow(input.currentTime, "12:30", "14:00") && !input.loggedLunch
                        }
                    ),
                    cooldown = Cooldown(cooldownDurationMs = 10_800_000L), // 3 hours cooldown
                    targetTask = "LUNCH_CHECKIN",
                    targetConstraints = "Prompt gentle lunch break reminder."
                ),

                // 5. Night/wind-down period → wind-down candidate
                Rule(
                    id = "rule_night_wind_down",
                    name = "Night Wind-Down Medication Trigger",
                    description = "Fires during night window if medication/wind-down flag is active",
                    eventType = "WIND_DOWN_CANDIDATE",
                    priority = EventPriority.MEDIUM,
                    condition = RuleCondition(
                        threshold = TriggerThreshold(
                            timeWindowStart = "21:00",
                            timeWindowEnd = "23:00"
                        ),
                        evaluator = { input ->
                            input.medicationFlag && isTimeInWindow(input.currentTime, "21:00", "23:00")
                        }
                    ),
                    cooldown = Cooldown(cooldownDurationMs = 10_800_000L), // 3 hours cooldown
                    targetTask = "NIGHT_MEDICATION_REMINDER",
                    targetConstraints = "Remind user of wind-down routine and medication."
                ),

                // 6. Routine deviation → possible proactive event
                Rule(
                    id = "rule_routine_deviation",
                    name = "Routine Schedule Anomaly Trigger",
                    description = "Fires when RoutineEngine flags significant routine deviation",
                    eventType = "ROUTINE_DEVIATION_ALERT",
                    priority = EventPriority.HIGH,
                    condition = RuleCondition(
                        evaluator = { input -> input.routineDeviation }
                    ),
                    cooldown = Cooldown(cooldownDurationMs = 1_800_000L), // 30 minutes cooldown
                    targetTask = "ROUTINE_DEVIATION_ALERT",
                    targetConstraints = "Inquire gently about schedule adjustment."
                ),

                // 7. Non-Workday Community Event / Tribe Finder Trigger
                Rule(
                    id = "rule_tribe_finder",
                    name = "Non-Workday Tribe Finder Trigger",
                    description = "Fires during commute window on non-work days to suggest nearby events",
                    eventType = "TRIBE_FINDER_RECOMMENDATION",
                    priority = EventPriority.LOW,
                    condition = RuleCondition(
                        threshold = TriggerThreshold(
                            timeWindowStart = "17:00",
                            timeWindowEnd = "21:00",
                            isNonWorkDayOnly = true
                        ),
                        evaluator = { input ->
                            !input.isWorkDay && isTimeInWindow(input.currentTime, "17:00", "21:00")
                        }
                    ),
                    cooldown = Cooldown(cooldownDurationMs = 7_200_000L),
                    targetTask = "TRIBE_FINDER_RECOMMENDATION",
                    targetConstraints = "Suggest community event recommendations."
                )
            )
        )

        loadDefaultMvpRules()
        Log.i(TAG, "Loaded ${configurableRules.size} configurable rules.")
    }

    /**
     * Backward-compatibility loader for legacy RuleDefinition calls.
     */
    fun loadDefaultMvpRules() {
        legacyRules.clear()
        legacyRules.addAll(
            listOf(
                RuleDefinition("rule_wake_deviation", "Wake Time Deviation", "Fires on wake deviation", RuleTriggerType.WAKE_TIME_DEVIATION, targetTask = "MORNING_ROUTINE_REMINDER"),
                RuleDefinition("rule_tribe_finder", "Tribe Finder", "Fires on non-workday commute", RuleTriggerType.TRIBE_FINDER_LOOKUP, targetTask = "TRIBE_FINDER_RECOMMENDATION"),
                RuleDefinition("rule_lunch_reminder", "Lunch Reminder", "Fires on lunch window", RuleTriggerType.LUNCH_REMINDER, targetTask = "LUNCH_CHECKIN"),
                RuleDefinition("rule_overtime_checkin", "Late Office Departure", "Fires on overtime", RuleTriggerType.OVERTIME_CHECKIN, targetTask = "OVERTIME_CHECKIN"),
                RuleDefinition("rule_medication_reminder", "Night Medication", "Fires on night window", RuleTriggerType.MEDICATION_REMINDER, targetTask = "NIGHT_MEDICATION_REMINDER")
            )
        )
    }

    /**
     * Registers a custom rule dynamically.
     */
    fun addRule(rule: Rule) {
        configurableRules.removeAll { it.id == rule.id }
        configurableRules.add(rule)
        Log.d(TAG, "Added rule: ${rule.id} (${rule.name})")
    }

    /**
     * Unregisters a rule by ID.
     */
    fun removeRule(ruleId: String) {
        configurableRules.removeAll { it.id == ruleId }
        Log.d(TAG, "Removed rule: $ruleId")
    }

    /**
     * Retrieves a rule by ID.
     */
    fun getRule(ruleId: String): Rule? {
        return configurableRules.firstOrNull { it.id == ruleId }
    }

    /**
     * Clears all configured rules.
     */
    fun clearRules() {
        configurableRules.clear()
    }

    /**
     * Resets cooldowns across all rules (useful for testing).
     */
    fun resetCooldowns() {
        for (rule in configurableRules) {
            rule.cooldown.reset()
        }
    }

    /**
     * Evaluates all configured rules deterministically against current context signals.
     * Prevents duplicate events by checking rule cooldowns.
     */
    fun evaluate(input: RuleEvaluationInput): RuleEvaluationResult? {
        val nowMs = input.currentTimeMs
        Log.d(TAG, "Evaluating ${configurableRules.size} rules for time: ${input.currentTime}, location: ${input.currentLocation}")

        for (rule in configurableRules) {
            if (!rule.enabled) continue

            // Duplicate prevention & Cooldown handling
            if (rule.isCoolingDown(nowMs)) {
                Log.d(TAG, "Rule '${rule.id}' is cooling down (${rule.cooldown.remainingCooldownMs(nowMs)}ms remaining). Skipping.")
                continue
            }

            // Condition evaluation
            if (rule.condition.evaluate(input)) {
                Log.i(TAG, "Rule Triggered: ${rule.name} [ID: ${rule.id}, Event: ${rule.eventType}, Priority: ${rule.priority}]")

                // Mark rule triggered to enforce cooldown
                rule.markTriggered(nowMs)

                val proactiveEvent = ProactiveEvent(
                    eventId = "evt_${rule.id}_${nowMs}",
                    eventType = rule.eventType,
                    priority = rule.priority,
                    targetTask = rule.targetTask,
                    targetConstraints = rule.targetConstraints,
                    contextSummary = "Time: ${input.currentTime}, Location: ${input.currentLocation}, WorkDay: ${input.isWorkDay}",
                    timestampMs = nowMs
                )

                val legacyTriggerType = mapEventTypeToLegacyTriggerType(rule.eventType)
                val legacyDefinition = RuleDefinition(
                    id = rule.id,
                    name = rule.name,
                    description = rule.description,
                    triggerType = legacyTriggerType,
                    enabled = rule.enabled,
                    systemRole = rule.systemRole,
                    targetTask = rule.targetTask,
                    targetConstraints = rule.targetConstraints
                )

                val isTribeFinder = rule.eventType == "TRIBE_FINDER_RECOMMENDATION"

                return RuleEvaluationResult(
                    triggeredRule = legacyDefinition,
                    contextSignals = ContextSignals(
                        time = input.currentTime,
                        location = input.currentLocation,
                        routineDeviation = input.routineDeviation || rule.eventType == "ROUTINE_DEVIATION_ALERT" || rule.eventType == "OVERTIME_CHECKIN" || rule.eventType == "MORNING_ROUTINE_REMINDER"
                    ),
                    systemRole = rule.systemRole,
                    task = rule.targetTask,
                    constraints = rule.targetConstraints,
                    retrievedDataLookupRequired = isTribeFinder,
                    retrievedDataCategory = if (isTribeFinder) "COMMUNITY_EVENTS" else null,
                    proactiveEvent = proactiveEvent
                )
            }
        }

        Log.d(TAG, "No rule condition satisfied or all satisfied rules are in cooldown.")
        return null
    }

    private fun mapEventTypeToLegacyTriggerType(eventType: String): RuleTriggerType {
        return when (eventType) {
            "MORNING_ROUTINE_REMINDER" -> RuleTriggerType.WAKE_TIME_DEVIATION
            "WORK_ROUTINE_RECOGNITION" -> RuleTriggerType.WORK_ROUTINE_RECOGNITION
            "OVERTIME_CHECKIN" -> RuleTriggerType.OVERTIME_CHECKIN
            "LUNCH_REMINDER" -> RuleTriggerType.LUNCH_REMINDER
            "WIND_DOWN_CANDIDATE" -> RuleTriggerType.WIND_DOWN_CANDIDATE
            "ROUTINE_DEVIATION_ALERT" -> RuleTriggerType.ROUTINE_DEVIATION_ALERT
            "TRIBE_FINDER_RECOMMENDATION" -> RuleTriggerType.TRIBE_FINDER_LOOKUP
            else -> RuleTriggerType.WAKE_TIME_DEVIATION
        }
    }

    private fun isTimeInWindow(currentTimeStr: String, startStr: String, endStr: String): Boolean {
        val currentMins = timeToMinutes(currentTimeStr)
        val startMins = timeToMinutes(startStr)
        val endMins = timeToMinutes(endStr)
        return currentMins in startMins..endMins
    }

    private fun calculateTimeDifferenceMinutes(timeA: String, timeB: String): Int {
        return timeToMinutes(timeA) - timeToMinutes(timeB)
    }

    private fun timeToMinutes(timeStr: String): Int {
        return try {
            val parts = timeStr.split(":")
            parts[0].toInt() * 60 + parts[1].toInt()
        } catch (e: Exception) {
            0
        }
    }

    companion object {
        private const val TAG = "RuleEngine"
    }
}
