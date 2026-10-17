package com.example.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.IndeterminateCheckBox
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.components.GlassBackground
import com.example.ui.components.NeuIconButton
import com.example.ui.theme.CrimsonPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Keeps the text while the app process lives (leaving the screen does not lose your HTML). */
private object HtmlViewerStore {
    var text: String = ""
}

private class HtmlColors(val dark: Boolean) {
    val bg: Color = if (dark) Color(0xFF1A1C22) else Color(0xFFFFFFFF)
    val gutter: Color = if (dark) Color(0xFF23262E) else Color(0xFFE9EAEE)
    val text: Color = if (dark) Color(0xFFE6E6E6) else Color(0xFF1A1A1A)
    val muted: Color = if (dark) Color(0xFF7C8089) else Color(0xFF8A8D94)
    val border: Color = if (dark) Color(0xFF343842) else Color(0xFFD5D7DD)
    val tag: Color = if (dark) Color(0xFF8AB4F8) else Color(0xFF0B57D0)
    val attr: Color = if (dark) Color(0xFFE6B450) else Color(0xFF8A5A00)
    val string: Color = if (dark) Color(0xFF9CCB7E) else Color(0xFF1E7B34)
    val comment: Color = if (dark) Color(0xFF7C8089) else Color(0xFF7A7F87)
    val doctype: Color = if (dark) Color(0xFFC49BF0) else Color(0xFF7A3E9D)
}

private val VOID_TAGS = setOf(
    "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"
)

// ---------------------------------------------------------------------------------------------
// Syntax highlighting
// ---------------------------------------------------------------------------------------------

private fun highlightHtml(src: String, c: HtmlColors): AnnotatedString {
    val b = AnnotatedString.Builder(src)
    val n = src.length
    var i = 0
    fun span(color: Color, from: Int, to: Int, italic: Boolean = false) {
        if (to > from) {
            b.addStyle(SpanStyle(color = color, fontStyle = if (italic) FontStyle.Italic else null), from, to)
        }
    }
    while (i < n) {
        if (src[i] != '<') {
            i++
            continue
        }
        if (src.startsWith("<!--", i)) {
            val e = src.indexOf("-->", i + 4)
            val end = if (e < 0) n else e + 3
            span(c.comment, i, end, true)
            i = end
            continue
        }
        var j = i + 1
        var closing = false
        if (j < n && src[j] == '/') {
            closing = true
            j++
        } else if (j < n && (src[j] == '!' || src[j] == '?')) {
            j++
        }
        val nameStart = j
        while (j < n && (src[j].isLetterOrDigit() || src[j] == '-' || src[j] == ':' || src[j] == '_')) j++
        val isDoctype = src.startsWith("<!", i)
        if (j == nameStart && !isDoctype) {
            i++ // a lone "<" in text
            continue
        }
        span(if (isDoctype) c.doctype else c.tag, i, j)
        val tagName = src.substring(nameStart, j).lowercase()
        var k = j
        while (k < n && src[k] != '>') {
            val ch = src[k]
            if (ch == '"' || ch == '\'') {
                var e = k + 1
                while (e < n && src[e] != ch) e++
                val end = minOf(e + 1, n)
                span(if (isDoctype) c.doctype else c.string, k, end)
                k = end
            } else if (ch.isLetter()) {
                var e = k
                while (e < n && (src[e].isLetterOrDigit() || src[e] == '-' || src[e] == ':' || src[e] == '_' || src[e] == '.')) e++
                span(if (isDoctype) c.doctype else c.attr, k, e)
                k = e
            } else {
                k++
            }
        }
        val close = if (k < n) k + 1 else n
        span(if (isDoctype) c.doctype else c.tag, k, close)
        i = close
        if (!closing && (tagName == "script" || tagName == "style")) {
            val e = src.indexOf("</$tagName", i, ignoreCase = true)
            i = if (e < 0) n else e
        }
    }
    return b.toAnnotatedString()
}

