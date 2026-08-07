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

    @Test
    fun `older push cannot replace the latest preview`() {
        val conversations = listOf(
            conversation(
                chatId = "target",
                content = "latest message",
                timestamp = 2_000L,
                latestMessageId = "latest-id",
                latestMessageSeq = 20L
            )
        )
        val delayedMessage = outgoingMessage(
            chatId = "target",
            content = "delayed message",
            timestamp = 1_000L,
            msgId = "older-id",
            msgSeq = 10L
        ).copy(direction = "left")

        val updated = conversations.withLatestMessage(delayedMessage)

        assertSame(conversations, updated)
        assertEquals("latest message", updated!!.single().chatContent)
    }

    @Test
    fun `newer message sequence replaces preview even when timestamp moves backwards`() {
        val conversations = listOf(
            conversation(
                chatId = "target",
                content = "old message",
                timestamp = 2_000L,
                latestMessageId = "old-id",
                latestMessageSeq = 20L
            )
        )
        val latestMessage = outgoingMessage(
            chatId = "target",
            content = "latest message",
            timestamp = 1_999L,
            msgId = "latest-id",
            msgSeq = 21L
        )

        val updated = conversations.withLatestMessage(latestMessage)!!

        assertEquals("latest message", updated.single().chatContent)
        assertEquals(21L, updated.single().latestMessageSeq)
    }

    @Test
    fun `older message sequence cannot replace preview with a later timestamp`() {
        val conversations = listOf(
            conversation(
                chatId = "target",
                content = "latest message",
                timestamp = 2_000L,
                latestMessageId = "latest-id",
                latestMessageSeq = 21L
            )
        )
        val delayedMessage = outgoingMessage(
            chatId = "target",
            content = "delayed message",
            timestamp = 3_000L,
            msgId = "older-id",
            msgSeq = 20L
        )

        val updated = conversations.withLatestMessage(delayedMessage)

        assertSame(conversations, updated)
        assertEquals("latest message", updated!!.single().chatContent)
    }

    @Test
    fun `duplicate latest push does not reorder or increase unread count`() {
        val otherConversation = conversation(chatId = "other", timestamp = 3_000L)
        val targetConversation = conversation(
            chatId = "target",
            content = "local content",
            unreadCount = 2,
            timestamp = 2_000L,
            latestMessageId = "same-id",
            latestMessageSeq = 42L
        )
        val duplicate = outgoingMessage(
            chatId = "target",
            content = "server content",
            timestamp = 2_000L,
            msgId = "same-id",
            msgSeq = 42L
        ).copy(direction = "left")

        val updated = listOf(otherConversation, targetConversation)
            .withLatestMessage(duplicate)!!

        assertEquals("other", updated[0].chatId)
        assertEquals("target", updated[1].chatId)
        assertEquals("server content", updated[1].chatContent)
        assertEquals(2, updated[1].unreadMessage)
    }

    @Test
    fun `same id in another chat type does not update the wrong conversation`() {
        val group = conversation(chatId = "shared", chatType = 2, content = "group")
        val user = conversation(chatId = "shared", chatType = 1, content = "user")
        val directMessage = outgoingMessage(
            chatId = "shared",
            chatType = 1,
            content = "direct latest",
            timestamp = 3_000L
        )

        val updated = listOf(group, user).withLatestMessage(directMessage)!!

        assertEquals(1, updated[0].chatType)
        assertEquals("direct latest", updated[0].chatContent)
        assertEquals("group", updated[1].chatContent)
    }

    @Test
    fun `send timestamp identifies latest message when conversation update time differs`() {
        val conversations = listOf(
            conversation(
                chatId = "target",
                content = "old content",
                timestamp = 3_000L,
                sendTimestamp = 2_000L
            )
        )
        val editedMessage = outgoingMessage(
            chatId = "target",
            content = "edited content",
            timestamp = 2_000L
        ).copy(isEdited = true)

        val updated = conversations.withEditedLatestMessage(editedMessage)

        assertEquals("edited content", updated.single().chatContent)
        assertEquals(2_000L, updated.single().latestMessageTimestamp)
    }

    @Test
    fun `known latest message id allows timestamp-less edit`() {
        val conversations = listOf(
            conversation(
                chatId = "target",
                content = "old content",
                timestamp = 2_000L,
                latestMessageId = "message-id"
            )
        )
        val editedMessage = outgoingMessage(
            chatId = "target",
            content = "edited content",
            timestamp = 0L
        ).copy(isEdited = true)

        val updated = conversations.withEditedLatestMessage(editedMessage)

        assertEquals("edited content", updated.single().chatContent)
    }

    @Test
    fun `stream chunks only append to the matching latest text message`() {
        val latest = conversation(
            chatId = "target",
            content = "hello",
            latestMessageId = "latest-id",
            latestContentType = MessageItem.CONTENT_TYPE_TEXT
        )
        val other = conversation(
            chatId = "other",
            content = "unchanged",
            latestMessageId = "other-id",
            latestContentType = MessageItem.CONTENT_TYPE_TEXT
        )

        val updated = listOf(latest, other)
            .withStreamedLatestMessage("latest-id", " world")

        assertEquals("hello world", updated[0].chatContent)
        assertEquals("unchanged", updated[1].chatContent)
        assertSame(updated, updated.withStreamedLatestMessage("older-id", "ignored"))
    }

    @Test
    fun `recalling latest message replaces preview with recall state`() {
        val conversations = listOf(
            conversation(
                chatId = "target",
                content = "latest content",
                timestamp = 2_000L,
                latestMessageId = "message-id"
            )
        )
        val recalled = outgoingMessage(
            chatId = "target",
            content = "latest content",
            timestamp = 0L
        ).copy(isRecalled = true)

        val updated = conversations.withRecalledLatestMessage(recalled)

        assertEquals("此消息已被撤回", updated.single().chatContent)
    }

    @Test
    fun `message id is required before an equal timestamp edit is trusted`() {
        val conversation = conversation(
            chatId = "target",
            timestamp = 2_000L,
            latestMessageId = null
        )
        val edit = outgoingMessage(
            chatId = "target",
            content = "edited",
            timestamp = 2_000L,
            msgId = "unknown-id"
        )

        assertEquals(LatestMessageRelation.UNKNOWN, conversation.relationToLatest(edit))
        assertEquals(
            LatestMessageRelation.DIFFERENT,
            conversation.relationToLatest(edit.copy(timestamp = 1_999L))
        )
    }

    @Test
    fun `refresh replaces stale known preview when no websocket update raced it`() {
        val stale = conversation(
            chatId = "target",
            content = "stale",
            timestamp = 2_000L,
            latestMessageId = "same-id",
            latestMessageSeq = 20L
        )
        val refreshed = stale.copy(chatContent = "server edited")

        val merged = mergeRefreshedConversations(
            refreshed = listOf(refreshed),
            current = listOf(stale),
            protectedKeys = emptySet()
        )

        assertEquals("server edited", merged.single().chatContent)
    }

    @Test
    fun `refresh batch replaces a cached preview with the latest server message`() {
        val stale = conversation(
            chatId = "target",
            content = "stale",
            timestamp = 1_000L,
            latestMessageId = "old-id",
            latestMessageSeq = 10L
        )
        val latest = outgoingMessage(
            chatId = "target",
            content = "latest",
            timestamp = 2_000L,
            msgId = "new-id",
            msgSeq = 11L
        )

        val refreshed = listOf(stale).withLatestMessages(
            messagesByConversation = mapOf((2 to "target") to latest),
            incrementUnread = false
        )

        assertEquals("latest", refreshed.single().chatContent)
        assertEquals("new-id", refreshed.single().latestMessageId)
        assertEquals(11L, refreshed.single().latestMessageSeq)
        assertEquals(0, refreshed.single().unreadMessage)
    }

    @Test
    fun `refresh does not clear existing unread indicators`() {
        val current = conversation(
            chatId = "target",
            content = "local preview",
            unreadCount = 3,
            at = 1
        )
        val refreshed = current.copy(
            chatContent = "server preview",
            unreadMessage = 0,
            at = 0
        )

        val merged = mergeRefreshedConversations(
            refreshed = listOf(refreshed),
            current = listOf(current),
            protectedKeys = emptySet()
        )

        assertEquals("server preview", merged.single().chatContent)
        assertEquals(3, merged.single().unreadMessage)
        assertEquals(1, merged.single().at)
    }

    @Test
    fun `cache snapshot replacement preserves unread indicators before writing Room rows`() {
        val cached = conversation(
            chatId = "target",
            content = "cached preview",
            unreadCount = 3,
            at = 1
        )
        val server = cached.copy(
            chatContent = "server preview",
            unreadMessage = 0,
            at = 0
        )

        val merged = mergeCachedConversationSnapshot(
            refreshed = listOf(server),
            cached = listOf(cached)
        )

        assertEquals("server preview", merged.single().chatContent)
        assertEquals(3, merged.single().unreadMessage)
        assertEquals(1, merged.single().at)
    }

    @Test
    fun `local read guard wins over a stale refresh with unread messages`() {
        val conversation = conversation(
            chatId = "target",
            content = "latest preview",
            unreadCount = 3,
            at = 1
        )

        val merged = mergeCachedConversationSnapshot(
            refreshed = listOf(conversation),
            cached = listOf(conversation.copy(unreadMessage = 0, at = 0)),
            locallyRead = setOf(ConversationKey("target", 2))
        )

        assertEquals(0, merged.single().unreadMessage)
        assertEquals(0, merged.single().at)
    }

    @Test
    fun `refresh accepts a higher server unread count`() {
        val current = conversation(chatId = "target", unreadCount = 2)
        val refreshed = current.copy(unreadMessage = 4)

        val merged = mergeRefreshedConversations(
            refreshed = listOf(refreshed),
            current = listOf(current),
            protectedKeys = emptySet()
        )

        assertEquals(4, merged.single().unreadMessage)
    }

    @Test
    fun `refresh preserves a raced websocket preview and its front position`() {
        val serverFirst = conversation(
            chatId = "other",
            content = "server first",
            timestamp = 3_000L,
            latestMessageId = "other-id",
            latestMessageSeq = 30L
        )
        val staleTarget = conversation(
            chatId = "target",
            content = "stale target",
            timestamp = 2_000L,
            latestMessageId = "old-id",
            latestMessageSeq = 20L
        )
        val websocketTarget = staleTarget.copy(
            chatContent = "websocket latest",
            timestampMs = 4_000L,
            sendTimestamp = 4_000L,
            latestMessageId = "ws-id",
            latestMessageSeq = 40L
        )

        val merged = mergeRefreshedConversations(
            refreshed = listOf(serverFirst, staleTarget),
            current = listOf(websocketTarget, serverFirst),
            protectedKeys = setOf(2 to "target")
        )

        assertEquals(listOf("target", "other"), merged.map(ConversationItem::chatId))
        assertEquals("websocket latest", merged.first().chatContent)
        assertEquals("ws-id", merged.first().latestMessageId)
    }

    private fun conversation(
        chatId: String,
        content: String = "old message",
        unreadCount: Int = 0,
        at: Int = 0,
        timestamp: Long = 1L,
        sendTimestamp: Long = timestamp,
        chatType: Int = 2,
        latestMessageId: String? = null,
        latestMessageSeq: Long = 0L,
        latestContentType: Int = 0
    ) = ConversationItem(
        chatId = chatId,
        chatType = chatType,
        name = chatId,
        chatContent = content,
        timestampMs = timestamp,
        sendTimestamp = sendTimestamp,
        unreadMessage = unreadCount,
        at = at,
        avatarUrl = "",
        latestMessageId = latestMessageId,
        latestMessageSeq = latestMessageSeq,
        latestContentType = latestContentType
    )

    private fun outgoingMessage(
        chatId: String,
        content: String,
        contentType: Int = MessageItem.CONTENT_TYPE_TEXT,
        timestamp: Long = 2L,
        chatType: Int = 2,
        msgId: String = "message-id",
        msgSeq: Long = 0L
    ) = MessageItem(
        msgId = msgId,
        senderId = "me",
        senderName = "Me",
        senderAvatar = "",
        chatId = chatId,
        chatType = chatType,
        content = content,
        contentType = contentType,
        timestamp = timestamp,
        msgSeq = msgSeq,
        direction = "right"
    )
}
