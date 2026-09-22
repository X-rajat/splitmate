package app.splitmate.ui.screens.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.splitmate.data.local.entities.ExpenseEntity
import app.splitmate.data.remote.dto.BalanceEntryDto
import app.splitmate.data.remote.dto.InvitationDto
import app.splitmate.data.remote.dto.SettlementSuggestionDto
import app.splitmate.data.repository.ExpenseRepository
import app.splitmate.data.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupDetailUiState(
    val groupId: String = "",
    val expenses: List<ExpenseEntity> = emptyList(),
    val balances: List<BalanceEntryDto> = emptyList(),
    val suggestedSettlements: List<SettlementSuggestionDto> = emptyList(),
    val invitation: InvitationDto? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val groupRepository: GroupRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val groupId: String = checkNotNull(savedStateHandle["groupId"])
    private val _uiState = MutableStateFlow(GroupDetailUiState(groupId = groupId))
    val uiState: StateFlow<GroupDetailUiState> = _uiState

    init {
        viewModelScope.launch {
            expenseRepository.observeCachedExpenses(groupId).collect { expenses ->
                _uiState.value = _uiState.value.copy(expenses = expenses)
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                expenseRepository.refresh(groupId)
                val balances = expenseRepository.getBalances(groupId)
                val suggestions = expenseRepository.getSuggestedSettlements(groupId)
                _uiState.value = _uiState.value.copy(balances = balances, suggestedSettlements = suggestions, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Could not refresh - showing cached data")
            }
        }
    }

    fun generateInvite() {
        viewModelScope.launch {
            try {
                val invitation = groupRepository.createInvitation(groupId)
                _uiState.value = _uiState.value.copy(invitation = invitation)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Could not create invitation")
            }
        }
    }
}
