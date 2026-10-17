package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale

/**
 * User-chosen wallpaper (Theme Settings). null = no custom photo, the default
 * neumorphic background is used. [dim] is the 0..0.8 overlay strength.
 */
data class AppBackdropState(val image: ImageBitmap? = null, val dim: Float = 0.35f)

val LocalAppBackdrop = compositionLocalOf { AppBackdropState() }

/** Draws the custom photo + readability overlay. Draws nothing when no photo is set. */
@Composable
fun BackdropImage(isDarkMode: Boolean, modifier: Modifier = Modifier.fillMaxSize()) {
    val backdrop = LocalAppBackdrop.current
    val img = backdrop.image ?: return
    Box(modifier = modifier) {
        Image(
            bitmap = img,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Dark overlay in dark mode, light overlay in day mode: keeps text readable on any photo.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background((if (isDarkMode) Color.Black else Color.White).copy(alpha = backdrop.dim.coerceIn(0f, 0.85f)))
        )
    }
}
