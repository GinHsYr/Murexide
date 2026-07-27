package com.juhao.murexide.network

import com.juhao.murexide.data.MessageItem

/**
 * Keeps the latest real-time message for each conversation during the current account session.
 *
 * A SharedFlow intentionally does not replay events to a collector that does not exist yet. This
 * small cache closes that lifecycle gap without polling the conversation API. The cache is cleared
 * whenever the active account changes.
 */
internal class LatestConversationMessageCache(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES
) {
    private val lock = Any()
    private val messages = LinkedHashMap<ConversationKey, MessageItem>()

    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    fun record(message: MessageItem, currentUserId: String?): Boolean = synchronized(lock) {
        val key = message.conversationKey(currentUserId) ?: return@synchronized false
        val existing = messages[key]
        if (existing != null && !message.shouldReplace(existing)) {
            return@synchronized false
        }

        val resolved = if (existing?.msgId == message.msgId && message.msgId.isNotBlank()) {
            existing.mergePartialUpdate(message)
        } else {
            message
        }
        messages.remove(key)
        messages[key] = resolved
        trimToSize()
        true
    }

    fun updateIfLatest(message: MessageItem): Boolean = synchronized(lock) {
        if (message.msgId.isBlank()) return@synchronized false
        val key = messages.entries
            .firstOrNull { (_, latest) -> latest.msgId == message.msgId }
            ?.key
            ?: return@synchronized false
        val latest = messages.getValue(key)
        messages.remove(key)
        messages[key] = latest.mergePartialUpdate(message)
        true
    }

    fun appendStreamContent(msgId: String, content: String): Boolean = synchronized(lock) {
        if (msgId.isBlank() || content.isEmpty()) return@synchronized false
        val key = messages.entries
            .firstOrNull { (_, latest) -> latest.msgId == msgId }
            ?.key
            ?: return@synchronized false
        val latest = messages.getValue(key)
        messages.remove(key)
        messages[key] = latest.copy(content = latest.content + content)
        true
    }

    fun snapshot(): List<MessageItem> = synchronized(lock) {
        messages.values.toList()
    }

    fun clear() = synchronized(lock) {
        messages.clear()
    }

    private fun trimToSize() {
        while (messages.size > maxEntries) {
            val oldest = messages.keys.firstOrNull() ?: return
            messages.remove(oldest)
        }
    }

    private data class ConversationKey(
        val chatType: Int,
        val chatId: String
    )

    private fun MessageItem.conversationKey(currentUserId: String?): ConversationKey? {
        val conversationId = when {
            chatType == CHAT_TYPE_USER &&
                senderId.isNotBlank() &&
                senderId != currentUserId -> senderId
            else -> chatId
        }
        return conversationId
            .takeIf { it.isNotBlank() }
            ?.let { ConversationKey(chatType = chatType, chatId = it) }
    }

    private fun MessageItem.shouldReplace(existing: MessageItem): Boolean {
        if (msgId.isNotBlank() && msgId == existing.msgId) return true

        if (msgSeq > 0L && existing.msgSeq > 0L && msgSeq != existing.msgSeq) {
            return msgSeq > existing.msgSeq
        }
        if (timestamp > 0L && existing.timestamp > 0L && timestamp != existing.timestamp) {
            return timestamp > existing.timestamp
        }

        // WebSocket callbacks are serialized by OkHttp. When the server omits ordering metadata,
        // the last observed push is the strongest ordering signal available.
        return true
    }

    private fun MessageItem.mergePartialUpdate(incoming: MessageItem): MessageItem {
        return incoming.copy(
            senderId = incoming.senderId.ifBlank { senderId },
            senderName = incoming.senderName.ifBlank { senderName },
            senderAvatar = incoming.senderAvatar.ifBlank { senderAvatar },
            senderType = incoming.senderType.takeIf { it > 0 } ?: senderType,
            chatId = incoming.chatId.ifBlank { chatId },
            chatType = incoming.chatType.takeIf { it > 0 } ?: chatType,
            content = when {
                incoming.isEdited -> incoming.content
                incoming.content.isNotEmpty() -> incoming.content
                else -> content
            },
            contentType = incoming.contentType.takeIf { it > 0 } ?: contentType,
            timestamp = incoming.timestamp.takeIf { it > 0 } ?: timestamp,
            deleteTime = incoming.deleteTime.takeIf { it > 0 } ?: deleteTime,
            msgSeq = incoming.msgSeq.takeIf { it > 0 } ?: msgSeq,
            direction = if (incoming.senderId.isBlank()) direction else incoming.direction,
            isRecalled = isRecalled || incoming.isRecalled,
            isEdited = isEdited || incoming.isEdited
        )
    }

    private companion object {
        const val CHAT_TYPE_USER = 1
        const val DEFAULT_MAX_ENTRIES = 256
    }
}
