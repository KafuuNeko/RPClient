package me.kafuuneko.rpclient.libs.regex

/**
 * 合并旧应用级作用域，并保持未授权 Preset 不会因迁入始终启用的 Global 而扩大权限。
 */
internal fun mergeLegacyGlobalAndPreset(
    globalScripts: List<RegexScript>,
    presetScripts: List<RegexScript>,
    presetAuthorized: Boolean,
    createId: () -> String = { java.util.UUID.randomUUID().toString() },
): List<RegexScript> {
    val reservedIds = mutableSetOf<String>()
    val normalizedGlobal = globalScripts.normalizeRegexScriptIds(reservedIds, createId)
    val normalizedPreset = presetScripts
        .map { script ->
            if (presetAuthorized) script else script.copy(disabled = true)
        }
        .normalizeRegexScriptIds(reservedIds, createId)
    return normalizedGlobal + normalizedPreset
}
