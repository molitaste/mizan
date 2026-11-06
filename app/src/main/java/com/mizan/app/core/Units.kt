package com.mizan.app.core

enum class Dim { MASS, VOLUME, COUNT, OTHER }

/** A culinary volume standard. Cups and spoons must always have one before they can be converted. */
enum class VolumeStd { US, IMPERIAL, METRIC }

/**
 * @param base grams per unit (MASS) or millilitres per unit (VOLUME). Null = cannot convert
 *             (e.g. a cup whose standard is not yet known).
 * @param family for spoon/cup units: the ambiguous family code ("cup", "tbsp", "tsp", "floz", "pint").
 */
data class UnitDef(
    val code: String,
    val dim: Dim,
    val base: Double?,
    val en: String,
    val ar: String,
    val std: VolumeStd? = null,
    val family: String? = null,
) {
    val ambiguous: Boolean get() = dim == Dim.VOLUME && base == null
}

object Units {
    private val list = listOf(
        // mass
        UnitDef("mg", Dim.MASS, 0.001, "mg", "ملغ"),
        UnitDef("g", Dim.MASS, 1.0, "g", "غ"),
        UnitDef("kg", Dim.MASS, 1000.0, "kg", "كغ"),
        UnitDef("oz", Dim.MASS, 28.349523125, "oz", "أونصة"),
        UnitDef("lb", Dim.MASS, 453.59237, "lb", "رطل"),
        // metric volume
        UnitDef("ml", Dim.VOLUME, 1.0, "mL", "مل"),
        UnitDef("cl", Dim.VOLUME, 10.0, "cL", "سل"),
        UnitDef("dl", Dim.VOLUME, 100.0, "dL", "دل"),
        UnitDef("l", Dim.VOLUME, 1000.0, "L", "لتر"),
        // US customary
        UnitDef("tsp_us", Dim.VOLUME, 4.92892159375, "US tsp", "ملعقة صغيرة (أمريكية)", VolumeStd.US, "tsp"),
        UnitDef("tbsp_us", Dim.VOLUME, 14.78676478125, "US tbsp", "ملعقة كبيرة (أمريكية)", VolumeStd.US, "tbsp"),
        UnitDef("floz_us", Dim.VOLUME, 29.5735295625, "US fl oz", "أونصة سائلة (أمريكية)", VolumeStd.US, "floz"),
        UnitDef("cup_us", Dim.VOLUME, 236.5882365, "US cup", "كوب (أمريكي)", VolumeStd.US, "cup"),
        UnitDef("pint_us", Dim.VOLUME, 473.176473, "US pint", "باينت (أمريكي)", VolumeStd.US, "pint"),
        UnitDef("quart_us", Dim.VOLUME, 946.352946, "US quart", "كوارت (أمريكي)", VolumeStd.US, "quart"),
        UnitDef("gal_us", Dim.VOLUME, 3785.411784, "US gallon", "غالون (أمريكي)", VolumeStd.US, "gal"),
        // Imperial (UK)
        UnitDef("tsp_imp", Dim.VOLUME, 5.9193880208, "Imp tsp", "ملعقة صغيرة (إمبراطورية)", VolumeStd.IMPERIAL, "tsp"),
        UnitDef("tbsp_imp", Dim.VOLUME, 17.7581640625, "Imp tbsp", "ملعقة كبيرة (إمبراطورية)", VolumeStd.IMPERIAL, "tbsp"),
        UnitDef("floz_imp", Dim.VOLUME, 28.4130625, "Imp fl oz", "أونصة سائلة (إمبراطورية)", VolumeStd.IMPERIAL, "floz"),
        UnitDef("cup_imp", Dim.VOLUME, 284.130625, "Imp cup", "كوب (إمبراطوري)", VolumeStd.IMPERIAL, "cup"),
        UnitDef("pint_imp", Dim.VOLUME, 568.26125, "Imp pint", "باينت (إمبراطوري)", VolumeStd.IMPERIAL, "pint"),
        UnitDef("quart_imp", Dim.VOLUME, 1136.5225, "Imp quart", "كوارت (إمبراطوري)", VolumeStd.IMPERIAL, "quart"),
        UnitDef("gal_imp", Dim.VOLUME, 4546.09, "Imp gallon", "غالون (إمبراطوري)", VolumeStd.IMPERIAL, "gal"),
        // Metric culinary (cup 250 mL, tbsp 15 mL, tsp 5 mL)
        UnitDef("tsp_metric", Dim.VOLUME, 5.0, "metric tsp", "ملعقة صغيرة (مترية)", VolumeStd.METRIC, "tsp"),
        UnitDef("tbsp_metric", Dim.VOLUME, 15.0, "metric tbsp", "ملعقة كبيرة (مترية)", VolumeStd.METRIC, "tbsp"),
        UnitDef("cup_metric", Dim.VOLUME, 250.0, "metric cup", "كوب (متري)", VolumeStd.METRIC, "cup"),
        // Ambiguous (standard not confirmed yet) – never converted
        UnitDef("tsp", Dim.VOLUME, null, "tsp (?)", "ملعقة صغيرة (؟)", null, "tsp"),
        UnitDef("tbsp", Dim.VOLUME, null, "tbsp (?)", "ملعقة كبيرة (؟)", null, "tbsp"),
        UnitDef("floz", Dim.VOLUME, null, "fl oz (?)", "أونصة سائلة (؟)", null, "floz"),
        UnitDef("cup", Dim.VOLUME, null, "cup (?)", "كوب (؟)", null, "cup"),
        UnitDef("pint", Dim.VOLUME, null, "pint (?)", "باينت (؟)", null, "pint"),
        UnitDef("quart", Dim.VOLUME, null, "quart (?)", "كوارت (؟)", null, "quart"),
        UnitDef("gal", Dim.VOLUME, null, "gallon (?)", "غالون (؟)", null, "gal"),
        // count / other
        UnitDef("pc", Dim.COUNT, null, "pc", "حبة"),
        UnitDef("pinch", Dim.OTHER, null, "pinch", "رشة"),
        UnitDef("", Dim.OTHER, null, "—", "—"),
    )
    private val byCode = list.associateBy { it.code }

