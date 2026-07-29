package com.juhao.murexide.data

private const val IMAGE_BASE_URL = "https://chat-img.jwznb.com"
private const val VIDEO_BASE_URL = "https://chat-video1.jwznb.com"
private const val AUDIO_BASE_URL = "https://chat-audio1.jwznb.com"
private const val FILE_BASE_URL = "https://chat-file.jwznb.com"

internal fun createOutgoingMessage(
    msgId: String,
    senderId: String,
    senderName: String,
    senderAvatar: String,
    chatId: String,
    chatType: Int,
    content: MessageContent,
    contentType: Int,
    quoteMsgId: String?,
    commandId: Long? = null,
    commandName: String? = null,
    timestamp: Long = System.currentTimeMillis()
): MessageItem {
    return MessageItem(
        msgId = msgId,
        senderId = senderId,
        senderName = senderName,
        senderAvatar = senderAvatar,
        senderType = 1,
        chatId = chatId,
        chatType = chatType,
        content = content.text,
        contentType = contentType,
        timestamp = timestamp,
        direction = "right",
        quoteMsgId = quoteMsgId,
        quoteMsgText = content.quoteMsgText,
        quoteImageUrl = content.quoteImageUrl,
        imageUrl = content.image.toMediaUrl(IMAGE_BASE_URL),
        imageWidth = content.media?.width,
        imageHeight = content.media?.height,
        audioUrl = content.audio.toMediaUrl(AUDIO_BASE_URL),
        audioTime = content.audioTime,
        videoUrl = content.video.toMediaUrl(VIDEO_BASE_URL),
        fileUrl = content.fileKey.toMediaUrl(FILE_BASE_URL),
        fileName = content.fileName,
        fileSize = content.fileSize,
        cmdName = commandName,
        cmdId = commandId
    )
}

internal fun upsertNewestMessage(
    messages: List<MessageItem>,
    message: MessageItem
): List<MessageItem> {
    if (messages.none { it.msgId == message.msgId }) {
        return listOf(message) + messages
    }
    return messages.map { existing ->
        if (existing.msgId == message.msgId) {
            mergeMessageIdentity(existing, message)
        } else {
            existing
        }
    }
}

/**
 * Reconciles a freshly loaded history page with messages already visible in this chat.
 *
 * History responses retain the original message sender, including for recalled messages. Merge a
 * loaded recall with an already visible copy only to preserve identity when a partial event omitted it.
 */
internal fun reconcileLoadedMessages(
    existingMessages: List<MessageItem>,
    loadedMessages: List<MessageItem>
): List<MessageItem> {
    val existingById = existingMessages.associateBy(MessageItem::msgId)
    return loadedMessages.map { loaded ->
        val existing = existingById[loaded.msgId]
        if (existing != null && loaded.isRecalled && existing.hasReliableSender) {
            mergeMessageIdentity(existing, loaded)
        } else {
            loaded
        }
    }
}

private fun mergeMessageIdentity(
    existing: MessageItem,
    incoming: MessageItem
): MessageItem {
    if (incoming.isRecalled && !incoming.hasReliableSender && existing.hasReliableSender) {
        return incoming.copy(
            senderId = existing.senderId,
            senderName = existing.senderName,
            senderAvatar = existing.senderAvatar,
            senderType = existing.senderType,
            direction = existing.direction,
            hasReliableSender = true
        )
    }

    return incoming.copy(
        senderId = incoming.senderId.ifBlank { existing.senderId },
        senderName = incoming.senderName.ifBlank { existing.senderName },
        senderAvatar = incoming.senderAvatar.ifBlank { existing.senderAvatar }
    )
}

private fun String?.toMediaUrl(baseUrl: String): String? {
    val value = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return when {
        value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true) -> value
        value.startsWith("//") -> "https:$value"
        else -> "$baseUrl/${value.trimStart('/')}"
    }
}
