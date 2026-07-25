package com.juhao.murexide.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationModelsTest {
    @Test
    fun `outgoing message updates preview without increasing unread count`() {
        val otherConversation = conversation(chatId = "other", content = "other message")
        val targetConversation = conversation(
            chatId = "target",
            content = "old message",
            unreadCount = 2
        )
        val message = outgoingMessage(
            chatId = "target",
            content = "latest message",
            timestamp = 1234L
        )

        val updated = listOf(otherConversation, targetConversation).withLatestMessage(message)

        assertNotNull(updated)
        assertEquals("target", updated!![0].chatId)
        assertEquals("latest message", updated[0].chatContent)
        assertEquals(1234L, updated[0].timestampMs)
        assertEquals(2, updated[0].unreadMessage)
        assertEquals("other", updated[1].chatId)
    }

    @Test
    fun `outgoing media message uses display preview`() {
        val message = outgoingMessage(
            chatId = "target",
            content = "",
            contentType = MessageItem.CONTENT_TYPE_IMAGE
        )

        val updated = listOf(conversation(chatId = "target")).withLatestMessage(message)

        assertEquals("[图片消息]", updated!!.single().chatContent)
    }

    @Test
    fun `unknown conversation requests a server refresh`() {
        val message = outgoingMessage(chatId = "missing", content = "new")

        assertNull(listOf(conversation(chatId = "target")).withLatestMessage(message))
    }

    private fun conversation(
        chatId: String,
        content: String = "old message",
        unreadCount: Int = 0
    ) = ConversationItem(
        chatId = chatId,
        chatType = 2,
        name = chatId,
        chatContent = content,
        timestampMs = 1L,
        unreadMessage = unreadCount,
        avatarUrl = ""
    )

    private fun outgoingMessage(
        chatId: String,
        content: String,
        contentType: Int = MessageItem.CONTENT_TYPE_TEXT,
        timestamp: Long = 2L
    ) = MessageItem(
        msgId = "message-id",
        senderId = "me",
        senderName = "Me",
        senderAvatar = "",
        chatId = chatId,
        chatType = 2,
        content = content,
        contentType = contentType,
        timestamp = timestamp,
        direction = "right"
    )
}
