package com.juhao.murexide.ui.chat

import com.juhao.murexide.data.MessageItem

internal data class OlderMessagePage(
    val newMessages: List<MessageItem>,
    val nextAnchorMessageId: String?,
    val madeCursorProgress: Boolean
)

internal fun resolveOlderMessagePage(
    knownMessageIds: Set<String>,
    currentAnchorMessageId: String,
    messages: List<MessageItem>
): OlderMessagePage {
    val seenMessageIds = knownMessageIds.toMutableSet()
    val newMessages = messages.filter { message ->
        message.msgId.isNotBlank() && seenMessageIds.add(message.msgId)
    }
    val nextAnchorMessageId = messages.lastOrNull()
        ?.msgId
        ?.takeIf { it.isNotBlank() }

    return OlderMessagePage(
        newMessages = newMessages,
        nextAnchorMessageId = nextAnchorMessageId,
        madeCursorProgress = nextAnchorMessageId != null &&
            nextAnchorMessageId != currentAnchorMessageId
    )
}
