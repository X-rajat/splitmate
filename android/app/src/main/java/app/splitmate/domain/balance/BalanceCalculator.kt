package app.splitmate.domain.balance

import kotlin.math.floor

/**
 * Client-side mirror of the backend's balance engine (see backend/app/balance_engine.py).
 * Used so the app can show correct split previews instantly, offline, before the
 * expense is synced. All amounts are integer minor units (e.g. paise) - never Double.
 *
 * The backend remains the source of truth: every expense is re-validated server-side
 * with the exact same largest-remainder rounding rule, so a client and server never disagree.
 */
enum class SplitType { EQUAL, EXACT, PERCENTAGE, SHARES }

class BalanceEngineException(message: String) : Exception(message)

object BalanceCalculator {

    private fun largestRemainderDistribute(total: Long, weights: List<Long>): List<Long> {
        val n = weights.size
        if (n == 0) {
            if (total != 0L) throw BalanceEngineException("Cannot distribute a non-zero amount across zero participants")
            return emptyList()
        }
        val weightSum = weights.sum()
        if (weightSum <= 0) throw BalanceEngineException("Split weights must sum to a positive number")

        val raw = weights.map { total.toDouble() * it / weightSum.toDouble() }
        val floors = raw.map { floor(it).toLong() }
        var remainder = (total - floors.sum()).toInt()
        val remainders = raw.indices.sortedByDescending { raw[it] - floors[it] }
        val result = floors.toMutableList()
        var i = 0
        while (remainder > 0) {
            val idx = remainders[i % n]
            result[idx] = result[idx] + 1L
            remainder--
            i++
        }
        return result
    }

    fun equalSplit(totalMinor: Long, participantIds: List<String>): Map<String, Long> {
        if (participantIds.isEmpty()) throw BalanceEngineException("Equal split requires at least one participant")
        val amounts = largestRemainderDistribute(totalMinor, List(participantIds.size) { 1L })
        return participantIds.zip(amounts).toMap()
    }

    fun exactSplit(totalMinor: Long, exact: Map<String, Long>): Map<String, Long> {
        val sum = exact.values.sum()
        if (sum != totalMinor) throw BalanceEngineException("Exact amounts ($sum) must sum to total ($totalMinor)")
        if (exact.values.any { it < 0 }) throw BalanceEngineException("Amounts cannot be negative")
        return exact
    }

    fun percentageSplit(totalMinor: Long, percentagesBp: Map<String, Long>): Map<String, Long> {
        val totalBp = percentagesBp.values.sum()
        if (totalBp != 10000L) throw BalanceEngineException("Percentages must sum to 100%")
        val ids = percentagesBp.keys.toList()
        val amounts = largestRemainderDistribute(totalMinor, ids.map { percentagesBp.getValue(it) })
        return ids.zip(amounts).toMap()
    }

    fun sharesSplit(totalMinor: Long, shares: Map<String, Long>): Map<String, Long> {
        if (shares.values.any { it <= 0 }) throw BalanceEngineException("Shares must be positive")
        val ids = shares.keys.toList()
        val amounts = largestRemainderDistribute(totalMinor, ids.map { shares.getValue(it) })
        return ids.zip(amounts).toMap()
    }

    /** Formats minor units as a display string, e.g. 480000 (paise) -> "4,800.00". */
    fun formatMajorUnits(amountMinor: Long): String {
        val negative = amountMinor < 0
        val abs = kotlin.math.abs(amountMinor)
        val major = abs / 100
        val minor = abs % 100
        val sign = if (negative) "-" else ""
        return "$sign$major.${minor.toString().padStart(2, '0')}"
    }
}
