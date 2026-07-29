package com.juhao.murexide.data

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.core.graphics.scale
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

const val DEFAULT_EMOJI_ASSET_DIRECTORY = "sticker"
const val DEFAULT_EMOJI_FILE_EXTENSION = ".webp"

@Immutable
data class DefaultEmoji(
    val name: String,
    val fileName: String = "$name$DEFAULT_EMOJI_FILE_EXTENSION"
) {
    val marker: String
        get() = "[.$name]"

    val assetPath: String
        get() = "$DEFAULT_EMOJI_ASSET_DIRECTORY/$fileName"

    val assetUri: String
        get() = "file:///android_asset/${Uri.encode(assetPath, "/")}"
}

@Immutable
data class DefaultEmojiMatch(
    val emoji: DefaultEmoji,
    val start: Int,
    val endExclusive: Int
)

object DefaultEmojiCatalog {
    @Volatile
    private var cachedEmojis: List<DefaultEmoji>? = null

    /** 预构建的 name→Emoji 查找表，避免 [DefaultEmojiParser.findMatches] 重复创建 Map */
    @Volatile
    var emojisByName: Map<String, DefaultEmoji> = emptyMap()
        private set

    /** Returns true when the collection is the process-wide immutable catalog. */
    internal fun isCatalog(emojis: Collection<DefaultEmoji>): Boolean {
        return emojis === cachedEmojis
    }

    fun load(assetManager: AssetManager): List<DefaultEmoji> {
        cachedEmojis?.let { return it }

        return synchronized(this) {
            cachedEmojis ?: fromFileNames(
                assetManager.list(DEFAULT_EMOJI_ASSET_DIRECTORY).orEmpty().asIterable()
            ).also {
                cachedEmojis = it
                emojisByName = it.associateBy(DefaultEmoji::name)
            }
        }
    }

    /**
     * 后台预热 Bitmap 缓存，在 App 启动或进入聊天前调用。
     * 预解码所有表情到 [DefaultEmojiBitmapCache]，避免首次渲染卡顿。
     */
    fun prewarmBitmapCache(
        assetManager: AssetManager,
        targetHeights: IntArray,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    ) {
        val emojis = load(assetManager)
        scope.launch(Dispatchers.IO) {
            val requests = emojis.flatMap { emoji ->
                targetHeights.asSequence()
                    .filter { it > 0 && !DefaultEmojiBitmapCache.hasEntry(emoji.assetPath, it) }
                    .map { height -> emoji to height }
                    .toList()
            }
            requests.chunked(16).forEach { chunk ->
                chunk.map { (emoji, height) ->
                    DefaultEmojiBitmapCache.loadAsync(assetManager, emoji, height)
                }.awaitAll()
            }
        }
    }

    internal fun fromFileNames(fileNames: Iterable<String>): List<DefaultEmoji> {
        return fileNames
            .asSequence()
            .filter { it.endsWith(DEFAULT_EMOJI_FILE_EXTENSION, ignoreCase = true) }
            .map { fileName ->
                DefaultEmoji(
                    name = fileName.dropLast(DEFAULT_EMOJI_FILE_EXTENSION.length),
                    fileName = fileName
                )
            }
            .filter { it.name.isNotEmpty() && '[' !in it.name && ']' !in it.name }
            .distinctBy { it.name }
            .sortedBy { it.name }
            .toList()
    }
}

/**
 * 全局共享的表情 Bitmap 缓存，供输入框和 Compose 层共用。
 * 使用 LRU 策略，按 (assetPath@targetHeight) 作为 key。
 *
 * Bitmap 解码是最容易把输入和滚动拖入主线程的操作。这个对象提供同步的
 * [load] 兼容接口和异步的 [loadAsync] 接口；UI 代码必须使用后者，且相同
 * key 的并发请求会合并成一个 in-flight Deferred。
 */
