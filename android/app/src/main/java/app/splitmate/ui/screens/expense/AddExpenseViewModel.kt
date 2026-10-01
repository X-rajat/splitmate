package app.splitmate.ui.screens.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.splitmate.data.remote.dto.ExpenseCreateRequest
import app.splitmate.data.remote.dto.ExpensePaymentIn
import app.splitmate.data.remote.dto.GroupMemberDto
import app.splitmate.domain.balance.BalanceCalculator
import app.splitmate.domain.balance.BalanceEngineException
import app.splitmate.data.repository.ExpenseRepository
import app.splitmate.data.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

val EXPENSE_CATEGORIES = listOf(
    "food", "travel", "hotel", "transport", "shopping", "entertainment", "bills", "groceries", "rent", "utilities", "other",
)

data class AddExpenseUiState(
    val description: String = "",
    val amountText: String = "",
    val category: String = "other",
    val splitType: String = "equal",
    val members: List<GroupMemberDto> = emptyList(),
    val paidBy: String = "",
    val participantIds: Set<String> = emptySet(),
    val error: String? = null,
    val savedOffline: Boolean = false,
    val success: Boolean = false,
)

@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val groupRepository: GroupRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val groupId: String = checkNotNull(savedStateHandle["groupId"])
    private val _uiState = MutableStateFlow(AddExpenseUiState())
    val uiState: StateFlow<AddExpenseUiState> = _uiState

    init {
        loadMembers()
    }

    private fun loadMembers() {
        viewModelScope.launch {
            try {
                val members = groupRepository.listMembers(groupId)
                // Default to everyone in the split, paid by the first member, like Splitwise does.
                update {
                    it.copy(
                        members = members,
                        participantIds = members.map { m -> m.user_id }.toSet(),
                        paidBy = members.firstOrNull()?.user_id.orEmpty(),
                    )
                }
            } catch (e: Exception) {
                update { it.copy(error = "Could not load group members: ${e.message ?: "unknown error"}") }
            }
        }
    }

    fun update(transform: (AddExpenseUiState) -> AddExpenseUiState) {
        _uiState.value = transform(_uiState.value)
    }

    fun toggleParticipant(userId: String) {
        update {
            val next = if (userId in it.participantIds) it.participantIds - userId else it.participantIds + userId
            it.copy(participantIds = next)
        }
    }

    fun addGuest(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                val guest = groupRepository.addPlaceholderMember(groupId, name)
                update { it.copy(members = it.members + guest, participantIds = it.participantIds + guest.user_id, error = null) }
            } catch (e: Exception) {
                update { it.copy(error = "Could not add guest: ${e.message ?: "unknown error"}") }
            }
        }
    }

    fun submit() {
        val s = _uiState.value
        // Parse via BigDecimal, not Double*100, so "19.99" can't become 1998 paise
        // instead of 1999 from binary floating-point rounding.
        val amountMinor = try {
            s.amountText.trim().toBigDecimal().movePointRight(2).longValueExact()
        } catch (e: Exception) {
            null
        }
        if (amountMinor == null || amountMinor <= 0) {
            update { it.copy(error = "Enter a valid amount") }
            return
        }
        if (s.paidBy.isBlank() || s.participantIds.isEmpty()) {
            update { it.copy(error = "Choose who paid and who's splitting") }
            return
        }
        val liabilities = try {
            BalanceCalculator.equalSplit(amountMinor, s.participantIds.toList())
        } catch (e: BalanceEngineException) {
            update { it.copy(error = e.message) }
            return
        }

        if (s.description.isBlank()) {
            update { it.copy(error = "Enter a description") }
            return
        }

        viewModelScope.launch {
            val request = ExpenseCreateRequest(
                description = s.description,
                amount_minor = amountMinor,
                currency = "INR",
                category = s.category,
                split_type = s.splitType,
                participant_ids = s.participantIds.toList(),
                payments = listOf(ExpensePaymentIn(s.paidBy, amountMinor)),
            )
            try {
                val synced = expenseRepository.createExpense(groupId, request)
                update { it.copy(success = true, savedOffline = !synced, error = null) }
            } catch (e: Exception) {
                // A real server rejection (e.g. 401/422/500) - not an offline/IOException,
                // which ExpenseRepository already handles by queuing for later sync.
                update { it.copy(error = "Could not save expense: ${e.message ?: "unknown error"}") }
            }
        }
    }
}
