package app.splitmate.ui.screens.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.splitmate.data.remote.dto.ExpenseCreateRequest
import app.splitmate.data.remote.dto.ExpenseParticipantIn
import app.splitmate.data.remote.dto.ExpensePaymentIn
import app.splitmate.data.remote.dto.GroupMemberDto
import app.splitmate.data.repository.ExpenseRepository
import app.splitmate.data.repository.GroupRepository
import app.splitmate.domain.balance.BalanceEngineException
import app.splitmate.domain.expense.ExpensePayloadBuilder
import app.splitmate.domain.expense.PayerInput
import app.splitmate.domain.expense.SplitMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddExpenseUiState(
    val description: String = "",
    val amountText: String = "",
    val category: String = "other",
    val members: List<GroupMemberDto> = emptyList(),
    val membersLoading: Boolean = true,
    // Payer selection
    val multiPayer: Boolean = false,
    val singlePayerId: String? = null,
    val selectedPayerIds: Set<String> = emptySet(),
    val payerAmountText: Map<String, String> = emptyMap(),
    // Split selection
    val splitMode: SplitMode = SplitMode.EQUAL,
    val includedParticipantIds: Set<String> = emptySet(),
    val exactAmountText: Map<String, String> = emptyMap(),
    val percentageText: Map<String, String> = emptyMap(),
    val shareCounts: Map<String, Int> = emptyMap(),
    // Derived / status
    val validationError: String? = null,
    val error: String? = null,
    val savedOffline: Boolean = false,
    val success: Boolean = false,
)

