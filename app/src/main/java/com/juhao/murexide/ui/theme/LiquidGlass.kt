package com.juhao.murexide.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/** Whether the user opted into the GPU-backed liquid glass treatment. */
val LocalLiquidGlassEnabled = staticCompositionLocalOf { false }

/** User-controlled multiplier for the blur used by liquid glass surfaces. */
val LocalLiquidGlassBlur = staticCompositionLocalOf { 1f }

/** The nearest backdrop source used by glass surfaces. */
val LocalLiquidGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * Applies the Backdrop treatment when enabled and keeps a faithful opaque fallback otherwise.
 * The fallback is important for API levels where RenderEffect is unavailable.
 */
fun Modifier.liquidGlass(
    enabled: Boolean,
    backdrop: Backdrop?,
    shape: Shape,
    surfaceColor: Color,
    blurRadius: Dp = 6.dp,
    lensHeight: Dp = 0.dp,
    lensAmount: Dp = 0.dp,
    showHighlight: Boolean = true,
): Modifier {
    if (!enabled || backdrop == null) {
        return clip(shape).background(surfaceColor)
    }

    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(blurRadius.toPx())
            if (lensHeight > 0.dp && lensAmount > 0.dp) {
                lens(lensHeight.toPx(), lensAmount.toPx())
            }
        },
        highlight = if (showHighlight) {
            { Highlight.Plain }
        } else {
            null
        },
        shadow = { Shadow(radius = 3.dp, alpha = 0.08f) },
        innerShadow = { InnerShadow(radius = 2.dp, alpha = 0.08f) },
        onDrawSurface = {
            drawRect(surfaceColor)
        }
    )
}

/** A small glass surface used by settings and conversation-detail controls. */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    color: Color,
    blurRadius: Dp = 6.dp,
    lensHeight: Dp = 0.dp,
    lensAmount: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.liquidGlass(
            enabled = LocalLiquidGlassEnabled.current,
            backdrop = LocalLiquidGlassBackdrop.current,
            shape = shape,
            surfaceColor = color,
            blurRadius = blurRadius * LocalLiquidGlassBlur.current,
            lensHeight = lensHeight,
            lensAmount = lensAmount,
            showHighlight = liquidGlassHighlightEnabled(),
        ),
        content = { content() }
    )
}

@Composable
fun liquidGlassHighlightEnabled(): Boolean =
    MaterialTheme.colorScheme.background.liquidGlassLuminance() > 0.5f

private fun Color.liquidGlassLuminance(): Float =
    (0.2126f * red) + (0.7152f * green) + (0.0722f * blue)

/**
 * Creates a backdrop source for a screen whose content is rendered above a plain background.
 * Rich screens can provide their own source by applying [layerBackdrop] to their content.
 */
@Composable
fun rememberLiquidGlassBackdrop(): LayerBackdrop = rememberLayerBackdrop()

@Composable
fun LiquidGlassBackground(
    backdrop: LayerBackdrop,
    color: Color,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(color)
        )
        content()
    }
}
