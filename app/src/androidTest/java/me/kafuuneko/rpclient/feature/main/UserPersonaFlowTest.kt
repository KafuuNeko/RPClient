package me.kafuuneko.rpclient.feature.main

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.room.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext

/** 从真实设置卡片创建新身份并切回已有身份，校验两个选择均已提交。 */
class UserPersonaFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /** 通过设置页下拉入口创建身份，再切回原默认项。 */
    @Test
    fun settingsCreatesAndSwitchesDefaultPersona() {
        val database = GlobalContext.get().get<AppDatabase>()
        val dao = database.getUserPersonaDao()
        val original = runBlocking { dao.active() }
        val fixtureName = "Mira " + System.nanoTime()
        fun label(id: Int) = rule.activity.getString(id)
        try {
            // 从实际入口完成创建，等待数据库与界面状态同步后再切换。
            rule.onNodeWithContentDescription(label(R.string.settings)).performClick()
            rule.onNodeWithText(label(R.string.persona_label)).performClick()
            rule.onNodeWithText(label(R.string.persona_create)).performClick()
            rule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog()))
                .performTextInput(fixtureName)
            rule.onNodeWithText(label(R.string.confirm)).performClick()
            rule.waitUntil(10_000) { runBlocking { dao.active().name } == fixtureName }
            rule.onNodeWithText(label(R.string.persona_label)).performClick()
            rule.onNodeWithText(original.name).performClick()
            rule.waitUntil(10_000) { runBlocking { dao.active().id } == original.id }
            assertEquals(1, runBlocking { dao.list().count { it.name == fixtureName } })
        } finally {
            // 清理本用例创建的身份，避免影响后续共享应用进程的界面测试。
            runBlocking { dao.select(original.id) }
            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM user_personas WHERE name = ?",
                arrayOf(fixtureName)
            )
        }
    }
}