    val massUnits = listOf("g", "kg", "mg", "oz", "lb")
    val metricVolume = listOf("ml", "l", "cl", "dl")
    val usVolume = listOf("tsp_us", "tbsp_us", "floz_us", "cup_us", "pint_us", "quart_us", "gal_us")
    val impVolume = listOf("tsp_imp", "tbsp_imp", "floz_imp", "cup_imp", "pint_imp", "quart_imp", "gal_imp")
    val metricCulinary = listOf("tsp_metric", "tbsp_metric", "cup_metric")
    /** Units offered in ingredient pickers (unambiguous only, plus count / pinch / none). */
    val pickable: List<String> = massUnits + metricVolume + usVolume + impVolume + metricCulinary + listOf("pc", "pinch", "")

    /** Custom free-text units are stored as "x:<text>". */
    fun get(code: String): UnitDef = byCode[code]
        ?: if (code.startsWith("x:")) UnitDef(code, Dim.OTHER, null, code.removePrefix("x:"), code.removePrefix("x:"))
        else UnitDef(code, Dim.OTHER, null, code, code)

    fun label(code: String, arabic: Boolean): String = get(code).let { if (arabic) it.ar else it.en }

    /** Resolve an ambiguous family to a definite unit in the chosen standard. Returns code unchanged if not applicable. */
    fun resolve(code: String, std: VolumeStd): String {
        val u = get(code)
        if (!u.ambiguous) return code
        val fam = u.family ?: return code
        val suffix = when (std) { VolumeStd.US -> "_us"; VolumeStd.IMPERIAL -> "_imp"; VolumeStd.METRIC -> "_metric" }
        val candidate = fam + suffix
        if (byCode.containsKey(candidate)) return candidate
        // metric has no pint/quart/gallon/fl oz: those stay ambiguous for METRIC
        return code
    }

    /** Pure unit conversion inside one dimension. Null if not convertible. */
    fun convert(value: Double, from: String, to: String): Double? {
        if (from == to) return value
        val a = get(from)
        val b = get(to)
        if (a.dim != b.dim) return null
        if (a.dim != Dim.MASS && a.dim != Dim.VOLUME) return null
        val fa = a.base ?: return null
        val fb = b.base ?: return null
        return value * fa / fb
    }

    // ------------------------------------------------------------------ parsing aliases