object DefaultEmojiBitmapCache : LruCache<String, Bitmap>(24 * 1024) {
    private val inFlightLock = Any()
    private val inFlight = mutableMapOf<String, Deferred<Bitmap?>>()
    private val sourceHeights = ConcurrentHashMap<String, Int>()
    private val decodeScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO.limitedParallelism(2)
    )

    override fun sizeOf(key: String, value: Bitmap): Int {
        return (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    fun hasEntry(assetPath: String): Boolean {
        return snapshot().keys.any { it.startsWith("$assetPath@") }
    }

    fun hasEntry(assetPath: String, targetHeight: Int): Boolean {
        return get(cacheKey(assetPath, targetHeight)) != null
    }

    fun cacheKey(assetPath: String, targetHeight: Int): String {
        return "$assetPath@${targetHeight.coerceAtLeast(1)}"
    }

    fun get(emoji: DefaultEmoji, targetHeight: Int): Bitmap? {
        return get(cacheKey(emoji.assetPath, targetHeight))
    }

    /**
     * Schedules a decode on the bounded background dispatcher. Requests for the same
     * asset and target size share one Deferred, which prevents a grid and a message
     * bubble from decoding the same WebP simultaneously.
     */
    fun loadAsync(
        assetManager: AssetManager,
        emoji: DefaultEmoji,
        targetHeight: Int
    ): Deferred<Bitmap?> {
        val key = cacheKey(emoji.assetPath, targetHeight)
        get(key)?.let { return kotlinx.coroutines.CompletableDeferred(it) }

        return synchronized(inFlightLock) {
            get(key)?.let { return@synchronized kotlinx.coroutines.CompletableDeferred(it) }
            inFlight[key]?.let { return@synchronized it }

            val deferred = decodeScope.async {
                decodeAndCache(assetManager, emoji, targetHeight, key)
            }
            inFlight[key] = deferred
            deferred.invokeOnCompletion {
                synchronized(inFlightLock) {
                    if (inFlight[key] === deferred) inFlight.remove(key)
                }
            }
            deferred
        }
    }

    /** Fire-and-forget convenience for Android Views. */
    fun requestAsync(
        assetManager: AssetManager,
        emoji: DefaultEmoji,
        targetHeight: Int
    ): Deferred<Bitmap?> {
        return loadAsync(assetManager, emoji, targetHeight)
    }

    fun clearMemory() {
        synchronized(inFlightLock) { evictAll() }
    }

    /**
     * 使用 BitmapFactory.Options.inSampleSize 进行预缩放解码，
     * 避免先解码完整图片再 scale 造成的双份内存峰值。
     */
    fun load(assetManager: AssetManager, emoji: DefaultEmoji, targetHeight: Int): Bitmap? {
        val key = cacheKey(emoji.assetPath, targetHeight)
        get(key)?.let { return it }

        return decodeAndCache(assetManager, emoji, targetHeight, key)
    }

    private fun decodeAndCache(
        assetManager: AssetManager,
        emoji: DefaultEmoji,
        targetHeight: Int,
        key: String
    ): Bitmap? {
        get(key)?.let { return it }
        val safeHeight = targetHeight.coerceAtLeast(1)
        val bitmap = runCatching {
            val rawHeight = sourceHeights[emoji.assetPath] ?: run {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                assetManager.open(emoji.assetPath).use { stream ->
                    BitmapFactory.decodeStream(stream, null, bounds)
                }
                bounds.outHeight.takeIf { bounds.outWidth > 0 && it > 0 }
                    ?.also { sourceHeights[emoji.assetPath] = it }
                    ?: return@runCatching null
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(rawHeight, safeHeight)
            }
            val decoded = assetManager.open(emoji.assetPath).use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            } ?: return@runCatching null

            val width = (
                decoded.width.toFloat() / decoded.height.coerceAtLeast(1) * safeHeight
            ).roundToInt().coerceAtLeast(1)
            if (decoded.width == width && decoded.height == safeHeight) {
                decoded
            } else {
                decoded.scale(width, safeHeight, true).also {
                    if (it !== decoded && !decoded.isRecycled) decoded.recycle()
                }
            }
        }.getOrNull() ?: return null

        return synchronized(inFlightLock) {
            // A concurrent caller may have inserted first.
            val existing = get(key)
            if (existing != null) {
                if (existing !== bitmap && !bitmap.isRecycled) bitmap.recycle()
                return@synchronized existing
            }
            put(key, bitmap)
            bitmap
        }
    }

    private fun calculateInSampleSize(rawHeight: Int, targetHeight: Int): Int {
        var sampleSize = 1
        if (rawHeight > targetHeight) {
            val halfHeight = rawHeight / 2
            while (halfHeight / sampleSize > targetHeight) {
                sampleSize *= 2
            }
        }
        return sampleSize
    }
}

object DefaultEmojiParser {
    internal data class EditWindow(
        val oldStart: Int,
        val oldEndExclusive: Int,
        val newStart: Int,
        val newEndExclusive: Int
    )

    /**
     * 查找文本中的表情标记。
     * 使用低分配扫描器和 O(1) 名称查找，避免 Regex MatchResult 在长消息中产生
     * 大量短命对象。
     */
    fun findMatches(
        text: String,
        emojis: Collection<DefaultEmoji>
    ): List<DefaultEmojiMatch> {
        if (text.isEmpty() || emojis.isEmpty()) return emptyList()

        val lookup = DefaultEmojiCatalog.emojisByName.takeIf {
            DefaultEmojiCatalog.isCatalog(emojis)
        }
            ?: emojis.associateBy(DefaultEmoji::name)
        return findMatchesInRange(text, lookup, 0, text.length)
    }

