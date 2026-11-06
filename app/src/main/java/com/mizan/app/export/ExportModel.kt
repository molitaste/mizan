package com.mizan.app.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.mizan.app.core.Ingredient
import com.mizan.app.core.Num
import com.mizan.app.core.PctBasis
import com.mizan.app.core.Recipe
import com.mizan.app.core.RecipeContent
import com.mizan.app.core.Scaling
import com.mizan.app.core.Temp
import com.mizan.app.core.UnitPrefs
import com.mizan.app.core.Units
import com.mizan.app.data.Backup
import com.mizan.app.data.Media
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Labels used inside exported documents (document language chosen at export time). */
class DocText(val ar: Boolean) {
    private fun t(en: String, ar: String) = if (this.ar) ar else en
    val ingredients = t("Ingredients", "المكونات")
    val ingredient = t("Ingredient", "المكوّن")
    val amount = t("Amount", "الكمية")
    val unit = t("Unit", "الوحدة")
    val pct = t("%", "٪")
    val total = t("Total batch", "إجمالي الدفعة")
    val method = t("Preparation Method", "طريقة التحضير")
    val notes = t("Notes", "ملاحظات")
    val version = t("Version", "النسخة")
    val exported = t("Exported", "تاريخ التصدير")
    val batch = t("Batch factor", "معامل الدفعة")
    val category = t("Category", "الفئة")
    val yieldL = t("Yield", "الكمية الناتجة")
    val time = t("Time", "الوقت")
    val temp = t("Temperature", "الحرارة")
    val min = t("min", "دقيقة")
    val page = t("Page", "صفحة")
    val draft = t("Current draft (not saved as a version)", "المسودة الحالية (غير محفوظة كنسخة)")
    val pctTotal = t("% of total weight", "٪ من الوزن الكلي")
    fun pctOf(n: String) = t("% of $n (baker's %)", "٪ من $n (نسبة الخبّاز)")
    val pctMissing = t("Percentages and total weight are not shown: some ingredients have no known weight.", "لا تظهر النسب والوزن الكلي: بعض المكونات بلا وزن معروف.")
    val tempNote = t("Temperatures and times are not scaled with the batch.", "درجات الحرارة والأوقات لا تتغير مع حجم الدفعة.")
    val baseline = t("Baseline (1×)", "الأساس (1×)")
    val qty = t("Quantity", "الكمية")
    val weight1 = t("Weight 1× (g)", "الوزن 1× (غ)")
    val weightG = t("Weight (g)", "الوزن (غ)")
    val step = t("Step", "الخطوة")
    val title = t("Title", "العنوان")
    val instructions = t("Instructions", "التعليمات")
    val photo = t("Photo", "الصورة")
    val caption = t("Caption", "التعليق")
    val date = t("Date", "التاريخ")
    val type = t("Type", "النوع")
    val text = t("Text", "النص")
    val info = t("Info", "معلومات")
    val recipe = t("Recipe", "الوصفة")
    val sources = t("Original files", "الملفات الأصلية")
    val units = t("Unit settings", "إعدادات الوحدات")
    val editFactor = t("Change the batch factor (cell B5) and quantities recalculate.", "غيّر معامل الدفعة (الخلية B5) لإعادة حساب الكميات.")
    val original = t("Original", "الأصل")
}

data class ExRow(
    val ing: Ingredient,
    val scaled: Double?,
    val unitLabel: String,
    val grams1x: Double?,
    val pct: Double?,
    val original: String?,
)

/** Everything an exporter needs, already resolved for one recipe version + batch factor. */
class ExportDoc(
    val recipe: Recipe,
    val content: RecipeContent,
    val versionLabel: String,    // "v3 · Summer" or the draft text
    val versionNumber: Int?,
    val factor: Double,
    val ar: Boolean,
    val prefs: UnitPrefs,
    val showOriginals: Boolean,
) {
    val t = DocText(ar)
    val exportedAt: Long = System.currentTimeMillis()
    val exportedText: String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(exportedAt))
    val totals = Scaling.totals(content.ingredients)
    val pct = Scaling.percentages(content)
    val totalScaledG: Double? = totals.totalG?.let { it * factor }
    val basisLabel: String = if (content.pctBasis == PctBasis.INGREDIENT)
        t.pctOf(content.ingredients.find { it.id == content.pctBaseIngredientId }?.name ?: "?") else t.pctTotal
    val rows: List<ExRow> = content.ingredients.map { i ->
        val o = i.original
        val orig = if (showOriginals && o != null && o.amount != null && (o.unit != i.unit || !Num.approxEq(o.amount, i.amount)))
            "${Num.fmt(o.amount * factor)} ${Units.label(o.unit, ar)}" else null
        ExRow(i, Scaling.scaled(i.amount, factor), Units.label(i.unit, ar), Scaling.grams(i), pct[i.id], orig)
    }
    fun tempText(c: Double?): String = if (c == null) "" else Temp.display(c, prefs.fahrenheit)

    val fileBase: String = (content.title.ifBlank { "recipe" }).replace(Regex("""[\\/:*?"<>|\s]+"""), "_").take(40) +
        (versionNumber?.let { "_v$it" } ?: "_draft") + (if (!Num.approxEq(factor, 1.0)) "_x" + Num.fmt(factor).replace('.', '-') else "")
}

object Exporter {
    const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val MIME_PDF = "application/pdf"
    const val MIME_ZIP = "application/zip"

    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())

    fun outDir(ctx: Context): File {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val old = System.currentTimeMillis() - 2L * 60 * 60 * 1000
        dir.listFiles()?.forEach { if (it.lastModified() < old) it.delete() }
        return dir
    }

    fun pdf(ctx: Context, d: ExportDoc, color: Boolean, photos: Boolean): File {
        val f = File(outDir(ctx), "${d.fileBase}_${stamp()}.pdf")
        PdfExport.build(d, f, color, photos)
        return f
    }

    fun xlsx(ctx: Context, d: ExportDoc): File {
        val f = File(outDir(ctx), "${d.fileBase}_${stamp()}.xlsx")
        XlsxExport.build(d, f)
        return f
    }

    fun backup(ctx: Context): File {
        val f = File(outDir(ctx), "Mizan_backup_${stamp()}.zip")
        Backup.create(f)
        return f
    }

    fun share(ctx: Context, file: File, mime: String, title: String) {
        val uri = Media.uriFor(ctx, file)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, title))
    }

    fun copyTo(ctx: Context, file: File, target: Uri) {
        ctx.contentResolver.openOutputStream(target)?.use { out -> file.inputStream().use { it.copyTo(out) } }
    }
}
