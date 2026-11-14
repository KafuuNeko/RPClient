package me.kafuuneko.rpclient.libs.upgrade.steps

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.upgrade.AppUpgrade
import me.kafuuneko.rpclient.libs.upgrade.LegacyPromptSnapshot
import me.kafuuneko.rpclient.libs.upgrade.PromptPresetMigration
import me.kafuuneko.rpclient.libs.upgrade.UserPersonaUpgrade

/**
 * 将当前版本的旧提示词配置和用户身份统一迁入 Room。
 * - 两项迁移各自使用持久化标记，任一阶段失败后均可安全重试。
 * - Manager 完成全部版本迁移后，统一清理本版本负责的旧偏好。
 */
class Upgrade20260404(
    private val mDatabase: AppDatabase,
    private val mContext: Context,
    private val mPreferences: SharedPreferences = AppModel.preferences,
) : AppUpgrade {
    override val targetVersionCode: Int = 20260404

    /** 在 Room 结构升级后迁入身份和提示词，两项全部成功才推进版本检查点。 */
    override suspend fun migrate() = withContext(Dispatchers.IO) {
        UserPersonaUpgrade.migrate(mDatabase.openHelper.writableDatabase, mPreferences.all)
        migratePromptPreset()
    }

    /** 已有提示词选择只跳过提示词迁入，不影响本版本的人设升级。 */
    private fun migratePromptPreset() {
        if (mPreferences.contains("activePromptPresetId")) return
        val id = PromptPresetMigration.migrate(
            db = mDatabase.openHelper.writableDatabase,
            legacy = { LegacyPromptSnapshot.read(mPreferences.all) },
            migratedName = mContext.getString(R.string.prompt_template_migrated),
        )
        // 显式检查持久化结果：失败时保留旧偏好，下次复用已提交的数据库记录。
        check(mPreferences.edit().putLong("activePromptPresetId", id).commit()) {
            "Failed to persist active prompt preset"
        }
    }

    /** 确认两项迁入均完成，再同步删除旧键；失败时保留 Manager 的清理检查点。 */
    override suspend fun cleanup() = withContext(Dispatchers.IO) {
        check(mPreferences.contains("activePromptPresetId")) { "Prompt migration is incomplete" }
        UserPersonaUpgrade.validate(mDatabase.openHelper.writableDatabase)
        val editor = mPreferences.edit()
        (LegacyPromptSnapshot.keys + UserPersonaUpgrade.keys).forEach(editor::remove)
        check(editor.commit()) { "Failed to persist legacy preference cleanup" }
    }
}
