package com.juhao.murexide.network

import com.juhao.murexide.data.MessageItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestConversationMessageCacheTest {
    @Test
    fun `cache keeps the latest push for every group`() {
        val cache = LatestConversationMessageCache()

        cache.record(message(chatId = "group-a", msgId = "a-1", msgSeq = 1, content = "a1"), "me")
        cache.record(message(chatId = "group-b", msgId = "b-1", msgSeq = 1, content = "b1"), "me")
        cache.record(message(chatId = "group-a", msgId = "a-2", msgSeq = 2, content = "a2"), "me")

        val latestByChat = cache.snapshot().associateBy { it.chatId }
        assertEquals(2, latestByChat.size)
        assertEquals("a2", latestByChat.getValue("group-a").content)
        assertEquals("b1", latestByChat.getValue("group-b").content)
    }

    @Test
    fun `message sequence wins when timestamps move backwards`() {
        val cache = LatestConversationMessageCache()
        cache.record(
            message(chatId = "group", msgId = "old", msgSeq = 10, timestamp = 2_000),
            "me"
        )

        val recorded = cache.record(
            message(
                chatId = "group",
                msgId = "new",
                msgSeq = 11,
                timestamp = 1_999,
                content = "newest"
            ),
            "me"
        )

        assertTrue(recorded)
        assertEquals("newest", cache.snapshot().single().content)
    }

    @Test
    fun `older sequence cannot replace latest even with a later timestamp`() {
        val cache = LatestConversationMessageCache()
        cache.record(
            message(chatId = "group", msgId = "new", msgSeq = 11, timestamp = 2_000),
            "me"
        )

        val recorded = cache.record(
            message(chatId = "group", msgId = "old", msgSeq = 10, timestamp = 3_000),
            "me"
        )

        assertFalse(recorded)
        assertEquals("new", cache.snapshot().single().msgId)
    }

    @Test
    fun `incoming direct message is cached under the sender conversation`() {
        val cache = LatestConversationMessageCache()
        val incoming = message(
            chatId = "me",
            chatType = 1,
            senderId = "friend",
            content = "hello"
        )

        cache.record(incoming, currentUserId = "me")
        cache.record(
            message(
                chatId = "friend",
                chatType = 1,
                senderId = "me",
                msgId = "reply",
                msgSeq = 2,
                content = "reply"
            ),
            currentUserId = "me"
        )

        assertEquals(listOf("reply"), cache.snapshot().map { it.content })
    }

    @Test
    fun `stream and edit mutate only the cached latest message`() {
        val cache = LatestConversationMessageCache()
        val original = message(content = "hello", msgId = "latest")
        cache.record(original, "me")

        assertTrue(cache.appendStreamContent("latest", " world"))
        assertFalse(cache.appendStreamContent("older", " ignored"))
        assertTrue(
            cache.updateIfLatest(
                original.copy(
                    senderId = "",
                    content = "edited",
                    timestamp = 0,
                    msgSeq = 0,
                    isEdited = true
                )
            )
        )

        val latest = cache.snapshot().single()
        assertEquals("edited", latest.content)
        assertEquals("sender", latest.senderId)
        assertEquals(1_000L, latest.timestamp)
        assertTrue(latest.isEdited)
    }

    private fun message(
        chatId: String = "group",
        chatType: Int = 2,
        senderId: String = "sender",
        msgId: String = "message-id",
        msgSeq: Long = 1,
        timestamp: Long = 1_000,
        content: String = "message"
    ) = MessageItem(
        msgId = msgId,
        senderId = senderId,
        senderName = "Sender",
        senderAvatar = "",
        chatId = chatId,
        chatType = chatType,
        content = content,
        contentType = MessageItem.CONTENT_TYPE_TEXT,
        timestamp = timestamp,
        msgSeq = msgSeq,
        direction = if (senderId == "me") "right" else "left"
    )
}
