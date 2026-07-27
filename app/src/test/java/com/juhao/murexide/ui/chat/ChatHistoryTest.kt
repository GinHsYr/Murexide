package com.juhao.murexide.ui.chat

import com.juhao.murexide.data.MessageItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryTest {
    @Test
    fun olderPage_keepsServerOrderAndSkipsKnownOrDuplicateMessages() {
        val page = resolveOlderMessagePage(
            knownMessageIds = setOf("current", "known"),
            currentAnchorMessageId = "current",
            messages = listOf(
                message("known"),
                message("older-1"),
                message("older-1"),
                message("older-2")
            )
        )

        assertEquals(listOf("older-1", "older-2"), page.newMessages.map { it.msgId })
        assertEquals("older-2", page.nextAnchorMessageId)
        assertTrue(page.madeCursorProgress)
    }

    @Test
    fun repeatedAnchor_stopsPaginationEvenWhenTheServerReturnsMessages() {
        val page = resolveOlderMessagePage(
            knownMessageIds = setOf("current"),
            currentAnchorMessageId = "current",
            messages = listOf(message("older"), message("current"))
        )

        assertEquals(listOf("older"), page.newMessages.map { it.msgId })
        assertEquals("current", page.nextAnchorMessageId)
        assertFalse(page.madeCursorProgress)
    }

    @Test
    fun blankPageHasNoAnchorProgress() {
        val page = resolveOlderMessagePage(
            knownMessageIds = emptySet(),
            currentAnchorMessageId = "current",
            messages = emptyList()
        )

        assertTrue(page.newMessages.isEmpty())
        assertEquals(null, page.nextAnchorMessageId)
        assertFalse(page.madeCursorProgress)
    }

    private fun message(id: String) = MessageItem(
        msgId = id,
        senderId = "sender",
        senderName = "Sender",
        senderAvatar = "",
        contentType = MessageItem.CONTENT_TYPE_TEXT,
        timestamp = 0,
        direction = "left"
    )
}
