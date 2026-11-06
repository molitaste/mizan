package com.mizan.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.googlecode.tesseract.android.TessBaseAPI
import com.mizan.app.core.SrcLine
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.Normalizer

/** Result of reading one page. [ocr] = true when the text came from OCR (confidence available). */
data class PageText(val lines: List<SrcLine>, val ocr: Boolean, val meanConfidence: Int? = null)

/**
 * Offline text recognition with Tesseract (English + Arabic models bundled in the APK; no network, no key).
 * PDFs with a text layer are read directly; scanned pages are rendered and OCR'd.
 */
object Ocr {
    private val mutex = Mutex()
    private const val LANGS = "ara+eng"

    class MissingModels : Exception("OCR language files are missing from this build")

    private fun tessBase(ctx: Context): File {
        val base = File(ctx.filesDir, "tesseract")
        val data = File(base, "tessdata").apply { mkdirs() }
        for (lang in listOf("eng", "ara")) {
            val f = File(data, "$lang.traineddata")
            if (f.exists() && f.length() > 0) continue
            val tmp = File(data, "$lang.tmp")
            try {
                ctx.assets.open("tessdata/$lang.traineddata").use { i -> FileOutputStream(tmp).use { i.copyTo(it) } }
            } catch (_: Exception) {
                tmp.delete()
                throw MissingModels()
            }
            tmp.renameTo(f)
        }
        return base
    }

    /** Grey, upscale small images – Tesseract reads 30-40 px high text best. */
    private fun prepare(src: Bitmap): Bitmap {
        val scale = when {
            src.width < 1200 -> 2f
            src.width > 3000 -> 3000f / src.width
            else -> 1f
        }
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        val p = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        c.drawBitmap(src, null, android.graphics.Rect(0, 0, w, h), p)
        return out
    }

    private fun recognize(ctx: Context, bmp: Bitmap): PageText {
        val base = tessBase(ctx)
        val tess = TessBaseAPI()
        val img = prepare(bmp)
        try {
            if (!tess.init(base.absolutePath, LANGS)) throw MissingModels()
            tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
            tess.setImage(img)
            tess.getUTF8Text() // runs recognition
            val mean = tess.meanConfidence()

            // words with their confidence
            val low = mutableListOf<String>()
            tess.resultIterator?.let { it ->
                it.begin()
                do {
                    val w = it.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                    val conf = it.confidence(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                    if (!w.isNullOrBlank() && conf < 65f) low += w.trim()
                } while (it.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
                it.delete()
            }
            // lines with their confidence
            val lines = mutableListOf<SrcLine>()
            tess.resultIterator?.let { it ->
                it.begin()
                do {
                    val t = it.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
                    val conf = it.confidence(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
                    if (!t.isNullOrBlank()) {
                        val text = t.trim()
                        lines += SrcLine(text, conf, low.filter { w -> text.contains(w) })
                    }
                } while (it.next(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE))
                it.delete()
            }
            return PageText(lines, ocr = true, meanConfidence = mean)
        } finally {
            tess.recycle()
            if (img !== bmp) img.recycle()
        }
    }

    suspend fun readImage(ctx: Context, f: File): PageText = mutex.withLock {
        withContext(Dispatchers.Default) {
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val bmp = BitmapFactory.decodeFile(f.absolutePath, opts) ?: return@withContext PageText(emptyList(), true, 0)
            try {
                recognize(ctx, bmp)
            } finally {
                bmp.recycle()
            }
        }
    }

    fun pdfPageCount(f: File): Int = try {
        ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { it.pageCount } }
    } catch (_: Exception) {
        0
    }

    /** Text layer of each page ("" for scanned pages). Arabic presentation forms are normalised back to letters. */
    private fun pdfTextLayer(ctx: Context, f: File): List<String> = try {
        PDFBoxResourceLoader.init(ctx.applicationContext)
        PDDocument.load(f).use { doc ->
            val n = doc.numberOfPages
            (1..n).map { p ->
                val s = PDFTextStripper()
                s.setStartPage(p)
                s.setEndPage(p)
                s.setSortByPosition(true)
                Normalizer.normalize(s.getText(doc) ?: "", Normalizer.Form.NFKC)
            }
        }
    } catch (_: Throwable) {
        emptyList()
    }

    private fun renderPage(r: PdfRenderer, index: Int): Bitmap {
        r.openPage(index).use { page ->
            val scale = (2400f / page.width).coerceIn(1.5f, 3.5f)
            val w = (page.width * scale).toInt()
            val h = (page.height * scale).toInt()
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bmp
        }
    }

    /**
     * Reads every page of a PDF. Pages with fewer than 25 readable characters in their text layer
     * (scanned pages) – or all pages when [forceOcr] – are rendered and OCR'd.
     */
    suspend fun readPdf(ctx: Context, f: File, forceOcr: Boolean, progress: (Int, Int) -> Unit): List<PageText> = mutex.withLock {
        withContext(Dispatchers.Default) {
            val layer = if (forceOcr) emptyList() else pdfTextLayer(ctx, f)
            val out = mutableListOf<PageText>()
            ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { r ->
                    for (i in 0 until r.pageCount) {
                        withContext(Dispatchers.Main) { progress(i + 1, r.pageCount) }
                        val text = layer.getOrNull(i) ?: ""
                        if (text.count { !it.isWhitespace() } >= 25) {
                            out += PageText(text.lines().map { SrcLine(it) }, ocr = false)
                        } else {
                            val bmp = renderPage(r, i)
                            try {
                                out += recognize(ctx, bmp)
                            } finally {
                                bmp.recycle()
                            }
                        }
                    }
                }
            }
            out
        }
    }

    /** First page preview of a PDF (for thumbnails). */
    fun pdfThumb(f: File, width: Int = 400): Bitmap? = try {
        ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                r.openPage(0).use { page ->
                    val h = (width.toFloat() * page.height / page.width).toInt()
                    val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }
    } catch (_: Exception) {
        null
    }
}
