package com.example.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Colours source code for the dark code block of the note reader.
 * Detects HTML / CSS / JSON / Python-like / C-like code by itself, works without any library.
 */
object CodeHighlight {
    private data class Palette(
        val keyword: Color, val string: Color, val number: Color, val comment: Color,
        val type: Color, val function: Color, val attr: Color, val tag: Color,
        val doctype: Color, val base: Color
    )

    private fun palette(dark: Boolean) = if (dark) Palette(
        keyword = Color(0xFFD0A2F7), string = Color(0xFF9BE7A5), number = Color(0xFFFFA477),
        comment = Color(0xFFA0A7B4), type = Color(0xFF9CC2FF), function = Color(0xFF70E1D0),
        attr = Color(0xFFFFD166), tag = Color(0xFFFF7898), doctype = Color(0xFFC4B5FD),
        base = Color(0xFFE2E8F0)
    ) else Palette(
        keyword = Color(0xFF7B2CBF), string = Color(0xFF218739), number = Color(0xFFD6531F),
        comment = Color(0xFF737B87), type = Color(0xFF2864C5), function = Color(0xFF087F8C),
        attr = Color(0xFF9A6500), tag = Color(0xFFC2185B), doctype = Color(0xFF7046B8),
        base = Color(0xFF303846)
    )

    fun baseColor(isDarkMode: Boolean): Color = palette(isDarkMode).base

    private val keywords = setOf(
        "abstract", "as", "async", "await", "break", "by", "case", "catch", "class", "companion", "const", "constructor",
        "continue", "data", "def", "default", "del", "delete", "do", "elif", "else", "enum", "except", "export", "extends",
        "false", "False", "final", "finally", "for", "from", "fun", "function", "get", "global", "if", "implements",
        "import", "in", "init", "inline", "instanceof", "interface", "internal", "is", "lambda", "lateinit", "let", "lazy",
        "new", "None", "not", "null", "object", "of", "open", "or", "and", "override", "package", "pass", "private",
        "protected", "public", "raise", "return", "sealed", "self", "set", "static", "struct", "super", "suspend",
        "switch", "this", "throw", "throws", "true", "True", "try", "typeof", "undefined", "val", "var", "void",
        "when", "while", "with", "yield", "echo", "fi", "then", "done", "int", "string", "bool", "float", "double",
        "char", "long", "short", "byte", "important"
    )

    private val voidTags = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr")

    private enum class Lang { HTML, CSS, JSON, HASH, CLIKE }

    private fun detect(text: String): Lang {
        val t = text.trimStart()
        if (t.startsWith("<!") || t.startsWith("<?xml") || Regex("^<[a-zA-Z][^>]*>", RegexOption.MULTILINE).containsMatchIn(t.take(2000)) &&
            (t.contains("</") || t.contains("/>"))
        ) return Lang.HTML
        if ((t.startsWith("{") || t.startsWith("[")) && Regex("\"[^\"\\n]+\"\\s*:").containsMatchIn(t.take(2000))) return Lang.JSON
        val head = t.take(3000)
        val cssLike = Regex("(?m)^\\s*[.#@a-zA-Z][^{;\\n]*\\{\\s*$").containsMatchIn(head) &&
            Regex("(?m)^\\s*[a-z-]+\\s*:\\s*[^;\\n]+;").containsMatchIn(head) &&
            !Regex("\\b(fun|function|val|var|class|def|return)\\b").containsMatchIn(head)
        if (cssLike) return Lang.CSS
        val hashStyle = Regex("(?m)^\\s*(def |import |from \\w+ import|#!|elif |echo |[a-z_]+:\\s*$)").containsMatchIn(head) &&
            !head.contains("{")
        if (hashStyle) return Lang.HASH
        return Lang.CLIKE
    }

