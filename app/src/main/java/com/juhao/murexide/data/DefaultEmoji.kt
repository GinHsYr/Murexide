package com.juhao.murexide.data

import android.content.res.AssetManager
import android.net.Uri

const val DEFAULT_EMOJI_ASSET_DIRECTORY = "sticker"
const val DEFAULT_EMOJI_FILE_EXTENSION = ".webp"

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

data class DefaultEmojiMatch(
    val emoji: DefaultEmoji,
    val start: Int,
    val endExclusive: Int
)

object DefaultEmojiCatalog {
    @Volatile
    private var cachedEmojis: List<DefaultEmoji>? = null

    fun load(assetManager: AssetManager): List<DefaultEmoji> {
        cachedEmojis?.let { return it }

        return synchronized(this) {
            cachedEmojis ?: fromFileNames(
                assetManager.list(DEFAULT_EMOJI_ASSET_DIRECTORY).orEmpty().asIterable()
            ).also { cachedEmojis = it }
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

object DefaultEmojiParser {
    private val markerRegex = Regex("""\[\.([^\[\]]+)]""")

    fun findMatches(
        text: String,
        emojis: Collection<DefaultEmoji>
    ): List<DefaultEmojiMatch> {
        if (text.isEmpty() || emojis.isEmpty()) return emptyList()

        val emojisByName = emojis.associateBy(DefaultEmoji::name)
        return markerRegex.findAll(text).mapNotNull { match ->
            val emoji = emojisByName[match.groupValues[1]] ?: return@mapNotNull null
            DefaultEmojiMatch(
                emoji = emoji,
                start = match.range.first,
                endExclusive = match.range.last + 1
            )
        }.toList()
    }
}
