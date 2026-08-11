package com.juhao.murexide.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/** Whether the user opted into the GPU-backed liquid glass treatment. */
val LocalLiquidGlassEnabled = staticCompositionLocalOf { false }

/** User-controlled multiplier for the blur used by liquid glass surfaces. */
val LocalLiquidGlassBlur = staticCompositionLocalOf { 1f }

/** The nearest backdrop source used by glass surfaces. */
val LocalLiquidGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

private const val DEFAULT_MINIMUM_TEXT_CONTRAST = 4.5f

private data class LiquidGlassContrastContext(
    val glassColor: Color,
    val backgroundColor: Color,
    val minimumContrast: Float,
)

private val LocalLiquidGlassContrastContext =
    compositionLocalOf<LiquidGlassContrastContext?> { null }

private data class LiquidGlassForegroundSampling(
    val backdrop: Backdrop,
    val glassColor: Color,
    val blurRadius: Dp,
)

private val LocalLiquidGlassForegroundSampling =
    compositionLocalOf<LiquidGlassForegroundSampling?> { null }

private val AdaptiveForegroundShader = """
uniform shader content;
layout(color) uniform half4 glassColor;

half toLinear(half channel) {
    return channel <= 0.04045
        ? channel / 12.92
        : pow((channel + 0.055) / 1.055, 2.4);
}

half4 main(float2 coord) {
    half4 sample = content.eval(coord);
    half3 backdrop = sample.a > 0.001
        ? sample.rgb / sample.a
        : half3(0.0);
    half3 visible = mix(backdrop, glassColor.rgb, glassColor.a);
    half luminance = dot(
        half3(toLinear(visible.r), toLinear(visible.g), toLinear(visible.b)),
        half3(0.2126, 0.7152, 0.0722)
    );
    half foreground = luminance > 0.179 ? 0.0 : 1.0;
    return half4(foreground, foreground, foreground, 1.0);
}
""".trimIndent()

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

/** Keeps the preferred foreground when readable, otherwise flips it to black or white. */
internal fun liquidGlassContentColor(
    preferredColor: Color,
    glassColor: Color,
    backgroundColor: Color,
    minimumContrast: Float = DEFAULT_MINIMUM_TEXT_CONTRAST,
): Color {
    val visibleGlassColor = glassColor.compositeOver(backgroundColor)
    if (contrastRatio(preferredColor, visibleGlassColor) >= minimumContrast) {
        return preferredColor
    }

    val blackContrast = contrastRatio(Color.Black, visibleGlassColor)
    val whiteContrast = contrastRatio(Color.White, visibleGlassColor)
    return if (blackContrast >= whiteContrast) Color.Black else Color.White
}

private fun contrastRatio(foreground: Color, background: Color): Float {
    val visibleForeground = foreground.compositeOver(background)
    val lighter = maxOf(visibleForeground.luminance(), background.luminance())
    val darker = minOf(visibleForeground.luminance(), background.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

/** Supplies the adaptive foreground to content drawn on a glass surface. */
@Composable
fun ProvideLiquidGlassContentColor(
    glassColor: Color,
    preferredColor: Color = LocalContentColor.current,
    minimumContrast: Float = DEFAULT_MINIMUM_TEXT_CONTRAST,
    content: @Composable () -> Unit,
) {
    if (!LocalLiquidGlassEnabled.current) {
        content()
        return
    }
    val backgroundColor = MaterialTheme.colorScheme.background
    val contentColor = liquidGlassContentColor(
        preferredColor = preferredColor,
        glassColor = glassColor,
        backgroundColor = backgroundColor,
        minimumContrast = minimumContrast,
    )
    CompositionLocalProvider(
        LocalContentColor provides contentColor,
        LocalLiquidGlassContrastContext provides LiquidGlassContrastContext(
            glassColor = glassColor,
            backgroundColor = backgroundColor,
            minimumContrast = minimumContrast,
        ),
    ) {
        content()
    }
}

/** Returns the adaptive neutral while inside a glass surface and the exact fallback elsewhere. */
@Composable
internal fun resolvedLiquidGlassContentColor(fallbackColor: Color): Color {
    val context = LocalLiquidGlassContrastContext.current ?: return fallbackColor
    return liquidGlassContentColor(
        preferredColor = fallbackColor,
        glassColor = context.glassColor,
        backgroundColor = context.backgroundColor,
        minimumContrast = context.minimumContrast,
    )
}

/**
 * Makes the child itself black or white per pixel from the real backdrop underneath it.
 * The backdrop is thresholded in a shader and the child's alpha is used as a destination-in
 * mask, so images and message bubbles are included without a CPU screenshot/readback.
 */
@Composable
fun Modifier.adaptiveLiquidGlassForeground(): Modifier {
    val sampling = LocalLiquidGlassForegroundSampling.current ?: return this
    if (!isRuntimeShaderSupported()) return this

    return this
        .graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        }
        .drawPlainBackdrop(
            backdrop = sampling.backdrop,
            shape = { RectangleShape },
            effects = {
                vibrancy()
                if (sampling.blurRadius > 0.dp) {
                    blur(sampling.blurRadius.toPx())
                }
                runtimeShaderEffect(
                    key = "adaptive-liquid-glass-foreground",
                    shaderString = AdaptiveForegroundShader,
                    uniformShaderName = "content",
                ) {
                    setColorUniform("glassColor", sampling.glassColor)
                }
            },
        )
        .graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
            blendMode = BlendMode.DstIn
        }
}

@Composable
internal fun ProvideAdaptiveLiquidGlassForeground(
    backdrop: Backdrop?,
    glassColor: Color,
    blurRadius: Dp,
    content: @Composable () -> Unit,
) {
    if (!LocalLiquidGlassEnabled.current || backdrop == null) {
        content()
        return
    }
    CompositionLocalProvider(
        LocalLiquidGlassForegroundSampling provides LiquidGlassForegroundSampling(
            backdrop = backdrop,
            glassColor = glassColor,
            blurRadius = blurRadius,
        ),
    ) {
        content()
    }
}

/** A small glass surface used by settings and conversation-detail controls. */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    color: Color,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
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
        content = {
            ProvideLiquidGlassContentColor(
                glassColor = color,
                preferredColor = contentColor,
            ) {
                content()
            }
        }
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
