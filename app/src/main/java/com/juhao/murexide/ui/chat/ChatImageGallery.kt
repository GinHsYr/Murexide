package com.juhao.murexide.ui.chat

import com.juhao.murexide.data.MessageItem

internal enum class ChatMediaKind {
    IMAGE,
    VIDEO
}

internal data class ChatMediaGalleryEntry(
    val messageId: String,
    val sequence: Long,
    val url: String,
    val kind: ChatMediaKind
)

internal data class ChatMediaGallery(
    val entries: List<ChatMediaGalleryEntry>,
    val initialIndex: Int
)

internal data class EarlierChatMediaPage(
    val entries: List<ChatMediaGalleryEntry>,
    val nextAnchorMessageId: String?,
    val hasMoreMessages: Boolean
) {
    val shouldContinueLoading: Boolean
        get() = entries.isEmpty() && hasMoreMessages
}

/**
 * Builds the media pager in chronological order.
 *
 * The chat list is stored newest-first because it is rendered with
 * reverseLayout. Reverse it for the viewer so a right swipe moves toward
 * earlier media. Stickers deliberately remain standalone previews.
 */
internal fun buildChatMediaGallery(
    messages: List<MessageItem>,
    selectedMessageId: String
): ChatMediaGallery? {
    val entries = messages.asReversed().mapNotNull(MessageItem::toChatMediaGalleryEntry)

    val initialIndex = entries.indexOfFirst { it.messageId == selectedMessageId }
    return if (initialIndex >= 0) {
        ChatMediaGallery(entries = entries, initialIndex = initialIndex)
    } else {
        null
    }
}

internal fun MessageItem.toChatMediaGalleryEntry(): ChatMediaGalleryEntry? {
    if (isRecalled) return null

    val kind: ChatMediaKind
    val mediaUrl = when (contentType) {
        MessageItem.CONTENT_TYPE_IMAGE -> {
            kind = ChatMediaKind.IMAGE
            imageUrl
        }
        MessageItem.CONTENT_TYPE_VIDEO -> {
            kind = ChatMediaKind.VIDEO
            videoUrl
        }
        else -> return null
    }
    val url = mediaUrl?.takeIf { it.isNotBlank() } ?: return null

    return ChatMediaGalleryEntry(
        messageId = msgId,
        sequence = msgSeq,
        url = url,
        kind = kind
    )
}

internal fun buildEarlierChatMediaPage(
    messages: List<MessageItem>,
    knownMessageIds: Set<String>,
    currentAnchorMessageId: String,
    pageSize: Int
): EarlierChatMediaPage {
    val nextAnchor = messages.lastOrNull()?.msgId?.takeIf { it.isNotBlank() }
    val entries = messages
        .asReversed()
        .mapNotNull(MessageItem::toChatMediaGalleryEntry)
        .filter { it.messageId.isNotBlank() && it.messageId !in knownMessageIds }
    val hasMoreMessages = messages.size >= pageSize &&
        nextAnchor != null &&
        nextAnchor != currentAnchorMessageId

    return EarlierChatMediaPage(
        entries = entries,
        nextAnchorMessageId = nextAnchor,
        hasMoreMessages = hasMoreMessages
    )
}
