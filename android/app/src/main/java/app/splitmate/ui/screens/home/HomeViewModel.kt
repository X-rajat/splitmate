package app.splitmate.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.splitmate.data.local.entities.GroupEntity
import app.splitmate.data.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val groups: List<GroupEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isOffline: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(private val groupRepository: GroupRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        viewModelScope.launch {
            groupRepository.observeCachedGroups().collect { groups ->
                _uiState.value = _uiState.value.copy(groups = groups)
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                groupRepository.refresh()
                _uiState.value = _uiState.value.copy(isLoading = false, isOffline = false)
            } catch (e: Exception) {
                // Cached Room data (already emitted above) stays visible; just flag offline mode.
                _uiState.value = _uiState.value.copy(isLoading = false, isOffline = true)
            }
        }
    }
}