fun GroupMemberDto.displayName(): String = if (is_placeholder) "$name (guest)" else name

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
                mutate {
                    it.copy(
                        members = members,
                        membersLoading = false,
                        singlePayerId = members.firstOrNull()?.user_id,
                        includedParticipantIds = members.map { m -> m.user_id }.toSet(),
                        shareCounts = members.associate { m -> m.user_id to 1 },
                    )
                }
            } catch (e: Exception) {
                mutate { it.copy(membersLoading = false, error = "Could not load group members: ${e.message ?: "unknown error"}") }
            }
        }
    }

    private fun mutate(transform: (AddExpenseUiState) -> AddExpenseUiState) {
        val next = transform(_uiState.value)
        _uiState.value = next.copy(validationError = computeValidationError(next))
    }

    fun setDescription(value: String) = mutate { it.copy(description = value) }
    fun setAmountText(value: String) = mutate { it.copy(amountText = value) }
    fun setCategory(value: String) = mutate { it.copy(category = value) }

    fun toggleMultiPayer(enabled: Boolean) = mutate {
        it.copy(
            multiPayer = enabled,
            singlePayerId = if (enabled) null else it.members.firstOrNull()?.user_id,
            selectedPayerIds = emptySet(),
            payerAmountText = emptyMap(),
        )
    }

    fun setSinglePayer(memberId: String) = mutate { it.copy(singlePayerId = memberId) }

    fun togglePayerSelected(memberId: String) = mutate {
        val next = if (memberId in it.selectedPayerIds) it.selectedPayerIds - memberId else it.selectedPayerIds + memberId
        it.copy(selectedPayerIds = next)
    }

    fun setPayerAmountText(memberId: String, text: String) = mutate {
        it.copy(payerAmountText = it.payerAmountText + (memberId to text))
    }

    fun setSplitMode(mode: SplitMode) = mutate { it.copy(splitMode = mode) }

    fun toggleParticipant(memberId: String) = mutate {
        val next = if (memberId in it.includedParticipantIds) it.includedParticipantIds - memberId else it.includedParticipantIds + memberId
        it.copy(includedParticipantIds = next)
    }

    fun setExactAmountText(memberId: String, text: String) = mutate {
        it.copy(exactAmountText = it.exactAmountText + (memberId to text))
    }

    fun setPercentageText(memberId: String, text: String) = mutate {
        it.copy(percentageText = it.percentageText + (memberId to text))
    }

    fun setShares(memberId: String, shares: Int) = mutate {
        it.copy(shareCounts = it.shareCounts + (memberId to shares.coerceAtLeast(1)))
    }

    fun addGuest(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                val guest = groupRepository.addPlaceholderMember(groupId, name)
                mutate {
                    it.copy(
                        members = it.members + guest,
                        includedParticipantIds = it.includedParticipantIds + guest.user_id,
                        shareCounts = it.shareCounts + (guest.user_id to 1),
                        error = null,
                    )
                }
            } catch (e: Exception) {
                mutate { it.copy(error = "Could not add guest: ${e.message ?: "unknown error"}") }
            }
        }
    }

    /** Parses rupees -> paise, or a percentage -> basis points - both are a x100 minor-unit move. */
    private fun toMinorUnits(text: String): Long? = try {
        text.trim().toBigDecimal().movePointRight(2).longValueExact()
    } catch (e: Exception) {
        null
    }

    private fun buildPayments(s: AddExpenseUiState): List<PayerInput> {
        return if (s.multiPayer) {
            s.selectedPayerIds.mapNotNull { id ->
                toMinorUnits(s.payerAmountText[id].orEmpty())?.let { PayerInput(id, it) }
            }
        } else {
            s.singlePayerId?.let { id ->
                toMinorUnits(s.amountText)?.let { listOf(PayerInput(id, it)) }
            } ?: emptyList()
        }
    }

    private fun buildPayloadOrNull(s: AddExpenseUiState): Pair<Long, app.splitmate.domain.expense.ExpensePayload>? {
        val amountMinor = toMinorUnits(s.amountText) ?: return null
        val payload = ExpensePayloadBuilder.build(
            amountMinor = amountMinor,
            splitMode = s.splitMode,
            includedParticipantIds = s.includedParticipantIds.toList(),
            exactAmounts = s.exactAmountText.mapNotNull { (k, v) -> toMinorUnits(v)?.let { k to it } }.toMap(),
            percentagesBp = s.percentageText.mapNotNull { (k, v) -> toMinorUnits(v)?.let { k to it } }.toMap(),
            shares = s.shareCounts.mapValues { it.value.toLong() },
            payments = buildPayments(s),
        )
        return amountMinor to payload
    }

    /** Returns null if the current state would build a valid payload, else the error message. */
    private fun computeValidationError(s: AddExpenseUiState): String? {
        if (toMinorUnits(s.amountText) == null) return "Enter a valid amount"
        return try {
            buildPayloadOrNull(s)
            null
        } catch (e: BalanceEngineException) {
            e.message
        }
    }

    fun submit() {
        val s = _uiState.value
        if (s.description.isBlank()) {
            mutate { it.copy(error = "Enter a description") }
            return
        }
        val (amountMinor, payload) = try {
            buildPayloadOrNull(s) ?: run {
                mutate { it.copy(error = "Enter a valid amount") }
                return
            }
        } catch (e: BalanceEngineException) {
            mutate { it.copy(error = e.message) }
            return
        }

        viewModelScope.launch {
            val request = ExpenseCreateRequest(
                description = s.description,
                amount_minor = amountMinor,
                currency = "INR",
                category = s.category,
                split_type = payload.splitType,
                participant_ids = payload.participantIds,
                participants = payload.participants.map {
                    ExpenseParticipantIn(it.userId, it.amountMinor, it.percentageBp?.toInt(), it.shares?.toInt())
                },
                payments = payload.payments.map { ExpensePaymentIn(it.userId, it.amountMinor) },
            )
            try {
                val synced = expenseRepository.createExpense(groupId, request)
                mutate { it.copy(success = true, savedOffline = !synced, error = null) }
            } catch (e: Exception) {
                mutate { it.copy(error = "Could not save expense: ${e.message ?: "unknown error"}") }
            }
        }
    }
}
