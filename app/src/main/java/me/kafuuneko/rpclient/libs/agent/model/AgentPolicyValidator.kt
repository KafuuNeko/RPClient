package me.kafuuneko.rpclient.libs.agent.model

import me.kafuuneko.rpclient.libs.room.entity.ConversationToolPolicy

/** 运行配置与备份恢复共用的策略格式约束。 */
object AgentPolicyValidator {
    /** 解析稳定工具 ID 白名单，不接受隐式类型转换。 */
    fun enabledTools(raw: String): Set<String> {
        val value = AgentJson.parse(raw)
        require(value.isJsonArray && value.asJsonArray.size() <= 1000) { "Invalid enabled tool list" }
        val ids = value.asJsonArray.map {
            require(it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.matches(Regex("^[a-zA-Z0-9_.-]{1,128}$"))) { "Invalid enabled tool id" }
            it.asString
        }
        require(ids.distinct().size == ids.size) { "Duplicate enabled tool id" }
        return ids.toSet()
    }

    /** 校验成本限额和水位，提交时钟不允许负值。 */
    fun validate(policy: ConversationToolPolicy) {
        enabledTools(policy.enabledToolIdsJson)
        require(
            policy.maxToolRounds > 0 && policy.maxToolCalls > 0 && policy.maxModelRequests > 0 && policy.maxTotalTokens > 0 &&
                policy.toolTimeoutMillis > 0 && policy.maxActiveMillis > 0 && policy.compactionWatermarkPercent in 1..100 &&
                policy.maxGroupModelRequests > 0 && policy.maxGroupTotalTokens > 0 && policy.maxAutoBatches > 0 && policy.maxGroupActiveMillis > 0 &&
                policy.historyVersion >= 0 && policy.executionEpoch >= 0
        ) { "Invalid agent limits" }
    }
}
