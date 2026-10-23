package me.kafuuneko.rpclient.libs.character

/**
 * 世界书导入时的预算兼容策略。
 *
 * 本地以 0 表示跟随全局；正数表示单本固定 Token 预算。
 */
object LorebookImportPolicy {
    private const val MIN_RECOMMENDED_FIXED_TOKEN_BUDGET = 100

    /** 固定预算低于建议下限时，需要在持久化前由用户确认。 */
    fun requiresLowBudgetConfirmation(import: CharacterBookImport): Boolean {
        return import.lorebook.tokenBudget in 1 until MIN_RECOMMENDED_FIXED_TOKEN_BUDGET
    }

    /** 角色卡包含低固定预算的内嵌世界书时，也需要由用户确认。 */
    fun requiresLowBudgetConfirmation(import: CharacterCardImport): Boolean {
        return import.embeddedLorebook?.let(::requiresLowBudgetConfirmation) == true
    }

    /** 根据用户选择保留导入预算，或将其改为跟随全局。 */
    fun resolveBudget(
        import: CharacterBookImport,
        followGlobal: Boolean,
    ): CharacterBookImport {
        if (!followGlobal) return import
        return import.copy(lorebook = import.lorebook.copy(tokenBudget = 0))
    }

    /** 根据用户选择调整角色卡内嵌世界书的预算。 */
    fun resolveBudget(
        import: CharacterCardImport,
        followGlobal: Boolean,
    ): CharacterCardImport {
        val embeddedLorebook = import.embeddedLorebook ?: return import
        if (!followGlobal) return import
        return import.copy(embeddedLorebook = resolveBudget(import = embeddedLorebook, followGlobal = true))
    }
}
