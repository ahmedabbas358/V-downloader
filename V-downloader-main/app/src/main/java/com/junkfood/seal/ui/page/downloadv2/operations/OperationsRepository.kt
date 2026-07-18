package com.junkfood.seal.ui.page.downloadv2.operations

import com.junkfood.seal.App
import com.junkfood.seal.database.AppDatabase
import com.junkfood.seal.download.DownloaderV2
import com.junkfood.seal.download.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OperationsRepository(
    private val downloader: DownloaderV2,
    private val database: AppDatabase
) {

    fun getTasks(): List<Task> {
        return downloader.getTaskStateMap().keys.toList()
    }

    suspend fun clearHistory(tasks: List<Task>) {
        tasks.forEach { task ->
            downloader.remove(task)
            database.downloadOperationDao().deleteById(task.id)
        }
    }

    suspend fun cancelTask(task: Task) {
        downloader.cancel(task)
    }

    suspend fun retryTask(task: Task) {
        downloader.resume(task)
    }

    fun deleteTaskWithUndo(task: Task, deleteFile: Boolean, onUndoExpired: (Task) -> Unit = {}) {
        TrashManager.moveToTrash(task, deleteFile) { t ->
            downloader.remove(t)
            if (deleteFile && t.resultFilePath != null) {
                try {
                    java.io.File(t.resultFilePath).delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            onUndoExpired(t)
        }
    }
}
