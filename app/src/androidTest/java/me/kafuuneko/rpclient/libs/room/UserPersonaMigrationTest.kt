package me.kafuuneko.rpclient.libs.room

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.kafuuneko.rpclient.libs.room.migration.PersonaSchemaMigration
import me.kafuuneko.rpclient.libs.upgrade.UserPersonaUpgrade
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 用正式版数据库验证跨版本升级与用户人设迁入。 */
@RunWith(AndroidJUnit4::class)
class UserPersonaMigrationTest {
    private val mInstrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(mInstrumentation, AppDatabase::class.java)

    /** 正式版升级保留关系行，固定历史姓名并按字段恢复描述。 */
    @Test
    fun releasedDatabaseKeepsChildrenAndIndependentOverrides() {
        val name = "released-persona-migration"
        helper.createDatabase(name, 5).use(::seed)
        helper.runMigrationsAndValidate(name, 6, true, PersonaSchemaMigration).use(::verify)
    }

    private fun seed(db: SupportSQLiteDatabase) {
        // 旧用户头像属于独立文件索引，会话和故事共享迁入的默认身份。
        db.execSQL("INSERT INTO files(uuid,hash,mimeType) VALUES('avatar','hash','image/png')")
        db.execSQL(
            """
            INSERT INTO character(id,name,avatar,characterTags,description,personality,scenario,firstMessages,examplesOfDialogue,postHistoryInstructions)
            VALUES(1,'A','','[]','','','','','','')
        """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO chat_sessions(id,characterId,createTime,latestTime,lorebookEntrySet,title,userNote,userName,userDescription,worldInfoStateJson,autoSummaryPaused)
            VALUES(11,1,1,1,'[]','chat','','Mira',' {{user}} ','{}',0)
        """.trimIndent()
        )
        db.execSQL("INSERT INTO chat_messages(sessionId,createTime,source,content) VALUES(11,1,'User','hello')")
        db.execSQL(
            """
            INSERT INTO group_chat_sessions(id,title,createTime,latestTime,userName,userDescription,scenario,userNote,lorebookEntrySet,worldInfoStateJson,
                systemPromptOverride,groupNudgePromptOverride,newGroupChatPromptOverride,activationStrategy,allowSelfResponses,characterCardMode,includeMutedCards,autoModeEnabled,trimOtherSpeakers,autoSummaryPaused)
            VALUES(12,'group',1,1,'Alias','Archivist','','','[]','{}','','','','Natural',0,'Swap',0,0,1,0)
        """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO stories(id,title,memory,summary,authorNote,includeUserPersona,worldInfoGenerationStep,revision,createTime,latestTime)
            VALUES(13,'on','','','',1,0,2,1,1),(14,'off','','','',0,0,2,1,1)
        """.trimIndent()
        )
        db.execSQL("INSERT INTO story_volumes(id,storyId,title,sortOrder) VALUES(21,13,'volume',0)")
        db.execSQL(
            """
            INSERT INTO story_chapters(id,storyId,volumeId,title,content,sortOrder,contentRevision,createTime,latestTime)
            VALUES(22,13,21,'chapter','text',0,2,1,1)
        """.trimIndent()
        )
    }

    private fun verify(db: SupportSQLiteDatabase) {
        val legacy =
            mapOf("userName" to "Mira", "userDescription" to "Archivist", "userAvatar" to "avatar")
        UserPersonaUpgrade.migrate(db, legacy)
        // 混合覆盖必须逐字段保留，并维持故事两种注入选择。
        assertEquals(6, db.version)
        assertEquals(
            "Mira|Archivist|avatar",
            scalar(db, "SELECT name||'|'||description||'|'||avatarFileUuid FROM user_personas")
        )
        assertEquals("1", scalar(db, "SELECT defaultPersonaId FROM user_persona_settings"))
        assertEquals(
            "1",
            scalar(
                db,
                "SELECT userPersonaId FROM chat_sessions WHERE userName='Mira' AND userDescription=' {{user}} '"
            )
        )
        assertEquals(
            "1",
            scalar(
                db,
                "SELECT userPersonaId FROM group_chat_sessions WHERE userName='Alias' AND userDescription IS NULL"
            )
        )
        assertEquals(
            "1,-1",
            scalar(
                db,
                "SELECT group_concat(userPersonaId) FROM (SELECT userPersonaId FROM stories ORDER BY id)"
            )
        )
        assertEquals("hello", scalar(db, "SELECT content FROM chat_messages WHERE sessionId=11"))
        assertEquals(
            "text",
            scalar(db, "SELECT content FROM story_chapters WHERE storyId=13 AND volumeId=21")
        )
        // 数据库提交后的重复迁入保留用户后来编辑的内容。
        db.execSQL("UPDATE user_personas SET name='renamed' WHERE id=1")
        UserPersonaUpgrade.migrate(db, legacy)
        assertEquals("renamed", scalar(db, "SELECT name FROM user_personas"))
        UserPersonaUpgrade.validate(db)
    }

    private fun scalar(db: SupportSQLiteDatabase, sql: String): String = db.query(sql).use {
        check(it.moveToFirst()) { sql }
        it.getString(0)
    }
}
