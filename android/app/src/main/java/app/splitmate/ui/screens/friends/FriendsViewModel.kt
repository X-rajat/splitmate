package app.splitmate.ui.screens.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.splitmate.data.remote.dto.UserDto
import app.splitmate.data.repository.FriendRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FriendsUiState(
    val friends: List<UserDto> = emptyList(),
    val searchResults: List<UserDto> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class FriendsViewModel @Inject constructor(private val friendRepository: FriendRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(FriendsUiState())
    val uiState: StateFlow<FriendsUiState> = _uiState

    init {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(friends = friendRepository.listFriends())
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Could not load friends: ${e.message ?: "unknown error"}")
            }
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            try {
                val results = if (query.length >= 2) friendRepository.search(query) else emptyList()
                _uiState.value = _uiState.value.copy(searchResults = results, error = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Search failed: ${e.message ?: "unknown error"}")
            }
        }
    }

    fun sendRequest(userId: String) {
        viewModelScope.launch {
            try {
                friendRepository.sendRequest(userId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Could not send request: ${e.message ?: "unknown error"}")
            }
        }
    }
}
