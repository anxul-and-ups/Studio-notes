package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CrimsonPrimary

@Composable
fun CodeBlockView(
    code: String,
    language: String = "Code",
    isDarkMode: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val cleanCode = code.trim()

    val syntaxColoredText = buildAnnotatedString {
        append(cleanCode)
        val fullText = cleanCode

        // Keywords highlighting
        val keywords = listOf(
            "val", "var", "fun", "class", "import", "package", "def", "return", "if", "else",
            "for", "while", "in", "is", "null", "true", "false", "async", "await", "from"
        )
        for (kw in keywords) {
            val regex = Regex("\\b$kw\\b")
            regex.findAll(fullText).forEach { match ->
                addStyle(
                    SpanStyle(color = Color(0xFFFF79C6), fontWeight = FontWeight.Bold),
                    match.range.first,
                    match.range.last + 1
                )
            }
        }

        // Strings highlighting
        val stringRegex = Regex("\".*?\"|'.*?'")
        stringRegex.findAll(fullText).forEach { match ->
            addStyle(
                SpanStyle(color = Color(0xFFF1FA8C)),
                match.range.first,
                match.range.last + 1
            )
        }

        // Numbers highlighting
        val numRegex = Regex("\\b\\d+(\\.\\d+)?\\b")
        numRegex.findAll(fullText).forEach { match ->
            addStyle(
                SpanStyle(color = Color(0xFFBD93F9)),
                match.range.first,
                match.range.last + 1
            )
        }

        // Comments
        val commentRegex = Regex("#.*|//.*")
        commentRegex.findAll(fullText).forEach { match ->
            addStyle(
                SpanStyle(color = Color(0xFF6272A4)),
                match.range.first,
                match.range.last + 1
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isDarkMode) Color(0xF00A0A10) else Color(0xF21C1C24))
            .border(1.dp, Color(0x33FF2D55), RoundedCornerShape(14.dp))
    ) {
        // Header bar: Language Tag + Copy Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x33FFFFFF))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    tint = CrimsonPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = language.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE2E8F0),
                    letterSpacing = 1.sp
                )
            }

            FilledTonalButton(
                onClick = {
                    clipboard.setText(AnnotatedString(cleanCode))
                    Toast.makeText(context, "Code copied to clipboard!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = CrimsonPrimary.copy(alpha = 0.25f),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(vertical = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy code",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Code content with horizontal scrolling and native text selection
        SelectionContainer {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                Text(
                    text = syntaxColoredText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = Color(0xFFF8F8F2)
                )
            }
        }
    }
}
