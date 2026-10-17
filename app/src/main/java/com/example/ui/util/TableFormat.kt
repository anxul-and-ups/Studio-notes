package com.example.ui.util

/**
 * Markdown-table (de)serialisation shared by the table editor and the inline preview.
 * Cells may contain "|" (stored as "\|") and never lose their position, even when empty.
 */
object TableFormat {

    /** Splits one "| a | b |" line into cells, honouring "\|" escapes and keeping empty cells. */
    private fun splitLine(line: String): List<String> {
        val t = line.trim().removePrefix("|").let { if (it.endsWith("|") && !it.endsWith("\\|")) it.dropLast(1) else it }
        val cells = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < t.length) {
            val c = t[i]
            if (c == '\\' && i + 1 < t.length && t[i + 1] == '|') { sb.append('|'); i += 2; continue }
            if (c == '|') { cells.add(sb.toString().trim()); sb.clear() } else sb.append(c)
            i++
        }
        cells.add(sb.toString().trim())
        return cells
    }

    private fun isSeparator(cells: List<String>) =
        cells.isNotEmpty() && cells.all { it.isNotEmpty() && it.all { ch -> ch == '-' || ch == ':' } }

    fun parse(raw: String): List<List<String>> {
        if (raw.isBlank()) return emptyList()
        val rows = mutableListOf<List<String>>()
        for (line in raw.lines()) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue
            when {
                trimmed.startsWith("|") -> {
                    val cells = splitLine(trimmed)
                    if (!isSeparator(cells)) rows.add(cells)
                }
                trimmed.contains("\t") -> rows.add(trimmed.split("\t").map { it.trim() })
                trimmed.contains(",") -> rows.add(trimmed.split(",").map { it.trim() })
            }
        }
        val width = rows.maxOfOrNull { it.size } ?: 0
        return rows.map { r -> r + List(width - r.size) { "" } }
    }

    fun serialize(rows: List<List<String>>): String {
        if (rows.isEmpty()) return ""
        val cols = rows.maxOf { it.size }.coerceAtLeast(1)
        fun cell(s: String) = s.replace("\n", " ").replace("|", "\\|").ifBlank { " " }
        val sb = StringBuilder()
        rows.forEachIndexed { idx, r ->
            sb.append("|")
            for (i in 0 until cols) sb.append(" ").append(cell(r.getOrElse(i) { "" })).append(" |")
            sb.append("\n")
            if (idx == 0) {
                sb.append("|")
                repeat(cols) { sb.append("---|") }
                sb.append("\n")
            }
        }
        return sb.toString().trimEnd()
    }

    fun isEffectivelyEmpty(rows: List<List<String>>) = rows.all { r -> r.all { it.isBlank() } }
}