    /** Returns the text with colours. Very long text is returned in one colour so the screen stays fast. */
    fun highlight(text: String, isDarkMode: Boolean = true): AnnotatedString {
        if (text.isEmpty() || text.length > 150_000) return AnnotatedString(text)
        val b = AnnotatedString.Builder(text)
        val colors = palette(isDarkMode)
        when (detect(text)) {
            Lang.HTML -> html(text, b, colors)
            Lang.CSS -> code(text, 0, text.length, b, Lang.CSS, colors)
            Lang.JSON -> json(text, b, colors)
            Lang.HASH -> code(text, 0, text.length, b, Lang.HASH, colors)
            Lang.CLIKE -> code(text, 0, text.length, b, Lang.CLIKE, colors)
        }
        return b.toAnnotatedString()
    }

    private fun AnnotatedString.Builder.paint(color: Color, from: Int, to: Int, italic: Boolean = false, bold: Boolean = false) {
        if (to > from) {
            addStyle(
                SpanStyle(
                    color = color,
                    fontStyle = if (italic) FontStyle.Italic else null,
                    fontWeight = if (bold) FontWeight.Medium else null
                ),
                from, to
            )
        }
    }

    // ---------------------------------------------------------------- HTML
    private fun html(src: String, b: AnnotatedString.Builder, colors: Palette) {
        val n = src.length
        var i = 0
        while (i < n) {
            if (src[i] != '<') {
                i++
                continue
            }
            if (src.startsWith("<!--", i)) {
                val e = src.indexOf("-->", i + 4)
                val end = if (e < 0) n else e + 3
                b.paint(colors.comment, i, end, italic = true)
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
                i++
                continue
            }
            b.paint(if (isDoctype) colors.doctype else colors.tag, i, j)
            val tagName = src.substring(nameStart, j).lowercase()
            var k = j
            while (k < n && src[k] != '>') {
                val ch = src[k]
                if (ch == '"' || ch == '\'') {
                    var e = k + 1
                    while (e < n && src[e] != ch) e++
                    val end = minOf(e + 1, n)
                    b.paint(if (isDoctype) colors.doctype else colors.string, k, end)
                    k = end
                } else if (ch.isLetter()) {
                    var e = k
                    while (e < n && (src[e].isLetterOrDigit() || src[e] == '-' || src[e] == ':' || src[e] == '_' || src[e] == '.')) e++
                    b.paint(if (isDoctype) colors.doctype else colors.attr, k, e)
                    k = e
                } else {
                    k++
                }
            }
            val close = if (k < n) k + 1 else n
            b.paint(if (isDoctype) colors.doctype else colors.tag, k, close)
            i = close
            if (!closing && (tagName == "script" || tagName == "style")) {
                val e = src.indexOf("</$tagName", i, ignoreCase = true)
                val bodyEnd = if (e < 0) n else e
                code(src, i, bodyEnd, b, if (tagName == "style") Lang.CSS else Lang.CLIKE, colors)
                i = bodyEnd
            }
        }
    }

    // ---------------------------------------------------------------- JSON
    private fun json(src: String, b: AnnotatedString.Builder, colors: Palette) {
        val n = src.length
        var i = 0
        while (i < n) {
            val c = src[i]
            if (c == '"') {
                var j = i + 1
                while (j < n) {
                    if (src[j] == '\\') {
                        j += 2
                        continue
                    }
                    if (src[j] == '"') break
                    j++
                }
                val end = minOf(j + 1, n)
                var k = end
                while (k < n && (src[k] == ' ' || src[k] == '\t')) k++
                val isKey = k < n && src[k] == ':'
                b.paint(if (isKey) colors.attr else colors.string, i, end)
                i = end
                continue
            }
            if (c.isDigit() || (c == '-' && i + 1 < n && src[i + 1].isDigit())) {
                var j = i + 1
                while (j < n && (src[j].isDigit() || src[j] == '.' || src[j] == 'e' || src[j] == 'E' || src[j] == '+' || src[j] == '-')) j++
                b.paint(colors.number, i, j)
                i = j
                continue
            }
            if (c.isLetter()) {
                var j = i
                while (j < n && src[j].isLetter()) j++
                val w = src.substring(i, j)
                if (w == "true" || w == "false" || w == "null") b.paint(colors.keyword, i, j)
                i = j
                continue
            }
            i++
        }
    }

