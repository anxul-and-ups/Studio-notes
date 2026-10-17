package com.example.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.util.TableFormat

/**
 * Inline table shown inside the write-mode note. It is a clean preview only —
 * there are no add/delete row or column controls here any more. Tapping the table
 * opens the full Table Editor where everything (rows, columns, delete table) lives.
 */
@Composable
fun InteractiveTableView(
    tableData: String,
    isEditable: Boolean = true,
    isDarkMode: Boolean = true,
    onTableChange: (String) -> Unit = {},
    onDeleteTable: () -> Unit = {},
    onOpenEditor: () -> Unit = {}
) {
    val rows = remember(tableData) { TableFormat.parse(tableData) }
    if (rows.isEmpty()) return

    val border = Color(0x44FF2D55)
    val textColor = if (isDarkMode) Color.White else Color(0xFF111111)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isDarkMode) Color(0x331E0510) else Color(0x14000000))
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable(onClick = onOpenEditor)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Table", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CrimsonPrimary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Edit, null, tint = CrimsonPrimary, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text("Tap to edit", fontSize = 11.sp, color = CrimsonPrimary)
            }
        }
        Box(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Column {
                rows.forEachIndexed { r, cells ->
                    Row(
                        modifier = Modifier
                            .height(IntrinsicSize.Min)
                            .background(
                                when {
                                    r == 0 -> if (isDarkMode) Color(0x4DFF2D55) else Color(0x26FF2D55)
                                    r % 2 == 0 -> if (isDarkMode) Color(0x14FFFFFF) else Color(0x0A000000)
                                    else -> Color.Transparent
                                }
                            )
                    ) {
                        cells.forEach { cell ->
                            Box(
                                modifier = Modifier
                                    .width(112.dp)
                                    .fillMaxHeight()
                                    .border(0.5.dp, border)
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = cell,
                                    color = textColor,
                                    fontSize = 12.sp,
                                    fontWeight = if (r == 0) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
