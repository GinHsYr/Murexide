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
        if (existing.msgId == message.msgId) message else existing
    }
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
