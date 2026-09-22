package app.splitmate.ui.screens.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.splitmate.data.remote.dto.ExpenseCreateRequest
import app.splitmate.data.remote.dto.ExpensePaymentIn
import app.splitmate.domain.balance.BalanceCalculator
import app.splitmate.domain.balance.BalanceEngineException
import app.splitmate.data.repository.ExpenseRepository
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
    val paidBy: String = "",
    val participantIds: Set<String> = emptySet(),
    val error: String? = null,
    val savedOffline: Boolean = false,
    val success: Boolean = false,
)

@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val groupId: String = checkNotNull(savedStateHandle["groupId"])
    private val _uiState = MutableStateFlow(AddExpenseUiState())
    val uiState: StateFlow<AddExpenseUiState> = _uiState

    fun update(transform: (AddExpenseUiState) -> AddExpenseUiState) {
        _uiState.value = transform(_uiState.value)
    }

    fun submit() {
        val s = _uiState.value
        val amountMinor = (s.amountText.toDoubleOrNull()?.times(100))?.toLong()
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
            val synced = expenseRepository.createExpense(groupId, request)
            update { it.copy(success = true, savedOffline = !synced, error = null) }
        }
    }
}
