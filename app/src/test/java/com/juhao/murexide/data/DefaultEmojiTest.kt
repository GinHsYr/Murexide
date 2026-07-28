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
        val workingDirectory = File(System.getProperty("user.dir"))
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
}
