package com.mizan.app.core

/**
 * Ratio maths. Every result is computed from the baseline at full precision; rounding happens only in Num.fmt.
 */
object Scaling {

    /** Baseline weight in grams, or null when the weight is not known / not validly convertible. */
    fun grams(i: Ingredient): Double? {
        val a = i.amount ?: return null
        if (!a.isFinite() || a < 0) return null
        val u = Units.get(i.unit)
        return when (u.dim) {
            Dim.MASS -> a * (u.base ?: return null)
            Dim.VOLUME -> {
                val ml = a * (u.base ?: return null)       // ambiguous cup/spoon → null
                val d = i.densityGPerMl ?: return null       // never assume 1 mL = 1 g
                if (d <= 0) null else ml * d
            }
            Dim.COUNT -> {
                val w = i.pieceWeightG ?: return null
                if (w <= 0) null else a * w
            }
            Dim.OTHER -> null
        }
    }

    data class Totals(
        val totalG: Double?,              // null unless every ingredient has a known weight
        val knownG: Double,               // sum of the ingredients whose weight is known
        val missing: List<Ingredient>,    // ingredients without a known weight
    ) {
        val allWeighed: Boolean get() = missing.isEmpty() && totalG != null
    }

    fun totals(ings: List<Ingredient>): Totals {
        var sum = 0.0
        val missing = mutableListOf<Ingredient>()
        for (i in ings) {
            val g = grams(i)
            if (g == null) missing += i else sum += g
        }
        val all = missing.isEmpty() && ings.isNotEmpty()
        return Totals(if (all) sum else null, sum, missing)
    }

    /**
     * Weight percentage for each ingredient id. Basis TOTAL requires every weight to be known;
     * basis INGREDIENT (baker's %) needs the base ingredient's and that row's weight.
     * Percentages do not depend on the batch factor.
     */
    fun percentages(c: RecipeContent): Map<String, Double> {
        val out = HashMap<String, Double>()
        when (c.pctBasis) {
            PctBasis.TOTAL -> {
                val t = totals(c.ingredients)
                val total = t.totalG ?: return out
                if (total <= 0) return out
                c.ingredients.forEach { i -> grams(i)?.let { out[i.id] = it / total * 100.0 } }
            }
            PctBasis.INGREDIENT -> {
                val base = c.ingredients.find { it.id == c.pctBaseIngredientId } ?: return out
                val bg = grams(base) ?: return out
                if (bg <= 0) return out
                c.ingredients.forEach { i -> grams(i)?.let { out[i.id] = it / bg * 100.0 } }
            }
        }
        return out
    }

    /** Factor from editing one row in Scale Batch mode. Null when the row can't drive scaling or input is invalid. */
    fun factorFromRow(baseline: Double?, newAmount: Double?): Double? {
        if (baseline == null || !baseline.isFinite() || baseline <= 0) return null
        if (newAmount == null || !newAmount.isFinite() || newAmount <= 0) return null
        return newAmount / baseline
    }

    /** Factor for a target total weight (grams). Only valid when every ingredient weight is known. */
    fun factorFromTotal(ings: List<Ingredient>, targetG: Double?): Double? {
        if (targetG == null || !targetG.isFinite() || targetG <= 0) return null
        val total = totals(ings).totalG ?: return null
        if (total <= 0) return null
        return targetG / total
    }

    fun validFactor(f: Double?): Boolean = f != null && f.isFinite() && f > 0

    /** Scaled amount = baseline × factor. Baseline is never modified. */
    fun scaled(baseline: Double?, factor: Double): Double? = baseline?.let { it * factor }

    /** Applies a factor permanently (used when the user chooses "make this batch the new formula"). */
    fun applyFactor(c: RecipeContent, factor: Double): RecipeContent =
        c.copy(ingredients = c.ingredients.map { it.copy(amount = it.amount?.times(factor)) })
}
