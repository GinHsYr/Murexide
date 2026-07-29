package com.juhao.murexide.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultEmojiTest {
    private val emojis = listOf(
        DefaultEmoji("猪头"),
        DefaultEmoji("OK"),
        DefaultEmoji("笑哭")
    )

    @Test
    fun `catalog filters webp files and sorts by emoji name`() {
        assertEquals(
            listOf("OK", "猪头", "笑哭"),
            DefaultEmojiCatalog.fromFileNames(
                listOf("猪头.webp", "ignored.png", "笑哭.WEBP", "OK.webp")
            ).map(DefaultEmoji::name)
        )
    }

    @Test
    fun `all bundled default emoji assets are uniquely named webp files`() {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        val assetDirectory = sequenceOf(
            File(workingDirectory, "src/main/assets/sticker"),
            File(workingDirectory, "app/src/main/assets/sticker")
        ).first(File::isDirectory)
        val files = assetDirectory.listFiles().orEmpty().filter(File::isFile)

        assertEquals(119, files.size)
        assertTrue(files.all { it.extension.equals("webp", ignoreCase = true) })
        assertEquals(119, files.map(File::nameWithoutExtension).distinct().size)
    }

    @Test
    fun `parser returns only known markers in mixed text`() {
        val text = "这是[.猪头]和[.不存在]，再来[.猪头][.OK]"
        val matches = DefaultEmojiParser.findMatches(
            text = text,
            emojis = emojis
        )

        assertEquals(listOf("猪头", "猪头", "OK"), matches.map { it.emoji.name })
        assertEquals("[.猪头]", text.substring(matches.first().start, matches.first().endExclusive))
    }

    @Test
    fun `emoji marker is derived from exact asset base name`() {
        assertEquals("[.OK]", DefaultEmoji("OK").marker)
        assertEquals("sticker/猪头.webp", DefaultEmoji("猪头").assetPath)
    }

    @Test
    fun `incremental parser preserves unaffected matches and shifts suffix`() {
        val oldText = "前[.猪头]中[.OK]后"
        val oldMatches = DefaultEmojiParser.findMatches(oldText, emojis)
        val newText = "前[.猪头]中新内容[.OK]后"

        val updated = DefaultEmojiParser.updateMatchesIncrementally(
            oldText = oldText,
            newText = newText,
            oldMatches = oldMatches,
            emojis = emojis
        )

        assertEquals(listOf("猪头", "OK"), updated.map { it.emoji.name })
        assertEquals(
            listOf("[.猪头]", "[.OK]"),
            updated.map { newText.substring(it.start, it.endExclusive) }
        )
    }

    @Test
    fun `incremental parser removes a marker edited in the middle`() {
        val oldText = "A[.猪头]B[.OK]C"
        val oldMatches = DefaultEmojiParser.findMatches(oldText, emojis)
        val newText = "A猪B[.OK]C"

        val updated = DefaultEmojiParser.updateMatchesIncrementally(
            oldText = oldText,
            newText = newText,
            oldMatches = oldMatches,
            emojis = emojis
        )

        assertEquals(listOf("OK"), updated.map { it.emoji.name })
        assertEquals("[.OK]", newText.substring(updated.single().start, updated.single().endExclusive))
    }

    @Test
    fun `incremental parser matches full parse across marker boundary edits`() {
        val versions = listOf(
            "A[.猪头]B[.OK]C",
            "XA[.猪头]B[.OK]C",
            "XA[.猪坏头]B[.OK]C",
            "XA[.笑哭][.猪头]B[.OK]C",
            "XA[.笑哭]B[.OK]C",
            "[.OK]XA[.笑哭]B[.OK]C[.",
            "普通文本",
            "普通[.猪头][.猪头][.OK]文本"
        )
        var oldText = ""
        var incremental = emptyList<DefaultEmojiMatch>()

        versions.forEach { newText ->
            incremental = DefaultEmojiParser.updateMatchesIncrementally(
                oldText = oldText,
                newText = newText,
                oldMatches = incremental,
                emojis = emojis
            )
            assertEquals(DefaultEmojiParser.findMatches(newText, emojis), incremental)
            oldText = newText
        }
    }

    @Test
    fun `parser handles hundreds of emoji markers in one message`() {
        val text = buildString {
            repeat(500) { index -> append(if (index % 2 == 0) "[.猪头]" else "[.OK]") }
        }

        val matches = DefaultEmojiParser.findMatches(text, emojis)

        assertEquals(500, matches.size)
        assertEquals(0, matches.first().start)
        assertEquals(text.length, matches.last().endExclusive)
    }
}
