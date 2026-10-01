package app.splitmate.domain.expense

import app.splitmate.domain.balance.BalanceEngineException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExpensePayloadBuilderTest {

    private val singlePayer = listOf(PayerInput("rj", 100_00))

    @Test
    fun `equal split builds participant_ids and no participants list`() {
        val payload = ExpensePayloadBuilder.build(
            amountMinor = 100_00,
            splitMode = SplitMode.EQUAL,
            includedParticipantIds = listOf("rj", "amit"),
            payments = singlePayer,
        )
        assertEquals("equal", payload.splitType)
        assertEquals(listOf("rj", "amit"), payload.participantIds)
        assertEquals(emptyList<ParticipantInput>(), payload.participants)
        assertEquals(singlePayer, payload.payments)
    }

    @Test
    fun `exact split builds participants with amount_minor`() {
        val payload = ExpensePayloadBuilder.build(
            amountMinor = 100_00,
            splitMode = SplitMode.EXACT,
            includedParticipantIds = listOf("rj", "amit"),
            exactAmounts = mapOf("rj" to 60_00L, "amit" to 40_00L),
            payments = singlePayer,
        )
        assertEquals("exact", payload.splitType)
        assertEquals(emptyList<String>(), payload.participantIds)
        assertEquals(
            setOf(ParticipantInput("rj", amountMinor = 60_00), ParticipantInput("amit", amountMinor = 40_00)),
            payload.participants.toSet(),
        )
    }

    @Test
    fun `exact split rejects amounts that do not sum to total`() {
        assertThrows(BalanceEngineException::class.java) {
            ExpensePayloadBuilder.build(
                amountMinor = 100_00,
                splitMode = SplitMode.EXACT,
                includedParticipantIds = listOf("rj", "amit"),
                exactAmounts = mapOf("rj" to 60_00L, "amit" to 30_00L),
                payments = singlePayer,
            )
        }
    }

    @Test
    fun `exact split rejects a missing amount for an included participant`() {
        assertThrows(BalanceEngineException::class.java) {
            ExpensePayloadBuilder.build(
                amountMinor = 100_00,
                splitMode = SplitMode.EXACT,
                includedParticipantIds = listOf("rj", "amit"),
                exactAmounts = mapOf("rj" to 100_00L),
                payments = singlePayer,
            )
        }
    }

    @Test
    fun `percentage split builds participants with percentage_bp`() {
        val payload = ExpensePayloadBuilder.build(
            amountMinor = 100_00,
            splitMode = SplitMode.PERCENTAGE,
            includedParticipantIds = listOf("rj", "amit"),
            percentagesBp = mapOf("rj" to 6000L, "amit" to 4000L),
            payments = singlePayer,
        )
        assertEquals(
            setOf(ParticipantInput("rj", percentageBp = 6000), ParticipantInput("amit", percentageBp = 4000)),
            payload.participants.toSet(),
        )
    }

    @Test
    fun `percentage split rejects percentages that do not sum to 100`() {
        assertThrows(BalanceEngineException::class.java) {
            ExpensePayloadBuilder.build(
                amountMinor = 100_00,
                splitMode = SplitMode.PERCENTAGE,
                includedParticipantIds = listOf("rj", "amit"),
                percentagesBp = mapOf("rj" to 6000L, "amit" to 3000L),
                payments = singlePayer,
            )
        }
    }

    @Test
    fun `shares split builds participants with shares`() {
        val payload = ExpensePayloadBuilder.build(
            amountMinor = 100_00,
            splitMode = SplitMode.SHARES,
            includedParticipantIds = listOf("rj", "amit"),
            shares = mapOf("rj" to 2L, "amit" to 1L),
            payments = singlePayer,
        )
        assertEquals(
            setOf(ParticipantInput("rj", shares = 2), ParticipantInput("amit", shares = 1)),
            payload.participants.toSet(),
        )
    }

    @Test
    fun `rejects when no participants are included`() {
        assertThrows(BalanceEngineException::class.java) {
            ExpensePayloadBuilder.build(
                amountMinor = 100_00,
                splitMode = SplitMode.EQUAL,
                includedParticipantIds = emptyList(),
                payments = singlePayer,
            )
        }
    }

    @Test
    fun `rejects when no payers are given`() {
        assertThrows(BalanceEngineException::class.java) {
            ExpensePayloadBuilder.build(
                amountMinor = 100_00,
                splitMode = SplitMode.EQUAL,
                includedParticipantIds = listOf("rj"),
                payments = emptyList(),
            )
        }
    }

    @Test
    fun `rejects when multiple payer amounts do not sum to total`() {
        assertThrows(BalanceEngineException::class.java) {
            ExpensePayloadBuilder.build(
                amountMinor = 100_00,
                splitMode = SplitMode.EQUAL,
                includedParticipantIds = listOf("rj", "amit"),
                payments = listOf(PayerInput("rj", 40_00), PayerInput("amit", 40_00)),
            )
        }
    }

    @Test
    fun `accepts multiple payers whose amounts sum exactly to total`() {
        val payload = ExpensePayloadBuilder.build(
            amountMinor = 100_00,
            splitMode = SplitMode.EQUAL,
            includedParticipantIds = listOf("rj", "amit"),
            payments = listOf(PayerInput("rj", 60_00), PayerInput("amit", 40_00)),
        )
        assertEquals(100_00L, payload.payments.sumOf { it.amountMinor })
    }

    @Test
    fun `rejects a non-positive amount`() {
        assertThrows(BalanceEngineException::class.java) {
            ExpensePayloadBuilder.build(
                amountMinor = 0,
                splitMode = SplitMode.EQUAL,
                includedParticipantIds = listOf("rj"),
                payments = singlePayer,
            )
        }
    }
}
