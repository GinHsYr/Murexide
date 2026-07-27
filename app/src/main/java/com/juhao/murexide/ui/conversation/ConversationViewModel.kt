package com.juhao.murexide.ui.conversation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.data.ConversationItem
import com.juhao.murexide.data.MessageItem
import com.juhao.murexide.data.withEditedLatestMessage
import com.juhao.murexide.data.withLatestMessage
import com.juhao.murexide.data.withRecalledLatestMessage
import com.juhao.murexide.data.withStreamedLatestMessage
import com.juhao.murexide.network.WebSocketManager
import com.juhao.murexide.repository.ConversationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.juhao.murexide.network.NetworkClient
import com.juhao.murexide.ui.theme.UiCache
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType

sealed class ConversationUiState {
    object Loading : ConversationUiState()
    data class Success(
        val conversations: List<ConversationItem>,
        val stickyConversations: List<StickyItem> = emptyList()
    ) : ConversationUiState()
    data class Error(val message: String) : ConversationUiState()
}

@Serializable
data class StickyItem(
    val id: Long,
    val chatType: Int,
    val chatId: String,
    val chatName: String,
    val avatarUrl: String,
    val certificationLevel: Int
)

@Serializable
data class StickyListResponse(
    val code: Int,
    val data: StickyData? = null,
    val msg: String
)

@Serializable
data class StickyData(
    val sticky: List<StickyItem> = emptyList()
)

