package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CrimsonPrimary

@Composable
fun InteractiveChecklistView(
    content: String,
    isDarkMode: Boolean = true,
    onContentChange: (String) -> Unit
) {
    val lines = content.lines()
    val hasChecklist = lines.any { it.trimStart().startsWith("[ ]") || it.trimStart().startsWith("[x]", ignoreCase = true) }

    if (!hasChecklist) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        lines.forEachIndexed { index, line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("[ ]") || trimmed.startsWith("[x]", ignoreCase = true)) {
                val isChecked = trimmed.startsWith("[x]", ignoreCase = true)
                val taskText = trimmed.substring(3).trimStart()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newPrefix = if (isChecked) "[ ] " else "[x] "
                            val updatedLines = lines.toMutableList()
                            val indent = line.substring(0, line.indexOf('['))
                            updatedLines[index] = "$indent$newPrefix$taskText"
                            onContentChange(updatedLines.joinToString("\n"))
                        }
                        .padding(vertical = 4.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = if (isChecked) "Checked" else "Unchecked",
                        tint = if (isChecked) CrimsonPrimary else (if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color.Gray),
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = taskText.ifBlank { "Checklist Task" },
                        fontSize = 14.sp,
                        fontWeight = if (isChecked) FontWeight.Normal else FontWeight.Medium,
                        color = if (isChecked) (if (isDarkMode) Color.White.copy(0.5f) else Color.Gray) else (if (isDarkMode) Color.White else Color.Black),
                        textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None
                    )
                }
            }
        }
    }
}
