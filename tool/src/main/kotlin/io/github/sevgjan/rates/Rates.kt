package io.github.sevgjan.rates

import kotlinx.serialization.Serializable
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** ECB reference rates against [base], as served by Frankfurter. */
@Serializable
data class RatesSnapshot(
    val base: String = "EUR",
    val date: String,
    val rates: Map<String, Double>,
) {
    val codes: List<String> get() = (rates.keys + base).sorted()

    fun rateOf(code: String): Double? = if (code == base) 1.0 else rates[code]

    fun convert(amount: Double, from: String, to: String): Double? {
        val fromRate = rateOf(from) ?: return null
        val toRate = rateOf(to) ?: return null
        return amount / fromRate * toRate
    }
}

/** Keypad input kept as text so "12." and "0.5" display exactly as typed. */
object AmountInput {
    const val BACKSPACE = '<'
    private const val MAX_DIGITS = 12
    private const val MAX_DECIMALS = 2

    fun press(current: String, key: Char): String = when (key) {
        BACKSPACE -> current.dropLast(1)
        '.' -> when {
            '.' in current -> current
            current.isEmpty() -> "0."
            else -> "$current."
        }
        in '0'..'9' -> when {
            current.count { it.isDigit() } >= MAX_DIGITS -> current
            '.' in current && current.substringAfter('.').length >= MAX_DECIMALS -> current
            current == "0" -> key.toString()
            else -> current + key
        }
        else -> current
    }

    fun value(text: String): Double = text.toDoubleOrNull() ?: 0.0

    /** Text for an amount that should become the new input, e.g. after switching base. */
    fun fromValue(value: Double): String {
        val text = "%.2f".format(Locale.US, value).trimEnd('0').trimEnd('.')
        return if (text == "0") "" else text
    }
}

fun formatAmount(value: Double, locale: Locale = Locale.US): String {
    val pattern = if (value != 0.0 && kotlin.math.abs(value) < 0.01) "#,##0.0000" else "#,##0.00"
    return DecimalFormat(pattern, DecimalFormatSymbols(locale)).format(value)
}
