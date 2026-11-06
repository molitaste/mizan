package com.mizan.app.export

import android.graphics.Bitmap
import com.mizan.app.core.Num
import com.mizan.app.core.PctBasis
import com.mizan.app.data.Media
import com.mizan.app.data.Store
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a genuine Office Open XML workbook (.xlsx) without external libraries:
 *  Recipe – editable numbers, unit column, live formulas (quantity = baseline × factor, totals, %)
 *  Preparation – ordered steps with embedded photos and captions
 *  Notes – dated notes
 *  Info – version, export date, unit settings, sources
 * Arabic text is stored as normal Unicode (editable, searchable); Arabic workbooks use right-to-left sheets.
 */
internal object XlsxExport {
    private const val S_TITLE = 1
    private const val S_HEAD = 2
    private const val S_TEXT = 3
    private const val S_NUM = 4
    private const val S_NUM_IN = 5   // editable input (highlighted)
    private const val S_PCT = 6
    private const val S_TOTAL = 7
    private const val S_TOTAL_NUM = 8
    private const val S_LABEL = 9
    private const val S_SMALL = 10
    private const val S_WRAP = 11

    private class C(val v: Any?, val s: Int = S_TEXT, val f: String? = null)

    private class Pic(val media: Int, val col: Int, val row: Int, val w: Int, val h: Int)

    private class Sheet(val name: String, val widths: List<Double>) {
        val rows = StringBuilder()
        val merges = mutableListOf<String>()
        val pics = mutableListOf<Pic>()
        var r = 0
        fun row(cells: List<C>, ht: Double? = null) {
            val rn = r + 1
            rows.append("<row r=\"$rn\"")
            if (ht != null) rows.append(" ht=\"$ht\" customHeight=\"1\"")
            rows.append(">")
            cells.forEachIndexed { i, c ->
                val ref = col(i) + rn
                val v = c.v
                when {
                    c.f != null -> {
                        rows.append("<c r=\"$ref\" s=\"${c.s}\"><f>${xml(c.f)}</f>")
                        if (v is Double && v.isFinite()) rows.append("<v>${num(v)}</v>")
                        rows.append("</c>")
                    }
                    v == null || (v is String && v.isEmpty()) -> rows.append("<c r=\"$ref\" s=\"${c.s}\"/>")
                    v is Double -> if (v.isFinite()) rows.append("<c r=\"$ref\" s=\"${c.s}\"><v>${num(v)}</v></c>") else rows.append("<c r=\"$ref\" s=\"${c.s}\"/>")
                    v is Int -> rows.append("<c r=\"$ref\" s=\"${c.s}\"><v>$v</v></c>")
                    else -> rows.append("<c r=\"$ref\" s=\"${c.s}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(v.toString())}</t></is></c>")
                }
            }
            rows.append("</row>")
            r++
        }
        fun merge(c1: Int, r1: Int, c2: Int, r2: Int) { merges += "${col(c1)}${r1 + 1}:${col(c2)}${r2 + 1}" }
        fun colPx(c: Int) = (widths.getOrElse(c) { 10.0 } * 7 + 5).toInt()
    }

    private val media = mutableListOf<ByteArray>()

    private fun addPic(sh: Sheet, f: File, col: Int, row: Int, maxW: Int, maxH: Int): Boolean {
        val bmp = Media.decodeSmall(f, maxW * 2, maxH * 2) ?: return false
        try {
            val sc = minOf(maxW.toFloat() / bmp.width, maxH.toFloat() / bmp.height)
            val w = (bmp.width * sc).toInt().coerceAtLeast(1)
            val h = (bmp.height * sc).toInt().coerceAtLeast(1)
            val stored = Bitmap.createScaledBitmap(bmp, (w * 2).coerceAtMost(bmp.width), (h * 2).coerceAtMost(bmp.height), true)
            val out = ByteArrayOutputStream()
            stored.compress(Bitmap.CompressFormat.JPEG, 82, out)
            if (stored !== bmp) stored.recycle()
            media += out.toByteArray()
            sh.pics += Pic(media.size, col, row, w, h)
            return true
        } finally {
            bmp.recycle()
        }
    }

