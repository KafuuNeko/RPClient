package me.kafuuneko.rpclient.libs.agent.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** 单项剧情变更；value 为 null 表示删除键，JSON null 不作为剧情值保存。 */
data class SceneStateChange(val path: List<String>, val value: JsonElement?)

/**
 * 半结构化剧情状态的统一格式边界。
 * - 固定地点、时间、属性、物品和目标域，自定义数据限定为轻量分组与类型化值。
 * - 修改按路径应用，禁止替换整个根对象；版本锁由仓库处理。
 * - 显式 JSON 编码无需 Gson 反射及额外 R8 规则。
 */
object SceneStateCodec {
    private val mDomains = setOf("location", "time", "attributes", "inventory", "goals", "custom")

    /** 校验并规范化快照，备份恢复与运行写入使用相同约束。 */
    fun normalize(raw: String): String {
        val value = AgentJson.parse(raw).asJsonObject
        require(value.keySet().all { it in mDomains }) { "Unknown scene state domain" }
        // 固定域各自采用结构约束，不让自定义键覆盖物品数量或任务状态类型。
        value["location"]?.let { string(it, 500) }
        value["time"]?.let { string(it, 500) }
        value["attributes"]?.let { groups(it, 1) }
        value["custom"]?.let { groups(it, 3) }
        value["inventory"]?.let { records(it, setOf("name", "quantity", "notes"), "name") }
        value["goals"]?.let { records(it, setOf("title", "status", "notes"), "title") }
        return AgentJson.canonical(value)
    }

    /** 在快照副本上应用增量，任一字段非法时整批失败。 */
    fun apply(raw: String, changes: List<SceneStateChange>): String {
        require(changes.size in 1..100) { "Invalid scene change count" }
        val value = AgentJson.parse(normalize(raw)).asJsonObject
        changes.forEach { change ->
            require(
                change.path.size in 1..5 && change.path.first() in mDomains && change.path.all(
                    ::validKey
                )
            ) { "Invalid scene change path" }
            var parent = value
            // 只创建缺失的对象分组，不隐式把已有标量变成对象。
            change.path.dropLast(1).forEach { key ->
                if (!parent.has(key)) parent.add(key, JsonObject())
                require(parent[key].isJsonObject) { "Scene change crosses a scalar" }
                parent = parent.getAsJsonObject(key)
            }
            if (change.value == null) parent.remove(change.path.last())
            else parent.add(change.path.last(), change.value.deepCopy())
        }
        return normalize(value.toString())
    }

    private fun records(value: JsonElement, allowed: Set<String>, title: String) {
        val objectValue = value.asJsonObject
        require(objectValue.size() <= 100) { "Too many scene records" }
        objectValue.entrySet().forEach { (key, item) ->
            require(validKey(key) && item.isJsonObject) { "Invalid scene record" }
            val record = item.asJsonObject
            require(
                record.keySet()
                    .all { it in allowed } && record.has(title)) { "Invalid scene record fields" }
            record.entrySet().forEach { (name, field) ->
                if (name == "quantity") require(field.isJsonPrimitive && field.asJsonPrimitive.isNumber && field.asBigDecimal >= 0.toBigDecimal()) { "Invalid item quantity" }
                else string(field, 500)
            }
        }
    }

    private fun groups(value: JsonElement, depth: Int) {
        val objectValue = value.asJsonObject
        require(objectValue.size() <= 100) { "Too many scene fields" }
        objectValue.entrySet().forEach { (key, child) ->
            require(validKey(key)) { "Invalid scene field key" }
            if (child.isJsonObject) {
                require(depth > 0) { "Scene group is too deep" }
                groups(child, depth - 1)
            } else scalar(child)
        }
    }

    private fun scalar(value: JsonElement) {
        when {
            value.isJsonArray -> {
                require(value.asJsonArray.size() <= 20) { "Too many scene tags" }
                value.asJsonArray.forEach { string(it, 80) }
            }

            value.isJsonPrimitive && value.asJsonPrimitive.isString -> string(value, 500)
            value.isJsonPrimitive && value.asJsonPrimitive.isNumber -> value.asBigDecimal
            value.isJsonPrimitive && value.asJsonPrimitive.isBoolean -> Unit
            else -> error("Invalid scene value")
        }
    }

    private fun string(value: JsonElement, maxLength: Int) {
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString && value.asString.length <= maxLength) { "Invalid scene text" }
    }

    private fun validKey(value: String) =
        value.isNotBlank() && value.length <= 100 && value.none { it.isISOControl() }
}
