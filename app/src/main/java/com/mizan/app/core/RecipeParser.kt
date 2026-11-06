package com.mizan.app.core

/** One line of recognised text. [confidence] 0–100 (null = typed / text-layer PDF, fully trusted). */
data class SrcLine(val text: String, val confidence: Float? = null, val lowWords: List<String> = emptyList())

data class ImportDraft(
    val title: String = "",
    val titleUncertain: Boolean = false,
    val ingredients: List<Ingredient> = emptyList(),
    val steps: List<Step> = emptyList(),
    val notes: String = "",
    val unassigned: List<String> = emptyList(),  // lines the parser could not place – shown, never dropped
    val rawText: String = "",
    val lowConfidenceWords: List<String> = emptyList(),
) {
    val hasAmbiguousUnits: Boolean get() = ingredients.any { Units.get(it.unit).ambiguous }
}

/**
 * Turns recognised lines into a structured draft. It only extracts what is written: nothing is invented,
 * missing quantities stay empty and are flagged for the review screen.
 */
object RecipeParser {
    private enum class Sec { NONE, ING, METHOD, NOTES }

    private val H_ING = listOf("ingredients", "ingredient", "for the base", "المكونات", "مكونات", "المقادير", "مقادير", "المواد", "المكوّنات")
    private val H_METHOD = listOf("method", "preparation", "preparation method", "instructions", "directions", "steps", "procedure", "how to make",
        "طريقة التحضير", "طريقة العمل", "طريقة الإعداد", "طريقة الاعداد", "التحضير", "الطريقة", "خطوات التحضير", "الخطوات", "طريقه التحضير")
    private val H_NOTES = listOf("notes", "note", "tips", "chef's notes", "ملاحظات", "ملاحظة", "نصائح", "ملاحظات الشيف")

    private val BULLET = Regex("""^\s*(?:[•●▪◦·\-–—*]+)\s*""")
    private val STEP_START = Regex("""^\s*(?:(?:step|الخطوة|خطوة)\s*)?(\d{1,2})\s*[.)\-:،]\s*|^\s*(?:step|الخطوة|خطوة)\s*(\d{1,2})\s+""", RegexOption.IGNORE_CASE)
    private val QTY_START = Regex("""^(""" + Num.QTY + """)(?:\s*(?:-|–|to)\s*(""" + Num.QTY + """))?\s*""")
    private val QTY_END = Regex("""[\s:：\-–]+(""" + Num.QTY + """)(?:\s*(?:-|–)\s*(""" + Num.QTY + """))?\s*([^\d]{0,24})$""")
    private val MINUTES = Regex("""(\d+(?:[.,]\d+)?)\s*(?:min\b|mins\b|minutes?\b|دقيقة|دقائق|د\b)""", RegexOption.IGNORE_CASE)
    private val HOURS = Regex("""(\d+(?:[.,]\d+)?)\s*(?:h\b|hr\b|hrs\b|hours?\b|ساعة|ساعات|ساعتين)""", RegexOption.IGNORE_CASE)

    private fun norm(s: String) = s.lowercase().trim().trimEnd(':', '：', '.', '-', ' ').trim()

    private fun heading(line: String): Sec? {
        val n = norm(line)
        if (n.length > 40) return null
        if (H_ING.any { n == it || n.startsWith("$it ") && n.length < 30 }) return Sec.ING
        if (H_METHOD.any { n == it || n.startsWith("$it ") && n.length < 30 }) return Sec.METHOD
        if (H_NOTES.any { n == it || n.startsWith("$it ") && n.length < 30 }) return Sec.NOTES
        return null
    }

    /** Parse one ingredient line. Returns null if the line has no recognisable quantity *and* looks like prose. */
    fun parseIngredient(raw: String, lowConf: Boolean = false): Ingredient {
        val line = Num.normalizeDigits(BULLET.replace(raw, "")).trim()
        var name: String
        var amount: Double? = null
        var unit = ""
        var uncertain = lowConf
        var note = ""

        val start = QTY_START.find(line)
        val end = QTY_END.find(line)
        if (start != null && start.range.first == 0) {
            val isRange = start.groupValues[2].isNotEmpty()
            amount = if (isRange) null else Num.parse(start.groupValues[1])
            if (isRange) { uncertain = true; note = "range: ${start.value.trim()}" }
            var rest = line.substring(start.range.last + 1)
            Units.matchPrefix(rest)?.let { (code, len) ->
                unit = code
                rest = rest.substring(len)
            }
            name = rest.trim().removePrefix("of ").removePrefix("من ").trim().trim(',', '،', ':', '-').trim()
        } else if (end != null) {
            val isRange = end.groupValues[2].isNotEmpty()
            amount = if (isRange) null else Num.parse(end.groupValues[1])
            if (isRange) { uncertain = true; note = "range" }
            val tail = end.groupValues[3].trim()
            if (tail.isNotEmpty()) {
                val m = Units.matchPrefix(tail)
                if (m != null && tail.substring(m.second).isBlank()) unit = m.first
                else { uncertain = true; note = tail }
            }
            name = line.substring(0, end.range.first).trim().trim(',', '،', ':', '-').trim()
        } else {
            name = line
            uncertain = true
        }
        if (amount == null && note.isEmpty()) uncertain = true
        if (unit == "oz" && !line.lowercase().contains("ounce")) {
            // weight ounce assumed; fluid ounces must be written "fl oz"
            note = (note + " oz = weight ounce (fl oz for volume)").trim()
        }
        if (Units.get(unit).ambiguous) uncertain = true
        if (unit.isEmpty() && amount != null) unit = "pc" // "3 eggs" = a count
        if (name.isEmpty()) { name = line; uncertain = true }
        return Ingredient(
            name = name,
            amount = amount,
            unit = unit,
            original = Original(text = raw.trim(), amount = amount, unit = unit),
            uncertain = uncertain,
            note = note,
        )
    }

