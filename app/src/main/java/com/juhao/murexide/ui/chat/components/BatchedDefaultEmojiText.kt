package com.juhao.murexide.ui.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.juhao.murexide.data.DefaultEmoji
import com.juhao.murexide.data.DefaultEmojiMatch
import com.juhao.murexide.data.DefaultEmojiParser
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One text layout + one draw node for an arbitrary number of bundled emoji markers.
 * Unlike InlineTextContent, this does not create a Compose Layout/Image child for every
 * occurrence, so a message containing hundreds of emoji remains cheap to compose.
 */
@Composable
internal fun BatchedDefaultEmojiText(
    text: String,
    timestampText: String,
    emojis: List<DefaultEmoji>,
    bodyStyle: TextStyle,
    timestampStyle: TextStyle,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 16)
    val matches = remember(text, emojis) {
        DefaultEmojiParser.findMatches(text, emojis)
    }
    val targetHeightPx = with(density) {
        val fontSize = bodyStyle.fontSize.takeIf { it.type == TextUnitType.Sp } ?: 14.sp
        (fontSize.toPx() * 1.2f).roundToInt().coerceAtLeast(1)
    }
    val bitmaps = rememberDefaultEmojiBitmaps(context, matches, targetHeightPx)
    val imageBitmaps = remember(bitmaps) {
        bitmaps.mapValues { (_, bitmap) -> bitmap.asImageBitmap() }
    }
    val displayText = remember(text, timestampText, timestampStyle) {
        buildAnnotatedString {
            append(text)
            if (timestampText.isNotEmpty()) {
                append(' ')
                withStyle(timestampStyle.toSpanStyle()) { append(timestampText) }
            }
        }
    }
    val placeholders = remember(matches) {
        matches.map { match ->
            AnnotatedString.Range(
                item = Placeholder(
                    width = 1.2.em,
                    height = 1.2.em,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                ),
                start = match.start,
                end = match.endExclusive
            )
        }
    }
    val layoutState = remember { BatchedEmojiLayoutState() }

    Layout(
        content = {},
        modifier = modifier
            .semantics { this.text = displayText }
            .drawBehind {
                val layoutResult = layoutState.layoutResult ?: return@drawBehind
                drawText(layoutResult)
                layoutResult.placeholderRects.forEachIndexed { index, rect ->
                    val safeRect = rect ?: return@forEachIndexed
                    val match = layoutState.matches.getOrNull(index) ?: return@forEachIndexed
                    val image = imageBitmaps[match.emoji.assetPath] ?: return@forEachIndexed
                    val scale = min(
                        safeRect.width / image.width.coerceAtLeast(1),
                        safeRect.height / image.height.coerceAtLeast(1)
                    )
                    val imageWidth = (image.width * scale).roundToInt().coerceAtLeast(1)
                    val imageHeight = (image.height * scale).roundToInt().coerceAtLeast(1)
                    drawImage(
                        image = image,
                        dstOffset = IntOffset(
                            (safeRect.left + (safeRect.width - imageWidth) / 2f).roundToInt(),
                            (safeRect.top + (safeRect.height - imageHeight) / 2f).roundToInt()
                        ),
                        dstSize = IntSize(imageWidth, imageHeight),
                        filterQuality = FilterQuality.Medium
                    )
                }
            }
    ) { _, constraints ->
        val measured = textMeasurer.measure(
            text = displayText,
            style = bodyStyle,
            placeholders = placeholders,
            constraints = Constraints(maxWidth = constraints.maxWidth)
        )
        layoutState.layoutResult = measured
        layoutState.matches = matches
        layout(
            width = measured.size.width.coerceIn(constraints.minWidth, constraints.maxWidth),
            height = measured.size.height.coerceIn(constraints.minHeight, constraints.maxHeight)
        ) { }
    }
}

private class BatchedEmojiLayoutState {
    var layoutResult: TextLayoutResult? = null
    var matches: List<DefaultEmojiMatch> = emptyList()
}
