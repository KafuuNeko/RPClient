package me.kafuuneko.rpclient.libs.upgrade.steps

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.upgrade.AppUpgrade
import me.kafuuneko.rpclient.libs.upgrade.LegacyPromptSnapshot
import me.kafuuneko.rpclient.libs.upgrade.PromptPresetMigration
import me.kafuuneko.rpclient.libs.room.AppDatabase

/** 将旧单套配置迁为自定义预设，SP 选择成功保存后才允许清理旧键。 */
class Upgrade20260404(private val mDatabase: AppDatabase, private val mContext: Context) :
    AppUpgrade {
    override val targetVersionCode: Int = 20260404

    /** 已有新选择表示此前迁入或备份恢复已完成，不重复导入已删除配置。 */
    override suspend fun migrate() = withContext(Dispatchers.IO) {
        if (AppModel.preferences.contains("activePromptPresetId")) return@withContext
        val id = PromptPresetMigration.migrate(
            db = mDatabase.openHelper.writableDatabase,
            legacy = { LegacyPromptSnapshot.read(AppModel.preferences.all) },
            migratedName = mContext.getString(R.string.prompt_template_migrated),
        )
        // 显式检查持久化结果：失败时保留旧偏好，下次复用已提交的数据库记录。
        check(
            AppModel.preferences.edit().putLong("activePromptPresetId", id).commit()
        ) { "Failed to persist active prompt preset" }
    }

    /** 只清理迁移负责的旧键，不影响其他设置。 */
    override suspend fun cleanup() = withContext(Dispatchers.IO) {
        check(AppModel.preferences.contains("activePromptPresetId")) { "Prompt migration is incomplete" }
        val editor = AppModel.preferences.edit()
        LegacyPromptSnapshot.keys.forEach(editor::remove)
        check(editor.commit()) { "Failed to persist legacy prompt cleanup" }
    }
}
