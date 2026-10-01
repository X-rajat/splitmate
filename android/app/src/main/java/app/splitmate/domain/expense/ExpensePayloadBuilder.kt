package app.splitmate.domain.expense

import app.splitmate.domain.balance.BalanceCalculator
import app.splitmate.domain.balance.BalanceEngineException

enum class SplitMode { EQUAL, EXACT, PERCENTAGE, SHARES }

data class PayerInput(val userId: String, val amountMinor: Long)

data class ParticipantInput(
    val userId: String,
    val amountMinor: Long? = null,
    val percentageBp: Long? = null,
    val shares: Long? = null,
)

data class ExpensePayload(
    val splitType: String,
    val participantIds: List<String>,
    val participants: List<ParticipantInput>,
    val payments: List<PayerInput>,
)

/**
 * Builds and validates the payload for a new expense, client-side, before it's sent
 * to the backend. Mirrors backend/app/balance_engine.py's validation rules exactly
 * (via BalanceCalculator) so the user sees an error instantly instead of after a
 * round trip. The backend re-validates everything and remains the source of truth.
 */
object ExpensePayloadBuilder {

    fun build(
        amountMinor: Long,
        splitMode: SplitMode,
        includedParticipantIds: List<String>,
        exactAmounts: Map<String, Long> = emptyMap(),
        percentagesBp: Map<String, Long> = emptyMap(),
        shares: Map<String, Long> = emptyMap(),
        payments: List<PayerInput>,
    ): ExpensePayload {
        if (amountMinor <= 0) throw BalanceEngineException("Enter a valid amount")
        if (includedParticipantIds.isEmpty()) throw BalanceEngineException("Choose at least one participant")
        if (payments.isEmpty()) throw BalanceEngineException("Choose who paid")

        val paidTotal = payments.sumOf { it.amountMinor }
        if (paidTotal != amountMinor) {
            throw BalanceEngineException("Payments ($paidTotal) must sum to the expense total ($amountMinor)")
        }

        return when (splitMode) {
            SplitMode.EQUAL -> {
                BalanceCalculator.equalSplit(amountMinor, includedParticipantIds)
                ExpensePayload("equal", includedParticipantIds, emptyList(), payments)
            }
            SplitMode.EXACT -> {
                val map = includedParticipantIds.associateWith {
                    exactAmounts[it] ?: throw BalanceEngineException("Enter an amount for every participant")
                }
                BalanceCalculator.exactSplit(amountMinor, map)
                ExpensePayload("exact", emptyList(), map.map { ParticipantInput(it.key, amountMinor = it.value) }, payments)
            }
            SplitMode.PERCENTAGE -> {
                val map = includedParticipantIds.associateWith {
                    percentagesBp[it] ?: throw BalanceEngineException("Enter a percentage for every participant")
                }
                BalanceCalculator.percentageSplit(amountMinor, map)
                ExpensePayload("percentage", emptyList(), map.map { ParticipantInput(it.key, percentageBp = it.value) }, payments)
            }
            SplitMode.SHARES -> {
                val map = includedParticipantIds.associateWith {
                    shares[it] ?: throw BalanceEngineException("Enter shares for every participant")
                }
                BalanceCalculator.sharesSplit(amountMinor, map)
                ExpensePayload("shares", emptyList(), map.map { ParticipantInput(it.key, shares = it.value) }, payments)
            }
        }
    }
}
