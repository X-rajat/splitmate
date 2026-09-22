package app.splitmate.domain.balance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BalanceCalculatorTest {

    @Test
    fun `equal split divides evenly`() {
        val result = BalanceCalculator.equalSplit(480000, listOf("rj", "amit", "rahul", "neha"))
        assertEquals(120000L, result["rj"])
        assertEquals(480000L, result.values.sum())
    }

    @Test
    fun `equal split with remainder sums exactly`() {
        val result = BalanceCalculator.equalSplit(1000, listOf("a", "b", "c"))
        assertEquals(1000L, result.values.sum())
    }

    @Test
    fun `exact split validates total`() {
        assertThrows(BalanceEngineException::class.java) {
            BalanceCalculator.exactSplit(1000, mapOf("a" to 400L, "b" to 400L))
        }
    }

    @Test
    fun `percentage split must sum to 100`() {
        assertThrows(BalanceEngineException::class.java) {
            BalanceCalculator.percentageSplit(1000, mapOf("a" to 5000L, "b" to 4000L))
        }
    }

    @Test
    fun `shares split distributes proportionally`() {
        val result = BalanceCalculator.sharesSplit(40000, mapOf("rj" to 2L, "amit" to 1L, "rahul" to 1L))
        assertEquals(20000L, result["rj"])
        assertEquals(40000L, result.values.sum())
    }

    @Test
    fun `format major units renders paise correctly`() {
        assertEquals("4800.00", BalanceCalculator.formatMajorUnits(480000))
        assertEquals("-850.50", BalanceCalculator.formatMajorUnits(-85050))
    }

    @Test
    fun `rounding never loses a paisa across many totals`() {
        for (total in 1L..500L) {
            val result = BalanceCalculator.equalSplit(total, listOf("a", "b", "c", "d"))
            assertEquals(total, result.values.sum())
        }
    }
}
