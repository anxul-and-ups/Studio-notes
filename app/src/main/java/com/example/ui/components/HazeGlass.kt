package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeSource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale

/**
 * ═══════════════════════════════════════════════════════════
 * HAZE GLASS SYSTEM — Real-time iOS-style Glassmorphism
 * ═══════════════════════════════════════════════════════════
 *
 * Usage:
 *  1. Create a HazeState in your screen:
 *     val hazeState = remember { HazeState() }
 *
 *  2. Wrap your BACKGROUND content with HazeBackground:
 *     HazeBackground(hazeState, isDarkMode) { ... }
 *
 *  3. Use HazeGlassCard for glass surfaces:
 *     HazeGlassCard(hazeState, isDarkMode) { ... }
 */

/**
 * Kill switch for the real Haze blur.
 * false = crash-safe "frosted" look (translucent gradient + edge highlight, no
 * RenderEffect). Flip to true once Haze is confirmed stable on the device.
 */
const val ENABLE_HAZE_BLUR = false

/**
 * Glass surface — real blur of whatever is behind it.
 */
@Composable
fun HazeGlassCard(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    isDarkMode: Boolean = true,
    strong: Boolean = false,
    borderWidth: Dp = 1.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    val pressScale by animateFloatAsState(
        targetValue = if (onClick != null) 1f else 1f,
        animationSpec = tween(150),
        label = "glass_press"
    )

    val tintColor = if (isDarkMode) {
        if (strong) Color(0x66202020) else Color(0x33FFFFFF)
    } else {
        if (strong) Color(0x55FFFFFF) else Color(0x33FFFFFF)
    }

    val borderBrush = if (isDarkMode) {
        Brush.linearGradient(
            colors = listOf(
                Color(0x33FFFFFF),
                Color(0x0AFFFFFF),
                Color(0x1AFFFFFF)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0x88FFFFFF),
                Color(0x22FFFFFF),
                Color(0x44FFFFFF)
            )
        )
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    } else Modifier

    val fallbackBrush = if (isDarkMode) {
        Brush.verticalGradient(
            listOf(
                if (strong) Color(0xCC202632) else Color(0x33FFFFFF),
                if (strong) Color(0xCC161B25) else Color(0x14FFFFFF)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                if (strong) Color(0xE6FFFFFF) else Color(0x99FFFFFF),
                if (strong) Color(0xCCF3F6FA) else Color(0x66FFFFFF)
            )
        )
    }

    Box(
        modifier = modifier
            .scale(pressScale)
            .clip(shape)
            .then(
                if (ENABLE_HAZE_BLUR) {
                    Modifier.hazeChild(
                        state = hazeState,
                        style = HazeStyle(
                            blurRadius = if (strong) 40.dp else 24.dp,
                            tint = HazeTint(tintColor),
                            noiseFactor = 0.05f
                        )
                    )
                } else {
                    Modifier.background(fallbackBrush)
                }
            )
            .border(borderWidth, borderBrush, shape)
            .then(clickableModifier)
    ) {
        content()
    }
}

/**
 * Background wrapper — marks content as the "source" for Haze blur.
 * Put this AROUND your background image/gradient.
 */
@Composable
fun HazeBackground(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (ENABLE_HAZE_BLUR) Modifier.hazeSource(state = hazeState) else Modifier)
    ) {
        content()
    }
}

/**
 * Simple glass pill/chip — for small rounded elements.
 */
@Composable
fun HazeGlassChip(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    isDarkMode: Boolean = true,
    shape: Shape = RoundedCornerShape(50),
    content: @Composable BoxScope.() -> Unit
) {
    val tintColor = if (isDarkMode) Color(0x33FFFFFF) else Color(0x44FFFFFF)

    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (ENABLE_HAZE_BLUR) {
                    Modifier.hazeChild(
                        state = hazeState,
                        style = HazeStyle(
                            blurRadius = 16.dp,
                            tint = HazeTint(tintColor)
                        )
                    )
                } else {
                    Modifier.background(tintColor)
                }
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(Color(0x33FFFFFF), Color(0x11FFFFFF))
                ),
                shape = shape
            )
    ) {
        content()
    }
}