private fun splitLines(a: AnnotatedString): List<AnnotatedString> {
    val out = ArrayList<AnnotatedString>()
    val s = a.text
    var start = 0
    for (idx in s.indices) {
        if (s[idx] == '\n') {
            out.add(a.subSequence(start, idx))
            start = idx + 1
        }
    }
    out.add(a.subSequence(start, s.length))
    return out
}

// ---------------------------------------------------------------------------------------------
// Format (pretty print)
// ---------------------------------------------------------------------------------------------

private sealed class Tok {
    class Tag(val raw: String, val name: String, val kind: Int) : Tok() // 0 open, 1 close, 2 void/self-closing, 3 doctype/pi
    class Text(val s: String) : Tok()
    class Comment(val s: String) : Tok()
    class Raw(val s: String) : Tok()
}

private val RAW_TAGS = setOf("script", "style", "pre", "textarea")

private fun tokenize(src: String): List<Tok> {
    val out = ArrayList<Tok>()
    val n = src.length
    var i = 0
    while (i < n) {
        if (src[i] == '<') {
            if (src.startsWith("<!--", i)) {
                val e = src.indexOf("-->", i + 4)
                val end = if (e < 0) n else e + 3
                out.add(Tok.Comment(src.substring(i, end)))
                i = end
                continue
            }
            if (i + 1 < n && (src[i + 1] == '!' || src[i + 1] == '?')) {
                val e = src.indexOf('>', i)
                val end = if (e < 0) n else e + 1
                out.add(Tok.Tag(src.substring(i, end), "", 3))
                i = end
                continue
            }
            val isClose = i + 1 < n && src[i + 1] == '/'
            val nameStart = if (isClose) i + 2 else i + 1
            if (nameStart < n && src[nameStart].isLetter()) {
                var j = nameStart
                while (j < n && (src[j].isLetterOrDigit() || src[j] == '-' || src[j] == ':' || src[j] == '_')) j++
                val name = src.substring(nameStart, j).lowercase()
                var k = j
                var quote = '\u0000'
                while (k < n) {
                    val ch = src[k]
                    if (quote != '\u0000') {
                        if (ch == quote) quote = '\u0000'
                    } else if (ch == '"' || ch == '\'') {
                        quote = ch
                    } else if (ch == '>') {
                        break
                    }
                    k++
                }
                val end = if (k < n) k + 1 else n
                val raw = src.substring(i, end)
                val selfClosed = raw.endsWith("/>")
                val kind = when {
                    isClose -> 1
                    selfClosed || name in VOID_TAGS -> 2
                    else -> 0
                }
                out.add(Tok.Tag(raw, name, kind))
                i = end
                if (kind == 0 && name in RAW_TAGS) {
                    val e = src.indexOf("</$name", i, ignoreCase = true)
                    val contentEnd = if (e < 0) n else e
                    if (contentEnd > i) out.add(Tok.Raw(src.substring(i, contentEnd)))
                    i = contentEnd
                }
                continue
            }
        }
        var j = i
        while (j < n && src[j] != '<') j++
        if (j == i) j = i + 1 // a lone "<"
        val text = src.substring(i, j).trim().replace(Regex("\\s+"), " ")
        if (text.isNotEmpty()) out.add(Tok.Text(text))
        i = j
    }
    return out
}

