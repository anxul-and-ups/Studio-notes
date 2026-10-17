package com.example.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

object RichTextFormatter {

    data class TextSpan(
        val start: Int,
        val end: Int,
        val type: String,
        val value: String? = null
    )

    /**
     * An attached file. For images the extra fields describe a NON-destructive edit: the original
     * file is never modified, the edit is re-applied whenever the image is drawn / exported.
     *
     *  - id            non-blank => the image sits INLINE in the note text on a "[img:<id>]" line
     *  - widthFraction share of the note width the image frame takes (0.2..1)
     *  - zoom, posX, posY  zoom and offset (fraction of frame size) of the picture inside its frame
     *  - rotation      0/90/180/270, applied before cropping
     *  - crop*         fraction (0..0.45) trimmed from each side of the (rotated) image
     *  - locked        locked images cannot be moved / resized / rotated / cropped
     *  - order         insertion order (informational; inline position comes from the text line)
     */
    data class AttachmentInfo(
        val uri: String,
        val fileName: String,
        val mimeType: String,
        val sizeBytes: Long,
        val id: String = "",
        val srcWidth: Int = 0,
        val srcHeight: Int = 0,
        val widthFraction: Float = 1f,
        val zoom: Float = 1f,
        val posX: Float = 0f,
        val posY: Float = 0f,
        val rotation: Int = 0,
        val cropLeft: Float = 0f,
        val cropTop: Float = 0f,
        val cropRight: Float = 0f,
        val cropBottom: Float = 0f,
        val locked: Boolean = false,
        val order: Int = 0
    )

    fun serializeSpans(spans: List<TextSpan>): String {
        val array = JSONArray()
        for (span in spans) {
            val obj = JSONObject()
            obj.put("start", span.start)
            obj.put("end", span.end)
            obj.put("type", span.type)
            if (span.value != null) {
                obj.put("value", span.value)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun deserializeSpans(json: String?): List<TextSpan> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<TextSpan>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val start = obj.getInt("start")
                val end = obj.getInt("end")
                val type = obj.getString("type")
                val value = if (obj.has("value")) obj.getString("value") else null
                list.add(TextSpan(start, end, type, value))
            }
        } catch (e: Exception) {
            // Return empty list on parse error
        }
        return list
    }

    fun serializeAttachments(attachments: List<AttachmentInfo>): String {
        val array = JSONArray()
        for (att in attachments) {
            val obj = JSONObject()
            obj.put("uri", att.uri)
            obj.put("fileName", att.fileName)
            obj.put("mimeType", att.mimeType)
            obj.put("sizeBytes", att.sizeBytes)
            obj.put("id", att.id)
            obj.put("srcW", att.srcWidth)
            obj.put("srcH", att.srcHeight)
            obj.put("wf", att.widthFraction.toDouble())
            obj.put("zoom", att.zoom.toDouble())
            obj.put("px", att.posX.toDouble())
            obj.put("py", att.posY.toDouble())
            obj.put("rot", att.rotation)
            obj.put("cl", att.cropLeft.toDouble())
            obj.put("ct", att.cropTop.toDouble())
            obj.put("cr", att.cropRight.toDouble())
            obj.put("cb", att.cropBottom.toDouble())
            obj.put("locked", att.locked)
            obj.put("order", att.order)
            array.put(obj)
        }
        return array.toString()
    }

