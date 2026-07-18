package com.junkfood.seal.ui.page.downloadv2.operations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.junkfood.seal.download.Task
import com.junkfood.seal.ui.page.downloadv2.Filter
import com.junkfood.seal.util.PreferenceUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortMode {
    DATE_DESC, DATE_ASC
}

data class OperationsState(
    val tasks: List<Task> = emptyList(),
    val selectedTaskIds: Set<String> = emptySet(),
    val isMultiSelectMode: Boolean = false,
    val sortMode: SortMode = SortMode.DATE_DESC,
    val activeFilter: Filter = Filter.All,
    val trashedTaskIds: Set<String> = emptySet()
)

class OperationsViewModel(
    private val repository: OperationsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OperationsState())
    val state: StateFlow<OperationsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val savedSortMode = PreferenceUtil.getInt(PreferenceUtil.OPERATIONS_SORT_MODE, SortMode.DATE_DESC.ordinal)
            val savedFilter = PreferenceUtil.getInt(PreferenceUtil.OPERATIONS_ACTIVE_FILTER, Filter.All.ordinal)
            _state.value = _state.value.copy(
                sortMode = SortMode.entries.getOrElse(savedSortMode) { SortMode.DATE_DESC },
                activeFilter = Filter.entries.getOrElse(savedFilter) { Filter.All }
            )
        }
        viewModelScope.launch {
            TrashManager.trashedTaskIds.collect { trashed ->
                _state.value = _state.value.copy(trashedTaskIds = trashed)
            }
        }
    }

    fun toggleSelection(taskId: String) {
        val currentSelected = _state.value.selectedTaskIds.toMutableSet()
        if (currentSelected.contains(taskId)) {
            currentSelected.remove(taskId)
        } else {
            currentSelected.add(taskId)
        }
        
        _state.value = _state.value.copy(
            selectedTaskIds = currentSelected,
            isMultiSelectMode = currentSelected.isNotEmpty()
        )
    }

    fun clearSelection() {
        _state.value = _state.value.copy(
            selectedTaskIds = emptySet(),
            isMultiSelectMode = false
        )
    }

    fun deleteSelected(deleteFiles: Boolean, onTaskDeleted: (Task) -> Unit) {
        val selectedIds = _state.value.selectedTaskIds
        val tasksToDelete = repository.getTasks().filter { it.id in selectedIds }
        
        tasksToDelete.forEach { task ->
            TrashManager.moveToTrash(task, deleteFiles) { permanentlyDeletedTask ->
                viewModelScope.launch {
                    repository.clearHistory(listOf(permanentlyDeletedTask))
                    onTaskDeleted(permanentlyDeletedTask)
                }
            }
        }
        clearSelection()
    }

    fun setSortMode(mode: SortMode) {
        _state.value = _state.value.copy(sortMode = mode)
        PreferenceUtil.updateValue(PreferenceUtil.OPERATIONS_SORT_MODE, mode.ordinal)
    }

    fun setFilter(filter: Filter) {
        _state.value = _state.value.copy(activeFilter = filter)
        PreferenceUtil.updateValue(PreferenceUtil.OPERATIONS_ACTIVE_FILTER, filter.ordinal)
    }

    fun deleteTasks(tasks: List<Task>, deleteFile: Boolean, onUndoExpired: (Task) -> Unit = {}) {
        tasks.forEach { task ->
            repository.deleteTaskWithUndo(task, deleteFile, onUndoExpired)
        }
        clearSelection()
    }

    fun undoDeletion(taskId: String) {
        TrashManager.undo(taskId)
    }
}
