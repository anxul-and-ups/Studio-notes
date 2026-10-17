package com.example.ui.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import android.util.Base64
import com.example.data.model.NoteEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Result of saving an export into shared device storage. */
data class SavedExport(val uri: Uri?, val displayPath: String, val file: File?)

object NoteExporter {

    /** Formats that can carry images/attachments. Everything else gets a warning. */
    val FORMATS_WITH_MEDIA_SUPPORT = setOf(".pdf", ".docx", ".html")

    const val MEDIA_WARNING =
        "This format may not support images or attachments. Some content may not be included in the exported file."

    fun needsMediaWarning(extension: String, note: NoteEntity): Boolean {
        val attachments = RichTextFormatter.deserializeAttachments(note.attachmentsJson)
        return attachments.isNotEmpty() && extension !in FORMATS_WITH_MEDIA_SUPPORT
    }

    fun mimeFor(extension: String): String = when (extension) {
        ".pdf" -> "application/pdf"
        ".docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        ".html" -> "text/html"
        ".xml" -> "application/xml"
        ".json" -> "application/json"
        ".py" -> "text/x-python"
        else -> "text/plain"
    }

    /** Builds the export file in cache (used for both Share and Save). */
    fun buildFile(context: Context, note: NoteEntity, fileName: String, extension: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val target = File(dir, fileName)
        when (extension) {
            ".pdf" -> writePdf(note, target)
            ".docx" -> writeDocx(note, target)
            ".html" -> target.writeText(buildHtml(note), Charsets.UTF_8)
            ".json" -> target.writeText(buildJson(note), Charsets.UTF_8)
            ".xml" -> target.writeText(buildXml(note), Charsets.UTF_8)
            else -> target.writeText(buildPlainText(note), Charsets.UTF_8)
        }
        return target
    }

