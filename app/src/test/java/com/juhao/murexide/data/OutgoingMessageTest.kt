package com.juhao.murexide.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutgoingMessageTest {
    @Test
    fun `creates an immediately displayable outgoing text message`() {
        val message = createOutgoingMessage(
            msgId = "message-id",
            senderId = "me",
            senderName = "My name",
            senderAvatar = "avatar",
            chatId = "chat",
            chatType = 2,
            content = MessageContent(
                text = "hello",
                quoteMsgText = "Someone: earlier message"
            ),
            contentType = MessageItem.CONTENT_TYPE_TEXT,
            quoteMsgId = "quoted-id",
            commandId = 12,
            commandName = "command",
            timestamp = 1234
        )

        assertEquals("message-id", message.msgId)
        assertEquals("me", message.senderId)
        assertEquals("hello", message.content)
        assertEquals("quoted-id", message.quoteMsgId)
        assertEquals("Someone: earlier message", message.quoteMsgText)
        assertEquals("command", message.cmdName)
        assertEquals(12L, message.cmdId)
        assertEquals(1234L, message.timestamp)
        assertTrue(message.isMine)
    }

    @Test
    fun `resolves uploaded media keys for local display`() {
        val message = createOutgoingMessage(
            msgId = "media-id",
            senderId = "me",
            senderName = "",
            senderAvatar = "",
            chatId = "chat",
            chatType = 1,
            content = MessageContent(
                image = "images/photo.webp",
                audio = "audio/voice.m4a",
                video = "video/clip.mp4",
                fileKey = "files/report.pdf",
                fileName = "report.pdf",
                fileSize = 42,
                media = MessageMedia(
                    fileKey = "images/photo.webp",
                    width = 1200,
                    height = 1600
                )
            ),
            contentType = MessageItem.CONTENT_TYPE_IMAGE,
            quoteMsgId = null,
            timestamp = 1234
        )

        assertEquals("https://chat-img.jwznb.com/images/photo.webp", message.imageUrl)
        assertEquals("https://chat-audio1.jwznb.com/audio/voice.m4a", message.audioUrl)
        assertEquals("https://chat-video1.jwznb.com/video/clip.mp4", message.videoUrl)
        assertEquals("https://chat-file.jwznb.com/files/report.pdf", message.fileUrl)
        assertEquals("report.pdf", message.fileName)
        assertEquals(42L, message.fileSize)
        assertEquals(1200L, message.imageWidth)
        assertEquals(1600L, message.imageHeight)
    }

    @Test
    fun `keeps absolute media urls unchanged`() {
        val imageUrl = "https://example.com/photo.webp"
        val message = createOutgoingMessage(
            msgId = "absolute-id",
            senderId = "me",
            senderName = "",
            senderAvatar = "",
            chatId = "chat",
            chatType = 1,
            content = MessageContent(image = imageUrl),
            contentType = MessageItem.CONTENT_TYPE_STICKER,
            quoteMsgId = null,
            timestamp = 1234
        )

        assertEquals(imageUrl, message.imageUrl)
    }

    @Test
    fun `inserts a new outgoing message at the start`() {
        val oldMessage = textMessage("old", "old content")
        val newMessage = textMessage("new", "new content")

        assertEquals(listOf(newMessage, oldMessage), upsertNewestMessage(listOf(oldMessage), newMessage))
    }

    @Test
    fun `replaces the local message when the server sends the same id`() {
        val localMessage = textMessage("same", "local content")
        val serverMessage = textMessage("same", "server content").copy(msgSeq = 42)

        val messages = upsertNewestMessage(listOf(localMessage), serverMessage)

        assertEquals(1, messages.size)
        assertEquals(serverMessage, messages.single())
    }

    @Test
    fun `keeps the local sender profile when the server message omits it`() {
        val localMessage = textMessage("same", "local content").copy(
            senderId = "me",
            senderName = "My name",
            senderAvatar = "https://example.com/me.png"
        )
        val serverMessage = textMessage("same", "server content").copy(
            senderId = "",
            senderName = "",
            senderAvatar = "",
            msgSeq = 42
        )

        val message = upsertNewestMessage(listOf(localMessage), serverMessage).single()

        assertEquals("me", message.senderId)
        assertEquals("My name", message.senderName)
        assertEquals("https://example.com/me.png", message.senderAvatar)
        assertEquals("server content", message.content)
        assertEquals(42L, message.msgSeq)
    }

    @Test
    fun `recalled update keeps original author and direction`() {
        val original = textMessage("same", "member message").copy(
            senderId = "member-id",
            senderName = "Member",
            senderAvatar = "member.png",
            direction = "left"
        )
        val recalledFromServer = original.copy(
            senderId = "owner-id",
            senderName = "Group owner",
            senderAvatar = "owner.png",
            direction = "right",
            isRecalled = true,
            recalledById = "owner-id",
            recalledByName = "Group owner",
            hasReliableSender = false
        )

        val message = upsertNewestMessage(listOf(original), recalledFromServer).single()

        assertEquals("member-id", message.senderId)
        assertEquals("Member", message.senderName)
        assertEquals("member.png", message.senderAvatar)
        assertEquals("left", message.direction)
        assertEquals("owner-id", message.recalledById)
        assertEquals("Group owner", message.recalledByName)
        assertTrue(message.hasReliableSender)
    }

    @Test
    fun `cold loaded recall keeps original sender marked as reliable`() {
        val recalled = textMessage("same", "message").copy(
            senderId = "member-id",
            senderName = "Member",
            senderAvatar = "member.png",
            direction = "left",
            isRecalled = true,
            deleteTime = 1234
        )

        val message = reconcileLoadedMessages(emptyList(), listOf(recalled)).single()

        assertTrue(message.hasReliableSender)
        assertEquals("member-id", message.senderId)
        assertEquals("Member", message.senderName)
        assertEquals("member.png", message.senderAvatar)
        assertEquals("left", message.direction)
        assertEquals(null, message.recalledById)
        assertEquals(null, message.recalledByName)
    }

    @Test
    fun `recall display never exposes the operator`() {
        val recalled = textMessage("same", "message").copy(
            isRecalled = true,
            recalledById = "owner-id",
            recalledByName = "Group owner"
        )
        assertEquals("此消息已被撤回", recalled.getRecallDisplayContent())

        val withoutName = recalled.copy(recalledByName = null)
        assertEquals("此消息已被撤回", withoutName.getRecallDisplayContent())
    }

    private fun textMessage(id: String, text: String): MessageItem {
        return createOutgoingMessage(
            msgId = id,
            senderId = "me",
            senderName = "",
            senderAvatar = "",
            chatId = "chat",
            chatType = 1,
            content = MessageContent(text = text),
            contentType = MessageItem.CONTENT_TYPE_TEXT,
            quoteMsgId = null,
            timestamp = 1234
        )
    }
}
