package com.comai.ui.screens.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comai.contextengine.db.Memory
import com.comai.memory.MemoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MemoryViewModel(
    private val memoryRepository: MemoryRepository
) : ViewModel() {

    val memories: StateFlow<List<Memory>> = memoryRepository.getAllMemoriesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            memoryRepository.deleteMemory(id)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            memoryRepository.clearAll()
        }
    }
}
