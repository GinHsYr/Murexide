package com.juhao.murexide.ui.chat

import com.juhao.murexide.data.MessageItem
import com.juhao.murexide.data.reconcileLoadedMessages

internal data class ServerHistorySnapshot(
    val messages: List<MessageItem>,
    val nextAnchorMessageId: String?,
    val hasMore: Boolean
)

/**
 * Replaces any best-effort cached snapshot with the server's contiguous newest page.
 *
 * Cached rows can come from separate visits and therefore must never establish the history cursor.
 */
internal fun resolveServerHistorySnapshot(
    existingMessages: List<MessageItem>,
    serverMessages: List<MessageItem>
): ServerHistorySnapshot {
    val resolvedMessages = reconcileLoadedMessages(
        existingMessages = existingMessages,
        loadedMessages = serverMessages
    )
    val nextAnchorMessageId = resolvedMessages.lastOrNull()
        ?.msgId
        ?.takeIf { it.isNotBlank() }
    return ServerHistorySnapshot(
        messages = resolvedMessages,
        nextAnchorMessageId = nextAnchorMessageId,
        hasMore = nextAnchorMessageId != null
    )
}

internal data class OlderMessagePage(
    val newMessages: List<MessageItem>,
    val nextAnchorMessageId: String?,
    val madeCursorProgress: Boolean
)

internal data class CachedHistoryPage(
    val newMessages: List<MessageItem>,
    val nextAnchorMessage: MessageItem?,
    val madeCursorProgress: Boolean,
    val hasMore: Boolean
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

internal fun resolveCachedHistoryPage(
    knownMessageIds: Set<String>,
    currentAnchorMessageId: String,
    messages: List<MessageItem>,
    pageSize: Int
): CachedHistoryPage {
    val page = resolveOlderMessagePage(
        knownMessageIds = knownMessageIds,
        currentAnchorMessageId = currentAnchorMessageId,
        messages = messages
    )
    val nextAnchorMessage = messages.lastOrNull()
        ?.takeIf { it.msgId.isNotBlank() }
    return CachedHistoryPage(
        newMessages = page.newMessages,
        nextAnchorMessage = nextAnchorMessage,
        madeCursorProgress = page.madeCursorProgress,
        hasMore = messages.size >= pageSize && page.madeCursorProgress
    )
}