    private fun looksLikeIngredient(line: String): Boolean {
        val l = Num.normalizeDigits(BULLET.replace(line, "")).trim()
        if (l.length > 70) return false
        if (STEP_START.find(l)?.let { it.range.first == 0 && l.length > 35 } == true) return false
        val s = QTY_START.find(l)
        if (s != null && s.range.first == 0) {
            val rest = l.substring(s.range.last + 1)
            return Units.matchPrefix(rest) != null || rest.split(' ').size <= 5
        }
        val e = QTY_END.find(l) ?: return false
        val tail = e.groupValues[3].trim()
        return tail.isEmpty() || Units.matchPrefix(tail) != null
    }

    fun minutesIn(text: String): Double? {
        val t = Num.normalizeDigits(text)
        HOURS.find(t)?.let { m -> return m.groupValues[1].replace(',', '.').toDoubleOrNull()?.times(60) }
        MINUTES.find(t)?.let { m -> return m.groupValues[1].replace(',', '.').toDoubleOrNull() }
        if (t.contains("ساعتين")) return 120.0
        return null
    }

    /** Group method lines into steps (numbering, bullets, or sentence ends). */
    fun splitSteps(lines: List<SrcLine>): List<Step> {
        val chunks = mutableListOf<Pair<StringBuilder, Boolean>>()
        val anyNumbered = lines.any { STEP_START.find(Num.normalizeDigits(it.text))?.range?.first == 0 || BULLET.find(it.text)?.range?.first == 0 && BULLET.find(it.text)!!.value.isNotBlank() }
        for (l in lines) {
            val t = Num.normalizeDigits(l.text).trim()
            if (t.isEmpty()) continue
            val low = (l.confidence ?: 100f) < 60f
            val num = STEP_START.find(t)?.takeIf { it.range.first == 0 }
            val bul = BULLET.find(t)?.takeIf { it.range.first == 0 && it.value.isNotBlank() }
            val startNew = when {
                chunks.isEmpty() -> true
                num != null || bul != null -> true
                !anyNumbered -> chunks.last().first.trimEnd().let { it.endsWith('.') || it.endsWith('!') || it.endsWith('؟') || it.endsWith('?') }
                else -> false
            }
            val body = when {
                num != null -> t.substring(num.range.last + 1)
                bul != null -> t.substring(bul.range.last + 1)
                else -> t
            }.trim()
            if (startNew) chunks += StringBuilder(body) to low
            else {
                val (sb, lw) = chunks.last()
                sb.append(' ').append(body)
                chunks[chunks.size - 1] = sb to (lw || low)
            }
        }
        return chunks.map { (sb, low) ->
            val text = sb.toString().trim()
            var title = ""
            var body = text
            val colon = text.indexOfFirst { it == ':' || it == '：' }
            if (colon in 2..40 && colon < text.length - 3) {
                title = text.substring(0, colon).trim()
                body = text.substring(colon + 1).trim()
            }
            Step(
                title = title,
                text = body,
                minutes = minutesIn(body),
                tempC = Temp.find(body).firstOrNull()?.c,
                originalText = text,
                uncertain = low,
            )
        }
    }

    fun parse(lines: List<SrcLine>): ImportDraft {
        val clean = lines.map { it.copy(text = it.text.replace(Regex("[\\s\u00A0]+"), " ").trim()) }
            .filter { l -> l.text.any { it.isLetterOrDigit() } }
            .filterNot { l -> Regex("^((page|صفحة)\\s*\\d{1,3}(\\s*(/|of|من)\\s*\\d{1,3})?|-\\s*\\d{1,3}\\s*-)$", RegexOption.IGNORE_CASE).matches(l.text) }
        val raw = clean.joinToString("\n") { it.text }
        val lowWords = clean.flatMap { it.lowWords }.distinct()
        if (clean.isEmpty()) return ImportDraft(rawText = raw)

        var title = ""
        var titleLow = false
        val ing = mutableListOf<Ingredient>()
        val method = mutableListOf<SrcLine>()
        val notes = mutableListOf<String>()
        val unassigned = mutableListOf<String>()
        val hasHeadings = clean.any { heading(it.text) != null }

        var sec = Sec.NONE
        for ((idx, l) in clean.withIndex()) {
            val h = heading(l.text)
            if (h != null) { sec = h; continue }
            val low = (l.confidence ?: 100f) < 60f
            when (sec) {
                Sec.ING -> if (looksLikeIngredient(l.text) || l.text.length <= 40) ing += parseIngredient(l.text, low) else method += l
                Sec.METHOD -> method += l
                Sec.NOTES -> notes += l.text
                Sec.NONE -> {
                    if (title.isEmpty() && !looksLikeIngredient(l.text) && l.text.length <= 60) {
                        title = l.text; titleLow = low
                    } else if (looksLikeIngredient(l.text)) {
                        ing += parseIngredient(l.text, low)
                    } else if (!hasHeadings) {
                        method += l
                    } else if (idx < 3 && title.isNotEmpty()) {
                        unassigned += l.text // subtitle / yield line before the first heading
                    } else unassigned += l.text
                }
            }
        }
        return ImportDraft(
            title = title,
            titleUncertain = titleLow,
            ingredients = ing,
            steps = splitSteps(method),
            notes = notes.joinToString("\n"),
            unassigned = unassigned,
            rawText = raw,
            lowConfidenceWords = lowWords,
        )
    }

    fun parseText(text: String): ImportDraft = parse(text.lines().map { SrcLine(it) })
}
