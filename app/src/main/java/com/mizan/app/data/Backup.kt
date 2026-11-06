package com.mizan.app.data

import android.content.Context
import android.net.Uri
import com.mizan.app.core.Recipe
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Full backup ZIP: every recipe with its whole version history, photos and original source files. */
object Backup {
    fun create(out: File) {
        Store.flush()
        ZipOutputStream(FileOutputStream(out)).use { z ->
            val recipes = Store.recipes.value
            recipes.forEach { r ->
                z.putNextEntry(ZipEntry("recipes/${r.id}.json"))
                z.write(Json.recipe(r).toString().toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
            val media = recipes.flatMap { it.allMedia() }.toSet()
            media.forEach { name ->
                val f = Store.media(name)
                if (f.exists()) {
                    z.putNextEntry(ZipEntry("media/$name")); f.inputStream().use { it.copyTo(z) }; z.closeEntry()
                }
            }
            recipes.flatMap { it.sources }.map { it.file }.toSet().forEach { name ->
                val f = Store.source(name)
                if (f.exists()) {
                    z.putNextEntry(ZipEntry("sources/$name")); f.inputStream().use { it.copyTo(z) }; z.closeEntry()
                }
            }
        }
    }

    /** Returns the number of recipes restored, or null if the file is not a Mizan backup. */
    fun restore(ctx: Context, uri: Uri): Int? {
        val recipes = mutableListOf<Recipe>()
        val input = ctx.contentResolver.openInputStream(uri) ?: return null
        ZipInputStream(input).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val n = e.name
                val safe = File(n).name
                if (!e.isDirectory && safe.isNotBlank() && !safe.startsWith(".")) {
                    when {
                        n.startsWith("recipes/") && safe.endsWith(".json") ->
                            runCatching { Json.recipe(JSONObject(z.readBytes().toString(Charsets.UTF_8))) }.getOrNull()
                                ?.takeIf { it.id.isNotEmpty() }?.let { recipes += it }
                        n.startsWith("media/") -> FileOutputStream(Store.media(safe)).use { z.copyTo(it) }
                        n.startsWith("sources/") -> FileOutputStream(Store.source(safe)).use { z.copyTo(it) }
                    }
                }
                z.closeEntry()
                e = z.nextEntry
            }
        }
        if (recipes.isEmpty()) return null
        Store.importRecipes(recipes)
        return recipes.size
    }
}