class ConversationViewModel(
    private val token: String,
    private val repository: ConversationRepository = ConversationRepository(),
    private val wsManager: WebSocketManager = WebSocketManager.getInstance()
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<ConversationUiState>(ConversationUiState.Loading)
    val uiState: StateFlow<ConversationUiState> = _uiState

    private val _isWsConnected = MutableStateFlow(true)
    val isWsConnected: StateFlow<Boolean> = _isWsConnected
    
    private var currentMd5: String = ""
    private val json = Json { ignoreUnknownKeys = true }
    private val recentMessages = LinkedHashMap<String, RecentMessage>()
    private var stickyConversations: List<StickyItem> = emptyList()

    init {
        loadConversations()
        observeWebSocket()
        observeWsConnection()
    }

    private fun observeWsConnection() {
        viewModelScope.launch {
            wsManager.connectionState.collect { connected ->
                _isWsConnected.value = connected
            }
        }
    }

    private fun observeWebSocket() {
        viewModelScope.launch {
            wsManager.messageFlow.collect { event ->
                when (event) {
                    is WebSocketManager.WsEvent.NewMessage -> handleNewMessage(event.message)
                    is WebSocketManager.WsEvent.LocalMessageSent -> handleNewMessage(event.message)
                    is WebSocketManager.WsEvent.LatestMessageResolved -> {
                        handleResolvedLatestMessage(event.message)
                    }
                    is WebSocketManager.WsEvent.EditMessage -> handleEditedMessage(event.message)
                    is WebSocketManager.WsEvent.StreamContent -> {
                        handleStreamContent(event.msgId, event.content)
                    }
                    is WebSocketManager.WsEvent.MessageDeleted -> {
                        handleRecalledMessage(event.message)
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun handleNewMessage(message: MessageItem) {
        val resolvedMessage = rememberMessage(message, incrementUnread = true)
        var conversationMissing = false
        _uiState.update { state ->
            if (state is ConversationUiState.Success) {
                val conversations = state.conversations.withLatestMessage(resolvedMessage)
                if (conversations != null) {
                    state.copy(conversations = conversations)
                } else {
                    conversationMissing = true
                    state
                }
            } else {
                state
            }
        }

        if (conversationMissing) {
            refresh()
        } else {
            val state = _uiState.value
            if (state is ConversationUiState.Success) {
                UiCache.conversation.value = state.conversations
            }
        }
    }

    private fun handleEditedMessage(message: MessageItem) {
        val resolvedMessage = rememberMessage(message)
        _uiState.update { state ->
            if (state is ConversationUiState.Success) {
                state.copy(
                    conversations = state.conversations.withEditedLatestMessage(resolvedMessage)
                )
            } else {
                state
            }
        }

        val state = _uiState.value
        if (state is ConversationUiState.Success) {
            UiCache.conversation.value = state.conversations
        }
    }

    private fun handleResolvedLatestMessage(message: MessageItem) {
        val resolvedMessage = rememberMessage(message, incrementUnread = false)
        _uiState.update { state ->
            if (state is ConversationUiState.Success) {
                state.conversations.withLatestMessage(
                    message = resolvedMessage,
                    incrementUnread = false
                )?.let { state.copy(conversations = it) } ?: state
            } else {
                state
            }
        }
        syncConversationCache()
    }

    private fun handleStreamContent(msgId: String, content: String) {
        rememberStreamContent(msgId, content)
        _uiState.update { state ->
            if (state is ConversationUiState.Success) {
                state.copy(
                    conversations = state.conversations.withStreamedLatestMessage(
                        msgId = msgId,
                        content = content
                    )
                )
            } else {
                state
            }
        }
        syncConversationCache()
    }

    private fun handleRecalledMessage(message: MessageItem) {
        val resolvedMessage = rememberMessage(message.copy(isRecalled = true))
        _uiState.update { state ->
            if (state is ConversationUiState.Success) {
                state.copy(
                    conversations = state.conversations.withRecalledLatestMessage(resolvedMessage)
                )
            } else {
                state
            }
        }
        syncConversationCache()
    }

    private fun rememberMessage(
        message: MessageItem,
        incrementUnread: Boolean? = null
    ): MessageItem {
        if (message.msgId.isBlank()) return message

        val existing = recentMessages.remove(message.msgId)
        val existingMessage = existing?.message
        val resolved = if (existingMessage == null) {
            message
        } else {
            message.copy(
                senderId = message.senderId.ifBlank { existingMessage.senderId },
                senderName = message.senderName.ifBlank { existingMessage.senderName },
                senderAvatar = message.senderAvatar.ifBlank { existingMessage.senderAvatar },
                chatId = message.chatId.ifBlank { existingMessage.chatId },
                chatType = message.chatType.takeIf { it > 0 } ?: existingMessage.chatType,
                content = when {
                    message.isEdited -> message.content
                    existingMessage.content.length > message.content.length &&
                        existingMessage.content.startsWith(message.content) -> existingMessage.content
                    else -> message.content
                },
                contentType = message.contentType.takeIf { it > 0 } ?: existingMessage.contentType,
                timestamp = message.timestamp.takeIf { it > 0 } ?: existingMessage.timestamp,
                msgSeq = message.msgSeq.takeIf { it > 0 } ?: existingMessage.msgSeq,
                direction = if (message.senderId.isBlank()) existingMessage.direction else message.direction,
                isRecalled = existingMessage.isRecalled || message.isRecalled,
                isEdited = existingMessage.isEdited || message.isEdited
            )
        }

        recentMessages[message.msgId] = RecentMessage(
            message = resolved,
            incrementUnread = incrementUnread ?: existing?.incrementUnread ?: false
        )
        while (recentMessages.size > MAX_RECENT_MESSAGES) {
            val oldestKey = recentMessages.keys.firstOrNull() ?: break
            recentMessages.remove(oldestKey)
        }
        return resolved
    }

    private fun rememberStreamContent(msgId: String, content: String) {
        if (msgId.isBlank() || content.isEmpty()) return
        val recent = recentMessages.remove(msgId) ?: return
        recentMessages[msgId] = recent.copy(
            message = recent.message.copy(content = recent.message.content + content)
        )
    }

    private fun overlayRecentMessages(
        conversations: List<ConversationItem>
    ): List<ConversationItem> {
        val cachedMessages = wsManager.latestConversationMessagesSnapshot()
        val cachedMessageIds = cachedMessages
            .mapNotNull { message -> message.msgId.takeIf { it.isNotBlank() } }
            .toSet()
        val messagesToOverlay = cachedMessages.map { message ->
            RecentMessage(
                message = message,
                incrementUnread = recentMessages[message.msgId]?.incrementUnread ?: false
            )
        } + recentMessages.values.filter { recent ->
            recent.message.msgId.isBlank() || recent.message.msgId !in cachedMessageIds
        }

        return messagesToOverlay
            .sortedWith(
                compareBy<RecentMessage> { it.message.timestamp }
                    .thenBy { it.message.msgSeq }
            )
            .fold(conversations) { current, recent ->
                current.withLatestMessage(
                    message = recent.message,
                    incrementUnread = recent.incrementUnread
                ) ?: current
            }
    }

    private fun syncConversationCache() {
        val state = _uiState.value
        if (state is ConversationUiState.Success) {
            UiCache.conversation.value = state.conversations
        }
    }

    fun loadConversations() {
        viewModelScope.launch {
            _uiState.value = ConversationUiState.Loading

            fetchStickyList()
            repository.getConversationList(token, currentMd5).onSuccess { conversations ->
                val mergedConversations = overlayRecentMessages(conversations)
                _uiState.update { state ->
                    UiCache.conversation.value = mergedConversations
                    if (state is ConversationUiState.Success) {
                        state.copy(
                            conversations = mergedConversations,
                            stickyConversations = stickyConversations
                        )
                    } else {
                        ConversationUiState.Success(
                            conversations = mergedConversations,
                            stickyConversations = stickyConversations
                        )
                    }
                }
            }.onFailure { error ->
                _uiState.value = ConversationUiState.Error(error.message ?: "加载失败")
            }
        }
    }

    private fun fetchStickyList() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val requestBody = "{}".toRequestBody("application/json".toMediaType())
                    val request = Request.Builder()
                        .url("${NetworkClient.BASE_URL}/v1/sticky/list")
                        .post(requestBody)
                        .header("token", token)
                        .build()

                    NetworkClient.okHttpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body.string()
                            Log.d("ConversationViewModel", "Sticky list response: $body")
                            val stickyResponse = json.decodeFromString<StickyListResponse>(body)
                            if (stickyResponse.code == 1) {
                                val stickyList = stickyResponse.data?.sticky ?: emptyList()
                                stickyConversations = stickyList
                                _uiState.update { state ->
                                    if (state is ConversationUiState.Success) {
                                        state.copy(stickyConversations = stickyList)
                                    } else {
                                        state
                                    }
                                }
                            }
                        } else {
                            Log.e("ConversationViewModel", "Sticky list error: ${response.code}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ConversationViewModel", "Failed to fetch sticky list", e)
                }
            }
        }
    }

    fun refresh() {
        currentMd5 = ""
        loadConversations()
    }

    fun clearUnread(chatId: String) {
        recentMessages.entries.forEach { entry ->
            val recent = entry.value
            if (recent.message.belongsToConversation(chatId)) {
                entry.setValue(recent.copy(incrementUnread = false))
            }
        }
        val currentState = _uiState.value
        if (currentState is ConversationUiState.Success) {
            val conversations = currentState.conversations.map {
                if (it.chatId == chatId) it.copy(unreadMessage = 0, at = 0) else it
            }
            _uiState.update { currentState.copy(conversations = conversations) }
            syncConversationCache()
        }
    }
}

private data class RecentMessage(
    val message: MessageItem,
    val incrementUnread: Boolean
)

private fun MessageItem.belongsToConversation(conversationId: String): Boolean {
    return chatId == conversationId ||
        (chatType == 1 && senderId == conversationId)
}

private const val MAX_RECENT_MESSAGES = 100
