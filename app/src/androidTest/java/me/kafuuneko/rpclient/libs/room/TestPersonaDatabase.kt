package me.kafuuneko.rpclient.libs.room

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import me.kafuuneko.rpclient.libs.room.migration.PersonaSchemaMigration
import me.kafuuneko.rpclient.libs.upgrade.UserPersonaUpgrade

/** 独立的测试主库按应用启动顺序迁入默认人设，不读取设备偏好。 */
internal fun RoomDatabase.Builder<AppDatabase>.withTestPersonas(): RoomDatabase.Builder<AppDatabase> =
    addMigrations(PersonaSchemaMigration).addCallback(object : RoomDatabase.Callback() {
        /** 为独立测试库建立默认身份，避免读取在线用户偏好。 */
        override fun onOpen(db: SupportSQLiteDatabase) {
            UserPersonaUpgrade.migrate(db, emptyMap<String, String>())
        }
    })
