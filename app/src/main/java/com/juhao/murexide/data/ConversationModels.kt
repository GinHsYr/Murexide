package com.juhao.murexide.data

import kotlinx.serialization.Serializable

@Serializable
data class ConversationItem(
    val chatId: String,
    val chatType: Int,
    val name: String,
    val remark: String? = null,
    val chatContent: String,
    val timestampMs: Long,
    val unreadMessage: Int = 0,
    val at: Int = 0,
    val avatarUrl: String,
    val doNotDisturb: Int = 0,
    val certificationLevel: Int = 0
) {
    val displayName: String
        get() = remark?.takeIf { it.isNotBlank() } ?: name
    
    val hasUnread: Boolean
        get() = unreadMessage > 0
    
    val isAtMentioned: Boolean
        get() = at > 0
}

internal fun List<ConversationItem>.withLatestMessage(
    message: MessageItem
): List<ConversationItem>? {
    val index = indexOfFirst {
        it.chatId == message.chatId ||
            (message.chatType == 1 && it.chatId == message.senderId)
    }
    if (index == -1) return null

    val conversations = toMutableList()
    val oldConversation = conversations.removeAt(index)
    conversations.add(
        0,
        oldConversation.copy(
            chatContent = message.getDisplayContent(),
            timestampMs = message.timestamp,
            unreadMessage = oldConversation.unreadMessage + if (message.isMine) 0 else 1
        )
    )
    return conversations
}
