package com.example.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.preferences.AppPreferences
import com.example.ui.components.GlassBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.NeuIconButton
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.util.ThemeImageProcessor

@Composable
fun ThemeSettingsScreen(
    preferences: AppPreferences,
    isDarkMode: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val themeMode by preferences.themeMode.collectAsState()
    val panelLocked by preferences.themePanelLocked.collectAsState()
    val savedPath by preferences.bgImagePath.collectAsState()
    val dim by preferences.bgDim.collectAsState()

    val textColor = if (isDarkMode) Color.White else Color(0xFF111111)
    val subColor = if (isDarkMode) Color.White.copy(0.6f) else Color(0xFF666666)

    // Editing state for a newly picked photo
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var rotation by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offX by remember { mutableFloatStateOf(0f) }
    var offY by remember { mutableFloatStateOf(0f) }
    var blurAmt by remember { mutableFloatStateOf(0f) }
    var frameW by remember { mutableFloatStateOf(1f) }
    var frameH by remember { mutableFloatStateOf(1f) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val bmp = ThemeImageProcessor.loadBitmap(context, uri)
            if (bmp == null) {
                Toast.makeText(context, "Could not open this image", Toast.LENGTH_SHORT).show()
            } else {
                source = bmp; rotation = 0; zoom = 1f; offX = 0f; offY = 0f; blurAmt = 0f
            }
        }
    }

    val rotated = remember(source, rotation) { source?.let { ThemeImageProcessor.rotate(it, rotation) } }

    val cfg = LocalConfiguration.current
    val screenRatio = cfg.screenWidthDp.toFloat() / cfg.screenHeightDp.toFloat()

    GlassBackground(isDarkMode = isDarkMode) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeuIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    isDarkMode = isDarkMode,
                    size = 38.dp, iconSize = 18.dp,
                    tint = textColor,
                    onClick = onBack
                )
                Spacer(Modifier.width(12.dp))
                Text("Theme Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textColor)
            }

            Spacer(Modifier.height(18.dp))

            // ---- Theme mode ----
            Text("THEME MODE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CrimsonPrimary, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(
                    AppPreferences.THEME_DAY to "Day",
                    AppPreferences.THEME_DARK to "Dark",
                    AppPreferences.THEME_SYSTEM to "System"
                ).forEach { (value, label) ->
                    val selected = themeMode == value
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) CrimsonPrimary else (if (isDarkMode) Color(0x1AFFFFFF) else Color(0x0F000000)))
                            .border(1.dp, if (selected) CrimsonPrimary else (if (isDarkMode) Color(0x26FFFFFF) else Color(0x1F718096)), RoundedCornerShape(14.dp))
                            .clickable { preferences.setThemeMode(value) }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (selected) Color.White else textColor)
                    }
                }
            }
            Text(
                "System follows your phone's day / night setting automatically.",
                fontSize = 11.sp, color = subColor, modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(Modifier.height(22.dp))

            // ---- Photo background ----
            Text("BACKGROUND PHOTO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CrimsonPrimary, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))

            val src = rotated
            if (src == null) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    isDarkMode = isDarkMode,
                    onClick = { picker.launch("image/*") }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, null, tint = CrimsonPrimary, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                if (savedPath.isBlank()) "Choose a photo" else "Change photo",
                                fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textColor
                            )
                            Text(
                                "Shows behind notes, read mode, sidebar and file editor",
                                fontSize = 11.sp, color = subColor
                            )
                        }
                    }
                }

                if (savedPath.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Overlay strength (readability)", fontSize = 12.sp, color = subColor)
                    Slider(
                        value = dim,
                        onValueChange = { preferences.setBgDim(it) },
                        valueRange = 0f..0.8f,
                        colors = SliderDefaults.colors(thumbColor = CrimsonPrimary, activeTrackColor = CrimsonPrimary)
                    )
                    OutlinedButton(
                        onClick = {
                            ThemeImageProcessor.delete(context)
                            preferences.setBgImagePath("")
                            preferences.bumpBgImageVersion()
                            Toast.makeText(context, "Background removed", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.Delete, null, tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Remove photo", color = Color(0xFFFF5252))
                    }
                }
            } else {
                // Live preview in the exact shape of the phone screen. Drag = move, pinch = zoom.
                Text("Drag to move • Pinch to zoom", fontSize = 11.sp, color = subColor)
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .align(Alignment.CenterHorizontally)
                        .aspectRatio(screenRatio)
                        .clip(RoundedCornerShape(18.dp))
                        .border(2.dp, CrimsonPrimary, RoundedCornerShape(18.dp))
                        .onSizeChanged { frameW = it.width.toFloat(); frameH = it.height.toFloat() }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                zoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                                val maxX = frameW * (zoom - 1f) / 2f + frameW * 0.25f
                                val maxY = frameH * (zoom - 1f) / 2f + frameH * 0.25f
                                offX = (offX + pan.x).coerceIn(-maxX, maxX)
                                offY = (offY + pan.y).coerceIn(-maxY, maxY)
                            }
                        }
                ) {
                    Image(
                        bitmap = src.asImageBitmap(),
                        contentDescription = "Preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = offX; translationY = offY }
                            .blur((blurAmt * 20f).dp)
                    )
                    // Same overlay the app will use so the preview is honest
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            (if (isDarkMode) Color.Black else Color.White).copy(alpha = dim)
                        )
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    NeuIconButton(icon = Icons.Default.RotateLeft, contentDescription = "Rotate left", isDarkMode = isDarkMode, size = 44.dp, iconSize = 22.dp, tint = textColor,
                        onClick = { rotation = (rotation + 270) % 360; offX = 0f; offY = 0f })
                    NeuIconButton(icon = Icons.Default.RotateRight, contentDescription = "Rotate right", isDarkMode = isDarkMode, size = 44.dp, iconSize = 22.dp, tint = textColor,
                        onClick = { rotation = (rotation + 90) % 360; offX = 0f; offY = 0f })
                    NeuIconButton(icon = Icons.Default.ZoomOut, contentDescription = "Zoom out", isDarkMode = isDarkMode, size = 44.dp, iconSize = 22.dp, tint = textColor,
                        onClick = { zoom = (zoom - 0.25f).coerceAtLeast(1f) })
                    NeuIconButton(icon = Icons.Default.ZoomIn, contentDescription = "Zoom in", isDarkMode = isDarkMode, size = 44.dp, iconSize = 22.dp, tint = textColor,
                        onClick = { zoom = (zoom + 0.25f).coerceAtMost(5f) })
                    NeuIconButton(icon = Icons.Default.Refresh, contentDescription = "Reset", isDarkMode = isDarkMode, size = 44.dp, iconSize = 22.dp, tint = CrimsonPrimary,
                        onClick = { zoom = 1f; offX = 0f; offY = 0f; rotation = 0; blurAmt = 0f })
                }

                Spacer(Modifier.height(8.dp))
                Text("Blur  ${(blurAmt * 100).toInt()}%", fontSize = 12.sp, color = subColor)
                Slider(
                    value = blurAmt, onValueChange = { blurAmt = it }, valueRange = 0f..1f,
                    colors = SliderDefaults.colors(thumbColor = CrimsonPrimary, activeTrackColor = CrimsonPrimary)
                )
                Text("Overlay  ${(dim * 100).toInt()}%", fontSize = 12.sp, color = subColor)
                Slider(
                    value = dim, onValueChange = { preferences.setBgDim(it) }, valueRange = 0f..0.8f,
                    colors = SliderDefaults.colors(thumbColor = CrimsonPrimary, activeTrackColor = CrimsonPrimary)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { source = null }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(
                        onClick = {
                            val out = ThemeImageProcessor.render(src, frameW, frameH, zoom, offX, offY, blurAmt)
                            val path = ThemeImageProcessor.saveJpeg(context, out)
                            preferences.setBgImagePath(path)
                            preferences.bumpBgImageVersion()
                            source = null
                            Toast.makeText(context, "Background applied", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                        modifier = Modifier.weight(1f)
                    ) { Text("Apply") }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ---- Panel lock ----
            Text("PRIVACY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CrimsonPrimary, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), isDarkMode = isDarkMode) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Lock this panel with PIN", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = textColor)
                        Text("Theme Settings will ask for your PIN before opening", fontSize = 11.sp, color = subColor)
                    }
                    Switch(
                        checked = panelLocked,
                        onCheckedChange = {
                            if (it && !preferences.hasCustomPin.value) {
                                Toast.makeText(context, "Create a PIN first in Settings → Security Area", Toast.LENGTH_LONG).show()
                            } else preferences.setThemePanelLocked(it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CrimsonPrimary)
                    )
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}
