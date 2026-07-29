package com.juhao.murexide.ui.chat.components

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.juhao.murexide.data.DefaultEmoji
import com.juhao.murexide.data.DefaultEmojiBitmapCache
import com.juhao.murexide.data.DefaultEmojiMatch

/**
 * Loads the distinct assets used by one message as a single asynchronous batch. The
 * cache itself limits decode parallelism and de-duplicates requests shared with the
 * emoji picker and editor. A message therefore recomposes once per batch instead of
 * once per occurrence.
 */
@Composable
internal fun rememberDefaultEmojiBitmaps(
    context: Context,
    matches: List<DefaultEmojiMatch>,
    targetHeightPx: Int
): Map<String, Bitmap> {
    val emojis = remember(matches) {
        matches.asSequence()
            .map { it.emoji }
            .distinctBy(DefaultEmoji::assetPath)
            .toList()
    }
    val assetKey = remember(emojis) {
        emojis.joinToString("\u0000", transform = DefaultEmoji::assetPath)
    }
    var bitmaps by remember(assetKey, targetHeightPx) {
        mutableStateOf(
            emojis.mapNotNull { emoji ->
                DefaultEmojiBitmapCache.get(emoji, targetHeightPx)?.let {
                    emoji.assetPath to it
                }
            }.toMap()
        )
    }

    LaunchedEffect(assetKey, targetHeightPx) {
        if (emojis.isEmpty()) {
            bitmaps = emptyMap()
            return@LaunchedEffect
        }

        val pending = emojis.filterNot { it.assetPath in bitmaps }.map { emoji ->
            emoji to DefaultEmojiBitmapCache.loadAsync(
                context.assets,
                emoji,
                targetHeightPx
            )
        }
        val loaded = pending.map { (emoji, deferred) ->
            emoji to deferred.await()
        }.mapNotNull { (emoji, bitmap) ->
            bitmap?.let { emoji.assetPath to it }
        }.toMap()
        if (loaded.isNotEmpty()) bitmaps = bitmaps + loaded
    }

    return bitmaps
}

@Composable
internal fun rememberDefaultEmojiBitmap(
    context: Context,
    emoji: DefaultEmoji,
    targetHeightPx: Int
): Bitmap? {
    val key = remember(emoji.assetPath, targetHeightPx) {
        DefaultEmojiBitmapCache.cacheKey(emoji.assetPath, targetHeightPx)
    }
    var bitmap by remember(key) {
        mutableStateOf(DefaultEmojiBitmapCache.get(key))
    }
    LaunchedEffect(key) {
        if (bitmap == null) {
            bitmap = DefaultEmojiBitmapCache
                .loadAsync(context.assets, emoji, targetHeightPx)
                .await()
        }
    }
    return bitmap
}
