package com.mizan.app.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.mizan.app.core.Num
import com.mizan.app.core.PctBasis
import com.mizan.app.data.Media
import com.mizan.app.data.Store
import java.io.File
import java.io.FileOutputStream

/**
 * A4 PDF drawn with Android's text engine: Arabic letters are shaped and laid out right-to-left by the
 * platform, and the fonts used are embedded in the PDF. Colour or black & white (no heavy fills in B/W).
 */
internal object PdfExport {
    private const val W = 595f
    private const val H = 842f
    private const val M = 40f
    private const val CW = W - 2 * M
    private const val FOOT = H - 28f

    private class P(val color: Boolean) {
        val teal = if (color) Color.rgb(0x0F, 0x6B, 0x64) else Color.BLACK
        val tealDark = if (color) Color.rgb(0x0D, 0x4D, 0x48) else Color.BLACK
        val tealSoft = if (color) Color.rgb(0xE3, 0xF2, 0xEF) else Color.WHITE
        val orange = if (color) Color.rgb(0xEE, 0x7A, 0x1A) else Color.BLACK
        val ink = Color.rgb(0x1C, 0x27, 0x26).takeIf { color } ?: Color.BLACK
        val muted = if (color) Color.rgb(0x55, 0x61, 0x5F) else Color.rgb(0x33, 0x33, 0x33)
        val line = if (color) Color.rgb(0xE2, 0xD9, 0xC6) else Color.BLACK
        val zebra = if (color) Color.rgb(0xFB, 0xF8, 0xF1) else Color.WHITE
    }

    private class Ctx(val d: ExportDoc, val p: P, val photos: Boolean) {
        val doc = PdfDocument()
        var page: PdfDocument.Page? = null
        lateinit var c: Canvas
        var no = 0
        var y = 0f
        val rtl = d.ar
    }

    private fun tp(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
    private fun fill(c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c; style = Paint.Style.FILL }
    private fun stroke(c: Int, w: Float = 0.7f) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c; style = Paint.Style.STROKE; strokeWidth = w }

    private val RTL_CHARS = Regex("[\\u0590-\\u08FF\\uFB1D-\\uFDFF\\uFE70-\\uFEFF]")
    private fun startsRtl(s: CharSequence): Boolean {
        for (ch in s) {
            if (RTL_CHARS.matches(ch.toString())) return true
            if (ch.isLetter()) return false
        }
        return false
    }

    /**
     * Text block aligned to the document's reading side. Mixed Arabic/English is handled by the bidi
     * algorithm inside StaticLayout; alignment is forced to the document start edge.
     */
    private fun layout(ctx: Ctx, text: CharSequence, p: TextPaint, width: Float, center: Boolean = false, end: Boolean = false): StaticLayout {
        val textRtl = startsRtl(text)
        val wantRight = if (end) !ctx.rtl else ctx.rtl
        val align = when {
            center -> Layout.Alignment.ALIGN_CENTER
            wantRight == textRtl -> Layout.Alignment.ALIGN_NORMAL
            else -> Layout.Alignment.ALIGN_OPPOSITE
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, p, width.toInt().coerceAtLeast(10))
            .setAlignment(align)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setLineSpacing(1f, 1.15f)
            .build()
    }

    /** x position of a column given left-to-right coordinates; mirrored for Arabic documents. */
    private fun x(ctx: Ctx, ltrX: Float, w: Float) = if (ctx.rtl) W - ltrX - w else ltrX

    private fun draw(ctx: Ctx, l: StaticLayout, ltrX: Float, y: Float) {
        ctx.c.save(); ctx.c.translate(x(ctx, ltrX, l.width.toFloat()), y); l.draw(ctx.c); ctx.c.restore()
    }