private fun formatHtml(src: String): String {
    val toks = tokenize(src)
    val sb = StringBuilder()
    var depth = 0
    fun line(s: String) {
        sb.append("  ".repeat(depth.coerceAtLeast(0))).append(s).append('\n')
    }
    var idx = 0
    while (idx < toks.size) {
        val t = toks[idx]
        when (t) {
            is Tok.Comment -> line(t.s)
            is Tok.Text -> line(t.s)
            is Tok.Raw -> line(t.s.trim())
            is Tok.Tag -> when (t.kind) {
                3, 2 -> line(t.raw)
                1 -> {
                    depth--
                    line(t.raw)
                }
                else -> {
                    val t1 = toks.getOrNull(idx + 1)
                    val t2 = toks.getOrNull(idx + 2)
                    if (t1 is Tok.Text && t2 is Tok.Tag && t2.kind == 1 && t2.name == t.name && t1.s.length <= 120) {
                        line(t.raw + t1.s + t2.raw)
                        idx += 2
                    } else if (t1 is Tok.Tag && t1.kind == 1 && t1.name == t.name) {
                        line(t.raw + t1.raw)
                        idx += 1
                    } else if (t1 is Tok.Raw && t2 is Tok.Tag && t2.kind == 1 && t2.name == t.name) {
                        if (t.name == "pre" || t.name == "textarea") {
                            // whitespace matters here: keep it exactly
                            sb.append("  ".repeat(depth.coerceAtLeast(0))).append(t.raw).append(t1.s).append(t2.raw).append('\n')
                        } else {
                            line(t.raw)
                            depth++
                            val body = t1.s.trim('\n', '\r').lines().map { it.trimEnd() }
                            val minIndent = body.filter { it.isNotBlank() }.minOfOrNull { l -> l.length - l.trimStart().length } ?: 0
                            for (l in body) {
                                if (l.isBlank()) continue
                                line(l.substring(minOf(minIndent, l.length)))
                            }
                            depth--
                            line(t2.raw)
                        }
                        idx += 2
                    } else {
                        line(t.raw)
                        depth++
                    }
                }
            }
        }
        idx++
    }
    return sb.toString().trimEnd('\n')
}

// ---------------------------------------------------------------------------------------------
// Folding (expand / collapse)
// ---------------------------------------------------------------------------------------------

/** start line -> end line of every element that spans several lines. */
private fun computeFolds(src: String): Map<Int, Int> {
    if (src.isEmpty()) return emptyMap()
    // mask comments and script/style bodies so "<div>" inside them is ignored (keeps all offsets)
    val chars = src.toCharArray()
    fun mask(from: Int, to: Int) {
        for (p in from until minOf(to, chars.size)) if (chars[p] != '\n') chars[p] = ' '
    }
    var cmt = Regex("<!--[\\s\\S]*?-->").find(src)
    while (cmt != null) {
        mask(cmt.range.first, cmt.range.last + 1)
        cmt = cmt.next()
    }
    var raw = Regex("<(script|style)\\b[^>]*>([\\s\\S]*?)</\\1\\s*>", RegexOption.IGNORE_CASE).find(src)
    while (raw != null) {
        val g = raw.groups[2]
        if (g != null) mask(g.range.first, g.range.last + 1)
        raw = raw.next()
    }
    val masked = String(chars)

    val lineStarts = ArrayList<Int>()
    lineStarts.add(0)
    for (p in masked.indices) if (masked[p] == '\n') lineStarts.add(p + 1)
    fun lineOf(pos: Int): Int {
        var lo = 0
        var hi = lineStarts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (lineStarts[mid] <= pos) lo = mid else hi = mid - 1
        }
        return lo
    }

    val folds = HashMap<Int, Int>()
    val stack = ArrayList<Pair<String, Int>>()
    val tagRegex = Regex("<(/?)([a-zA-Z][a-zA-Z0-9:-]*)((?:\"[^\"]*\"|'[^']*'|[^>\"'])*)>")
    for (m in tagRegex.findAll(masked)) {
        val closing = m.groupValues[1] == "/"
        val name = m.groupValues[2].lowercase()
        val selfClosed = m.groupValues[3].trimEnd().endsWith("/")
        val line = lineOf(m.range.first)
        if (closing) {
            var k = stack.size - 1
            while (k >= 0 && stack[k].first != name) k--
            if (k >= 0) {
                val openLine = stack[k].second
                while (stack.size > k) stack.removeAt(stack.size - 1)
                if (line > openLine) {
                    val prev = folds[openLine]
                    if (prev == null || line > prev) folds[openLine] = line
                }
            }
        } else if (!selfClosed && name !in VOID_TAGS) {
            stack.add(Pair(name, line))
        }
    }
    return folds
}

