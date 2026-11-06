package com.mizan.app.core

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round

/** Number parsing (decimals, fractions, Arabic-Indic digits) and display formatting. Pure Kotlin. */
object Num {
    private val VULGAR = mapOf(
        '½' to 0.5, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3, '¼' to 0.25, '¾' to 0.75,
        '⅕' to 0.2, '⅖' to 0.4, '⅗' to 0.6, '⅘' to 0.8, '⅙' to 1.0 / 6, '⅚' to 5.0 / 6,
        '⅛' to 0.125, '⅜' to 0.375, '⅝' to 0.625, '⅞' to 0.875,
    )

    /** Arabic-Indic (٠-٩) and Persian (۰-۹) digits → ASCII; Arabic decimal/thousands separators normalised. */
    fun normalizeDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when (ch) {
                in '٠'..'٩' -> sb.append('0' + (ch - '٠'))
                in '۰'..'۹' -> sb.append('0' + (ch - '۰'))
                '٫' -> sb.append('.')
                '٬' -> sb.append(',')
                '⁄', '∕' -> sb.append('/')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** Regex fragment matching one quantity token: "1 1/2", "1½", "½", "1/2", "1.5", "1,5", "1000". */
    const val QTY = """(?:\d+\s+\d+\s*/\s*\d+|\d+\s*[½⅓⅔¼¾⅕⅖⅗⅘⅙⅚⅛⅜⅝⅞]|[½⅓⅔¼¾⅕⅖⅗⅘⅙⅚⅛⅜⅝⅞]|\d+\s*/\s*\d+|\d+(?:[.,]\d+)?)"""

    /**
     * Parses a user or OCR quantity. Returns null for empty / invalid / non-finite.
     * Accepts "1 1/2", "1½", "3/4", "0,5", "1.25", "١٫٥", "2-3" (→ null: ranges are ambiguous).
     */
    fun parse(input: String?): Double? {
        if (input == null) return null
        var s = normalizeDigits(input).trim()
        if (s.isEmpty()) return null
        s = s.replace(' ', ' ')
        // "1,000.5" → thousands comma; "1,5" → decimal comma
        if (Regex("""^\d{1,3}(,\d{3})+(\.\d+)?$""").matches(s)) s = s.replace(",", "")
        else if (Regex("""^\d+,\d+$""").matches(s)) s = s.replace(',', '.')

        // mixed: "1 1/2"
        Regex("""^(\d+)\s+(\d+)\s*/\s*(\d+)$""").matchEntire(s)?.let { m ->
            val d = m.groupValues[3].toDouble()
            if (d == 0.0) return null
            return m.groupValues[1].toDouble() + m.groupValues[2].toDouble() / d
        }
        // "1½" or "½"
        Regex("""^(\d*)\s*([½⅓⅔¼¾⅕⅖⅗⅘⅙⅚⅛⅜⅝⅞])$""").matchEntire(s)?.let { m ->
            val whole = m.groupValues[1].ifEmpty { "0" }.toDouble()
            return whole + (VULGAR[m.groupValues[2][0]] ?: return null)
        }
        // "3/4"
        Regex("""^(\d+(?:\.\d+)?)\s*/\s*(\d+(?:\.\d+)?)$""").matchEntire(s)?.let { m ->
            val d = m.groupValues[2].toDouble()
            if (d == 0.0) return null
            return m.groupValues[1].toDouble() / d
        }
        if (!Regex("""^[+-]?(\d+(\.\d*)?|\.\d+)([eE][+-]?\d+)?$""").matches(s)) return null
        val v = s.toDoubleOrNull() ?: return null
        return if (v.isFinite()) v else null
    }

    /** Positive, finite number or null. */
    fun parsePositive(input: String?): Double? = parse(input)?.takeIf { it > 0 }

    /** Display rounding only. Values are never stored rounded. */
    fun fmt(v: Double?, maxDecimals: Int? = null): String {
        if (v == null || !v.isFinite()) return ""
        val a = abs(v)
        val d = maxDecimals ?: when {
            a == 0.0 -> 0
            a >= 1000 -> 1
            a >= 100 -> 1
            a >= 10 -> 2
            a >= 1 -> 2
            a >= 0.1 -> 3
            else -> 4
        }
        return trim(v, d)
    }

    fun pct(v: Double?): String = if (v == null || !v.isFinite()) "" else trim(v, 2)

    fun trim(v: Double, decimals: Int): String {
        var p = 1.0
        repeat(decimals) { p *= 10 }
        val r = round(v * p) / p
        if (r == floor(r) && abs(r) < 1e15) return r.toLong().toString()
        var s = String.format(java.util.Locale.US, "%.${decimals}f", r)
        if (s.contains('.')) s = s.trimEnd('0').trimEnd('.')
        return s
    }

    fun approxEq(a: Double?, b: Double?, eps: Double = 1e-9): Boolean {
        if (a == null || b == null) return a == b
        return abs(a - b) <= eps * maxOf(1.0, abs(a), abs(b))
    }
}
