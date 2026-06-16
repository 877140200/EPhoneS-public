package com.susking.ephone_s.features.worldbook.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.aidata.data.local.entity.WorldBookEntity
import com.susking.ephone_s.aidata.domain.repository.WorldBookRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// 构造函数已经依赖于接口，是正确的。
class WorldBookViewModel(private val repository: WorldBookRepository) : ViewModel() {

    companion object {
        // 定义一个唯一的内部标签来代表“无分类”
        const val UNCATEGORIZED_TAG = "UNCATEGORIZED_TAG"
        // 定义系统世界书的分类名称
        const val SYSTEM_WORLD_BOOK_CATEGORY = "系统"
    }

    // 所有世界书的Flow，UI层可以订阅这个Flow来获取实时更新
    private val allWorldBooks = repository.getAllWorldBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // 确保“系统”世界书存在
        viewModelScope.launch {
            if (repository.getWorldBookById(SYSTEM_WORLD_BOOK_CATEGORY) == null) {
                val timestamp = System.currentTimeMillis()
                val systemWorldBook = WorldBookEntity(
                    title = SYSTEM_WORLD_BOOK_CATEGORY,
                    category = SYSTEM_WORLD_BOOK_CATEGORY,
                    isSystem = true,
                    order = -1, // 系统世界书始终在最前面，赋予一个负值确保其排在普通世界书之前
                    createdAt = timestamp,
                    updatedAt = timestamp
                )
                repository.insertWorldBook(systemWorldBook)
            }
        }
    }

    // 1. 衍生出所有分类的Flow
    val categories: StateFlow<List<String>> = allWorldBooks.map { books ->
        val bookCategories = books.map { it.category }.distinct()
        val hasUncategorized = bookCategories.any { it.isBlank() }
        val regularCategories = bookCategories.filter { it.isNotBlank() }.sorted()

        // 构建最终的分类列表
        mutableListOf<String>().apply {
            addAll(regularCategories)
            if (hasUncategorized) {
                add(UNCATEGORIZED_TAG) // 如果存在无分类的书，则添加我们的特殊标签
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 2. 保存当前选中的分类
    private val _selectedCategory = MutableStateFlow<String?>(null) // null 表示 "全部"
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    // 3. 组合出过滤后的世界书列表
    val filteredWorldBooks: StateFlow<List<WorldBookEntity>> =
        combine(allWorldBooks, _selectedCategory) { books, category ->
            when (category) {
                null -> books // "全部"
                UNCATEGORIZED_TAG -> books.filter { it.category.isBlank() } // "无分类"
                else -> books.filter { it.category == category } // 其他分类
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 用于发送一次性事件，例如显示消息、导航等
    private val _eventFlow = MutableSharedFlow<WorldBookEvent>()
    val eventFlow: SharedFlow<WorldBookEvent> = _eventFlow.asSharedFlow()

    // 4. 更新选中分类的方法
    fun selectCategory(category: String?) {
        _selectedCategory.value = category
    }

    // 添加新的世界书
    fun createWorldBook(title: String, category: String) {
        viewModelScope.launch {
            val timestamp = System.currentTimeMillis()
            // 获取当前最大的order值，新世界书的order在此基础上递增
            val maxOrder = allWorldBooks.value.maxOfOrNull { it.order } ?: 0
            val newWorldBook = WorldBookEntity(
                title = title,
                category = category,
                createdAt = timestamp,
                updatedAt = timestamp,
                order = maxOrder + 1
            )
            repository.insertWorldBook(newWorldBook)
            _eventFlow.emit(WorldBookEvent.ShowMessage("世界书 '${title}' 创建成功"))
        }
    }

    // 更新世界书
    fun updateWorldBook(worldBook: WorldBookEntity, newTitle: String, newCategory: String) {
        viewModelScope.launch {
            val updatedWorldBook = worldBook.copy(
                title = newTitle,
                category = newCategory,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateWorldBook(updatedWorldBook)
            _eventFlow.emit(WorldBookEvent.ShowMessage("世界书 '${newTitle}' 更新成功"))
        }
    }

    // 更新世界书名称
    fun updateWorldBookName(worldBook: WorldBookEntity, newName: String) {
        viewModelScope.launch {
            val updatedWorldBook = worldBook.copy(title = newName, updatedAt = System.currentTimeMillis())
            repository.updateWorldBook(updatedWorldBook)
            _eventFlow.emit(WorldBookEvent.ShowMessage("名称已更新"))
        }
    }

    // 更新世界书分类
    fun updateWorldBookCategory(worldBook: WorldBookEntity, newCategory: String) {
        viewModelScope.launch {
            val updatedWorldBook = worldBook.copy(category = newCategory, updatedAt = System.currentTimeMillis())
            repository.updateWorldBook(updatedWorldBook)
            _eventFlow.emit(WorldBookEvent.ShowMessage("分类已更新"))
        }
    }

    // 删除世界书
    fun deleteWorldBook(worldBook: WorldBookEntity) {
        viewModelScope.launch {
            if (worldBook.isSystem) {
                _eventFlow.emit(WorldBookEvent.ShowMessage("系统世界书无法删除"))
                return@launch
            }
            repository.deleteWorldBook(worldBook)
            _eventFlow.emit(WorldBookEvent.ShowMessage("世界书 '${worldBook.title}' 删除成功"))
        }
    }

    // 在世界书拖动排序后更新其顺序
    fun onWorldBookMoved(fromPosition: Int, toPosition: Int) {
        viewModelScope.launch {
            val currentList = filteredWorldBooks.value.toMutableList()
            if (fromPosition < 0 || fromPosition >= currentList.size ||
                toPosition < 0 || toPosition >= currentList.size) {
                return@launch
            }

            val movedWorldBook = currentList[fromPosition]
            currentList.removeAt(fromPosition)
            currentList.add(toPosition, movedWorldBook)

            // 重新计算并保存所有受影响世界书的order
            // 注意：系统世界书的order固定为-1，不参与手动排序
            val nonSystemBooks = currentList.filter { !it.isSystem }
            nonSystemBooks.forEachIndexed { index, worldBook ->
                if (worldBook.order != index) { // 只有order变化时才更新
                    repository.updateWorldBookOrder(worldBook.worldBookId, index)
                }
            }
            // 提交新的列表以更新UI (Flow会自动处理)
            // filteredWorldBooks 会重新计算并提交，所以不需要手动提交
        }
    }

    // 根据ID获取世界书 (如果需要详情页或编辑对话框需要预填充数据)
    suspend fun getWorldBookById(worldBookId: Long): WorldBookEntity? {
        return repository.getWorldBookById(worldBookId)
    }

    // 导出世界书
    fun exportWorldBook(worldBook: WorldBookEntity) {
        viewModelScope.launch {
            // TODO: 实现世界书导出逻辑，例如保存到文件、分享等
            _eventFlow.emit(WorldBookEvent.ShowMessage("导出功能待实现: ${worldBook.title}"))
        }
    }

    // 导入世界书
    fun importWorldBook(data: String) {
        viewModelScope.launch {
            // TODO: 实现世界书导入逻辑，例如从文件读取并解析
            _eventFlow.emit(WorldBookEvent.ShowMessage("导入功能待实现"))
        }
    }
}

// 定义ViewModel可能发出的事件
sealed class WorldBookEvent {
    data class ShowMessage(val message: String) : WorldBookEvent()
    // 更多事件，例如：NavigateToDetail(val worldBookId: Long), ShowError(val error: Throwable) 等
}

// ViewModelFactory 用于实例化 WorldBookViewModel 并传入 WorldBookRepository
// Factory 依赖于接口，是正确的。
class WorldBookViewModelFactory(private val repository: WorldBookRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorldBookViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WorldBookViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}