    fun build(d: ExportDoc, out: File) {
        media.clear()
        val t = d.t
        val content = d.content

        // ------------------------------------------------ Recipe sheet
        // A name | B baseline | C unit | D quantity (=B*factor) | E weight 1× g | F weight g (=E*factor) | G % | H original
        val rec = Sheet(t.recipe, listOf(30.0, 13.0, 12.0, 13.0, 13.0, 13.0, 10.0, 22.0))
        rec.row(listOf(C(content.title.ifBlank { "—" }, S_TITLE)), 30.0); rec.merge(0, 0, 7, 0)
        rec.row(listOf(C(t.version, S_LABEL), C(d.versionLabel)))                 // row 2
        rec.row(listOf(C(t.exported, S_LABEL), C(d.exportedText)))                // row 3
        rec.row(listOf(C(t.category, S_LABEL), C(content.category)))              // row 4
        rec.row(listOf(C(t.batch, S_LABEL), C(d.factor, S_NUM_IN), C(t.editFactor, S_SMALL)))  // row 5: factor in B5
        rec.merge(2, 4, 7, 4)
        if (content.yieldText.isNotBlank()) rec.row(listOf(C(t.yieldL, S_LABEL), C(content.yieldText))) else rec.row(emptyList())
        rec.row(emptyList(), 8.0)
        rec.row(listOf(t.ingredient, t.baseline, t.unit, t.qty, t.weight1, t.weightG, t.pct, t.original).map { C(it, S_HEAD) }, 22.0)
        val first = rec.r + 1
        val last = first + d.rows.size - 1
        val allWeighed = d.totals.allWeighed
        val baseRowIdx = if (content.pctBasis == PctBasis.INGREDIENT) d.rows.indexOfFirst { it.ing.id == content.pctBaseIngredientId } else -1
        d.rows.forEachIndexed { i, r ->
            val rn = first + i
            val pctFormula: String? = when {
                r.grams1x == null -> null
                content.pctBasis == PctBasis.TOTAL && allWeighed -> "IF(\$F\$${last + 1}=0,\"\",F$rn/\$F\$${last + 1}*100)"
                content.pctBasis == PctBasis.INGREDIENT && baseRowIdx >= 0 && d.rows[baseRowIdx].grams1x != null ->
                    "IF(\$F\$${first + baseRowIdx}=0,\"\",F$rn/\$F\$${first + baseRowIdx}*100)"
                else -> null
            }
            rec.row(listOf(
                C(r.ing.name, S_TEXT),
                C(r.ing.amount, S_NUM_IN),
                C(r.unitLabel, S_TEXT),
                if (r.ing.amount != null) C(r.scaled, S_NUM, "B$rn*\$B\$5") else C(null, S_NUM),
                C(r.grams1x, S_NUM),
                if (r.grams1x != null) C(r.grams1x * d.factor, S_NUM, "E$rn*\$B\$5") else C(null, S_NUM),
                if (pctFormula != null) C(r.pct, S_PCT, pctFormula) else C(null, S_PCT),
                C(r.original ?: "", S_SMALL),
            ), 20.0)
        }
        if (d.rows.isNotEmpty()) {
            rec.row(listOf(
                C(t.total, S_TOTAL), C(null, S_TOTAL), C(if (allWeighed) "g" else "", S_TOTAL), C(null, S_TOTAL),
                if (allWeighed) C(d.totals.totalG, S_TOTAL_NUM, "SUM(E$first:E$last)") else C(null, S_TOTAL),
                if (allWeighed) C(d.totalScaledG, S_TOTAL_NUM, "SUM(F$first:F$last)") else C(null, S_TOTAL),
                if (allWeighed && content.pctBasis == PctBasis.TOTAL) C(100.0, S_TOTAL_NUM, "SUM(G$first:G$last)") else C(null, S_TOTAL),
                C(null, S_TOTAL),
            ), 22.0)
        }
        rec.row(listOf(C(d.basisLabel + if (!allWeighed && d.rows.isNotEmpty()) " — " + t.pctMissing else "", S_SMALL))); rec.merge(0, rec.r - 1, 7, rec.r - 1)
        rec.row(listOf(C(t.tempNote, S_SMALL))); rec.merge(0, rec.r - 1, 7, rec.r - 1)

        // ------------------------------------------------ Preparation sheet
        val prep = Sheet(t.method, listOf(7.0, 22.0, 60.0, 10.0, 14.0, 34.0, 26.0))
        prep.row(listOf(t.step, t.title, t.instructions, t.time + " (" + t.min + ")", t.temp, t.photo, t.caption).map { C(it, S_HEAD) }, 22.0)
        content.steps.forEachIndexed { i, st ->
            val lines = (st.text.length / 70 + 1).coerceIn(1, 12)
            val photos = st.photos
            val firstHt = if (photos.isNotEmpty()) 120.0 else (16.0 * lines).coerceAtLeast(20.0)
            val rowIdx = prep.r
            prep.row(listOf(
                C(i + 1, S_NUM), C(st.title, S_WRAP), C(st.text, S_WRAP), C(st.minutes, S_NUM),
                C(d.tempText(st.tempC), S_TEXT), C(null), C(photos.firstOrNull()?.caption ?: "", S_WRAP),
            ), maxOf(firstHt, 16.0 * lines))
            photos.firstOrNull()?.let { addPic(prep, Store.media(it.file), 5, rowIdx, prep.colPx(5) - 10, 150) }
            photos.drop(1).forEach { ph ->
                val ri = prep.r
                prep.row(listOf(C(null), C(null), C(null), C(null), C(null), C(null), C(ph.caption, S_WRAP)), 120.0)
                addPic(prep, Store.media(ph.file), 5, ri, prep.colPx(5) - 10, 150)
            }
        }

        // ------------------------------------------------ Notes sheet
        val notes = Sheet(t.notes, listOf(18.0, 16.0, 90.0))
        notes.row(listOf(t.date, t.type, t.text).map { C(it, S_HEAD) }, 22.0)
        content.notes.sortedBy { it.at }.forEach { n ->
            val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(n.at))
            notes.row(listOf(C(date), C(PdfExport.noteKind(d.ar, n.kind)), C(n.text, S_WRAP)), (16.0 * (n.text.length / 90 + 1)).coerceIn(20.0, 300.0))
        }

