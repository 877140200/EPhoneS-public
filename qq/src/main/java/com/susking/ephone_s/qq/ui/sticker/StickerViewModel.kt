package com.susking.ephone_s.qq.ui.sticker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.aidata.data.local.entity.StickerCategoryEntity
import com.susking.ephone_s.aidata.data.local.entity.StickerEntity
import com.susking.ephone_s.aidata.domain.repository.StickerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Collections
import javax.inject.Inject

@HiltViewModel
class StickerViewModel @Inject constructor(
    private val stickerRepository: StickerRepository
) : ViewModel() {

    // 所有分类
    private val _allCategories = MutableStateFlow<List<StickerCategoryEntity>>(emptyList())
    val allCategories: StateFlow<List<StickerCategoryEntity>> = _allCategories

    // 所有用户添加的表情
    private val _userStickers = MutableStateFlow<List<StickerEntity>>(emptyList())

    // 对外只读暴露全量表情列表，供重名校验等场景读取当前快照（不受分类/搜索筛选影响）
    val allStickers: StateFlow<List<StickerEntity>> = _userStickers

    // 当前选中的分类ID
    private val _activeStickerCategoryId = MutableStateFlow<Int?>(null)
    val activeStickerCategoryId: StateFlow<Int?> = _activeStickerCategoryId

    // 搜索关键词
    private val _searchTerm = MutableStateFlow<String>("")
    val searchTerm: StateFlow<String> = _searchTerm

    // 当前显示的表情列表 (根据分类和搜索词筛选)
    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredStickers: StateFlow<List<StickerEntity>> = combine(
        _userStickers,
        _activeStickerCategoryId,
        _searchTerm
    ) { stickers, activeCategoryId, searchTerm ->
        val filteredByCategory = when (activeCategoryId) {
            null -> stickers // 全部
            0 -> stickers.filter { it.categoryId == null } // 未分类 (虚拟ID 0)
            else -> stickers.filter { it.categoryId == activeCategoryId }
        }

        if (searchTerm.isBlank()) {
            filteredByCategory
        } else {
            filteredByCategory.filter {
                it.name.contains(searchTerm, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // 管理模式
    private val _isManagementMode = MutableStateFlow(false)
    val isManagementMode: StateFlow<Boolean> = _isManagementMode

    init {
        viewModelScope.launch {
            stickerRepository.getAllCategories().collect { categories ->
                val virtualCategories = mutableListOf(
                    StickerCategoryEntity(id = -1, name = "全部"),
                    StickerCategoryEntity(id = 0, name = "未分类")
                )
                virtualCategories.addAll(categories)
                _allCategories.value = virtualCategories
            }
        }

        viewModelScope.launch {
            stickerRepository.getAllStickers().collect { stickers ->
                _userStickers.value = stickers.sortedBy { it.displayOrder }
            }
        }
    }

    fun setSearchTerm(term: String) {
        _searchTerm.value = term
    }

    fun toggleManagementMode() {
        _isManagementMode.value = !_isManagementMode.value
    }

    fun setActiveCategory(categoryId: Int?) {
        _activeStickerCategoryId.value = categoryId
    }

    fun insertSticker(sticker: StickerEntity) {
        viewModelScope.launch {
            stickerRepository.insertSticker(sticker)
        }
    }

    fun insertStickers(stickers: List<StickerEntity>) {
        viewModelScope.launch {
            stickerRepository.insertStickers(stickers)
        }
    }

    fun deleteStickers(stickersToDelete: List<StickerEntity>) {
        viewModelScope.launch {
            stickerRepository.deleteStickers(stickersToDelete)
        }
    }

    /**
     * 重命名单个表情。
     * @param sticker 要重命名的表情
     * @param newName 新名称（调用方需保证已去除首尾空白且非空）
     */
    fun renameSticker(sticker: StickerEntity, newName: String) {
        viewModelScope.launch {
            stickerRepository.updateStickers(listOf(sticker.copy(name = newName)))
        }
    }

    fun insertCategory(category: StickerCategoryEntity) {
        viewModelScope.launch {
            stickerRepository.insertCategory(category)
        }
    }

    fun deleteCategory(category: StickerCategoryEntity) {
        viewModelScope.launch {
            stickerRepository.deleteCategory(category)
        }
    }

    fun moveSticker(fromPosition: Int, toPosition: Int) {
        val currentList = filteredStickers.value.toMutableList()
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(currentList, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(currentList, i, i - 1)
            }
        }
        _userStickers.value = currentList
        updateStickerOrder(currentList)
    }

    fun pinStickers(stickersToPin: List<StickerEntity>) {
        viewModelScope.launch {
            val currentStickers = _userStickers.value.toMutableList()
            val pinnedIds = stickersToPin.map { it.id }.toSet()

            // 从列表中移除要置顶的表情
            val (pinned, others) = currentStickers.partition { it.id in pinnedIds }

            // 将置顶的表情移到列表顶部
            val reorderedList = pinned + others

            // 立即更新displayOrder属性，以确保乐观更新和最终状态一致
            val finalList = reorderedList.mapIndexed { index, sticker ->
                sticker.copy(displayOrder = index)
            }

            // 使用包含正确displayOrder的列表进行乐观更新
            _userStickers.value = finalList

            // 使用相同的列表更新数据库
            stickerRepository.updateStickers(finalList)
        }
    }

    private fun updateStickerOrder(listToUpdate: List<StickerEntity>) {
        viewModelScope.launch {
            val stickersToUpdate = listToUpdate.mapIndexed { index, sticker ->
                sticker.copy(displayOrder = index)
            }
            stickerRepository.updateStickers(stickersToUpdate)
        }
    }
}