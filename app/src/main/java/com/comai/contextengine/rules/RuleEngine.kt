package com.comai.contextengine.rules

import android.util.Log
import com.comai.contextengine.contract.ContextSignals

class RuleEngine(
    val rules: MutableList<RuleDefinition> = mutableListOf()
) {

    init {
        if (rules.isEmpty()) {
            loadDefaultMvpRules()
        }
    }

    fun loadDefaultMvpRules() {
        rules.clear()
        rules.addAll(
            listOf(
                RuleDefinition(
                    id = "rule_wake_deviation",
                    name = "Wake Time Deviation Trigger",
                    description = "Fires when user wake time deviates significantly from baseline",
                    triggerType = RuleTriggerType.WAKE_TIME_DEVIATION,
                    targetTask = "MORNING_ROUTINE_REMINDER",
                    wakeDeviationThresholdMins = 30
                ),
                RuleDefinition(
                    id = "rule_tribe_finder",
                    name = "Commute / Non-Workday Tribe Finder Trigger",
                    description = "Fires during commute window on non-work days to suggest nearby events",
                    triggerType = RuleTriggerType.TRIBE_FINDER_LOOKUP,
                    targetTask = "TRIBE_FINDER_RECOMMENDATION",
                    commuteWindowStart = "17:00",
                    commuteWindowEnd = "21:00"
                ),
                RuleDefinition(
                    id = "rule_lunch_reminder",
                    name = "Lunch Time Window Trigger",
                    description = "Fires when lunch time window is reached with no logged meal",
                    triggerType = RuleTriggerType.LUNCH_REMINDER,
                    targetTask = "LUNCH_CHECKIN",
                    lunchWindowStart = "12:30",
                    lunchWindowEnd = "14:00"
                ),
                RuleDefinition(
                    id = "rule_overtime_checkin",
                    name = "Late Office Departure Trigger",
                    description = "Fires when user stays at office past historical departure time",
                    triggerType = RuleTriggerType.OVERTIME_CHECKIN,
                    targetTask = "OVERTIME_CHECKIN",
                    overtimeThresholdMins = 45
                ),
                RuleDefinition(
                    id = "rule_medication_reminder",
                    name = "Night Medication Flag Trigger",
                    description = "Fires during night window if medication flag is active in profile",
                    triggerType = RuleTriggerType.MEDICATION_REMINDER,
                    targetTask = "NIGHT_MEDICATION_REMINDER",
                    nightWindowStart = "21:00",
                    nightWindowEnd = "23:00"
                )
            )
        )
        Log.i(TAG, "Loaded ${rules.size} default MVP rule definitions.")
    }

    fun evaluate(input: RuleEvaluationInput): RuleEvaluationResult? {
        Log.d(TAG, "Evaluating context rules for time: ${input.currentTime}, location: ${input.currentLocation}")

        for (rule in rules) {
            if (!rule.enabled) continue

            when (rule.triggerType) {
                RuleTriggerType.WAKE_TIME_DEVIATION -> {
                    val deviationMins = calculateTimeDifferenceMinutes(input.wakeTime, input.baselineWakeTime)
                    if (kotlin.math.abs(deviationMins) >= rule.wakeDeviationThresholdMins) {
                        Log.i(TAG, "Triggered rule: ${rule.name} (deviation: ${deviationMins}m)")
                        return RuleEvaluationResult(
                            triggeredRule = rule,
                            contextSignals = ContextSignals(
                                time = input.currentTime,
                                location = input.currentLocation,
                                routineDeviation = true
                            ),
                            systemRole = rule.systemRole,
                            task = rule.targetTask,
                            constraints = rule.targetConstraints
                        )
                    }
                }

                RuleTriggerType.TRIBE_FINDER_LOOKUP -> {
                    if (!input.isWorkDay && isTimeInWindow(input.currentTime, rule.commuteWindowStart, rule.commuteWindowEnd)) {
                        Log.i(TAG, "Triggered rule: ${rule.name} (non-work day free evening)")
                        return RuleEvaluationResult(
                            triggeredRule = rule,
                            contextSignals = ContextSignals(
                                time = input.currentTime,
                                location = input.currentLocation,
                                routineDeviation = true
                            ),
                            systemRole = rule.systemRole,
                            task = rule.targetTask,
                            constraints = rule.targetConstraints,
                            retrievedDataLookupRequired = true,
                            retrievedDataCategory = "COMMUNITY_EVENTS"
                        )
                    }
                }

                RuleTriggerType.LUNCH_REMINDER -> {
                    if (isTimeInWindow(input.currentTime, rule.lunchWindowStart, rule.lunchWindowEnd) && !input.loggedLunch) {
                        Log.i(TAG, "Triggered rule: ${rule.name} (lunch window reached, unlogged)")
                        return RuleEvaluationResult(
                            triggeredRule = rule,
                            contextSignals = ContextSignals(
                                time = input.currentTime,
                                location = input.currentLocation,
                                routineDeviation = false
                            ),
                            systemRole = rule.systemRole,
                            task = rule.targetTask,
                            constraints = rule.targetConstraints
                        )
                    }
                }

                RuleTriggerType.OVERTIME_CHECKIN -> {
                    if (input.isWorkDay && input.currentLocation.equals("Office", ignoreCase = true)) {
                        val overtimeMins = calculateTimeDifferenceMinutes(input.currentTime, input.historicalOfficeDepartureTime)
                        if (overtimeMins >= rule.overtimeThresholdMins) {
                            Log.i(TAG, "Triggered rule: ${rule.name} (overtime: ${overtimeMins}m)")
                            return RuleEvaluationResult(
                                triggeredRule = rule,
                                contextSignals = ContextSignals(
                                    time = input.currentTime,
                                    location = input.currentLocation,
                                    routineDeviation = true
                                ),
                                systemRole = rule.systemRole,
                                task = rule.targetTask,
                                constraints = rule.targetConstraints
                            )
                        }
                    }
                }

                RuleTriggerType.MEDICATION_REMINDER -> {
                    if (input.medicationFlag && isTimeInWindow(input.currentTime, rule.nightWindowStart, rule.nightWindowEnd)) {
                        Log.i(TAG, "Triggered rule: ${rule.name} (night window medication trigger)")
                        return RuleEvaluationResult(
                            triggeredRule = rule,
                            contextSignals = ContextSignals(
                                time = input.currentTime,
                                location = input.currentLocation,
                                routineDeviation = false
                            ),
                            systemRole = rule.systemRole,
                            task = rule.targetTask,
                            constraints = rule.targetConstraints
                        )
                    }
                }
            }
        }

        Log.d(TAG, "No rule triggered. Context classified as noise/routine.")
        return null
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
