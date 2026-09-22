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
)

@HiltViewModel
class FriendsViewModel @Inject constructor(private val friendRepository: FriendRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(FriendsUiState())
    val uiState: StateFlow<FriendsUiState> = _uiState

    init {
        viewModelScope.launch { _uiState.value = _uiState.value.copy(friends = friendRepository.listFriends()) }
    }

    fun search(query: String) {
        viewModelScope.launch {
            val results = if (query.length >= 2) friendRepository.search(query) else emptyList()
            _uiState.value = _uiState.value.copy(searchResults = results)
        }
    }

    fun sendRequest(userId: String) {
        viewModelScope.launch { friendRepository.sendRequest(userId) }
    }
}