    /**
     * Item 28/36: saves into the public Downloads/AU Notes folder through
     * MediaStore (Android 10+, no permission needed under scoped storage) or
     * the public Downloads directory on older versions. Returns null if the
     * file could not be written.
     */
    fun saveToDevice(context: Context, source: File, displayName: String, mime: String): SavedExport? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AU Notes")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
                resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } } ?: return null
                val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
                SavedExport(uri, "Downloads/AU Notes/$displayName", null)
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AU Notes")
                dir.mkdirs()
                val dest = File(dir, displayName)
                source.copyTo(dest, overwrite = true)
                android.media.MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf(mime), null)
                SavedExport(Uri.fromFile(dest), dest.absolutePath, dest)
            }
        } catch (e: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------- text-ish formats

    private fun tableRows(note: NoteEntity): List<List<String>> =
        note.tableData.lines().filter { it.isNotBlank() }.map { row -> row.split("|").map { it.trim() }.filter { it.isNotEmpty() } }

    private fun buildPlainText(note: NoteEntity): String {
        // (marker lines like [img:abc123] are layout data, never exported as text)
        val sb = StringBuilder()
        sb.append(note.title).append("\n==================\n").append(ImageMarkers.strip(note.content))
        val rows = tableRows(note)
        if (rows.isNotEmpty()) {
            sb.append("\n\n[Table]\n")
            rows.forEach { sb.append(it.joinToString(" | ")).append('\n') }
        }
        val attachments = RichTextFormatter.deserializeAttachments(note.attachmentsJson)
        if (attachments.isNotEmpty()) {
            sb.append("\n\n[Attachments]\n")
            attachments.forEach { sb.append("- ").append(it.fileName).append('\n') }
        }
        return sb.toString()
    }

    private fun buildJson(note: NoteEntity): String {
        // JSONObject escapes quotes/newlines correctly (the old hand-built string did not).
        val attachments = RichTextFormatter.deserializeAttachments(note.attachmentsJson)
        return JSONObject().apply {
            put("title", note.title)
            put("category", note.category)
            put("folder", note.folder)
            put("content", ImageMarkers.strip(note.content))
            put("table", JSONArray(tableRows(note).map { JSONArray(it) }))
            put("attachments", JSONArray(attachments.map { it.fileName }))
        }.toString(2)
    }

    private fun xmlEscape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun buildXml(note: NoteEntity): String {
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<note>\n")
        sb.append("  <title>").append(xmlEscape(note.title)).append("</title>\n")
        sb.append("  <category>").append(xmlEscape(note.category)).append("</category>\n")
        sb.append("  <content>").append(xmlEscape(ImageMarkers.strip(note.content))).append("</content>\n")
        tableRows(note).forEach { r ->
            sb.append("  <row>").append(r.joinToString("") { "<cell>${xmlEscape(it)}</cell>" }).append("</row>\n")
        }
        sb.append("</note>\n")
        return sb.toString()
    }

    private fun htmlEscape(s: String) = xmlEscape(s).replace("\n", "<br/>")

    private fun buildHtml(note: NoteEntity): String {
        val spans = RichTextFormatter.deserializeSpans(note.styleSpansJson)
        val body = StringBuilder()
        val text = ImageMarkers.blank(note.content)
        // Split at every span boundary so styles nest correctly per segment.
        val cuts = (spans.flatMap { listOf(it.start, it.end) } + listOf(0, text.length))
            .map { it.coerceIn(0, text.length) }.distinct().sorted()
        for (i in 0 until cuts.size - 1) {
            val s = cuts[i]; val e = cuts[i + 1]
            if (s >= e) continue
            val active = spans.filter { it.start <= s && it.end >= e }
            var css = ""
            active.forEach {
                when (it.type) {
                    "bold" -> css += "font-weight:bold;"
                    "italic" -> css += "font-style:italic;"
                    "underline" -> css += "text-decoration:underline;"
                    "strikethrough" -> css += "text-decoration:line-through;"
                    "code" -> css += "font-family:monospace;background:#eee;"
                    "color" -> it.value?.let { c -> css += "color:$c;" }
                }
            }
            body.append("<span style=\"$css\">").append(htmlEscape(text.substring(s, e))).append("</span>")
        }
        val tbl = StringBuilder()
        val rows = tableRows(note)
        if (rows.isNotEmpty()) {
            tbl.append("<table border=\"1\" cellpadding=\"6\" style=\"border-collapse:collapse;margin:12px 0\">")
            rows.forEachIndexed { idx, r ->
                tbl.append("<tr>")
                r.forEach { tbl.append(if (idx == 0) "<th>" else "<td>").append(htmlEscape(it)).append(if (idx == 0) "</th>" else "</td>") }
                tbl.append("</tr>")
            }
            tbl.append("</table>")
        }
        val imgs = StringBuilder()
        RichTextFormatter.deserializeAttachments(note.attachmentsJson).forEach { att ->
            if (att.mimeType.startsWith("image/")) {
                val bytes = loadScaledJpeg(att.uri, 1400)
                if (bytes != null) {
                    imgs.append("<p><img style=\"max-width:100%\" src=\"data:image/jpeg;base64,")
                        .append(Base64.encodeToString(bytes, Base64.NO_WRAP)).append("\"/></p>")
                }
            } else {
                imgs.append("<p>Attachment: ").append(htmlEscape(att.fileName)).append("</p>")
            }
        }
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<title>${xmlEscape(note.title)}</title></head><body style=\"font-family:sans-serif;max-width:800px;margin:24px auto;padding:0 12px\">" +
            "<h1 style=\"color:#FF2D55\">${xmlEscape(note.title)}</h1><div>$body</div>$tbl$imgs</body></html>"
    }

    // ---------------------------------------------------------------- images

    private fun decodeScaled(path: String, maxDim: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > maxDim * 2 || bounds.outHeight / sample > maxDim * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeFile(path, opts) ?: return null
            val scale = minOf(1f, maxDim.toFloat() / maxOf(bmp.width, bmp.height))
            if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true) else bmp
        } catch (e: Exception) { null }
    }

    private fun loadScaledJpeg(path: String, maxDim: Int): ByteArray? {
        val bmp = decodeScaled(path, maxDim) ?: return null
        val out = ByteArrayOutputStream()
        // White background so transparent PNGs don't turn black in JPEG.
        val flat = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply { drawColor(AColor.WHITE); drawBitmap(bmp, 0f, 0f, null) }
        flat.compress(Bitmap.CompressFormat.JPEG, 88, out)
        return out.toByteArray()
    }

    // ---------------------------------------------------------------- PDF

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f

    private class Pager {
        val doc = PdfDocument()
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var pageNo = 0
        var y = MARGIN
        val bottom = PAGE_H - MARGIN
        val contentW = PAGE_W - 2 * MARGIN

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            canvas = page!!.canvas
            canvas!!.drawColor(AColor.WHITE)
            y = MARGIN
        }

        fun ensure(height: Float) { if (page == null || y + height > bottom) newPage() }

        fun finish(file: File) {
            page?.let { doc.finishPage(it) }
            FileOutputStream(file).use { doc.writeTo(it) }
            doc.close()
        }
    }

    private fun buildSpannable(note: NoteEntity): SpannableStringBuilder {
        val text = ImageMarkers.blank(note.content)
        val sp = SpannableStringBuilder(text)
        val flag = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        RichTextFormatter.deserializeSpans(note.styleSpansJson).forEach { span ->
            val s = span.start.coerceIn(0, text.length)
            val e = span.end.coerceIn(0, text.length)
            if (s >= e) return@forEach
            when (span.type) {
                "bold" -> sp.setSpan(StyleSpan(Typeface.BOLD), s, e, flag)
                "italic" -> sp.setSpan(StyleSpan(Typeface.ITALIC), s, e, flag)
                "underline" -> sp.setSpan(UnderlineSpan(), s, e, flag)
                "strikethrough" -> sp.setSpan(StrikethroughSpan(), s, e, flag)
                "code" -> {
                    sp.setSpan(TypefaceSpan("monospace"), s, e, flag)
                    sp.setSpan(BackgroundColorSpan(AColor.rgb(238, 238, 238)), s, e, flag)
                }
                "color" -> span.value?.let { hex ->
                    try { sp.setSpan(ForegroundColorSpan(AColor.parseColor(hex)), s, e, flag) } catch (_: Exception) {}
                }
            }
        }
        return sp
    }

    /** Draws a layout across as many pages as needed, splitting between lines. */
    private fun drawLayout(pager: Pager, layout: StaticLayout, left: Float) {
        var line = 0
        val total = layout.lineCount
        while (line < total) {
            pager.ensure((layout.getLineBottom(line) - layout.getLineTop(line)).toFloat())
            val startTop = layout.getLineTop(line)
            var end = line
            while (end < total && (layout.getLineBottom(end) - startTop) <= (pager.bottom - pager.y)) end++
            if (end == line) end = line + 1 // a single very tall line: draw it anyway
            val endBottom = layout.getLineBottom(end - 1)
            val c = pager.canvas!!
            c.save()
            c.translate(left, pager.y - startTop)
            c.clipRect(0f, startTop.toFloat(), layout.width.toFloat(), endBottom.toFloat())
            layout.draw(c)
            c.restore()
            pager.y += (endBottom - startTop)
            line = end
            if (line < total) pager.newPage()
        }
    }

    private fun makeLayout(text: CharSequence, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setLineSpacing(3f, 1f).setIncludePad(false).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

    /**
     * Draws one image at the current position with its saved edit (rotation, crop, zoom, offset)
     * and its saved size (fraction of the page's content width).
     */
    private fun drawImageAt(pager: Pager, att: RichTextFormatter.AttachmentInfo, paint: TextPaint, cw: Int) {
        pager.y += 6f
        val maxH = pager.bottom - MARGIN
        var w = pager.contentW * att.widthFraction.coerceIn(0.2f, 1f)
        var h = AttachmentRenderer.frameHeightPx(att, w)
        if (h > maxH) { w *= maxH / h; h = maxH }
        val bmp = AttachmentRenderer.frameBitmap(att, (w * 2).toInt().coerceIn(200, 2400))
        if (bmp == null) {
            drawLayout(pager, makeLayout("[Image could not be loaded: ${att.fileName}]", paint, cw), MARGIN)
            return
        }
        pager.ensure(h)
        val left = MARGIN + (pager.contentW - w) / 2f * 0f // images are left-aligned like in the editor
        pager.canvas!!.drawBitmap(bmp, null, android.graphics.RectF(left, pager.y, left + w, pager.y + h),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        pager.y += h + 6f
    }

    private fun writePdf(note: NoteEntity, target: File) {
        val pager = Pager()
        pager.newPage()
        val cw = pager.contentW.toInt()

        // Title
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AColor.rgb(255, 45, 85); textSize = 22f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        drawLayout(pager, makeLayout(note.title.ifBlank { "Untitled Note" }, titlePaint, cw), MARGIN)
        pager.y += 10f

        // Body — default text is dark on white paper regardless of app theme,
        // explicit color spans keep their chosen colors.
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AColor.rgb(25, 25, 25); textSize = note.fontSize.coerceIn(9, 28).toFloat() * 0.8f
        }
        val allAtts = RichTextFormatter.deserializeAttachments(note.attachmentsJson)
        val placedInline = HashSet<String>()
        if (note.content.isNotEmpty()) {
            val body = buildSpannable(note)
            if (note.isCodeFormat) body.setSpan(TypefaceSpan("monospace"), 0, body.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)

            // Walk the note line by line: ordinary text is drawn as one block (styles kept), every
            // "[img:id]" line draws its picture HERE, so the PDF mirrors the editor's order.
            val lines = note.content.split("\n")
            var offset = 0
            var segStart = 0
            fun flushText(endExclusive: Int) {
                var a = segStart
                var b = endExclusive
                // trim the line break that separated text from the image
                if (b > a && b <= body.length && body[b - 1] == '\n') b--
                if (b > a) drawLayout(pager, makeLayout(body.subSequence(a, b), bodyPaint, cw), MARGIN)
            }
            for (line in lines) {
                val lineStart = offset
                val lineEnd = offset + line.length
                val id = ImageMarkers.idOf(line)
                if (id != null) {
                    flushText(lineStart)
                    val att = allAtts.firstOrNull { it.id == id }
                    if (att != null) { placedInline.add(id); drawImageAt(pager, att, bodyPaint, cw) }
                    segStart = (lineEnd + 1).coerceAtMost(body.length)
                }
                offset = lineEnd + 1
            }
            if (segStart < body.length) {
                drawLayout(pager, makeLayout(body.subSequence(segStart, body.length), bodyPaint, cw), MARGIN)
            }
        }

        // Table
        val rows = tableRows(note)
        if (rows.isNotEmpty()) {
            pager.y += 14f
            drawTable(pager, rows, cw)
        }

        // Remaining attachments (legacy notes without inline position, or non-image files).
        allAtts.forEach { att ->
            if (att.id.isNotBlank() && att.mimeType.startsWith("image/")) {
                // inline images without a marker line (deleted from the text) are not exported
                return@forEach
            }
            pager.y += 12f
            if (att.mimeType.startsWith("image/")) {
                drawImageAt(pager, att, bodyPaint, cw)
            } else {
                drawLayout(pager, makeLayout("Attachment: ${att.fileName}", bodyPaint, cw), MARGIN)
            }
        }
        pager.finish(target)
    }

    private fun drawTable(pager: Pager, rows: List<List<String>>, contentW: Int) {
        val cols = rows.maxOf { it.size }.coerceAtLeast(1)
        val colW = contentW / cols
        val cellPad = 5
        val border = Paint().apply { color = AColor.rgb(160, 160, 160); style = Paint.Style.STROKE; strokeWidth = 0.8f }
        val headFill = Paint().apply { color = AColor.rgb(255, 232, 238); style = Paint.Style.FILL }
        rows.forEachIndexed { ri, row ->
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AColor.rgb(25, 25, 25); textSize = 10.5f
                typeface = if (ri == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            val layouts = (0 until cols).map { ci -> makeLayout(row.getOrElse(ci) { "" }, paint, colW - 2 * cellPad) }
            val rowH = (layouts.maxOf { it.height } + 2 * cellPad).toFloat()
            pager.ensure(rowH)
            val c = pager.canvas!!
            for (ci in 0 until cols) {
                val x = MARGIN + ci * colW
                val rect = android.graphics.RectF(x, pager.y, x + colW, pager.y + rowH)
                if (ri == 0) c.drawRect(rect, headFill)
                c.drawRect(rect, border)
                c.save(); c.translate(x + cellPad, pager.y + cellPad); layouts[ci].draw(c); c.restore()
            }
            pager.y += rowH
        }
    }

    // ---------------------------------------------------------------- DOCX (real OOXML)

    private fun xesc(s: String) = xmlEscape(s)

    private fun writeDocx(note: NoteEntity, target: File) {
        val spans = RichTextFormatter.deserializeSpans(note.styleSpansJson)
        val text = ImageMarkers.blank(note.content)
        val body = StringBuilder()

        // Title
        body.append("<w:p><w:r><w:rPr><w:b/><w:color w:val=\"FF2D55\"/><w:sz w:val=\"44\"/></w:rPr><w:t xml:space=\"preserve\">")
            .append(xesc(note.title)).append("</w:t></w:r></w:p>")

        // Body: one run per span-boundary segment, paragraphs split on newlines.
        val cuts = (spans.flatMap { listOf(it.start, it.end) } + listOf(0, text.length) + text.indices.filter { text[it] == '\n' }.flatMap { listOf(it, it + 1) })
            .map { it.coerceIn(0, text.length) }.distinct().sorted()
        body.append("<w:p>")
        for (i in 0 until cuts.size - 1) {
            val s = cuts[i]; val e = cuts[i + 1]
            if (s >= e) continue
            val seg = text.substring(s, e)
            if (seg == "\n") { body.append("</w:p><w:p>"); continue }
            val active = spans.filter { it.start <= s && it.end >= e }
            val rpr = StringBuilder()
            active.forEach {
                when (it.type) {
                    "bold" -> rpr.append("<w:b/>")
                    "italic" -> rpr.append("<w:i/>")
                    "underline" -> rpr.append("<w:u w:val=\"single\"/>")
                    "strikethrough" -> rpr.append("<w:strike/>")
                    "code" -> rpr.append("<w:rFonts w:ascii=\"Courier New\" w:hAnsi=\"Courier New\"/>")
                    "color" -> it.value?.removePrefix("#")?.takeIf { h -> h.length == 6 }?.let { h -> rpr.append("<w:color w:val=\"$h\"/>") }
                }
            }
            body.append("<w:r><w:rPr>").append(rpr).append("</w:rPr><w:t xml:space=\"preserve\">").append(xesc(seg)).append("</w:t></w:r>")
        }
        body.append("</w:p>")

        // Table
        val rows = tableRows(note)
        if (rows.isNotEmpty()) {
            val cols = rows.maxOf { it.size }.coerceAtLeast(1)
            body.append("<w:tbl><w:tblPr><w:tblW w:w=\"5000\" w:type=\"pct\"/><w:tblBorders>")
            listOf("top", "left", "bottom", "right", "insideH", "insideV").forEach {
                body.append("<w:$it w:val=\"single\" w:sz=\"4\" w:color=\"999999\"/>")
            }
            body.append("</w:tblBorders></w:tblPr>")
            rows.forEachIndexed { ri, r ->
                body.append("<w:tr>")
                for (ci in 0 until cols) {
                    body.append("<w:tc><w:p><w:r>")
                    if (ri == 0) body.append("<w:rPr><w:b/></w:rPr>")
                    body.append("<w:t xml:space=\"preserve\">").append(xesc(r.getOrElse(ci) { "" })).append("</w:t></w:r></w:p></w:tc>")
                }
                body.append("</w:tr>")
            }
            body.append("</w:tbl><w:p/>")
        }

        // Images
        val media = mutableListOf<Pair<String, ByteArray>>()
        RichTextFormatter.deserializeAttachments(note.attachmentsJson).forEach { att ->
            if (att.mimeType.startsWith("image/")) {
                val bmp = decodeScaled(att.uri, 1600)
                val bytes = loadScaledJpeg(att.uri, 1600)
                if (bmp != null && bytes != null) {
                    val idx = media.size + 1
                    media.add("image$idx.jpg" to bytes)
                    val widthEmu = 5486400L // 6 inches
                    val heightEmu = (widthEmu * bmp.height.toDouble() / bmp.width).toLong()
                    body.append(
                        "<w:p><w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">" +
                            "<wp:extent cx=\"$widthEmu\" cy=\"$heightEmu\"/><wp:docPr id=\"$idx\" name=\"Image $idx\"/>" +
                            "<a:graphic xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">" +
                            "<a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
                            "<pic:pic xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
                            "<pic:nvPicPr><pic:cNvPr id=\"$idx\" name=\"image$idx.jpg\"/><pic:cNvPicPr/></pic:nvPicPr>" +
                            "<pic:blipFill><a:blip r:embed=\"rIdImg$idx\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>" +
                            "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$widthEmu\" cy=\"$heightEmu\"/></a:xfrm>" +
                            "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic>" +
                            "</wp:inline></w:drawing></w:r></w:p>"
                    )
                }
            } else {
                body.append("<w:p><w:r><w:t xml:space=\"preserve\">Attachment: ").append(xesc(att.fileName)).append("</w:t></w:r></w:p>")
            }
        }

        val document = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" " +
            "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" " +
            "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\">" +
            "<w:body>$body<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\"/></w:sectPr></w:body></w:document>"

        val contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Default Extension=\"jpg\" ContentType=\"image/jpeg\"/>" +
            "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>"

        val rootRels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>"

        val docRels = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        media.forEachIndexed { i, (name, _) ->
            docRels.append("<Relationship Id=\"rIdImg${i + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/$name\"/>")
        }
        docRels.append("</Relationships>")

        ZipOutputStream(FileOutputStream(target)).use { zip ->
            fun put(name: String, data: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() }
            put("[Content_Types].xml", contentTypes.toByteArray())
            put("_rels/.rels", rootRels.toByteArray())
            put("word/document.xml", document.toByteArray())
            put("word/_rels/document.xml.rels", docRels.toString().toByteArray())
            media.forEach { (name, bytes) -> put("word/media/$name", bytes) }
        }
    }
}
