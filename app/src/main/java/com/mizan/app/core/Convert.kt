package com.mizan.app.core

enum class MeasureSystem { METRIC, US, CUSTOM }

data class UnitPrefs(
    val system: MeasureSystem = MeasureSystem.METRIC,
    val weightUnit: String = "g",
    val volumeUnit: String = "ml",
    val fahrenheit: Boolean = false,
    /** Convert volumes to weight on import – only ever when the row has a density. */
    val volumeToWeight: Boolean = false,
) {
    companion object {
        fun metric(f: Boolean = false) = UnitPrefs(MeasureSystem.METRIC, "g", "ml", f)
        fun us(f: Boolean = true) = UnitPrefs(MeasureSystem.US, "oz", "cup_us", f)
    }
}

object Temp {
    fun cToF(c: Double) = c * 9.0 / 5.0 + 32.0
    fun fToC(f: Double) = (f - 32.0) * 5.0 / 9.0
    fun display(c: Double?, fahrenheit: Boolean): String =
        if (c == null) "" else if (fahrenheit) "${Num.trim(cToF(c), 0)} °F" else "${Num.trim(c, 1)} °C"
    fun both(c: Double?): String = if (c == null) "" else "${Num.trim(c, 1)}°C / ${Num.trim(cToF(c), 0)}°F"

    // number, optional range, optional "degrees", then unit (°C, ºF, C°, "degrees Celsius", "درجة مئوية" …)
    private const val N = """(\d+(?:[.,]\d+)?)"""
    private val TEMP = Regex(
        N + """(?:\s*(?:-|–|—|to|إلى|الى|~)\s*""" + N + """)?\s*""" +
            """(?:(?:°|º|˚)\s*([CFcf])\b|([CF])\s*(?:°|º|˚)|(?:degrees?\s*)(celsius|fahrenheit|[CF]\b)|(?:درجة|درجات)\s*(?:حرارة\s*)?(مئوية|سيليزية|سلزيوس|فهرنهايت|فهرنهايتية)|(?:درجة|درجات)\s*([CFcf])\b)""",
    )
    // an already-present conversion in parentheses right after the temperature, e.g. " (180–185 °F)"
    private val PAREN = Regex("""^\s*\(([^)]*?(?:°|º|˚|درجة|degrees)[^)]*)\)""")

    private fun isF(m: MatchResult): Boolean {
        val g = (3..6).map { m.groupValues[it] }.firstOrNull { it.isNotEmpty() } ?: ""
        val x = g.lowercase()
        return x == "f" || x.startsWith("fahr") || x.startsWith("فهرنهايت")
    }

    data class Found(val c: Double, val range: IntRange)

    /** All temperatures (in Celsius) found in a text; ranges return the upper bound. */
    fun find(text: String): List<Found> {
        val t = Num.normalizeDigits(text)
        return TEMP.findAll(t).mapNotNull { m ->
            val hi = (m.groupValues[2].ifEmpty { m.groupValues[1] }).replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
            Found(if (isF(m)) fToC(hi) else hi, m.range)
        }.toList()
    }

    /**
     * Converts every temperature written in [text] to the target scale, keeping the surrounding words.
     * "82–85 °C (180–185 °F)" → "180–185 °F"; Arabic "85 درجة مئوية" → "185 درجة فهرنهايت".
     */
    fun convertText(text: String, toF: Boolean): String {
        val t = Num.normalizeDigits(text)
        val sb = StringBuilder()
        var pos = 0
        for (m in TEMP.findAll(t)) {
            if (m.range.first < pos) continue
            val srcF = isF(m)
            sb.append(text, pos, m.range.first)
            pos = m.range.last + 1
            if (srcF == toF) {
                sb.append(text, m.range.first, m.range.last + 1)
                continue
            }
            val a = m.groupValues[1].replace(',', '.').toDouble()
            val b = m.groupValues[2].takeIf { it.isNotEmpty() }?.replace(',', '.')?.toDouble()
            fun conv(v: Double) = if (toF) Num.trim(cToF(v), 0) else Num.trim(fToC(v), 1)
            val arabic = m.groupValues[6].isNotEmpty() || m.value.contains("درج")
            val unit = if (arabic) {
                if (toF) " درجة فهرنهايت" else " درجة مئوية"
            } else {
                if (toF) " °F" else " °C"
            }
            val sep = Regex("""\s*(?:-|–|—|to|إلى|الى|~)\s*""").find(m.value)?.value ?: "–"
            sb.append(conv(a))
            if (b != null) sb.append(sep).append(conv(b))
            sb.append(unit)
            // drop an existing parenthetical conversion to the target scale ("(180–185 °F)")
            val rest = t.substring(pos)
            PAREN.find(rest)?.let { p ->
                val inner = p.groupValues[1]
                val innerHasTarget = TEMP.find(inner)?.let { isF(it) == toF } ?: false
                if (innerHasTarget) pos += p.value.length
            }
        }
        sb.append(text, pos, text.length)
        return sb.toString()
    }
}