    fun deserializeAttachments(json: String?): List<AttachmentInfo> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<AttachmentInfo>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    AttachmentInfo(
                        uri = obj.optString("uri", ""),
                        fileName = obj.optString("fileName", "file"),
                        mimeType = obj.optString("mimeType", "*/*"),
                        sizeBytes = obj.optLong("sizeBytes", 0L),
                        id = obj.optString("id", ""),
                        srcWidth = obj.optInt("srcW", 0),
                        srcHeight = obj.optInt("srcH", 0),
                        widthFraction = obj.optDouble("wf", 1.0).toFloat().coerceIn(0.2f, 1f),
                        zoom = obj.optDouble("zoom", 1.0).toFloat().coerceIn(0.3f, 5f),
                        posX = obj.optDouble("px", 0.0).toFloat(),
                        posY = obj.optDouble("py", 0.0).toFloat(),
                        rotation = ((obj.optInt("rot", 0) % 360) + 360) % 360,
                        cropLeft = obj.optDouble("cl", 0.0).toFloat().coerceIn(0f, 0.45f),
                        cropTop = obj.optDouble("ct", 0.0).toFloat().coerceIn(0f, 0.45f),
                        cropRight = obj.optDouble("cr", 0.0).toFloat().coerceIn(0f, 0.45f),
                        cropBottom = obj.optDouble("cb", 0.0).toFloat().coerceIn(0f, 0.45f),
                        locked = obj.optBoolean("locked", false),
                        order = obj.optInt("order", 0)
                    )
                )
            }
        } catch (e: Exception) {
            // Return empty list on parse error
        }
        return list
    }

    fun isPropertyActiveThroughout(spans: List<TextSpan>, type: String, start: Int, end: Int): Boolean {
        if (start >= end) return false
        return spans.any { it.type == type && it.start <= start && it.end >= end }
    }

    fun adjustSpansForEdit(spans: List<TextSpan>, oldText: String, newText: String): List<TextSpan> {
        val diff = newText.length - oldText.length
        if (diff == 0) return spans
        // Find first divergence point
        var editStart = 0
        while (editStart < oldText.length && editStart < newText.length && oldText[editStart] == newText[editStart]) {
            editStart++
        }
        return spans.mapNotNull { span ->
            when {
                span.end <= editStart -> span
                span.start >= editStart -> {
                    val newStart = (span.start + diff).coerceAtLeast(editStart)
                    val newEnd = (span.end + diff).coerceAtLeast(newStart)
                    if (newStart < newEnd) span.copy(start = newStart, end = newEnd) else null
                }
                else -> {
                    val newEnd = (span.end + diff).coerceAtLeast(span.start)
                    if (span.start < newEnd) span.copy(end = newEnd) else null
                }
            }
        }
    }

    fun addSpan(spans: List<TextSpan>, start: Int, end: Int, type: String, value: String? = null): List<TextSpan> {
        if (start >= end) return spans
        return spans + TextSpan(start, end, type, value)
    }

    fun toggleBooleanProperty(spans: List<TextSpan>, type: String, start: Int, end: Int): List<TextSpan> {
        if (start >= end) return spans
        val existing = spans.filter { it.type == type && it.start <= start && it.end >= end }
        return if (existing.isNotEmpty()) {
            spans.filterNot { it.type == type && it.start <= start && it.end >= end }
        } else {
            spans + TextSpan(start, end, type)
        }
    }

    fun setValueProperty(spans: List<TextSpan>, type: String, start: Int, end: Int, value: String): List<TextSpan> {
        if (start >= end) return spans
        val filtered = spans.filterNot { it.type == type && it.start >= start && it.end <= end }
        return filtered + TextSpan(start, end, type, value)
    }

    /** Removes every span of [type] inside [start, end); spans that only partly overlap are trimmed / split. */
    fun clearType(spans: List<TextSpan>, type: String, start: Int, end: Int): List<TextSpan> {
        if (start >= end) return spans
        val out = mutableListOf<TextSpan>()
        for (sp in spans) {
            if (sp.type != type || sp.end <= start || sp.start >= end) { out.add(sp); continue }
            if (sp.start < start) out.add(sp.copy(end = start))
            if (sp.end > end) out.add(sp.copy(start = end))
        }
        return out
    }

    /** Removes ALL formatting spans in the range (Samsung Notes "clear formatting"). */
    fun clearAll(spans: List<TextSpan>, start: Int, end: Int): List<TextSpan> {
        var result = spans
        for (t in listOf("bold", "italic", "underline", "strikethrough", "code", "color", "highlight", "fontsize")) {
            result = clearType(result, t, start, end)
        }
        return result
    }

    fun buildStyledText(
        text: String,
        spans: List<TextSpan>,
        defaultColor: Color,
        fontSize: Float = 15f
    ): AnnotatedString {
        return buildAnnotatedString {
            append(text)
            for (span in spans) {
                val s = span.start.coerceIn(0, text.length)
                val e = span.end.coerceIn(0, text.length)
                if (s < e) {
                    when (span.type) {
                        "bold" -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), s, e)
                        "italic" -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), s, e)
                        "underline" -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), s, e)
                        "strikethrough" -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), s, e)
                        "code" -> addStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color(0x33888888)
                            ),
                            s,
                            e
                        )
                        "highlight" -> {
                            val bg = span.value?.let { hex ->
                                try { Color(android.graphics.Color.parseColor(hex)).copy(alpha = 0.45f) } catch (ex: Exception) { null }
                            } ?: Color(0x73FFEB3B)
                            addStyle(SpanStyle(background = bg), s, e)
                        }
                        "fontsize" -> {
                            val size = span.value?.toFloatOrNull()
                            if (size != null && size in 6f..96f) addStyle(SpanStyle(fontSize = size.sp), s, e)
                        }
                        "color" -> {
                            val color = span.value?.let { hex ->
                                try {
                                    Color(android.graphics.Color.parseColor(hex))
                                } catch (ex: Exception) {
                                    null
                                }
                            } ?: defaultColor
                            addStyle(SpanStyle(color = color), s, e)
                        }
                    }
                }
            }
        }
    }
}
