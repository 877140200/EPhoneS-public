package com.susking.ephone_s.qq.ui.memories

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryEmbeddingDao
import com.susking.ephone_s.aidata.data.local.entity.AppointmentEntity
import com.susking.ephone_s.aidata.data.local.entity.GeneralMemoryEntity
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEmbedding
import com.susking.ephone_s.aidata.domain.repository.LongTermMemoryRepository
import com.susking.ephone_s.aidata.domain.repository.MemoriesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QqMemoriesViewModel @Inject constructor(
    private val memoriesRepository: MemoriesRepository,
    private val longTermMemoryRepository: LongTermMemoryRepository,
    private val memoryEmbeddingDao: MemoryEmbeddingDao
) : ViewModel() {

    private val _memoryActionEvents: MutableSharedFlow<MemoryActionEvent> = MutableSharedFlow()
    val memoryActionEvents: SharedFlow<MemoryActionEvent> = _memoryActionEvents
    val allLongTermMemories: LiveData<List<LongTermMemory>> = longTermMemoryRepository.getAllMemories().asLiveData()

    fun getAppointmentsByContactId(contactId: String): LiveData<List<AppointmentEntity>> {
        return memoriesRepository.getAppointmentsByContactId(contactId)
    }

    fun getGeneralMemoriesByContactId(contactId: String): LiveData<List<GeneralMemoryEntity>> {
        return memoriesRepository.getMemoriesByContactId(contactId)
    }

    fun observeActiveEmbedding(memoryId: String): Flow<MemoryEmbedding?> {
        return memoryEmbeddingDao.observeActiveEmbeddingForMemory(memoryId)
    }

    fun insert(appointment: AppointmentEntity) = viewModelScope.launch {
        memoriesRepository.insert(appointment)
    }

    fun update(appointment: AppointmentEntity) = viewModelScope.launch {
        memoriesRepository.update(appointment)
    }

    fun delete(appointment: AppointmentEntity) = viewModelScope.launch {
        memoriesRepository.delete(appointment)
    }

    fun insertMemory(memory: GeneralMemoryEntity) = viewModelScope.launch {
        memoriesRepository.insertMemory(memory)
    }

    fun updateMemory(memory: GeneralMemoryEntity) = viewModelScope.launch {
        memoriesRepository.updateMemory(memory)
    }

    fun updateLongTermMemory(memory: LongTermMemory, memoryText: String) = viewModelScope.launch {
        _memoryActionEvents.emit(MemoryActionEvent.Failed("原子事件已改为只读纪念记录，不再允许编辑"))
    }

    fun vectorizeLongTermMemory(memory: LongTermMemory, memoryText: String) = viewModelScope.launch {
        _memoryActionEvents.emit(MemoryActionEvent.Failed("原子事件已改为只读纪念记录，不再允许手动向量化"))
    }

    fun deleteMemory(memory: GeneralMemoryEntity) = viewModelScope.launch {
        memoriesRepository.deleteMemory(memory)
    }

    sealed class MemoryActionEvent {
        data class Succeeded(val message: String) : MemoryActionEvent()
        data class Failed(val message: String) : MemoryActionEvent()
    }
}