        // ------------------------------------------------ Info sheet
        val info = Sheet(t.info, listOf(26.0, 70.0))
        info.row(listOf(C("Mizan / ميزان", S_TITLE)), 26.0); info.merge(0, 0, 1, 0)
        info.row(listOf(C(t.recipe, S_LABEL), C(content.title)))
        info.row(listOf(C(t.version, S_LABEL), C(d.versionLabel)))
        info.row(listOf(C(t.exported, S_LABEL), C(d.exportedText)))
        info.row(listOf(C(t.batch, S_LABEL), C(Num.fmt(d.factor) + "×")))
        info.row(listOf(C(t.units, S_LABEL), C("${d.prefs.system.name} · ${d.prefs.weightUnit} · ${d.prefs.volumeUnit} · ${if (d.prefs.fahrenheit) "°F" else "°C"}")))
        info.row(listOf(C(t.pct, S_LABEL), C(d.basisLabel)))
        info.row(listOf(C("ID", S_LABEL), C(d.recipe.id)))
        d.recipe.sources.forEach { s -> info.row(listOf(C(t.sources, S_LABEL), C(s.name.ifBlank { s.file }))) }

        write(out, listOf(rec, prep, notes, info), d.ar)
        media.clear()
    }

    // ------------------------------------------------------------------ package

    private fun write(out: File, sheets: List<Sheet>, rtl: Boolean) {
        ZipOutputStream(FileOutputStream(out)).use { z ->
            fun put(name: String, content: String) { z.putNextEntry(ZipEntry(name)); z.write(content.toByteArray(Charsets.UTF_8)); z.closeEntry() }
            val ct = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="jpeg" ContentType="image/jpeg"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/><Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>""")
            sheets.forEachIndexed { i, sh ->
                ct.append("""<Override PartName="/xl/worksheets/sheet${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
                if (sh.pics.isNotEmpty()) ct.append("""<Override PartName="/xl/drawings/drawing${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/>""")
            }
            ct.append("</Types>")
            put("[Content_Types].xml", ct.toString())
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/></Relationships>""")
            put("docProps/core.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"><dc:creator>Mizan</dc:creator></cp:coreProperties>""")

            val wb = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><bookViews><workbookView/></bookViews><sheets>""")
            val wbRels = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
            sheets.forEachIndexed { i, sh ->
                wb.append("""<sheet name="${xml(sheetName(sh.name))}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
                wbRels.append("""<Relationship Id="rId${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${i + 1}.xml"/>""")
            }
            wb.append("""</sheets><calcPr calcId="191029" fullCalcOnLoad="1"/></workbook>""")
            wbRels.append("""<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            put("xl/workbook.xml", wb.toString())
            put("xl/_rels/workbook.xml.rels", wbRels.toString())
            put("xl/styles.xml", styles(rtl))
            media.forEachIndexed { i, b -> z.putNextEntry(ZipEntry("xl/media/image${i + 1}.jpeg")); z.write(b); z.closeEntry() }

            sheets.forEachIndexed { i, sh ->
                val n = i + 1
                val ws = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
                ws.append("""<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr>""")
                ws.append("""<sheetViews><sheetView workbookViewId="0"${if (rtl) " rightToLeft=\"1\"" else ""}${if (i == 0) " tabSelected=\"1\"" else ""}/></sheetViews>""")
                ws.append("<cols>")
                sh.widths.forEachIndexed { c, w -> ws.append("""<col min="${c + 1}" max="${c + 1}" width="$w" customWidth="1"/>""") }
                ws.append("</cols><sheetData>").append(sh.rows).append("</sheetData>")
                if (sh.merges.isNotEmpty()) {
                    ws.append("""<mergeCells count="${sh.merges.size}">"""); sh.merges.forEach { ws.append("""<mergeCell ref="$it"/>""") }; ws.append("</mergeCells>")
                }
                ws.append("""<pageMargins left="0.5" right="0.5" top="0.6" bottom="0.6" header="0.3" footer="0.3"/><pageSetup paperSize="9" orientation="portrait" fitToWidth="1" fitToHeight="0"/>""")
                if (sh.pics.isNotEmpty()) ws.append("""<drawing r:id="rId1"/>""")
                ws.append("</worksheet>")
                put("xl/worksheets/sheet$n.xml", ws.toString())
                if (sh.pics.isNotEmpty()) {
                    put("xl/worksheets/_rels/sheet$n.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" Target="../drawings/drawing$n.xml"/></Relationships>""")
                    val dr = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
                    val drRels = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                    sh.pics.forEachIndexed { k, p ->
                        val emu = 9525L
                        val cx = p.w * emu
                        val cy = p.h * emu
                        dr.append("<xdr:oneCellAnchor><xdr:from><xdr:col>${p.col}</xdr:col><xdr:colOff>${5 * emu}</xdr:colOff><xdr:row>${p.row}</xdr:row><xdr:rowOff>${4 * emu}</xdr:rowOff></xdr:from><xdr:ext cx=\"$cx\" cy=\"$cy\"/>")
                        dr.append("<xdr:pic><xdr:nvPicPr><xdr:cNvPr id=\"${k + 2}\" name=\"Picture ${k + 1}\"/><xdr:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></xdr:cNvPicPr></xdr:nvPicPr>")
                        dr.append("<xdr:blipFill><a:blip r:embed=\"rId${k + 1}\"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill>")
                        dr.append("<xdr:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></xdr:spPr></xdr:pic><xdr:clientData/></xdr:oneCellAnchor>")
                        drRels.append("""<Relationship Id="rId${k + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image${p.media}.jpeg"/>""")
                    }
                    dr.append("</xdr:wsDr>"); drRels.append("</Relationships>")
                    put("xl/drawings/drawing$n.xml", dr.toString())
                    put("xl/drawings/_rels/drawing$n.xml.rels", drRels.toString())
                }
            }
        }
    }

    /** Excel sheet names: max 31 chars, none of : \ / ? * [ ] */
    private fun sheetName(s: String) = s.replace(Regex("""[:\\/?*\[\]]"""), " ").take(31).ifBlank { "Sheet" }

    private fun styles(rtl: Boolean): String {
        val ro = if (rtl) " readingOrder=\"2\"" else ""
        val h = if (rtl) "right" else "left"
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
            """<numFmts count="2"><numFmt numFmtId="164" formatCode="#,##0.###"/><numFmt numFmtId="165" formatCode="0.00"/></numFmts>""" +
            """<fonts count="5"><font><sz val="11"/><color rgb="FF1C2726"/><name val="Arial"/></font><font><b/><sz val="16"/><color rgb="FF0D4D48"/><name val="Arial"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Arial"/></font><font><b/><sz val="11"/><color rgb="FF1C2726"/><name val="Arial"/></font><font><sz val="9"/><color rgb="FF55615F"/><name val="Arial"/></font></fonts>""" +
            """<fills count="5"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF0D4D48"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFFDEBD8"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFD6EEE9"/><bgColor indexed="64"/></patternFill></fill></fills>""" +
            """<borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border><border><left style="thin"><color rgb="FFE2D9C6"/></left><right style="thin"><color rgb="FFE2D9C6"/></right><top style="thin"><color rgb="FFE2D9C6"/></top><bottom style="thin"><color rgb="FFE2D9C6"/></bottom><diagonal/></border></borders>""" +
            """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="12">""" +
            """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +                                                                                          // 0
            """<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment horizontal="$h" vertical="center"$ro/></xf>""" +       // 1 title
            """<xf numFmtId="0" fontId="2" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"$ro/></xf>""" + // 2 head
            """<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="$h" vertical="center"$ro/></xf>""" +   // 3 text
            """<xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>""" + // 4 num
            """<xf numFmtId="164" fontId="3" fillId="3" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>""" + // 5 input
            """<xf numFmtId="165" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>""" + // 6 pct
            """<xf numFmtId="0" fontId="3" fillId="4" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="$h" vertical="center"$ro/></xf>""" + // 7 total
            """<xf numFmtId="164" fontId="3" fillId="4" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>""" + // 8 total num
            """<xf numFmtId="0" fontId="3" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1"><alignment horizontal="$h" vertical="center"$ro/></xf>""" + // 9 label
            """<xf numFmtId="0" fontId="4" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment horizontal="$h" vertical="center" wrapText="1"$ro/></xf>""" + // 10 small
            """<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="$h" vertical="top" wrapText="1"$ro/></xf>""" + // 11 wrap
            """</cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""
    }

    private fun col(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) { val r = (n - 1) % 26; sb.insert(0, ('A' + r)); n = (n - 1) / 26 }
        return sb.toString()
    }

    private fun num(v: Double): String =
        if (v == Math.floor(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()

    private fun xml(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (ch in s) when {
            ch == '&' -> sb.append("&amp;")
            ch == '<' -> sb.append("&lt;")
            ch == '>' -> sb.append("&gt;")
            ch == '"' -> sb.append("&quot;")
            ch == '\n' || ch == '\r' || ch == '\t' -> sb.append(ch)
            ch.code < 0x20 || ch.code == 0xFFFE || ch.code == 0xFFFF -> {}
            else -> sb.append(ch)
        }
        return sb.toString()
    }
}
