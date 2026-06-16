package com.susking.ephone_s.features.worldbook.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.aidata.data.local.entity.WorldBookEntryEntity
import com.susking.ephone_s.aidata.domain.repository.WorldBookEntryRepository
import com.susking.ephone_s.aidata.domain.repository.WorldBookRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// 构造函数已经依赖于接口，是正确的。
class WorldBookDetailViewModel(
    private val entryRepository: WorldBookEntryRepository,
    private val worldBookRepository: WorldBookRepository, // 注入WorldBookRepository
    private val worldBookId: Long
) : ViewModel() {

    companion object {
        const val SYSTEM_ENTRY_BREAK_LIMIT = "破限"
        const val SYSTEM_ENTRY_WRITING_STYLE = "文风"
    }

    private val _isSystemBook = MutableStateFlow(false)
    val isSystemBook = _isSystemBook.asStateFlow()

    init {
        viewModelScope.launch {
            val worldBook = worldBookRepository.getWorldBookById(worldBookId)
            _isSystemBook.value = worldBook?.isSystem ?: false
            if (worldBook?.isSystem == true) {
                // 检查并创建“破限”条目
                if (entryRepository.getEntryByWorldBookIdAndName(worldBookId, SYSTEM_ENTRY_BREAK_LIMIT) == null) {
                    val newEntry = WorldBookEntryEntity(
                        worldBookId = worldBookId,
                        name = SYSTEM_ENTRY_BREAK_LIMIT,
                        content = "",
                        isSystemEntry = true,
                        displayOrder = 0
                    )
                    entryRepository.insertEntry(newEntry)
                }
                // 检查并创建“文风”条目
                if (entryRepository.getEntryByWorldBookIdAndName(worldBookId, SYSTEM_ENTRY_WRITING_STYLE) == null) {
                    val newEntry = WorldBookEntryEntity(
                        worldBookId = worldBookId,
                        name = SYSTEM_ENTRY_WRITING_STYLE,
                        content = "",
                        isSystemEntry = true,
                        displayOrder = 1
                    )
                    entryRepository.insertEntry(newEntry)
                }
            }
        }
    }

    // 使用 stateIn 将 Flow 转换为 StateFlow，以便UI可以订阅并获取最新状态和缓存数据
    val entries = entryRepository.getEntriesForWorldBook(worldBookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 用于发送一次性事件，例如显示消息
    private val _eventFlow = MutableSharedFlow<DetailEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    fun addEntry(name: String, content: String) {
        viewModelScope.launch {
            // 系统世界书不能手动添加条目
            val worldBook = worldBookRepository.getWorldBookById(worldBookId)
            if (worldBook?.isSystem == true) {
                _eventFlow.emit(DetailEvent.ShowMessage("系统世界书不能手动添加条目"))
                return@launch
            }

            // 新条目应该排在列表末尾
            val currentOrder = entries.value.size
            val newEntry = WorldBookEntryEntity(
                worldBookId = worldBookId,
                name = name,
                content = content,
                displayOrder = currentOrder
            )
            entryRepository.insertEntry(newEntry)
            _eventFlow.emit(DetailEvent.ShowMessage("条目已添加"))
        }
    }

    fun deleteEntryById(entryId: Long) {
        viewModelScope.launch {
            val entryToDelete = entries.value.find { it.entryId == entryId }
            entryToDelete?.let {
                if (it.isSystemEntry) {
                    _eventFlow.emit(DetailEvent.ShowMessage("系统条目无法删除"))
                    return@launch
                }
                entryRepository.deleteEntry(it)
                _eventFlow.emit(DetailEvent.ShowMessage("条目已删除"))
            }
        }
    }

    fun updateEntry(entry: WorldBookEntryEntity) {
        viewModelScope.launch {
            entryRepository.updateEntry(entry)
        }
    }

    fun updateEntry(entryId: Long, newName: String, newContent: String, newLampColor: String) {
        viewModelScope.launch {
            val entryToUpdate = entries.value.find { it.entryId == entryId }
            entryToUpdate?.let {
                val nameToUse = if (it.isSystemEntry) it.name else newName // 系统条目名称不可修改
                val updatedEntry = it.copy(name = nameToUse, content = newContent, lampColor = newLampColor)
                entryRepository.updateEntry(updatedEntry)
                _eventFlow.emit(DetailEvent.ShowMessage("条目已更新"))
            }
        }
    }

    fun updateEntryOrder(reorderedEntries: List<WorldBookEntryEntity>) {
        viewModelScope.launch {
            // 过滤掉系统条目，系统条目不参与手动排序
            val nonSystemEntries = reorderedEntries.filter { !it.isSystemEntry }
            // 为非系统条目分配新的 displayOrder
            val updatedEntries = nonSystemEntries.mapIndexed { index, entry ->
                entry.copy(displayOrder = index)
            }
            entryRepository.updateEntries(updatedEntries)
        }
    }
}

sealed class DetailEvent {
    data class ShowMessage(val message: String) : DetailEvent()
}

// ViewModelFactory 用于实例化 WorldBookDetailViewModel
// Factory 依赖于接口，是正确的。
class WorldBookDetailViewModelFactory(
    private val entryRepository: WorldBookEntryRepository,
    private val worldBookRepository: WorldBookRepository,
    private val worldBookId: Long
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorldBookDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WorldBookDetailViewModel(entryRepository, worldBookRepository, worldBookId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}