package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.NeuIconButton
import com.example.ui.theme.CrimsonPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun PdfViewerScreen(
    pdfUri: Uri?,
    pdfFile: File? = null,
    isDarkMode: Boolean = true,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(0) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    var pfd: ParcelFileDescriptor? by remember { mutableStateOf(null) }
    var renderer: PdfRenderer? by remember { mutableStateOf(null) }

    LaunchedEffect(pdfUri, pdfFile) {
        isLoading = true
        errorMessage = null
        withContext(Dispatchers.IO) {
            try {
                val fileDescriptor = when {
                    pdfFile != null -> {
                        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    }
                    pdfUri != null -> {
                        if (pdfUri.scheme == "content") {
                            context.contentResolver.openFileDescriptor(pdfUri, "r")
                        } else {
                            val tempFile = File(context.cacheUriDir(), "view_temp.pdf")
                            context.contentResolver.openInputStream(pdfUri)?.use { input ->
                                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                            }
                            ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                        }
                    }
                    else -> null
                }

                if (fileDescriptor != null) {
                    pfd = fileDescriptor
                    val pdfRenderer = PdfRenderer(fileDescriptor)
                    renderer = pdfRenderer
                    pageCount = pdfRenderer.pageCount
                    if (pageCount > 0) {
                        renderPage(pdfRenderer, 0) { bmp ->
                            currentBitmap = bmp
                            isLoading = false
                        }
                    } else {
                        errorMessage = "PDF contains no pages."
                        isLoading = false
                    }
                } else {
                    errorMessage = "Unable to open PDF file descriptor."
                    isLoading = false
                }
            } catch (e: Exception) {
                errorMessage = "Error reading PDF: ${e.localizedMessage}"
                isLoading = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                renderer?.close()
                pfd?.close()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }

    fun loadPage(index: Int) {
        val r = renderer ?: return
        if (index in 0 until pageCount) {
            currentPageIndex = index
            isLoading = true
            scale = 1f
            offset = Offset.Zero
            renderPage(r, index) { bmp ->
                currentBitmap = bmp
                isLoading = false
            }
        }
    }

    GlassBackground(isDarkMode = isDarkMode) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NeuIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = "Close PDF",
                    isDarkMode = isDarkMode,
                    size = 38.dp,
                    iconSize = 18.dp,
                    tint = if (isDarkMode) Color.White else Color.Black,
                    onClick = onClose
                )

                Text(
                    text = if (pageCount > 0) "Page ${currentPageIndex + 1} of $pageCount" else "AU PDF Viewer",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = CrimsonPrimary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeuIconButton(
                        icon = Icons.Default.ZoomIn,
                        contentDescription = "Zoom in",
                        isDarkMode = isDarkMode,
                        size = 36.dp,
                        iconSize = 18.dp,
                        tint = if (isDarkMode) Color.White else Color.Black,
                        onClick = {
                            scale = (scale + 0.25f).coerceAtMost(3.5f)
                        }
                    )

                    NeuIconButton(
                        icon = Icons.Default.ZoomOut,
                        contentDescription = "Zoom out",
                        isDarkMode = isDarkMode,
                        size = 36.dp,
                        iconSize = 18.dp,
                        tint = if (isDarkMode) Color.White else Color.Black,
                        onClick = {
                            scale = (scale - 0.25f).coerceAtLeast(1f)
                            if (scale == 1f) offset = Offset.Zero
                        }
                    )

                    NeuIconButton(
                        icon = Icons.Default.Share,
                        contentDescription = "Share PDF",
                        isDarkMode = isDarkMode,
                        size = 36.dp,
                        iconSize = 18.dp,
                        tint = CrimsonPrimary,
                        onClick = {
                            val uriToShare = pdfUri ?: pdfFile?.let { Uri.fromFile(it) }
                            if (uriToShare != null) {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uriToShare)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
                            }
                        }
                    )
                }
            }

            // PDF Content Viewport with Zoom and Pan
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.DarkGray.copy(alpha = 0.2f))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            if (scale > 1f) {
                                offset += pan
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = CrimsonPrimary)
                } else if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "Error",
                        color = Color(0xFFFF5252),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(24.dp)
                    )
                } else {
                    currentBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "PDF Page",
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                )
                        )
                    }
                }
            }

            // Bottom Navigation Controls
            if (pageCount > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassCard(
                        shape = RoundedCornerShape(12.dp),
                        isDarkMode = isDarkMode,
                        elevation = if (currentPageIndex > 0) 3.dp else 0.dp,
                        onClick = { if (currentPageIndex > 0) loadPage(currentPageIndex - 1) }
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (currentPageIndex > 0) (if (isDarkMode) Color.White else Color.Black) else Color.Gray
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Previous",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (currentPageIndex > 0) (if (isDarkMode) Color.White else Color.Black) else Color.Gray
                            )
                        }
                    }

                    Text(
                        text = "${currentPageIndex + 1} / $pageCount",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDarkMode) Color.White.copy(0.8f) else Color.DarkGray
                    )

                    GlassCard(
                        shape = RoundedCornerShape(12.dp),
                        isDarkMode = isDarkMode,
                        elevation = if (currentPageIndex < pageCount - 1) 3.dp else 0.dp,
                        onClick = { if (currentPageIndex < pageCount - 1) loadPage(currentPageIndex + 1) }
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Next",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (currentPageIndex < pageCount - 1) (if (isDarkMode) Color.White else Color.Black) else Color.Gray
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (currentPageIndex < pageCount - 1) (if (isDarkMode) Color.White else Color.Black) else Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun renderPage(renderer: PdfRenderer, pageIndex: Int, onResult: (Bitmap) -> Unit) {
    try {
        val page = renderer.openPage(pageIndex)
        val width = page.width * 2
        val height = page.height * 2
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        onResult(bitmap)
    } catch (e: Exception) {
        // Fallback empty bitmap
    }
}

private fun Context.cacheUriDir(): File {
    val dir = File(cacheDir, "pdf_cache")
    if (!dir.exists()) dir.mkdirs()
    return dir
}
