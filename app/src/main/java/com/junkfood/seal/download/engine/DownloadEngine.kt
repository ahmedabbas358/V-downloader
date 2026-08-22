package com.junkfood.seal.download.engine

import android.content.Context
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.download.Task
import com.junkfood.seal.download.TaskFactory
import com.junkfood.seal.download.engine.builder.DownloadCommandBuilder
import com.junkfood.seal.download.engine.builder.FormatSelectorBuilder
import com.junkfood.seal.download.engine.builder.NetworkOptionBuilder
import com.junkfood.seal.download.engine.builder.OutputTemplateBuilder
import com.junkfood.seal.download.engine.builder.SubtitleOptionBuilder
import com.junkfood.seal.download.engine.postprocess.PostDownloadCoordinator
import com.junkfood.seal.download.engine.resilience.FileCollisionResolver

import com.junkfood.seal.download.engine.subtitle.SubtitleManager

/**
 * DownloadEngine
 *
 * The unified facade representing the modular download engine.
 * Coordinates between:
 * - Queue Management ([DownloadQueueManager])
 * - Command Building ([DownloadCommandBuilder])
 * - Format & Subtitle Generation ([FormatSelectorBuilder], [SubtitleOptionBuilder])
 * - Execution ([DownloadTaskExecutor])
 * - Post-Processing ([PostDownloadCoordinator])
 * - Resilience & Fallbacks ([FileCollisionResolver])
 */
class DownloadEngine(
    private val appContext: Context = context,
    val queueManager: DownloadQueueManager = DownloadQueueManager(appContext),
) {

    fun getTaskStateMap(): SnapshotStateMap<Task, Task.State> =
        queueManager.taskStateMap

    fun enqueue(task: Task) =
        queueManager.enqueue(task)

    fun enqueue(task: Task, state: Task.State) =
        queueManager.enqueue(task, state)

    fun enqueue(taskWithState: TaskFactory.TaskWithState) {
        val (task, state) = taskWithState
        enqueue(task, state)
    }

    fun cancel(task: Task): Boolean =
        queueManager.cancel(task, isPaused = false)

    fun cancel(taskId: String): Boolean =
        queueManager.taskStateMap.keys.find { it.id == taskId }?.let { cancel(it) } ?: false

    fun pause(task: Task): Boolean =
        queueManager.cancel(task, isPaused = true)

    fun pause(taskId: String): Boolean =
        queueManager.taskStateMap.keys.find { it.id == taskId }?.let { pause(it) } ?: false

    fun restart(task: Task) =
        queueManager.restart(task)

    fun remove(task: Task): Boolean =
        queueManager.remove(task)

    fun prioritize(task: Task) =
        queueManager.prioritize(task)
}
