/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.runtime

import androidx.compose.runtime.Immutable

const val PROTOTYPE_EVENT_LIMIT = 32

@Immutable
data class PrototypeSnapshot(
    val schemaVersion: Int,
    val revision: Long,
    val values: Map<String, String>,
    val recentEvents: List<PrototypeEvent>,
)

@Immutable
data class PrototypeEvent(
    val sequence: Long,
    val action: String,
    val target: String,
    val outcome: String,
    val detail: String? = null,
) {
    fun exportLine(): String = buildString {
        append(sequence).append(':').append(action).append(':').append(target).append(':').append(outcome)
        detail?.takeIf(String::isNotBlank)?.let { append(':').append(it) }
    }
}

sealed interface PrototypeAction {
    val eventName: String
    val target: String

    data class Put(
        val key: String,
        val value: String,
        override val eventName: String,
        override val target: String = key,
    ) : PrototypeAction

    data class Remove(
        val key: String,
        override val eventName: String,
        override val target: String = key,
    ) : PrototypeAction

    data class Toggle(
        val key: String,
        val default: Boolean = false,
        override val eventName: String,
        override val target: String = key,
    ) : PrototypeAction

    data class Increment(
        val key: String,
        val amount: Int,
        val default: Int = 0,
        override val eventName: String,
        override val target: String = key,
    ) : PrototypeAction

    data class Record(
        override val eventName: String,
        override val target: String,
        val outcome: String,
        val detail: String? = null,
    ) : PrototypeAction

    data object ResetFakeData : PrototypeAction {
        override val eventName: String = "PrototypeDataReset"
        override val target: String = "prototype"
    }
}

enum class PrototypeScenario(
    val storageValue: String,
    val label: String,
    val explanation: String,
) {
    SUCCESS("success", "成功", "在固定延迟后完成。"),
    SLOW("slow", "缓慢完成", "保留工作中状态 1.4 秒，然后完成。"),
    OFFLINE("offline", "离线", "模拟设备离线；不会发起网络请求。"),
    RECOVERABLE_ERROR("recoverable-error", "可恢复错误", "模拟可明确重试的失败。"),
    CANCELLED("cancelled", "已取消", "模拟用户或系统取消；不提交结果。"),
    UNRESOLVED("unresolved", "结果未确认", "模拟没有最终回执的操作；不会伪装成成功。"),
    ;

    companion object {
        fun parse(raw: String?): PrototypeScenario = entries.firstOrNull { it.storageValue == raw } ?: SUCCESS
    }
}

@Immutable
data class PrototypeScenarioResult(
    val scenario: PrototypeScenario,
    val outcome: String,
    val successful: Boolean,
)
