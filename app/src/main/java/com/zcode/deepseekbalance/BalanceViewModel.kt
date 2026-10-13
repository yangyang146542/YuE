package com.zcode.deepseekbalance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiState {
    data object Idle : UiState
    data object Loading : UiState
    data class Success(val result: ProviderBalance) : UiState
    data class Error(val message: String) : UiState
}

class BalanceViewModel : ViewModel() {

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun query(provider: Provider, rawKey: String) {
        val key = rawKey.trim()
        if (key.isEmpty()) {
            _state.value = UiState.Error("请先填写 API Key")
            return
        }
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = try {
                UiState.Success(
                    withContext(Dispatchers.IO) { BalanceApi.fetch(provider, key) }
                )
            } catch (e: ApiError) {
                UiState.Error(e.message.orEmpty())
            } catch (e: Exception) {
                UiState.Error(e.message ?: "未知错误")
            }
        }
    }

    fun reset() {
        _state.value = UiState.Idle
    }
}
