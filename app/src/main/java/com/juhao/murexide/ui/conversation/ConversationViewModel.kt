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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    
    private val json = Json { ignoreUnknownKeys = true }
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
        var conversationMissing = false
        _uiState.update { state ->
            if (state is ConversationUiState.Success) {
                val conversations = state.conversations.withLatestMessage(message)
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
        _uiState.update { state ->
            if (state is ConversationUiState.Success) {
                state.copy(
                    conversations = state.conversations.withEditedLatestMessage(message)
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
        _uiState.update { state ->
            if (state is ConversationUiState.Success) {
                state.conversations.withLatestMessage(
                    message = message,
                    incrementUnread = false
                )?.let { state.copy(conversations = it) } ?: state
            } else {
                state
            }
        }
        syncConversationCache()
    }

    private fun handleStreamContent(msgId: String, content: String) {
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
        _uiState.update { state ->
            if (state is ConversationUiState.Success) {
                state.copy(
                    conversations = state.conversations.withRecalledLatestMessage(
                        message.copy(isRecalled = true)
                    )
                )
            } else {
                state
            }
        }
        syncConversationCache()
    }

    private fun syncConversationCache() {
        val state = _uiState.value
        if (state is ConversationUiState.Success) {
            UiCache.conversation.value = state.conversations
        }
    }

    fun loadConversations(refreshPreviews: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = ConversationUiState.Loading

            fetchStickyList()
            repository.getConversationList(token).onSuccess { conversations ->
                val displayedConversations = if (refreshPreviews) {
                    refreshMessagePreviews(conversations)
                } else {
                    conversations
                }
                _uiState.update { state ->
                    UiCache.conversation.value = displayedConversations
                    if (state is ConversationUiState.Success) {
                        state.copy(
                            conversations = displayedConversations,
                            stickyConversations = stickyConversations
                        )
                    } else {
                        ConversationUiState.Success(
                            conversations = displayedConversations,
                            stickyConversations = stickyConversations
                        )
                    }
                }
            }.onFailure { error ->
                _uiState.value = ConversationUiState.Error(error.message ?: "加载失败")
            }
        }
    }

    private suspend fun refreshMessagePreviews(
        conversations: List<ConversationItem>
    ): List<ConversationItem> = coroutineScope {
        val updateTime = System.currentTimeMillis()
        conversations.map { conversation ->
            async {
                repository.getLatestMessageByUpdate(
                    token = token,
                    chatId = conversation.chatId,
                    chatType = conversation.chatType,
                    updateTime = updateTime
                ).getOrNull()?.let { message ->
                    conversation.copy(
                        chatContent = message.getDisplayContent(),
                        timestampMs = message.timestamp,
                        sendTimestamp = message.timestamp,
                        latestMessageId = message.msgId,
                        latestMessageSeq = message.msgSeq,
                        latestContentType = message.contentType
                    )
                } ?: conversation
            }
        }.awaitAll()
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
        loadConversations(refreshPreviews = true)
    }

    fun clearUnread(chatId: String) {
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
