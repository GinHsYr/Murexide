package com.juhao.murexide.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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

    @Test
    fun `editing latest message updates preview without reordering conversation`() {
        val otherConversation = conversation(chatId = "other", content = "other message")
        val targetConversation = conversation(
            chatId = "target",
            content = "old message",
            unreadCount = 2,
            timestamp = 1_234L
        )
        val editedMessage = outgoingMessage(
            chatId = "target",
            content = "edited message",
            timestamp = 1_234L
        ).copy(isEdited = true)

        val updated = listOf(otherConversation, targetConversation)
            .withEditedLatestMessage(editedMessage)

        assertNotNull(updated)
        assertSame(otherConversation, updated[0])
        assertEquals("other", updated[0].chatId)
        assertEquals("target", updated[1].chatId)
        assertEquals("edited message", updated[1].chatContent)
        assertEquals(1_234L, updated[1].timestampMs)
        assertEquals(2, updated[1].unreadMessage)
    }

    @Test
    fun `editing older message does not replace latest preview`() {
        val conversations = listOf(
            conversation(
                chatId = "target",
                content = "latest message",
                timestamp = 2_000L
            )
        )
        val editedOlderMessage = outgoingMessage(
            chatId = "target",
            content = "edited older message",
            timestamp = 1_000L
        ).copy(isEdited = true)

        val updated = conversations.withEditedLatestMessage(editedOlderMessage)

        assertEquals(conversations, updated)
        assertEquals("latest message", updated.single().chatContent)
    }

    @Test
    fun `edited message without timestamp leaves previews unchanged`() {
        val conversations = listOf(conversation(chatId = "target"))
        val message = outgoingMessage(
            chatId = "target",
            content = "edited message",
            timestamp = 0L
        ).copy(isEdited = true)

        assertSame(conversations, conversations.withEditedLatestMessage(message))
    }

    private fun conversation(
        chatId: String,
        content: String = "old message",
        unreadCount: Int = 0,
        timestamp: Long = 1L
    ) = ConversationItem(
        chatId = chatId,
        chatType = 2,
        name = chatId,
        chatContent = content,
        timestampMs = timestamp,
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
