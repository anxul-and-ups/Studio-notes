package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.NeuDarkBg
import com.example.ui.theme.NeuDarkBorder
import com.example.ui.theme.NeuDarkHighlightBorder
import com.example.ui.theme.NeuDarkInset
import com.example.ui.theme.NeuDarkShadow
import com.example.ui.theme.NeuDarkSurface
import com.example.ui.theme.NeuDarkSurfaceDark
import com.example.ui.theme.NeuDarkSurfaceLight
import com.example.ui.theme.NeuLightBg
import com.example.ui.theme.NeuLightBorder
import com.example.ui.theme.NeuLightHighlightBorder
import com.example.ui.theme.NeuLightInset
import com.example.ui.theme.NeuLightShadow
import com.example.ui.theme.NeuLightSurface
import com.example.ui.theme.NeuLightSurfaceDark
import com.example.ui.theme.NeuLightSurfaceLight

/**
 * Neumorphic Card Component (replaces GlassCard).
 * Emulates tactile physical extrusions (convex) or debossed wells (concave/inset)
 * matching the surrounding background material.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    isDarkMode: Boolean = true,
    borderWidth: Dp = 1.dp,
    strong: Boolean = false,
    isInset: Boolean = false,
    elevation: Dp = 6.dp,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Smooth press feedback: tactile dip when pressed
    val currentElevation by animateDpAsState(
        targetValue = if (isInset || (onClick != null && isPressed)) 1.dp else elevation,
        animationSpec = tween(150),
        label = "neu_card_elevation"
    )

    // Neumorphic Dual Shadow Colors
    val shadowSpotColor = if (isDarkMode) NeuDarkShadow else NeuLightShadow
    val shadowAmbientColor = if (isDarkMode) NeuDarkShadow.copy(alpha = 0.5f) else NeuLightShadow.copy(alpha = 0.4f)

    // Surface convex vs concave fill gradient
    val surfaceBrush = if (isDarkMode) {
        if (isInset || (onClick != null && isPressed)) {
            // Concave / Debossed (darker on top-left, slightly lighter bottom-right)
            Brush.linearGradient(
                colors = listOf(
                    NeuDarkInset,
                    NeuDarkSurfaceDark,
                    NeuDarkSurface
                )
            )
        } else if (strong) {
            // Strong modal / dialog card
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF2A303D),
                    NeuDarkSurface,
                    Color(0xFF191B22)
                )
            )
        } else {
            // Convex / Extruded (lighter on top-left where light strikes, darker on bottom-right)
            Brush.linearGradient(
                colors = listOf(
                    NeuDarkSurfaceLight,
                    NeuDarkSurface,
                    NeuDarkSurfaceDark
                )
            )
        }
    } else {
        if (isInset || (onClick != null && isPressed)) {
            // Concave / Debossed
            Brush.linearGradient(
                colors = listOf(
                    NeuLightInset,
                    NeuLightSurfaceDark,
                    NeuLightSurface
                )
            )
        } else if (strong) {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFFFFFF),
                    NeuLightSurfaceLight,
                    NeuLightSurface
                )
            )
        } else {
            // Convex / Extruded
            Brush.linearGradient(
                colors = listOf(
                    NeuLightSurfaceLight,
                    NeuLightSurface,
                    NeuLightSurfaceDark
                )
            )
        }
    }

    // Beveled Border: Top-Left specular highlight, Bottom-Right ambient shadow rim
    val borderBrush = if (isDarkMode) {
        if (isInset || (onClick != null && isPressed)) {
            Brush.linearGradient(
                colors = listOf(
                    NeuDarkBorder,
                    Color(0x1A000000),
                    NeuDarkHighlightBorder.copy(alpha = 0.15f)
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    NeuDarkHighlightBorder,
                    Color(0x1FFFFFFF),
                    NeuDarkBorder
                )
            )
        }
    } else {
        if (isInset || (onClick != null && isPressed)) {
            Brush.linearGradient(
                colors = listOf(
                    NeuLightBorder,
                    Color(0x14000000),
                    NeuLightHighlightBorder
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    NeuLightHighlightBorder,
                    Color(0x66FFFFFF),
                    NeuLightBorder
                )
            )
        }
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = currentElevation,
                shape = shape,
                clip = false,
                ambientColor = shadowAmbientColor,
                spotColor = shadowSpotColor
            )
            .clip(shape)
            .background(surfaceBrush)
            .border(borderWidth, borderBrush, shape)
            .then(clickableModifier)
    ) {
        content()
    }
}

/**
 * Tactile Neumorphic Circular Icon Button with dual shadow and spring press feedback.
 */
@Composable
fun NeuIconButton(
    icon: ImageVector,
    contentDescription: String?,
    isDarkMode: Boolean = true,
    size: Dp = 42.dp,
    iconSize: Dp = 20.dp,
    tint: Color = CrimsonPrimary,
    shape: Shape = CircleShape,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.size(size),
        shape = shape,
        isDarkMode = isDarkMode,
        elevation = 5.dp,
        borderWidth = 1.dp,
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
fun NeuIconButton(
    painter: androidx.compose.ui.graphics.painter.Painter,
    contentDescription: String?,
    isDarkMode: Boolean = true,
    size: Dp = 42.dp,
    iconSize: Dp = 20.dp,
    tint: Color = CrimsonPrimary,
    shape: Shape = CircleShape,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.size(size),
        shape = shape,
        isDarkMode = isDarkMode,
        elevation = 5.dp,
        borderWidth = 1.dp,
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painter,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
