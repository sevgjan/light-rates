package io.github.sevgjan.rates

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RatesTest {
    private val snapshot = RatesSnapshot(
        base = "EUR",
        date = "2026-10-23",
        rates = mapOf("USD" to 1.1193, "GBP" to 0.83728, "JPY" to 175.51),
    )

    @Test
    fun convertsFromBase() {
        assertEquals(111.93, snapshot.convert(100.0, "EUR", "USD")!!, 1e-9)
    }

    @Test
    fun convertsAcrossNonBaseCurrencies() {
        // 100 USD -> EUR -> GBP
        assertEquals(100 / 1.1193 * 0.83728, snapshot.convert(100.0, "USD", "GBP")!!, 1e-9)
    }

    @Test
    fun unknownCurrencyIsNull() {
        assertNull(snapshot.convert(1.0, "EUR", "XXX"))
    }

    @Test
    fun codesIncludeBase() {
        assertEquals(listOf("EUR", "GBP", "JPY", "USD"), snapshot.codes)
    }

    @Test
    fun keypadInput() {
        fun type(keys: String) = keys.fold("") { text, key -> AmountInput.press(text, key) }
        assertEquals("12.5", type("12.5"))
        assertEquals("0.", type("."))
        assertEquals("1.25", type("1.257"))
        assertEquals("1.2", type("1..2"))
        assertEquals("7", type("07"))
        assertEquals("1", type("12${AmountInput.BACKSPACE}"))
        assertEquals("", type("${AmountInput.BACKSPACE}"))
    }

    @Test
    fun amountFromValueTrimsZeros() {
        assertEquals("111.93", AmountInput.fromValue(111.93))
        assertEquals("112", AmountInput.fromValue(111.999))
        assertEquals("", AmountInput.fromValue(0.0))
    }

    @Test
    fun formatsAmounts() {
        assertEquals("1,234.50", formatAmount(1234.5))
        assertEquals("0.0057", formatAmount(0.0057))
        assertEquals("0.00", formatAmount(0.0))
    }
}
