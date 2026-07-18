package com.junkfood.seal.util

import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import com.junkfood.seal.App.Companion.applicationScope
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.database.AppDatabase
import com.junkfood.seal.database.backup.Backup
import com.junkfood.seal.database.backup.BackupUtil.BackupType
import com.junkfood.seal.database.backup.BackupUtil.decodeToBackup
import com.junkfood.seal.database.objects.CommandTemplate
import com.junkfood.seal.database.objects.CookieProfile
import com.junkfood.seal.database.objects.DownloadedVideoInfo
import com.junkfood.seal.database.objects.DownloadOperation
import com.junkfood.seal.database.objects.OptionShortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DatabaseUtil {
    private const val DATABASE_NAME = "app_database"
    private val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_DownloadedVideoInfo_videoTitle` ON `DownloadedVideoInfo` (`videoTitle`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_DownloadOperation_videoUrl` ON `DownloadOperation` (`videoUrl`)")
        }
    }

    private val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `Subscription` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `url` TEXT NOT NULL, `title` TEXT NOT NULL, `lastCheckedTimestamp` INTEGER NOT NULL)")
        }
    }

    private val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            // Add sessionToken to CookieProfile for session management / persistence
            db.execSQL("ALTER TABLE `CookieProfile` ADD COLUMN `sessionToken` TEXT NOT NULL DEFAULT ''")
        }
    }

    val db by lazy {
        Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
            // Use WAL mode for better concurrent read/write performance and crash safety
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            // Only destroy data on downgrades (e.g. installing older APK), never on upgrades.
            // AutoMigration handles all forward migrations in AppDatabase.
            .fallbackToDestructiveMigrationOnDowngrade()
            .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
            .build()
    }
    private val dao by lazy { db.videoInfoDao() }

    fun initDatabase() {
        applicationScope.launch {
            getTemplateFlow().collect {
                if (it.isEmpty()) PreferenceUtil.initializeTemplateSample()
            }
        }
    }

    fun insertInfo(vararg infoList: DownloadedVideoInfo) {
        applicationScope.launch(Dispatchers.IO) {
            infoList.forEach { dao.insertInfoDistinctByPath(it) }
        }
    }

    fun getDownloadHistoryFlow() = dao.getDownloadHistoryFlow()

    private suspend fun getDownloadHistory() = dao.getDownloadHistory()

    fun getTemplateFlow() = dao.getTemplateFlow()

    fun getCookiesFlow() = dao.getCookieProfileFlow()

    fun getShortcuts() = dao.getOptionShortcuts()

    suspend fun deleteShortcut(shortcut: OptionShortcut) = dao.deleteShortcut(shortcut)

    suspend fun insertShortcut(shortcut: OptionShortcut) = dao.insertShortcut(shortcut)

    suspend fun getCookieById(id: Int) = dao.getCookieById(id)

    suspend fun deleteCookieProfile(profile: CookieProfile) = dao.deleteCookieProfile(profile)

    suspend fun insertCookieProfile(profile: CookieProfile) = dao.insertCookieProfile(profile)

    suspend fun updateCookieProfile(profile: CookieProfile) = dao.updateCookieProfile(profile)

    suspend fun getTemplateList() = dao.getTemplateList()

    suspend fun getShortcutList() = dao.getShortcutList()

    suspend fun deleteInfoList(infoList: List<DownloadedVideoInfo>, deleteFile: Boolean = false) {
        dao.deleteInfoList(infoList)
        infoList.forEach { info -> if (deleteFile) FileUtil.deleteFile(info.videoPath) }
    }

    suspend fun getInfoById(id: Int): DownloadedVideoInfo = dao.getInfoById(id)

    suspend fun deleteInfoById(id: Int) = dao.deleteInfoById(id)

    suspend fun insertTemplate(commandTemplate: CommandTemplate) =
        dao.insertTemplate(commandTemplate)

    suspend fun updateTemplate(commandTemplate: CommandTemplate) {
        dao.updateTemplate(commandTemplate)
    }

    suspend fun importBackup(backup: Backup, types: Set<BackupType>): Int {
        var cnt = 0
        backup.run {
            if (types.contains(BackupType.DownloadHistory)) {
                val itemList = getDownloadHistory()

                if (!downloadHistory.isNullOrEmpty()) {
                    dao.insertAll(
                        downloadHistory
                            .filterNot { itemList.contains(it) }
                            .map { it.copy(id = 0) }
                            .also { cnt += it.size }
                    )
                }
            }
            if (types.contains(BackupType.CommandTemplate)) {
                if (templates != null) {
                    val templateList = getTemplateList()
                    dao.importTemplates(
                        templates
                            .filterNot { templateList.contains(it) }
                            .map { it.copy(id = 0) }
                            .also { cnt += it.size }
                    )
                }
            }
            if (types.contains(BackupType.CommandShortcut)) {
                val shortcutList = getShortcutList()
                if (shortcuts != null) {
                    dao.insertAllShortcuts(
                        shortcuts
                            .filterNot { shortcutList.contains(it) }
                            .map { it.copy(id = 0) }
                            .also { cnt += it.size }
                    )
                }
            }
        }
        return cnt
    }

    suspend fun importTemplatesFromJson(json: String): Int {
        json
            .decodeToBackup()
            .onSuccess { backup ->
                return importBackup(
                    backup = backup,
                    types = setOf(BackupType.CommandTemplate, BackupType.CommandShortcut),
                )
            }
            .onFailure { it.printStackTrace() }
        return 0
    }

    suspend fun deleteTemplateById(id: Int) = dao.deleteTemplateById(id)

    suspend fun deleteTemplates(templates: List<CommandTemplate>) = dao.deleteTemplates(templates)

    // ---- Download Operations (persistent logging) ----

    fun getDownloadOperationsFlow() = dao.getDownloadOperationsFlow()

    fun insertDownloadOperation(operation: DownloadOperation) {
        applicationScope.launch(Dispatchers.IO) {
            try {
                dao.insertDownloadOperation(operation)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert download operation: ${e.message}")
            }
        }
    }

    suspend fun insertDownloadOperationSuspend(operation: DownloadOperation): Long {
        return dao.insertDownloadOperation(operation)
    }

    suspend fun updateDownloadOperation(operation: DownloadOperation) {
        dao.updateDownloadOperation(operation)
    }

    suspend fun deleteDownloadOperation(operation: DownloadOperation) {
        dao.deleteDownloadOperation(operation)
    }

    suspend fun clearDownloadOperations() {
        dao.clearDownloadOperations()
    }

    private const val TAG = "DatabaseUtil"
}