// ---------------------------------------------------------------------------------------------
// Sample + helpers
// ---------------------------------------------------------------------------------------------

private const val SAMPLE_HTML = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Hello from AU Notes</title>
  <style>
    body { font-family: sans-serif; margin: 24px; background: #fff7f9; }
    h1 { color: #ff2d55; }
    .card { padding: 16px; border-radius: 12px; background: white; box-shadow: 0 2px 8px rgba(0,0,0,.12); }
  </style>
</head>
<body>
  <!-- A small demo page -->
  <h1>HTML Viewer</h1>
  <div class="card">
    <p>Edit the code, then tap <b>Preview</b> to see the page.</p>
    <ul>
      <li>Highlight colours the code</li>
      <li>Format tidies the indentation</li>
      <li>Collapse all folds the tags</li>
    </ul>
    <button onclick="document.getElementById('msg').textContent = 'It works!'">Click me</button>
    <p id="msg"></p>
  </div>
</body>
</html>"""

private fun injectDarkScheme(html: String): String {
    val tag = "<meta name=\"color-scheme\" content=\"dark\"><style>html{color-scheme:dark}</style>"
    Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html)?.let {
        return html.substring(0, it.range.last + 1) + tag + html.substring(it.range.last + 1)
    }
    Regex("<html[^>]*>", RegexOption.IGNORE_CASE).find(html)?.let {
        return html.substring(0, it.range.last + 1) + tag + html.substring(it.range.last + 1)
    }
    Regex("<!doctype[^>]*>", RegexOption.IGNORE_CASE).find(html)?.let {
        return html.substring(0, it.range.last + 1) + tag + html.substring(it.range.last + 1)
    }
    return tag + html
}

// ---------------------------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------------------------

@Composable
fun HtmlViewerScreen(
    isDarkMode: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var text by remember { mutableStateOf(HtmlViewerStore.text) }
    var mode by remember { mutableIntStateOf(0) } // 0 editor, 1 preview, 2 highlight
    var viewerDark by remember { mutableStateOf(isDarkMode) }
    var collapsed by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var confirmClear by remember { mutableStateOf(false) }

    val colors = remember(viewerDark) { HtmlColors(viewerDark) }
    val appText = if (isDarkMode) Color.White else Color(0xFF1A1A1A)

    fun setText(value: String) {
        text = value
        HtmlViewerStore.text = value
    }

    BackHandler(enabled = mode != 0) { mode = 0 }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val content = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            val bytes = input.readBytes()
                            if (bytes.size > 5 * 1024 * 1024) throw IllegalStateException("File is larger than 5 MB")
                            String(bytes, Charsets.UTF_8)
                        } ?: throw IllegalStateException("Could not open the file")
                    }
                    setText(content)
                    collapsed = emptySet()
                    mode = 0
                    Toast.makeText(context, "File imported", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                            ?: throw IllegalStateException("Could not write the file")
                    }
                    Toast.makeText(context, "File saved", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val folds = remember(text) { computeFolds(text) }
    val lineCount = remember(text) { text.count { it == '\n' } + 1 }

    GlassBackground(isDarkMode = isDarkMode) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // ---- Top bar ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeuIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    isDarkMode = isDarkMode,
                    size = 38.dp, iconSize = 18.dp,
                    tint = appText,
                    onClick = onBack
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("HTML Viewer", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = appText)
                    Text(
                        text = if (text.isEmpty()) "Paste, import or write HTML" else "$lineCount lines · ${text.length} characters",
                        fontSize = 11.sp,
                        color = appText.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ---- Tools (same actions as the HTML viewer website) ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolButton(Icons.Default.Visibility, "Preview (Full page)", mode == 1, isDarkMode) { mode = 1 }
                ToolButton(Icons.Default.Code, "Highlight (Full page)", mode == 2, isDarkMode) { mode = 2 }
                ToolButton(Icons.Default.FormatAlignLeft, "Format", false, isDarkMode) {
                    if (text.isBlank()) {
                        Toast.makeText(context, "Nothing to format", Toast.LENGTH_SHORT).show()
                    } else {
                        setText(formatHtml(text))
                        collapsed = emptySet()
                    }
                }
                ToolButton(Icons.Default.AddBox, "Expand all", false, isDarkMode) {
                    mode = 2
                    collapsed = emptySet()
                }
                ToolButton(Icons.Default.IndeterminateCheckBox, "Collapse all", false, isDarkMode) {
                    mode = 2
                    collapsed = folds.keys.toSet()
                }
                ToolButton(Icons.Default.Description, "Sample", false, isDarkMode) {
                    setText(SAMPLE_HTML)
                    collapsed = emptySet()
                }
                ToolButton(Icons.Default.Delete, "Clear", false, isDarkMode) {
                    if (text.isNotEmpty()) confirmClear = true
                }
                ToolButton(Icons.Default.FileUpload, "Import from file", false, isDarkMode) {
                    importLauncher.launch(arrayOf("text/html", "text/plain", "text/*", "application/xhtml+xml", "*/*"))
                }
                ToolButton(Icons.Default.FileDownload, "Export to file", false, isDarkMode) {
                    exportLauncher.launch("page.html")
                }
                ToolButton(
                    if (viewerDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                    if (viewerDark) "Light mode" else "Dark mode",
                    false,
                    isDarkMode
                ) { viewerDark = !viewerDark }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ---- Mode tabs ----
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeTab("Editor", mode == 0, isDarkMode) { mode = 0 }
                ModeTab("Preview", mode == 1, isDarkMode) { mode = 1 }
                ModeTab("Highlight", mode == 2, isDarkMode) { mode = 2 }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ---- Content ----
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.bg)
                    .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            ) {
                when (mode) {
                    0 -> CodeEditor(
                        text = text,
                        onChange = { setText(it) },
                        colors = colors,
                        lineCount = lineCount
                    )
                    1 -> HtmlPreview(html = text, dark = viewerDark, colors = colors)
                    else -> HighlightView(
                        text = text,
                        colors = colors,
                        folds = folds,
                        collapsed = collapsed,
                        onToggle = { start ->
                            collapsed = if (start in collapsed) collapsed - start else collapsed + start
                        }
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear everything?") },
            text = { Text("The HTML text in the viewer will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    setText("")
                    collapsed = emptySet()
                    confirmClear = false
                }) { Text("Clear", color = CrimsonPrimary) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    val bg = when {
        selected -> CrimsonPrimary.copy(alpha = 0.18f)
        isDarkMode -> Color.White.copy(alpha = 0.08f)
        else -> Color.Black.copy(alpha = 0.06f)
    }
    val fg = if (selected) CrimsonPrimary else if (isDarkMode) Color.White else Color(0xFF1A1A1A)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(17.dp))
        Spacer(modifier = Modifier.width(7.dp))
        Text(label, fontSize = 12.sp, color = fg, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun ModeTab(label: String, selected: Boolean, isDarkMode: Boolean, onClick: () -> Unit) {
    val fg = if (selected) CrimsonPrimary else (if (isDarkMode) Color.White else Color(0xFF1A1A1A)).copy(alpha = 0.7f)
    Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) CrimsonPrimary.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

// ---------------------------------------------------------------------------------------------
// Editor
// ---------------------------------------------------------------------------------------------

@Composable
private fun CodeEditor(
    text: String,
    onChange: (String) -> Unit,
    colors: HtmlColors,
    lineCount: Int
) {
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    val style = TextStyle(
        color = colors.text,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 20.sp
    )
    val transformation = remember(colors.dark) {
        VisualTransformation { t ->
            TransformedText(
                if (t.length <= 200_000) highlightHtml(t.text, colors) else t,
                OffsetMapping.Identity
            )
        }
    }
    val numbers = remember(lineCount) { (1..lineCount).joinToString("\n") }

    Row(modifier = Modifier.fillMaxSize().verticalScroll(vScroll)) {
        Text(
            text = numbers,
            style = style.copy(color = colors.muted, textAlign = TextAlign.End),
            modifier = Modifier
                .background(colors.gutter)
                .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
                .widthIn(min = 26.dp)
        )
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val minWidth = maxWidth
            Box(modifier = Modifier.horizontalScroll(hScroll)) {
                BasicTextField(
                    value = text,
                    onValueChange = onChange,
                    textStyle = style,
                    cursorBrush = SolidColor(CrimsonPrimary),
                    visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    modifier = Modifier
                        .widthIn(min = minWidth)
                        .heightIn(min = 420.dp)
                        .padding(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) {
                                Text("Put your HTML text here", style = style.copy(color = colors.muted))
                            }
                            inner()
                        }
                    }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Highlight (read-only, with folding)
// ---------------------------------------------------------------------------------------------

@Composable
private fun HighlightView(
    text: String,
    colors: HtmlColors,
    folds: Map<Int, Int>,
    collapsed: Set<Int>,
    onToggle: (Int) -> Unit
) {
    if (text.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nothing to show yet. Write, import or load a sample.", fontSize = 13.sp, color = colors.muted)
        }
        return
    }
    val lines = remember(text, colors.dark) {
        splitLines(if (text.length <= 200_000) highlightHtml(text, colors) else AnnotatedString(text))
    }
    val visible = remember(lines.size, folds, collapsed) {
        val out = ArrayList<Int>(lines.size)
        var i = 0
        while (i < lines.size) {
            out.add(i)
            val end = folds[i]
            i = if (end != null && i in collapsed) end + 1 else i + 1
        }
        out
    }
    val style = TextStyle(
        color = colors.text,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 20.sp
    )
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(visible, key = { it }) { idx ->
            val canFold = folds.containsKey(idx)
            val isCollapsed = idx in collapsed
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = (idx + 1).toString(),
                    style = style.copy(color = colors.muted, textAlign = TextAlign.End),
                    modifier = Modifier
                        .background(colors.gutter)
                        .widthIn(min = 40.dp)
                        .padding(horizontal = 6.dp)
                )
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .clickable(enabled = canFold) { onToggle(idx) },
                    contentAlignment = Alignment.Center
                ) {
                    if (canFold) {
                        Text(if (isCollapsed) "▸" else "▾", fontSize = 12.sp, color = colors.tag)
                    }
                }
                Text(
                    text = if (isCollapsed) {
                        buildAnnotatedString {
                            append(lines[idx])
                            withStyleMuted(colors.muted) { append("  …") }
                        }
                    } else lines[idx],
                    style = style,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp)
                )
            }
        }
    }
}

private inline fun AnnotatedString.Builder.withStyleMuted(color: Color, block: () -> Unit) {
    pushStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold))
    try {
        block()
    } finally {
        pop()
    }
}

// ---------------------------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------------------------

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlPreview(html: String, dark: Boolean, colors: HtmlColors) {
    if (html.isBlank()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nothing to preview yet. Write, import or load a sample.", fontSize = 13.sp, color = colors.muted)
        }
        return
    }
    val doc = remember(html, dark) { if (dark) injectDarkScheme(html) else html }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                webViewClient = WebViewClient()
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { wv ->
            if (wv.tag != doc) {
                wv.tag = doc
                wv.loadDataWithBaseURL("https://localhost/", doc, "text/html", "UTF-8", null)
            }
        },
        onRelease = { wv ->
            wv.stopLoading()
            wv.destroy()
        }
    )
}
