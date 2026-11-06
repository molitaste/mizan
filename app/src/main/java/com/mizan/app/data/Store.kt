package com.mizan.app.data

import android.content.Context
import com.mizan.app.core.Recipe
import com.mizan.app.core.RecipeContent
import com.mizan.app.core.SourceFile
import com.mizan.app.core.Version
import com.mizan.app.core.newId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

/**
 * Device-local storage (no cloud sync):
 *   files/recipes/<id>.json  – one file per recipe (current draft + all saved versions)
 *   files/media/             – step photos and cover photos (real files, never temporary URLs)
 *   files/sources/           – original imported images / PDFs
 * The current draft is autosaved shortly after each change; versions are only created by Save Version.
 */
object Store {
    private lateinit var app: Context
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = HashMap<String, Job>()
    private val dirty = HashSet<String>()

    private val _recipes = MutableStateFlow<List<Recipe>>(emptyList())
    val recipes: StateFlow<List<Recipe>> = _recipes.asStateFlow()

    fun init(ctx: Context) {
        app = ctx.applicationContext
        _recipes.value = loadAll()
        scope.launch { cleanupOrphans() }
    }

    fun recipesDir() = File(app.filesDir, "recipes").apply { mkdirs() }
    fun mediaDir() = File(app.filesDir, "media").apply { mkdirs() }
    fun sourcesDir() = File(app.filesDir, "sources").apply { mkdirs() }
    fun media(name: String) = File(mediaDir(), name)
    fun source(name: String) = File(sourcesDir(), name)

    private fun file(id: String) = File(recipesDir(), "$id.json")

    private fun loadAll(): List<Recipe> {
        val out = mutableListOf<Recipe>()
        recipesDir().listFiles()?.filter { it.name.endsWith(".json") }?.forEach { f ->
            val r = readFile(f) ?: readFile(File(f.path + ".bak"))
            if (r != null && r.id.isNotEmpty()) out += r
        }
        return out.sortedByDescending { it.updatedAt }
    }

    private fun readFile(f: File): Recipe? = try {
        if (f.exists()) Json.recipe(JSONObject(f.readText())) else null
    } catch (_: Exception) {
        null
    }

    private fun writeNow(r: Recipe) {
        val f = file(r.id)
        val tmp = File(f.path + ".tmp")
        tmp.writeText(Json.recipe(r).toString())
        if (f.exists()) f.copyTo(File(f.path + ".bak"), overwrite = true)
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
    }

    private fun publish(r: Recipe) {
        val list = _recipes.value
        _recipes.value = (if (list.any { it.id == r.id }) list.map { if (it.id == r.id) r else it } else listOf(r) + list)
    }

    /** Save immediately (structural changes: new version, restore, favourite …). */
    private fun commit(r: Recipe) = synchronized(lock) {
        pending.remove(r.id)?.cancel()
        dirty.remove(r.id)
        publish(r)
        writeNow(r)
    }

    /** Autosave: update memory now, write to disk after a short pause in typing. */
    private fun autosave(r: Recipe) = synchronized(lock) {
        publish(r)
        dirty += r.id
        pending.remove(r.id)?.cancel()
        pending[r.id] = scope.launch {
            delay(600)
            synchronized(lock) {
                if (r.id in dirty) {
                    _recipes.value.find { it.id == r.id }?.let(::writeNow)
                    dirty.remove(r.id)
                }
            }
        }
    }

    /** Write every unsaved draft now (called when the app goes to the background). */
    fun flush() = synchronized(lock) {
        pending.values.forEach { it.cancel() }
        pending.clear()
        dirty.toList().forEach { id -> _recipes.value.find { it.id == id }?.let(::writeNow) }
        dirty.clear()
    }

    fun get(id: String): Recipe? = _recipes.value.find { it.id == id }

    /** [label] null = no saved version yet (a fresh manual recipe is only a draft until Save Version). */
    fun create(content: RecipeContent, sources: List<SourceFile> = emptyList(), label: String? = null): Recipe {
        val now = System.currentTimeMillis()
        val r = Recipe(
            id = newId(), createdAt = now, updatedAt = now, current = content, sources = sources,
            versions = if (label == null) emptyList() else listOf(Version(number = 1, label = label, at = now, content = content)),
        )
        commit(r)
        return r
    }

    fun updateCurrent(id: String, f: (RecipeContent) -> RecipeContent) {
        val r = get(id) ?: return
        val c = f(r.current)
        if (c == r.current) return
        autosave(r.copy(current = c, updatedAt = System.currentTimeMillis()))
    }

    fun setFavorite(id: String, fav: Boolean) {
        val r = get(id) ?: return
        commit(r.copy(favorite = fav))
    }

    fun saveVersion(id: String, label: String): Version? {
        val r = get(id) ?: return null
        val v = Version(number = r.nextVersionNumber, label = label.trim(), content = r.current)
        commit(r.copy(versions = r.versions + v, updatedAt = System.currentTimeMillis()))
        return v
    }

    /** Restore = new current version with the old content. Nothing in history is deleted. */
    fun restore(id: String, versionId: String, label: String): Version? {
        val r = get(id) ?: return null
        val old = r.versions.find { it.id == versionId } ?: return null
        val v = Version(number = r.nextVersionNumber, label = label, content = old.content)
        commit(r.copy(current = old.content, versions = r.versions + v, updatedAt = System.currentTimeMillis()))
        return v
    }

    fun duplicate(content: RecipeContent, newTitle: String, label: String): Recipe =
        create(content.copy(title = newTitle), emptyList(), label)

    fun addSources(id: String, s: List<SourceFile>) {
        val r = get(id) ?: return
        commit(r.copy(sources = r.sources + s))
    }

    fun delete(id: String) = synchronized(lock) {
        pending.remove(id)?.cancel()
        dirty.remove(id)
        _recipes.value = _recipes.value.filter { it.id != id }
        file(id).delete()
        File(file(id).path + ".bak").delete()
        scope.launch { cleanupOrphans(force = true) }
    }

    /** Replace or add whole recipes (backup restore). */
    fun importRecipes(list: List<Recipe>) = synchronized(lock) {
        list.forEach { commit(it) }
    }

    fun categories(): List<String> =
        _recipes.value.map { it.current.category.trim() }.filter { it.isNotEmpty() }.distinct().sorted()

    /**
     * Deletes media / source files referenced by no recipe (current draft *or any saved version*).
     * Files younger than a day are kept so photos taken for an unsaved edit are never lost.
     */
    private fun cleanupOrphans(force: Boolean = false) {
        try {
            val all = _recipes.value
            val usedMedia = all.flatMap { it.allMedia() }.toSet()
            val usedSources = all.flatMap { r -> r.sources.map { it.file } }.toSet()
            val cutoff = System.currentTimeMillis() - (if (force) 6L else 24L) * 3600 * 1000
            mediaDir().listFiles()?.forEach { if (it.name !in usedMedia && it.lastModified() < cutoff) it.delete() }
            sourcesDir().listFiles()?.forEach { if (it.name !in usedSources && it.lastModified() < cutoff) it.delete() }
        } catch (_: Exception) {
        }
    }
}
