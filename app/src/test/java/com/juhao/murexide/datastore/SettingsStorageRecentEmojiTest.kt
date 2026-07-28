package com.juhao.murexide.datastore

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsStorageRecentEmojiTest {
    @Test
    fun `used emoji moves to front without duplicates`() {
        val updated = updateRecentDefaultEmojiNames(
            recentNames = listOf("笑哭", "点赞", "OK", "点赞"),
            usedName = "点赞"
        )

        assertEquals(listOf("点赞", "笑哭", "OK"), updated)
    }

    @Test
    fun `recent emoji list keeps only the newest entries`() {
        val recentNames = (1..MAX_RECENT_DEFAULT_EMOJIS).map { "表情$it" }

        val updated = updateRecentDefaultEmojiNames(recentNames, "新表情")

        assertEquals(MAX_RECENT_DEFAULT_EMOJIS, updated.size)
        assertEquals("新表情", updated.first())
        assertEquals("表情${MAX_RECENT_DEFAULT_EMOJIS - 1}", updated.last())
    }

    @Test
    fun `recent emoji names round trip through storage format`() {
        val names = listOf("OK", "笑哭", "上箭头️")

        assertEquals(names, decodeRecentDefaultEmojiNames(encodeRecentDefaultEmojiNames(names)))
    }
}
