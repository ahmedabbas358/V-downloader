package com.junkfood.seal.download

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.junkfood.seal.download.engine.DownloadEngine
import org.koin.core.component.KoinComponent

interface DownloaderV2 {
    fun getTaskStateMap(): SnapshotStateMap<Task, Task.State>

    fun cancel(task: Task): Boolean

    fun cancel(taskId: String): Boolean {
        return getTaskStateMap()
            .keys
            .find { it.id == taskId }
            ?.let { cancel(it) }
            ?: false
    }

    fun pause(taskId: String): Boolean {
        return getTaskStateMap()
            .keys
            .find { it.id == taskId }
            ?.let { pause(it) }
            ?: false
    }

    fun restart(task: Task)

    fun pause(task: Task): Boolean

    /** Enqueue a [Task] with an empty [Task.State]. */
    fun enqueue(task: Task)

    fun enqueue(task: Task, state: Task.State)

    fun enqueue(taskWithState: TaskFactory.TaskWithState) {
        val (task, state) = taskWithState
        enqueue(task, state)
    }

    fun remove(task: Task): Boolean

    fun prioritize(task: Task)
}

internal object FakeDownloaderV2 : DownloaderV2 {
    override fun getTaskStateMap(): SnapshotStateMap<Task, Task.State> {
        return mutableStateMapOf()
    }

    override fun cancel(task: Task): Boolean = false

    override fun pause(task: Task): Boolean = false

    override fun restart(task: Task) = Unit

    override fun enqueue(task: Task) = Unit

    override fun enqueue(task: Task, state: Task.State) = Unit

    override fun remove(task: Task): Boolean = true

    override fun prioritize(task: Task) = Unit
}

/**
 * DownloaderV2Impl
 *
 * Implements [DownloaderV2] by delegating to the modular [DownloadEngine].
 */
class DownloaderV2Impl(
    private val appContext: Context,
    private val engine: DownloadEngine = DownloadEngine(appContext),
) : DownloaderV2, KoinComponent {

    override fun getTaskStateMap(): SnapshotStateMap<Task, Task.State> =
        engine.getTaskStateMap()

    override fun cancel(task: Task): Boolean =
        engine.cancel(task)

    override fun pause(task: Task): Boolean =
        engine.pause(task)

    override fun restart(task: Task) =
        engine.restart(task)

    override fun enqueue(task: Task) =
        engine.enqueue(task)

    override fun enqueue(task: Task, state: Task.State) =
        engine.enqueue(task, state)

    override fun remove(task: Task): Boolean =
        engine.remove(task)

    override fun prioritize(task: Task) =
        engine.prioritize(task)
}