    /**
     * Updates a previously parsed match list after an edit. The unchanged prefix and
     * suffix are reused and only the smallest marker-safe window is rescanned. This is
     * deliberately a pure function so the editor can update spans without allocating a
     * regex matcher on every keystroke.
     */
    internal fun updateMatchesIncrementally(
        oldText: String,
        newText: String,
        oldMatches: List<DefaultEmojiMatch>,
        emojis: Collection<DefaultEmoji>
    ): List<DefaultEmojiMatch> {
        if (oldText == newText) return oldMatches
        if (oldText.isEmpty() || oldMatches.isEmpty()) {
            return findMatches(newText, emojis)
        }

        val window = editWindow(oldText, newText)

        val lookup = DefaultEmojiCatalog.emojisByName.takeIf {
            DefaultEmojiCatalog.isCatalog(emojis)
        } ?: emojis.associateBy(DefaultEmoji::name)

        val result = ArrayList<DefaultEmojiMatch>(oldMatches.size + 4)
        oldMatches.forEach { match ->
            if (match.endExclusive <= window.oldStart) result += match
        }
        result += findMatchesInRange(
            text = newText,
            lookup = lookup,
            startInclusive = window.newStart,
            endExclusive = window.newEndExclusive
        )
        val delta = window.newEndExclusive - window.oldEndExclusive
        oldMatches.forEach { match ->
            if (match.start >= window.oldEndExclusive) {
                result += match.copy(
                    start = match.start + delta,
                    endExclusive = match.endExclusive + delta
                )
            }
        }
        return result
    }

    internal fun editWindow(oldText: String, newText: String): EditWindow {
        if (oldText == newText) {
            return EditWindow(oldText.length, oldText.length, newText.length, newText.length)
        }
        val prefix = oldText.commonPrefixWith(newText).length
        var suffix = 0
        val maxSuffix = minOf(oldText.length - prefix, newText.length - prefix)
        while (
            suffix < maxSuffix &&
            oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]
        ) {
            suffix++
        }

        val oldChangedEnd = oldText.length - suffix
        val newChangedEnd = newText.length - suffix
        val oldScanStart = markerSafeStart(oldText, prefix, oldChangedEnd)
        val newScanStart = markerSafeStart(newText, prefix, newChangedEnd)
        val scanStart = minOf(oldScanStart, newScanStart)
        return EditWindow(
            oldStart = scanStart,
            oldEndExclusive = markerSafeEnd(oldText, oldChangedEnd, scanStart),
            newStart = scanStart.coerceAtMost(newText.length),
            newEndExclusive = markerSafeEnd(newText, newChangedEnd, scanStart)
        )
    }

    private fun markerSafeStart(text: String, changedStart: Int, changedEnd: Int): Int {
        val candidate = text.lastIndexOf("[.", (changedStart - 1).coerceAtLeast(0))
        if (candidate >= 0) {
            val close = text.indexOf(']', candidate + 2)
            if (close < 0 || close >= changedStart || close >= changedEnd) return candidate
        }
        return changedStart.coerceIn(0, text.length)
    }

    private fun markerSafeEnd(text: String, changedEnd: Int, scanStart: Int): Int {
        var end = changedEnd.coerceIn(scanStart, text.length)
        val markerEnd = text.indexOf(']', end)
        if (markerEnd >= 0 && text.lastIndexOf("[.", markerEnd) >= scanStart) {
            end = markerEnd + 1
        }
        return end
    }

    internal fun findMatchesInRange(
        text: String,
        emojis: Collection<DefaultEmoji>,
        startInclusive: Int,
        endExclusive: Int
    ): List<DefaultEmojiMatch> {
        if (text.isEmpty() || emojis.isEmpty()) return emptyList()
        val lookup = DefaultEmojiCatalog.emojisByName.takeIf {
            DefaultEmojiCatalog.isCatalog(emojis)
        } ?: emojis.associateBy(DefaultEmoji::name)
        return findMatchesInRange(text, lookup, startInclusive, endExclusive)
    }

    private fun findMatchesInRange(
        text: String,
        lookup: Map<String, DefaultEmoji>,
        startInclusive: Int,
        endExclusive: Int
    ): List<DefaultEmojiMatch> {
        val start = startInclusive.coerceIn(0, text.length)
        val end = endExclusive.coerceIn(start, text.length)
        if (start >= end) return emptyList()

        val matches = ArrayList<DefaultEmojiMatch>()
        var cursor = text.indexOf("[.", start)
        while (cursor >= 0 && cursor < end) {
            val close = text.indexOf(']', cursor + 2)
            if (close < 0 || close + 1 > end) break
            val nameStart = cursor + 2
            if (nameStart < close) {
                lookup[text.substring(nameStart, close)]?.let { emoji ->
                    matches += DefaultEmojiMatch(emoji, cursor, close + 1)
                }
            }
            cursor = text.indexOf("[.", close + 1)
        }
        return matches
    }
}
