package com.junkfood.seal.ui.page.downloadv2.operations

import com.junkfood.seal.download.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class TrashedItem(
    val task: Task,
    val deleteFile: Boolean,
    val originalFilePath: String? = null
)

object TrashManager {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val trashMap = mutableMapOf<String, Job>()
    private val trashedItems = mutableMapOf<String, TrashedItem>()
    private val _trashedTaskIds = MutableStateFlow<Set<String>>(emptySet())
    val trashedTaskIds: StateFlow<Set<String>> = _trashedTaskIds.asStateFlow()

    private fun updateStateFlow() {
        _trashedTaskIds.value = trashedItems.keys.toSet()
    }

    fun moveToTrash(task: Task, deleteFile: Boolean, onPermanentDelete: suspend (Task) -> Unit) {
        val taskId = task.id
        
        // If already in trash, cancel the old job
        trashMap[taskId]?.cancel()
        
        val item = TrashedItem(task, deleteFile, task.resultFilePath)
        trashedItems[taskId] = item
        updateStateFlow()

        // Set a timer for 5 seconds to permanently delete
        trashMap[taskId] = scope.launch {
            delay(5000)
            executeDeletion(taskId, onPermanentDelete)
        }
    }

    fun undo(taskId: String): Task? {
        trashMap[taskId]?.cancel()
        trashMap.remove(taskId)
        val task = trashedItems.remove(taskId)?.task
        updateStateFlow()
        return task
    }

    suspend fun executeAllPendingDeletions(onPermanentDelete: suspend (Task) -> Unit) {
        val pendingIds = trashedItems.keys.toList()
        for (id in pendingIds) {
            trashMap[id]?.cancel()
            executeDeletion(id, onPermanentDelete)
        }
    }

    private suspend fun executeDeletion(taskId: String, onPermanentDelete: suspend (Task) -> Unit) {
        val item = trashedItems.remove(taskId) ?: return
        trashMap.remove(taskId)
        updateStateFlow()

        if (item.deleteFile) {
            item.task.resultFilePath?.let { path ->
                try {
                    val file = File(path)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        onPermanentDelete(item.task)
    }
}