    // ------------------------------------------------- C-like / CSS / hash languages
    private fun code(src: String, from: Int, to: Int, b: AnnotatedString.Builder, lang: Lang, colors: Palette) {
        val n = minOf(to, src.length)
        var i = from
        val cLike = lang != Lang.HASH
        var depth = 0 // braces depth, used for CSS property detection
        while (i < n) {
            val c = src[i]
            if (c == '{') depth++
            if (c == '}') depth = maxOf(0, depth - 1)

            if (cLike && c == '/' && i + 1 < n && src[i + 1] == '*') {
                val e = src.indexOf("*/", i + 2)
                val end = if (e < 0 || e + 2 > n) n else e + 2
                b.paint(colors.comment, i, end, italic = true)
                i = end
                continue
            }
            if (cLike && lang != Lang.CSS && c == '/' && i + 1 < n && src[i + 1] == '/') {
                var e = src.indexOf('\n', i)
                if (e < 0 || e > n) e = n
                b.paint(colors.comment, i, e, italic = true)
                i = e
                continue
            }
            if (lang == Lang.HASH && c == '#') {
                var e = src.indexOf('\n', i)
                if (e < 0 || e > n) e = n
                b.paint(colors.comment, i, e, italic = true)
                i = e
                continue
            }
            if (c == '"' || c == '\'' || (c == '`' && lang != Lang.CSS)) {
                var j = i + 1
                while (j < n) {
                    if (src[j] == '\\') {
                        j += 2
                        continue
                    }
                    if (src[j] == c || src[j] == '\n' && c != '`') break
                    j++
                }
                val end = minOf(j + 1, n)
                b.paint(colors.string, i, end)
                i = end
                continue
            }
            if (c == '#' && lang == Lang.CSS) {
                var j = i + 1
                while (j < n && (src[j].isDigit() || src[j] in 'a'..'f' || src[j] in 'A'..'F')) j++
                if (j - i in 4..9) {
                    b.paint(colors.number, i, j)
                    i = j
                    continue
                }
            }
            if (c == '@' && i + 1 < n && src[i + 1].isLetter()) {
                var j = i + 1
                while (j < n && (src[j].isLetterOrDigit() || src[j] == '_' || src[j] == '-')) j++
                b.paint(colors.attr, i, j)
                i = j
                continue
            }
            if (c.isDigit() || (c == '.' && i + 1 < n && src[i + 1].isDigit() && lang == Lang.CSS)) {
                var j = i
                while (j < n && (src[j].isLetterOrDigit() || src[j] == '.' || src[j] == '%' || src[j] == '_')) j++
                b.paint(colors.number, i, j)
                i = j
                continue
            }
            if (c.isLetter() || c == '_' || (c == '-' && lang == Lang.CSS && i + 1 < n && src[i + 1].isLetter())) {
                var j = i + 1
                while (j < n && (src[j].isLetterOrDigit() || src[j] == '_' || (lang == Lang.CSS && src[j] == '-'))) j++
                val word = src.substring(i, j)
                var k = j
                while (k < n && (src[k] == ' ' || src[k] == '\t')) k++
                val next = if (k < n) src[k] else ' '
                when {
                    lang == Lang.CSS && depth > 0 && next == ':' -> b.paint(colors.attr, i, j)
                    lang == Lang.CSS && word.startsWith("-") -> b.paint(colors.attr, i, j)
                    word in keywords -> b.paint(colors.keyword, i, j, bold = true)
                    next == '(' -> b.paint(colors.function, i, j)
                    word[0].isUpperCase() && lang != Lang.CSS -> b.paint(colors.type, i, j)
                    lang == Lang.CSS && depth == 0 -> b.paint(colors.type, i, j)
                }
                i = j
                continue
            }
            i++
        }
    }
}
