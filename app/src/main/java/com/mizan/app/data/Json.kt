package com.mizan.app.data

import com.mizan.app.core.Ingredient
import com.mizan.app.core.Note
import com.mizan.app.core.NoteKind
import com.mizan.app.core.Original
import com.mizan.app.core.PctBasis
import com.mizan.app.core.Recipe
import com.mizan.app.core.RecipeContent
import com.mizan.app.core.SourceFile
import com.mizan.app.core.Step
import com.mizan.app.core.StepPhoto
import com.mizan.app.core.Version
import org.json.JSONArray
import org.json.JSONObject

/** JSON (de)serialisation. Unknown / missing fields fall back to defaults so old files keep loading. */
internal object Json {
    private fun JSONObject.dbl(k: String): Double? =
        if (!has(k) || isNull(k)) null else optDouble(k).takeIf { it.isFinite() }
    private fun JSONObject.putD(k: String, v: Double?) = apply { if (v != null && v.isFinite()) put(k, v) }
    private fun JSONObject.str(k: String) = optString(k, "")
    private inline fun <T> JSONArray?.list(f: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        val out = ArrayList<T>(length())
        for (i in 0 until length()) optJSONObject(i)?.let { out += f(it) }
        return out
    }
    private fun <T> List<T>.arr(f: (T) -> JSONObject) = JSONArray().also { a -> forEach { a.put(f(it)) } }

    fun ing(i: Ingredient) = JSONObject()
        .put("id", i.id).put("name", i.name).put("unit", i.unit)
        .putD("amount", i.amount).putD("density", i.densityGPerMl).put("densitySrc", i.densitySource)
        .putD("pieceG", i.pieceWeightG).put("uncertain", i.uncertain).put("note", i.note)
        .apply { i.original?.let { o -> put("orig", JSONObject().put("text", o.text).putD("amount", o.amount).put("unit", o.unit)) } }

    fun ing(o: JSONObject) = Ingredient(
        id = o.str("id").ifEmpty { com.mizan.app.core.newId() },
        name = o.str("name"),
        amount = o.dbl("amount"),
        unit = o.optString("unit", "g"),
        densityGPerMl = o.dbl("density"),
        densitySource = o.str("densitySrc"),
        pieceWeightG = o.dbl("pieceG"),
        original = o.optJSONObject("orig")?.let { Original(it.str("text"), it.dbl("amount"), it.str("unit")) },
        uncertain = o.optBoolean("uncertain"),
        note = o.str("note"),
    )

    fun step(s: Step) = JSONObject()
        .put("id", s.id).put("title", s.title).put("text", s.text)
        .putD("minutes", s.minutes).putD("tempC", s.tempC)
        .put("photos", s.photos.arr { JSONObject().put("file", it.file).put("caption", it.caption) })
        .put("orig", s.originalText).put("uncertain", s.uncertain)

    fun step(o: JSONObject) = Step(
        id = o.str("id").ifEmpty { com.mizan.app.core.newId() },
        title = o.str("title"),
        text = o.str("text"),
        minutes = o.dbl("minutes"),
        tempC = o.dbl("tempC"),
        photos = o.optJSONArray("photos").list { StepPhoto(it.str("file"), it.str("caption")) }.filter { it.file.isNotEmpty() },
        originalText = o.str("orig"),
        uncertain = o.optBoolean("uncertain"),
    )

    fun note(n: Note) = JSONObject().put("id", n.id).put("at", n.at).put("kind", n.kind.name).put("text", n.text)
    fun note(o: JSONObject) = Note(
        id = o.str("id").ifEmpty { com.mizan.app.core.newId() },
        at = o.optLong("at", System.currentTimeMillis()),
        kind = runCatching { NoteKind.valueOf(o.str("kind")) }.getOrDefault(NoteKind.GENERAL),
        text = o.str("text"),
    )

    fun content(c: RecipeContent) = JSONObject()
        .put("title", c.title).put("category", c.category).put("yield", c.yieldText)
        .put("ingredients", c.ingredients.arr { ing(it) })
        .put("steps", c.steps.arr { step(it) })
        .put("notes", c.notes.arr { note(it) })
        .put("cover", c.coverPhoto ?: "")
        .put("pctBasis", c.pctBasis.name)
        .put("pctBase", c.pctBaseIngredientId ?: "")

    fun content(o: JSONObject) = RecipeContent(
        title = o.str("title"),
        category = o.str("category"),
        yieldText = o.str("yield"),
        ingredients = o.optJSONArray("ingredients").list { ing(it) },
        steps = o.optJSONArray("steps").list { step(it) },
        notes = o.optJSONArray("notes").list { note(it) },
        coverPhoto = o.str("cover").ifEmpty { null },
        pctBasis = runCatching { PctBasis.valueOf(o.str("pctBasis")) }.getOrDefault(PctBasis.TOTAL),
        pctBaseIngredientId = o.str("pctBase").ifEmpty { null },
    )

    fun recipe(r: Recipe) = JSONObject()
        .put("format", 1)
        .put("id", r.id).put("createdAt", r.createdAt).put("updatedAt", r.updatedAt).put("favorite", r.favorite)
        .put("current", content(r.current))
        .put("versions", r.versions.arr { v ->
            JSONObject().put("id", v.id).put("number", v.number).put("label", v.label).put("at", v.at).put("content", content(v.content))
        })
        .put("sources", r.sources.arr { s ->
            JSONObject().put("file", s.file).put("mime", s.mime).put("name", s.name).put("at", s.at).put("ocr", s.ocrText)
        })

    fun recipe(o: JSONObject) = Recipe(
        id = o.str("id"),
        createdAt = o.optLong("createdAt"),
        updatedAt = o.optLong("updatedAt"),
        favorite = o.optBoolean("favorite"),
        current = o.optJSONObject("current")?.let { content(it) } ?: RecipeContent(),
        versions = o.optJSONArray("versions").list { v ->
            Version(
                id = v.str("id"),
                number = v.optInt("number", 1),
                label = v.str("label"),
                at = v.optLong("at"),
                content = v.optJSONObject("content")?.let { content(it) } ?: RecipeContent(),
            )
        },
        sources = o.optJSONArray("sources").list { s ->
            SourceFile(s.str("file"), s.str("mime"), s.str("name"), s.optLong("at"), s.str("ocr"))
        },
    )
}
