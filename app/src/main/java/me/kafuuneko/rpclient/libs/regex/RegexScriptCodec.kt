package me.kafuuneko.rpclient.libs.regex

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

class RegexScriptCodec(
    private val mGson: Gson
) {
    fun parseList(json: String): List<RegexScript> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val root = JsonParser.parseString(json)
            val array = when {
                root.isJsonArray -> root.asJsonArray
                root.isJsonObject -> JsonArray().apply { add(root) }
                else -> JsonArray()
            }
            array.mapNotNull { element ->
                element.takeIf { it.isJsonObject }?.asJsonObject?.toScript()
            }
        }.getOrDefault(emptyList())
    }

    fun toJson(scripts: List<RegexScript>, pretty: Boolean = false): String {
        val array = JsonArray()
        scripts.forEach { array.add(it.toJsonObject()) }
        return if (pretty) {
            mGson.newBuilder().setPrettyPrinting().create().toJson(array)
        } else {
            mGson.toJson(array)
        }
    }

    private fun JsonObject.toScript(): RegexScript {
        return RegexScript(
            id = optString("id"),
            scriptName = optString("scriptName"),
            findRegex = optString("findRegex"),
            replaceString = optString("replaceString"),
            trimStrings = optStringArray("trimStrings"),
            placement = optIntArray("placement"),
            disabled = optBoolean("disabled"),
            markdownOnly = optBoolean("markdownOnly"),
            promptOnly = optBoolean("promptOnly"),
            runOnEdit = optBoolean("runOnEdit"),
            substituteRegex = optInt("substituteRegex", RegexFindMacroMode.Disabled.value),
            minDepth = optNullableInt("minDepth"),
            maxDepth = optNullableInt("maxDepth"),
            rawJson = mGson.toJson(this)
        )
    }

    private fun RegexScript.toJsonObject(): JsonObject {
        val output = runCatching {
            JsonParser.parseString(rawJson).asJsonObject.deepCopy()
        }.getOrDefault(JsonObject())
        output.addProperty("id", id)
        output.addProperty("scriptName", scriptName)
        output.addProperty("findRegex", findRegex)
        output.addProperty("replaceString", replaceString)
        output.add("trimStrings", JsonArray().apply { trimStrings.forEach(::add) })
        output.add("placement", JsonArray().apply { placement.forEach(::add) })
        output.addProperty("disabled", disabled)
        output.addProperty("markdownOnly", markdownOnly)
        output.addProperty("promptOnly", promptOnly)
        output.addProperty("runOnEdit", runOnEdit)
        output.addProperty("substituteRegex", substituteRegex)
        if (minDepth == null) output.add("minDepth", null) else output.addProperty("minDepth", minDepth)
        if (maxDepth == null) output.add("maxDepth", null) else output.addProperty("maxDepth", maxDepth)
        return output
    }

    private fun JsonObject.optString(name: String): String =
        get(name)?.takeIf { !it.isJsonNull }?.asString.orEmpty()

    private fun JsonObject.optBoolean(name: String): Boolean =
        runCatching { get(name)?.takeIf { !it.isJsonNull }?.asBoolean }.getOrNull() ?: false

    private fun JsonObject.optInt(name: String, default: Int): Int =
        runCatching { get(name)?.takeIf { !it.isJsonNull }?.asInt }.getOrNull() ?: default

    private fun JsonObject.optNullableInt(name: String): Int? =
        runCatching { get(name)?.takeIf { !it.isJsonNull }?.asInt }.getOrNull()

    private fun JsonObject.optStringArray(name: String): List<String> =
        runCatching {
            getAsJsonArray(name).mapNotNull { it.takeIf { value -> !value.isJsonNull }?.asString }
        }.getOrDefault(emptyList())

    private fun JsonObject.optIntArray(name: String): List<Int> =
        runCatching {
            getAsJsonArray(name).mapNotNull {
                runCatching { it.takeIf { value -> !value.isJsonNull }?.asInt }.getOrNull()
            }
        }.getOrDefault(emptyList())
}
