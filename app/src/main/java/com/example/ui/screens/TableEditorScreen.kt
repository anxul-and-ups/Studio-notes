package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassCard
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.util.TableFormat
import kotlinx.coroutines.delay

private val CELL_WIDTH = 132.dp
private val HANDLE_SIZE = 34.dp

/**
 * Full-screen table editor.
 * - Cells are real snapshot state, so typing works immediately.
 * - Keyboard "Next" jumps cell to cell; Next on the very last cell adds a new row.
 * - Row / column handles give insert, duplicate and delete actions.
 * - The whole table can be deleted from the ⋮ menu.
 */
@Composable
fun TableEditorScreen(
    initialTableData: String,
    isDarkMode: Boolean,
    onBack: () -> Unit,
    onSaveTable: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current

    val initialRows = remember { TableFormat.parse(initialTableData) }
    val rows = remember {
        mutableStateListOf<SnapshotStateList<String>>().apply {
            if (initialRows.isNotEmpty() && initialRows[0].isNotEmpty()) {
                initialRows.forEach { add(it.toMutableStateList()) }
            } else {
                repeat(3) { add(mutableStateListOf("", "")) }
            }
        }
    }
    val initialSerialized = remember { TableFormat.serialize(rows.map { it.toList() }) }

    var menuRow by remember { mutableStateOf(-1) }
    var menuCol by remember { mutableStateOf(-1) }
    var showMore by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var showDeleteTable by remember { mutableStateOf(false) }
    var pendingFocus by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var focusedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }

    val textColor = if (isDarkMode) Color.White else Color(0xFF111111)
    val subColor = if (isDarkMode) Color.White.copy(0.55f) else Color(0xFF777777)
    val bgColor = if (isDarkMode) Color(0xFF0E1015) else Color(0xFFF6F8FB)
    val gridBorder = if (isDarkMode) Color.White.copy(alpha = 0.22f) else Color(0xFFD0D5DD)
    val headerBg = if (isDarkMode) Color(0xFF1E222B) else Color(0xFFE9EDF3)
    val handleBg = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)

    fun cols() = rows.firstOrNull()?.size ?: 0
    fun currentTable() = rows.map { it.toList() }
    fun isDirty() = TableFormat.serialize(currentTable()) != initialSerialized

    fun addRow(at: Int = rows.size) {
        rows.add(at.coerceIn(0, rows.size), MutableList(cols().coerceAtLeast(1)) { "" }.toMutableStateList())
        pendingFocus = at.coerceIn(0, rows.size - 1) to 0
    }
    fun addColumn(at: Int = cols()) {
        val idx = at.coerceIn(0, cols())
        rows.forEach { it.add(idx, "") }
        pendingFocus = 0 to idx
    }
    fun save() {
        val table = currentTable()
        onSaveTable(if (TableFormat.isEffectivelyEmpty(table)) "" else TableFormat.serialize(table))
    }
    fun tryBack() { if (isDirty()) showDiscard = true else onBack() }

    BackHandler { tryBack() }

    LaunchedEffect(pendingFocus) {
        val target = pendingFocus ?: return@LaunchedEffect
        delay(80) // let the new cell compose first
        requesters["${target.first},${target.second}"]?.let {
            try { it.requestFocus() } catch (_: Exception) {}
        }
        pendingFocus = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ---- Top bar ----
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { tryBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = textColor)
                    }
                    Column {
                        Text("Table", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Text("${rows.size} rows × ${cols()} columns", fontSize = 11.sp, color = subColor)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(onClick = { showMore = true }) { Icon(Icons.Default.MoreVert, "More", tint = textColor) }
                        DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                            DropdownMenuItem(
                                text = { Text("Delete table", color = Color.Red) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) },
                                onClick = { showMore = false; showDeleteTable = true }
                            )
                        }
                    }
                    IconButton(onClick = { focusManager.clearFocus(); save() }) {
                        Icon(Icons.Default.Check, "Save table", tint = CrimsonPrimary, modifier = Modifier.size(28.dp))
                    }
                }
            }

            // ---- Grid ----
            Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp)) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                ) {
                    // Column handles
                    Row(modifier = Modifier.padding(start = HANDLE_SIZE + 4.dp, bottom = 4.dp)) {
                        for (c in 0 until cols()) {
                            Box(modifier = Modifier.width(CELL_WIDTH), contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (focusedCell?.second == c || menuCol == c) CrimsonPrimary.copy(0.22f) else handleBg)
                                        .clickable { menuCol = c }
                                        .padding(horizontal = 14.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        ('A' + (c % 26)).toString() + if (c >= 26) "${c / 26}" else "",
                                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                        color = if (focusedCell?.second == c || menuCol == c) CrimsonPrimary else subColor
                                    )
                                }
                                DropdownMenu(expanded = menuCol == c, onDismissRequest = { menuCol = -1 }) {
                                    DropdownMenuItem(
                                        text = { Text("Insert column left") },
                                        leadingIcon = { Icon(Icons.Default.KeyboardArrowLeft, null) },
                                        onClick = { menuCol = -1; addColumn(c) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Insert column right") },
                                        leadingIcon = { Icon(Icons.Default.KeyboardArrowRight, null) },
                                        onClick = { menuCol = -1; addColumn(c + 1) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete column", color = Color.Red) },
                                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) },
                                        onClick = {
                                            if (cols() > 1) rows.forEach { it.removeAt(c) }
                                            menuCol = -1
                                        }
                                    )
                                }
                            }
                        }
                    }

                    rows.forEachIndexed { r, row ->
                        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                            // Row handle
                            Box(
                                modifier = Modifier.width(HANDLE_SIZE).fillMaxHeight().padding(end = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (focusedCell?.first == r || menuRow == r) CrimsonPrimary.copy(0.22f) else handleBg)
                                        .clickable { menuRow = r }
                                        .padding(horizontal = 8.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        "${r + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                        color = if (focusedCell?.first == r || menuRow == r) CrimsonPrimary else subColor
                                    )
                                }
                                DropdownMenu(expanded = menuRow == r, onDismissRequest = { menuRow = -1 }) {
                                    DropdownMenuItem(
                                        text = { Text("Insert row above") },
                                        leadingIcon = { Icon(Icons.Default.ArrowUpward, null) },
                                        onClick = { menuRow = -1; addRow(r) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Insert row below") },
                                        leadingIcon = { Icon(Icons.Default.ArrowDownward, null) },
                                        onClick = { menuRow = -1; addRow(r + 1) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Duplicate row") },
                                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                                        onClick = { menuRow = -1; rows.add(r + 1, row.toList().toMutableStateList()) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete row", color = Color.Red) },
                                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) },
                                        onClick = {
                                            if (rows.size > 1) rows.removeAt(r)
                                            menuRow = -1
                                        }
                                    )
                                }
                            }

                            row.forEachIndexed { c, cellValue ->
                                val isLast = r == rows.lastIndex && c == row.lastIndex
                                val requester = requesters.getOrPut("$r,$c") { FocusRequester() }
                                Box(
                                    modifier = Modifier
                                        .width(CELL_WIDTH)
                                        .fillMaxHeight()
                                        .heightIn(min = 46.dp)
                                        .background(if (r == 0) headerBg else Color.Transparent)
                                        .border(
                                            if (focusedCell == (r to c)) 1.6.dp else 0.7.dp,
                                            if (focusedCell == (r to c)) CrimsonPrimary else gridBorder
                                        )
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    BasicTextField(
                                        value = cellValue,
                                        onValueChange = { row[c] = it },
                                        textStyle = TextStyle(
                                            color = textColor,
                                            fontSize = if (r == 0) 14.sp else 13.5.sp,
                                            fontWeight = if (r == 0) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        cursorBrush = SolidColor(CrimsonPrimary),
                                        keyboardOptions = KeyboardOptions(
                                            capitalization = KeyboardCapitalization.Sentences,
                                            imeAction = ImeAction.Next
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onNext = {
                                                if (isLast) addRow() else focusManager.moveFocus(FocusDirection.Next)
                                            }
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(requester)
                                            .onFocusChanged { st ->
                                                if (st.isFocused) focusedCell = r to c
                                                else if (focusedCell == (r to c)) focusedCell = null
                                            }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(120.dp))
                }
            }

            // ---- Bottom quick actions ----
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickAction("Row", Modifier.weight(1f), isDarkMode, textColor) { addRow() }
                QuickAction("Column", Modifier.weight(1f), isDarkMode, textColor) { addColumn() }
            }
        }
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("Save table changes?") },
            confirmButton = {
                TextButton(onClick = { showDiscard = false; save() }) { Text("Save", color = CrimsonPrimary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showDiscard = false }) { Text("Keep editing") }
                    TextButton(onClick = { showDiscard = false; onBack() }) { Text("Discard", color = Color.Red) }
                }
            }
        )
    }

    if (showDeleteTable) {
        AlertDialog(
            onDismissRequest = { showDeleteTable = false },
            title = { Text("Delete this table?") },
            text = { Text("The table will be removed from your note.") },
            confirmButton = {
                TextButton(onClick = { showDeleteTable = false; onSaveTable("") }) {
                    Text("Delete", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteTable = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun QuickAction(label: String, modifier: Modifier, isDarkMode: Boolean, textColor: Color, onClick: () -> Unit) {
    GlassCard(modifier = modifier, shape = RoundedCornerShape(14.dp), isDarkMode = isDarkMode, elevation = 3.dp, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Add, null, tint = CrimsonPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textColor)
        }
    }
}