    private fun newPage(ctx: Ctx) {
        ctx.page?.let { ctx.doc.finishPage(it) }
        ctx.no++
        val pg = ctx.doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), ctx.no).create())
        ctx.page = pg
        ctx.c = pg.canvas
        ctx.c.drawColor(Color.WHITE)
        ctx.c.drawLine(M, FOOT - 8, W - M, FOOT - 8, stroke(ctx.p.line))
        val ft = tp(7.5f, ctx.p.muted)
        val left = "Mizan · ${ctx.d.content.title} · ${ctx.d.versionLabel}"
        val right = "${ctx.d.t.page} ${ctx.no}"
        draw(ctx, layout(ctx, left, ft, CW * 0.75f), M, FOOT - 4)
        draw(ctx, layout(ctx, right, ft, CW * 0.2f, end = true), M + CW * 0.8f, FOOT - 4)
        ctx.y = M
    }

    /** Draws a long text block line by line, continuing on new pages as needed. */
    private fun flow(ctx: Ctx, l: StaticLayout, ltrX: Float) {
        var start = 0
        while (start < l.lineCount) {
            val avail = FOOT - 16 - ctx.y
            var end = start
            while (end < l.lineCount && l.getLineBottom(end) - l.getLineTop(start) <= avail) end++
            if (end == start) {
                if (ctx.y <= M + 1) end = start + 1 else { newPage(ctx); continue }
            }
            val top = l.getLineTop(start).toFloat()
            val bottom = l.getLineBottom(end - 1).toFloat()
            ctx.c.save()
            ctx.c.translate(x(ctx, ltrX, l.width.toFloat()), ctx.y - top)
            ctx.c.clipRect(0f, top, l.width.toFloat(), bottom)
            l.draw(ctx.c)
            ctx.c.restore()
            ctx.y += bottom - top
            start = end
            if (start < l.lineCount) newPage(ctx)
        }
    }

    private fun ensure(ctx: Ctx, h: Float) { if (ctx.y + h > FOOT - 16) newPage(ctx) }

    private fun heading(ctx: Ctx, text: String) {
        ensure(ctx, 60f)
        ctx.y += 10
        val l = layout(ctx, text, tp(15f, ctx.p.teal, true), CW)
        draw(ctx, l, M, ctx.y)
        ctx.y += l.height + 4
        ctx.c.drawLine(M, ctx.y, W - M, ctx.y, stroke(ctx.p.orange, 1.4f))
        ctx.y += 8
    }

    private fun photo(ctx: Ctx, f: File, ltrX: Float, top: Float, maxW: Float, maxH: Float): Float {
        val raw = Media.decodeSmall(f, (maxW * 2.5f).toInt(), (maxH * 2.5f).toInt()) ?: return 0f
        val bmp: Bitmap = if (ctx.p.color) raw else Media.grayscale(raw).also { raw.recycle() }
        val sc = minOf(maxW / bmp.width, maxH / bmp.height)
        val w = bmp.width * sc
        val h = bmp.height * sc
        val left = x(ctx, ltrX, w)
        val r = RectF(left, top, left + w, top + h)
        ctx.c.drawBitmap(bmp, null, r, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        ctx.c.drawRect(r, stroke(ctx.p.line, 0.6f))
        bmp.recycle()
        return h
    }

    fun build(d: ExportDoc, out: File, color: Boolean, photos: Boolean) {
        val ctx = Ctx(d, P(color), photos)
        val t = d.t
        val p = ctx.p
        newPage(ctx)

        // ---------------- title block
        val title = layout(ctx, d.content.title.ifBlank { "—" }, tp(24f, p.tealDark, true), CW)
        draw(ctx, title, M, ctx.y); ctx.y += title.height + 6
        val meta = listOfNotNull(
            d.content.category.takeIf { it.isNotBlank() }?.let { "${t.category}: $it" },
            d.content.yieldText.takeIf { it.isNotBlank() }?.let { "${t.yieldL}: $it" },
            "${t.version}: ${d.versionLabel}",
            "${t.batch}: ${Num.fmt(d.factor)}×",
            "${t.exported}: ${d.exportedText}",
        ).joinToString("   ·   ")
        val ml = layout(ctx, meta, tp(9f, p.muted), CW)
        draw(ctx, ml, M, ctx.y); ctx.y += ml.height + 6
        ctx.c.drawRect(M, ctx.y, W - M, ctx.y + 3f, fill(p.orange)); ctx.y += 12

        // ---------------- ingredient table
        heading(ctx, t.ingredients)
        val c1 = CW * 0.50f; val c2 = CW * 0.20f; val c3 = CW * 0.14f; val c4 = CW * 0.16f
        val x1 = M; val x2 = x1 + c1; val x3 = x2 + c2; val x4 = x3 + c3
        val hp = tp(10f, if (color) Color.WHITE else Color.BLACK, true)
        val rowPad = 6f
        fun headerRow() {
            val h = 22f
            ensure(ctx, h + 20)
            if (color) ctx.c.drawRect(M, ctx.y, W - M, ctx.y + h, fill(p.tealDark))
            else ctx.c.drawLine(M, ctx.y + h, W - M, ctx.y + h, stroke(Color.BLACK, 1.2f))
            draw(ctx, layout(ctx, t.ingredient, hp, c1 - 12), x1 + 6, ctx.y + 5)
            draw(ctx, layout(ctx, t.amount, hp, c2 - 12, end = true), x2 + 6, ctx.y + 5)
            draw(ctx, layout(ctx, t.unit, hp, c3 - 12), x3 + 6, ctx.y + 5)
            draw(ctx, layout(ctx, t.pct, hp, c4 - 12, end = true), x4 + 6, ctx.y + 5)
            ctx.y += h
        }
        headerRow()
        val body = tp(10.5f, p.ink)
        val bodyB = tp(10.5f, p.ink, true)
        val small = tp(8f, p.muted)
        d.rows.forEachIndexed { i, r ->
            val name = layout(ctx, r.ing.name.ifBlank { "—" }, body, c1 - 12)
            val orig = r.original?.let { layout(ctx, "${t.original}: $it", small, c1 - 12) }
            val h = name.height + (orig?.height ?: 0) + rowPad * 2
            if (ctx.y + h > FOOT - 16) { newPage(ctx); headerRow() }
            if (i % 2 == 1 && color) ctx.c.drawRect(M, ctx.y, W - M, ctx.y + h, fill(p.zebra))
            draw(ctx, name, x1 + 6, ctx.y + rowPad)
            orig?.let { draw(ctx, it, x1 + 6, ctx.y + rowPad + name.height) }
            draw(ctx, layout(ctx, r.scaled?.let { Num.fmt(it) } ?: "—", bodyB, c2 - 12, end = true), x2 + 6, ctx.y + rowPad)
            draw(ctx, layout(ctx, r.unitLabel, body, c3 - 12), x3 + 6, ctx.y + rowPad)
            draw(ctx, layout(ctx, r.pct?.let { Num.pct(it) + "%" } ?: "—", body, c4 - 12, end = true), x4 + 6, ctx.y + rowPad)
            ctx.y += h
            ctx.c.drawLine(M, ctx.y, W - M, ctx.y, stroke(p.line, 0.5f))
        }
        // total
        ensure(ctx, 30f)
        val th = 24f
        if (color) ctx.c.drawRect(M, ctx.y, W - M, ctx.y + th, fill(p.tealSoft))
        else ctx.c.drawLine(M, ctx.y + th, W - M, ctx.y + th, stroke(Color.BLACK, 1.2f))
        val tb = tp(11.5f, p.ink, true)
        draw(ctx, layout(ctx, t.total, tb, c1 - 12), x1 + 6, ctx.y + 5)
        draw(ctx, layout(ctx, d.totalScaledG?.let { Num.fmt(it) } ?: "—", tb, c2 - 12, end = true), x2 + 6, ctx.y + 5)
        draw(ctx, layout(ctx, if (d.totalScaledG != null) "g" else "", tb, c3 - 12), x3 + 6, ctx.y + 5)
        draw(ctx, layout(ctx, if (d.totalScaledG != null && d.content.pctBasis == PctBasis.TOTAL) "100%" else "", tb, c4 - 12, end = true), x4 + 6, ctx.y + 5)
        ctx.y += th + 4
        val basis = layout(ctx, d.basisLabel + (if (d.totals.missing.isNotEmpty()) "  —  " + t.pctMissing else ""), small, CW)
        draw(ctx, basis, M, ctx.y); ctx.y += basis.height + 2
        val tn = layout(ctx, t.tempNote, small, CW)
        draw(ctx, tn, M, ctx.y); ctx.y += tn.height + 6

        // ---------------- steps
        if (d.content.steps.isNotEmpty()) {
            heading(ctx, t.method)
            d.content.steps.forEachIndexed { i, st ->
                val numW = 26f
                val tw = CW - numW - 8
                val head = (if (st.title.isNotBlank()) st.title else "")
                val hl = head.takeIf { it.isNotEmpty() }?.let { layout(ctx, it, tp(12f, p.teal, true), tw) }
                val bl = layout(ctx, st.text, body, tw)
                val extra = listOfNotNull(
                    st.minutes?.let { "${t.time}: ${Num.fmt(it)} ${t.min}" },
                    st.tempC?.let { "${t.temp}: ${d.tempText(it)}" },
                ).joinToString("   ·   ")
                val el = extra.takeIf { it.isNotEmpty() }?.let { layout(ctx, it, tp(9.5f, p.orange, true), tw) }
                ensure(ctx, minOf(((hl?.height ?: 0) + bl.height + 8).toFloat(), 120f))
                // number badge
                val bx = x(ctx, M, 22f)
                if (color) ctx.c.drawRoundRect(RectF(bx, ctx.y, bx + 22f, ctx.y + 22f), 5f, 5f, fill(p.orange))
                else ctx.c.drawRoundRect(RectF(bx, ctx.y, bx + 22f, ctx.y + 22f), 5f, 5f, stroke(Color.BLACK, 1.2f))
                val nl = layout(ctx, "${i + 1}", tp(11f, if (color) Color.WHITE else Color.BLACK, true), 22f, center = true)
                ctx.c.save(); ctx.c.translate(bx, ctx.y + 4); nl.draw(ctx.c); ctx.c.restore()
                val startY = ctx.y
                hl?.let { draw(ctx, it, M + numW + 8, ctx.y); ctx.y += it.height + 2 }
                flow(ctx, bl, M + numW + 8)
                el?.let { ensure(ctx, it.height.toFloat()); draw(ctx, it, M + numW + 8, ctx.y + 2); ctx.y += it.height + 2 }
                if (ctx.y < startY + 24) ctx.y = startY + 24
                ctx.y += 6

                if (ctx.photos && st.photos.isNotEmpty()) {
                    val pw = (tw - 12) / 2
                    val ph = 150f
                    st.photos.chunked(2).forEach { pair ->
                        val capH = pair.maxOf { if (it.caption.isBlank()) 0 else layout(ctx, it.caption, small, pw).height } + 4
                        ensure(ctx, ph + capH + 6)
                        var maxH = 0f
                        pair.forEachIndexed { k, sp ->
                            val lx = M + numW + 8 + k * (pw + 12)
                            val h = photo(ctx, Store.media(sp.file), lx, ctx.y, pw, ph)
                            if (sp.caption.isNotBlank()) draw(ctx, layout(ctx, sp.caption, small, pw), lx, ctx.y + h + 2)
                            maxH = maxOf(maxH, h)
                        }
                        ctx.y += maxH + capH + 8
                    }
                }
                ctx.c.drawLine(M, ctx.y, W - M, ctx.y, stroke(p.line, 0.4f))
                ctx.y += 8
            }
        }

        // ---------------- notes
        if (d.content.notes.isNotEmpty()) {
            heading(ctx, t.notes)
            d.content.notes.sortedBy { it.at }.forEach { n ->
                val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(n.at))
                val hl = layout(ctx, "$date · ${noteKind(d.ar, n.kind)}", tp(9f, p.muted, true), CW)
                val bl = layout(ctx, n.text, body, CW)
                ensure(ctx, (hl.height + bl.height + 10).toFloat().coerceAtMost(120f))
                draw(ctx, hl, M, ctx.y); ctx.y += hl.height + 1
                flow(ctx, bl, M)
                ctx.y += 8
            }
        }

        ctx.page?.let { ctx.doc.finishPage(it) }
        FileOutputStream(out).use { ctx.doc.writeTo(it) }
        ctx.doc.close()
    }

    fun noteKind(ar: Boolean, k: com.mizan.app.core.NoteKind) = when (k) {
        com.mizan.app.core.NoteKind.GENERAL -> if (ar) "ملاحظة" else "Note"
        com.mizan.app.core.NoteKind.OBSERVATION -> if (ar) "ملاحظة تجربة" else "Observation"
        com.mizan.app.core.NoteKind.CHANGE -> if (ar) "تغيير" else "Change"
        com.mizan.app.core.NoteKind.RESULT -> if (ar) "نتيجة" else "Result"
    }
}
