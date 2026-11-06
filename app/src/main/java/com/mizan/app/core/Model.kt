package com.mizan.app.core

import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(16)

/** What the source document originally said, kept unchanged for side-by-side viewing. */
data class Original(
    val text: String = "",      // the raw line as read / typed
    val amount: Double? = null, // the source amount
    val unit: String = "",      // the source unit code
)

/**
 * One ingredient row. [amount] is the baseline amount in [unit] at full precision (never rounded).
 * Scaling never changes it; only Edit Formula / conversion does.
 */
data class Ingredient(
    val id: String = newId(),
    val name: String = "",
    val amount: Double? = null,
    val unit: String = "g",
    val densityGPerMl: Double? = null,   // user-supplied, needed for volume → weight
    val densitySource: String = "",      // where the density came from (e.g. "measured", "label")
    val pieceWeightG: Double? = null,    // user-supplied, needed for pieces → weight
    val original: Original? = null,
    val uncertain: Boolean = false,      // OCR / parser not sure – highlighted until confirmed
    val note: String = "",
)

data class StepPhoto(
    val file: String,            // file name inside the media folder (persistent)
    val caption: String = "",
)

data class Step(
    val id: String = newId(),
    val title: String = "",
    val text: String = "",
    val minutes: Double? = null,
    val tempC: Double? = null,   // canonical Celsius; displayed in the user's unit
    val photos: List<StepPhoto> = emptyList(),
    val originalText: String = "",
    val uncertain: Boolean = false,
)

enum class NoteKind { GENERAL, OBSERVATION, CHANGE, RESULT }

data class Note(
    val id: String = newId(),
    val at: Long = System.currentTimeMillis(),
    val kind: NoteKind = NoteKind.GENERAL,
    val text: String = "",
)

enum class PctBasis { TOTAL, INGREDIENT }

/** Everything that belongs to one version of a recipe. */
data class RecipeContent(
    val title: String = "",
    val category: String = "",
    val yieldText: String = "",
    val ingredients: List<Ingredient> = emptyList(),
    val steps: List<Step> = emptyList(),
    val notes: List<Note> = emptyList(),
    val coverPhoto: String? = null,
    val pctBasis: PctBasis = PctBasis.TOTAL,
    val pctBaseIngredientId: String? = null,  // for baker's percentage
)

data class Version(
    val id: String = newId(),
    val number: Int,
    val label: String = "",
    val at: Long = System.currentTimeMillis(),
    val content: RecipeContent,
)

data class SourceFile(
    val file: String,           // stored in the sources folder
    val mime: String,
    val name: String = "",
    val at: Long = System.currentTimeMillis(),
    val ocrText: String = "",   // what OCR / text extraction produced
)

data class Recipe(
    val id: String = newId(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val favorite: Boolean = false,
    val current: RecipeContent = RecipeContent(),
    val versions: List<Version> = emptyList(),
    val sources: List<SourceFile> = emptyList(),
) {
    val latest: Version? get() = versions.maxByOrNull { it.number }
    val hasUnsavedChanges: Boolean get() = latest?.content != current
    val nextVersionNumber: Int get() = (versions.maxOfOrNull { it.number } ?: 0) + 1

    fun allMedia(): Set<String> {
        val s = HashSet<String>()
        fun add(c: RecipeContent) {
            c.coverPhoto?.let { s += it }
            c.steps.forEach { st -> st.photos.forEach { s += it.file } }
        }
        add(current)
        versions.forEach { add(it.content) }
        return s
    }
}