object Converter {
    data class Result(val ingredient: Ingredient, val changed: Boolean, val reason: Reason? = null)

    enum class Reason { AMBIGUOUS_STANDARD, NEEDS_DENSITY, NEEDS_PIECE_WEIGHT, NOT_CONVERTIBLE }

    /** Best US spoon / cup unit for a millilitre amount (avoids "0.02 cup"). */
    private fun fitUs(ml: Double, preferred: String): String {
        val pref = Units.get(preferred)
        if (pref.std != VolumeStd.US) return preferred
        return when {
            ml < 14.78676478125 * 0.99 -> "tsp_us"
            ml < 236.5882365 / 4 -> "tbsp_us"
            preferred == "tsp_us" || preferred == "tbsp_us" -> "cup_us"
            else -> preferred
        }
    }

    /**
     * Converts the row's display unit to the user's preferred units. Amount precision is kept; the original
     * value is recorded in [Ingredient.original] when it wasn't already.
     */
    fun toPrefs(i: Ingredient, p: UnitPrefs): Result {
        val amount = i.amount ?: return Result(i, false)
        val u = Units.get(i.unit)
        val orig = i.original ?: Original(text = "", amount = i.amount, unit = i.unit)
        return when (u.dim) {
            Dim.MASS -> {
                if (i.unit == p.weightUnit) return Result(i, false)
                val v = Units.convert(amount, i.unit, p.weightUnit) ?: return Result(i, false, Reason.NOT_CONVERTIBLE)
                Result(i.copy(amount = v, unit = p.weightUnit, original = orig), true)
            }
            Dim.VOLUME -> {
                if (u.ambiguous) return Result(i, false, Reason.AMBIGUOUS_STANDARD)
                val ml = amount * u.base!!
                if (p.volumeToWeight) {
                    val d = i.densityGPerMl
                    if (d != null && d > 0) {
                        val g = ml * d
                        val v = Units.convert(g, "g", p.weightUnit)!!
                        return Result(i.copy(amount = v, unit = p.weightUnit, original = orig), true)
                    }
                }
                val target = fitUs(ml, p.volumeUnit)
                if (target == i.unit) return Result(i, false, if (p.volumeToWeight) Reason.NEEDS_DENSITY else null)
                val v = Units.convert(amount, i.unit, target) ?: return Result(i, false, Reason.NOT_CONVERTIBLE)
                Result(i.copy(amount = v, unit = target, original = orig), true, if (p.volumeToWeight) Reason.NEEDS_DENSITY else null)
            }
            Dim.COUNT -> {
                if (p.volumeToWeight && i.pieceWeightG != null && i.pieceWeightG > 0) {
                    val v = Units.convert(amount * i.pieceWeightG, "g", p.weightUnit)!!
                    Result(i.copy(amount = v, unit = p.weightUnit, original = orig), true)
                } else Result(i, false, if (p.volumeToWeight) Reason.NEEDS_PIECE_WEIGHT else null)
            }
            Dim.OTHER -> Result(i, false)
        }
    }

    /** Resolve ambiguous cups / spoons with the standard the user confirmed. */
    fun applyStandard(i: Ingredient, std: VolumeStd): Ingredient {
        val u = Units.get(i.unit)
        if (!u.ambiguous) return i
        val r = Units.resolve(i.unit, std)
        return if (r == i.unit) i else i.copy(unit = r)
    }

    data class ContentResult(val content: RecipeContent, val rows: List<Result>)

    fun content(c: RecipeContent, p: UnitPrefs, convertTemps: Boolean = true): ContentResult {
        val rows = c.ingredients.map { toPrefs(it, p) }
        val steps = if (!convertTemps) c.steps else c.steps.map { s ->
            val nt = Temp.convertText(s.text, p.fahrenheit)
            val ntt = Temp.convertText(s.title, p.fahrenheit)
            if (nt == s.text && ntt == s.title) s
            else s.copy(text = nt, title = ntt, originalText = s.originalText.ifEmpty { s.text })
        }
        return ContentResult(c.copy(ingredients = rows.map { it.ingredient }, steps = steps), rows)
    }
}