    private val aliases: List<Pair<String, String>> = buildList {
        fun add(code: String, vararg names: String) = names.forEach { add(it.lowercase() to code) }
        add("g", "g", "gr", "grs", "gram", "grams", "gramme", "grammes", "gm", "gms", "غ", "غم", "غرام", "غرامات", "غراما", "جم", "جرام", "جرامات", "جراما", "ج", "جم.")
        add("kg", "kg", "kgs", "kilo", "kilos", "kilogram", "kilograms", "kilogramme", "كغ", "كغم", "كيلو", "كيلوغرام", "كيلوجرام", "كجم", "كلغ")
        add("mg", "mg", "milligram", "milligrams", "ملغ", "مغ", "ملجم", "مليغرام")
        add("oz", "oz", "ozs", "ounce", "ounces", "أونصة", "اونصة", "أونصات", "اونصات", "أوقية")
        add("lb", "lb", "lbs", "pound", "pounds", "رطل", "أرطال", "باوند")
        add("ml", "ml", "mls", "milliliter", "milliliters", "millilitre", "millilitres", "cc", "مل", "ملل", "مللتر", "ملليلتر", "مليلتر", "ميليلتر", "مللي")
        add("cl", "cl", "centiliter", "centilitre", "سل")
        add("dl", "dl", "deciliter", "decilitre", "دل")
        add("l", "l", "lt", "ltr", "ltrs", "liter", "liters", "litre", "litres", "لتر", "لترات", "لترا", "ل")
        add("tsp", "tsp", "tsps", "teaspoon", "teaspoons", "tspn", "ملعقة صغيرة", "معلقة صغيرة", "ملاعق صغيرة", "م.ص", "م ص", "م. ص", "ملعقة شاي", "معلقة شاي")
        add("tbsp", "tbsp", "tbsps", "tbs", "tbl", "tablespoon", "tablespoons", "ملعقة كبيرة", "معلقة كبيرة", "ملاعق كبيرة", "م.ك", "م ك", "م. ك", "ملعقة طعام", "معلقة طعام")
        add("cup", "cup", "cups", "كوب", "أكواب", "اكواب", "كأس", "كاس", "كوبا")
        add("floz", "fl oz", "fl. oz", "fl.oz", "floz", "fluid ounce", "fluid ounces", "أونصة سائلة", "اونصة سائلة")
        add("pint", "pint", "pints", "pt", "باينت")
        add("quart", "quart", "quarts", "qt", "كوارت")
        add("gal", "gallon", "gallons", "gal", "غالون", "جالون")
        add("cup_us", "us cup", "us cups", "cup (us)", "كوب أمريكي")
        add("cup_imp", "imperial cup", "uk cup", "cup (uk)")
        add("cup_metric", "metric cup", "metric cups")
        add("tbsp_us", "us tbsp", "us tablespoon")
        add("tsp_us", "us tsp", "us teaspoon")
        add("tbsp_metric", "metric tbsp", "metric tablespoon")
        add("tsp_metric", "metric tsp", "metric teaspoon")
        add("floz_us", "us fl oz")
        add("floz_imp", "uk fl oz", "imperial fl oz")
        add("pc", "pc", "pcs", "piece", "pieces", "each", "ea", "حبة", "حبات", "حبه", "قطعة", "قطع", "عدد", "حبّة")
        add("pinch", "pinch", "pinches", "رشة", "رشه", "قليل")
    }.sortedByDescending { it.first.length }

    /** Case-insensitive unit alias lookup for a whole token, or null. */
    fun lookup(token: String): String? {
        val t = token.trim().trimEnd('.', ':', ',', ')').trimStart('(').lowercase()
        if (t.isEmpty()) return null
        return aliases.firstOrNull { it.first == t }?.second
    }

    /**
     * If [text] starts with a unit alias (followed by a non-letter or end), returns (code, matched length).
     * Single Latin letters ("c", "t", "l") are only accepted as a full token.
     */
    fun matchPrefix(text: String): Pair<String, Int>? {
        val lower = text.lowercase()
        for ((alias, code) in aliases) {
            if (!lower.startsWith(alias)) continue
            val end = alias.length
            val next = lower.getOrNull(end)
            if (next != null && next.isLetter()) continue
            // "l" alone could be the start of a word like "l'eau" – accepted only with a boundary (checked above)
            return code to end
        }
        return null
    }

    /** If [text] ends with a unit alias preceded by a boundary, returns (code, start index of alias). */
    fun matchSuffix(text: String): Pair<String, Int>? {
        val lower = text.lowercase()
        for ((alias, code) in aliases) {
            if (!lower.endsWith(alias)) continue
            val start = lower.length - alias.length
            val prev = lower.getOrNull(start - 1)
            if (prev != null && prev.isLetter()) continue
            return code to start
        }
        return null
    }
}
