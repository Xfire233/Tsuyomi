/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.runtime

import kotlinx.coroutines.delay

class PrototypeScenarioController(private val repository: PrototypeRepository) {
    fun selected(actionKey: String): PrototypeScenario =
        PrototypeScenario.parse(repository.string("scenario.$actionKey", PrototypeScenario.SUCCESS.storageValue))

    fun select(actionKey: String, scenario: PrototypeScenario) {
        repository.putString(
            key = "scenario.$actionKey",
            value = scenario.storageValue,
            eventName = "PrototypeScenarioSelected",
            target = actionKey,
        )
    }

    suspend fun run(actionKey: String, target: String = actionKey): PrototypeScenarioResult {
        val scenario = selected(actionKey)
        repository.record("PrototypeActionStarted", target, "working", scenario.storageValue)
        delay(if (scenario == PrototypeScenario.SLOW) 1_400L else 360L)
        val result = when (scenario) {
            PrototypeScenario.SUCCESS, PrototypeScenario.SLOW -> PrototypeScenarioResult(scenario, "success", true)
            PrototypeScenario.OFFLINE -> PrototypeScenarioResult(scenario, "offline", false)
            PrototypeScenario.RECOVERABLE_ERROR -> PrototypeScenarioResult(scenario, "recoverable-error", false)
            PrototypeScenario.CANCELLED -> PrototypeScenarioResult(scenario, "cancelled", false)
            PrototypeScenario.UNRESOLVED -> PrototypeScenarioResult(scenario, "unresolved", false)
        }
        repository.record("PrototypeActionFinished", target, result.outcome, scenario.storageValue)
        return result
    }
}
