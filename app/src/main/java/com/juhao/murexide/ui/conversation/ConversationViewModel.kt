package com.juhao.murexide.ui.conversation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.data.ConversationItem
import com.juhao.murexide.data.LatestMessageRelation
import com.juhao.murexide.data.MessageItem
import com.juhao.murexide.data.findConversationFor
import com.juhao.murexide.data.mergeRefreshedConversations
import com.juhao.murexide.data.relationToLatest
import com.juhao.murexide.data.withEditedLatestMessage
import com.juhao.murexide.data.withLatestMessage
import com.juhao.murexide.data.withLatestMessageIdentity
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

import com.juhao.murexide.network.NetworkClient
import com.juhao.murexide.ui.theme.UiCache
import com.juhao.murexide.utils.AppForegroundState
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
    companion object {
        private const val PREVIEW_REFRESH_BATCH_SIZE = 10
    }
    
    private val _uiState = MutableStateFlow<ConversationUiState>(ConversationUiState.Loading)
    val uiState: StateFlow<ConversationUiState> = _uiState

    private val _isWsConnected = MutableStateFlow(true)
    val isWsConnected: StateFlow<Boolean> = _isWsConnected

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing
    
    private val json = Json { ignoreUnknownKeys = true }
    private var stickyConversations: List<StickyItem> = emptyList()
    private var loadJob: Job? = null
    private var loadGeneration = 0
    private val resolvingLatestMutations = mutableSetOf<String>()
    private var realtimePreviewVersion = 0L
    private val realtimePreviewVersionByConversation = mutableMapOf<Pair<Int, String>, Long>()
    private var foregroundSyncEnabled = false

    init {
        loadConversations()
        observeWebSocket()
        observeWsConnection()
        observeAppForeground()
    }

    private fun observeAppForeground() {
        viewModelScope.launch {
            AppForegroundState.returnedToForeground.collect {
                if (foregroundSyncEnabled) refresh()
            }
        }
    }

    fun setForegroundSyncEnabled(enabled: Boolean) {
        foregroundSyncEnabled = enabled
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
            markRealtimePreview(message)
            val state = _uiState.value
            if (state is ConversationUiState.Success) {
                UiCache.conversation.value = state.conversations
            }
        }
    }

    private fun handleEditedMessage(message: MessageItem) {
        handleLatestMutation(message = message, recalled = false)
    }

    private fun handleLatestMutation(message: MessageItem, recalled: Boolean) {
        val state = _uiState.value as? ConversationUiState.Success ?: return
        val conversation = state.conversations.findConversationFor(message) ?: return

        when (conversation.relationToLatest(message)) {
            LatestMessageRelation.MATCHES -> applyLatestMutation(message, recalled)
            LatestMessageRelation.DIFFERENT -> Unit
            LatestMessageRelation.UNKNOWN -> resolveLatestMutation(
                conversation = conversation,
                eventMessage = message,
                recalled = recalled
            )
        }
    }

    private fun applyLatestMutation(message: MessageItem, recalled: Boolean) {
        _uiState.update { state ->
            if (state !is ConversationUiState.Success) return@update state
            val conversations = if (recalled) {
                state.conversations.withRecalledLatestMessage(message.copy(isRecalled = true))
            } else {
                state.conversations.withEditedLatestMessage(message.copy(isEdited = true))
            }
            state.copy(conversations = conversations)
        }
        markRealtimePreview(message)
        syncConversationCache()
    }

    private fun resolveLatestMutation(
        conversation: ConversationItem,
        eventMessage: MessageItem,
        recalled: Boolean
    ) {
        val resolutionKey = buildString {
            append(conversation.chatType)
            append(':')
            append(conversation.chatId)
            append(':')
            append(eventMessage.msgId)
            append(':')
            append(recalled)
        }
        if (!resolvingLatestMutations.add(resolutionKey)) return

        viewModelScope.launch {
            try {
                val latest = repository.getLatestMessage(
                    token = token,
                    chatId = conversation.chatId,
                    chatType = conversation.chatType
                ).getOrNull() ?: return@launch

                if (latest.msgId == eventMessage.msgId) {
                    val resolvedMutation = if (recalled) {
                        latest.copy(
                            isRecalled = true,
                            deleteTime = maxOf(latest.deleteTime, eventMessage.deleteTime),
                            updateTimestamp = maxOf(
                                latest.updateTimestamp,
                                eventMessage.updateTimestamp
                            )
                        )
                    } else {
                        latest.copy(
                            content = eventMessage.content.takeIf { it.isNotEmpty() }
                                ?: latest.content,
                            contentType = eventMessage.contentType.takeIf { it > 0 }
                                ?: latest.contentType,
                            isEdited = true,
                            buttons = eventMessage.buttons.takeIf { it.isNotEmpty() }
                                ?: latest.buttons,
                            updateTimestamp = maxOf(
                                latest.updateTimestamp,
                                eventMessage.updateTimestamp
                            )
                        )
                    }
                    applyLatestMutation(resolvedMutation, recalled)
                } else {
                    _uiState.update { currentState ->
                        if (currentState is ConversationUiState.Success) {
                            currentState.copy(
                                conversations = currentState.conversations
                                    .withLatestMessageIdentity(latest)
                            )
                        } else {
                            currentState
                        }
                    }
                    syncConversationCache()
                }
            } finally {
                resolvingLatestMutations.remove(resolutionKey)
            }
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
        markRealtimePreview(message)
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
        markRealtimePreview(msgId)
        syncConversationCache()
    }

    private fun handleRecalledMessage(message: MessageItem) {
        handleLatestMutation(message = message, recalled = true)
    }

    private fun syncConversationCache() {
        val state = _uiState.value
        if (state is ConversationUiState.Success) {
            UiCache.conversation.value = state.conversations
        }
    }

    private fun markRealtimePreview(message: MessageItem) {
        val state = _uiState.value as? ConversationUiState.Success ?: return
        val conversation = state.conversations.findConversationFor(message) ?: return
        markRealtimePreview(conversation.chatType to conversation.chatId)
    }

    private fun markRealtimePreview(messageId: String) {
        val state = _uiState.value as? ConversationUiState.Success ?: return
        val conversation = state.conversations.firstOrNull { it.latestMessageId == messageId }
            ?: return
        markRealtimePreview(conversation.chatType to conversation.chatId)
    }

    private fun markRealtimePreview(key: Pair<Int, String>) {
        realtimePreviewVersion += 1L
        realtimePreviewVersionByConversation[key] = realtimePreviewVersion
    }

    fun loadConversations(refreshPreviews: Boolean = false) {
        loadJob?.cancel()
        val generation = ++loadGeneration
        val refreshStartVersion = realtimePreviewVersion
        loadJob = viewModelScope.launch {
            val hadVisibleConversations = _uiState.value is ConversationUiState.Success
            _isRefreshing.value = true
            if (!hadVisibleConversations) {
                _uiState.value = ConversationUiState.Loading
            }
            fetchStickyList()
            repository.getConversationList(token).onSuccess { conversations ->
                if (refreshPreviews) {
                    refreshMessagePreviewsProgressively(
                        conversations = conversations,
                        generation = generation,
                        refreshStartVersion = refreshStartVersion
                    )
                } else {
                    publishConversations(conversations, refreshStartVersion)
                    if (generation == loadGeneration) _isRefreshing.value = false
                }
            }.onFailure { error ->
                if (generation != loadGeneration) return@onFailure
                _isRefreshing.value = false
                if (_uiState.value !is ConversationUiState.Success) {
                    _uiState.value = ConversationUiState.Error(error.message ?: "加载失败")
                } else {
                    Log.w("ConversationViewModel", "Conversation refresh failed", error)
                }
            }
        }
    }

    private suspend fun refreshMessagePreviewsProgressively(
        conversations: List<ConversationItem>,
        generation: Int,
        refreshStartVersion: Long
    ) {
        if (conversations.isEmpty()) {
            publishConversations(emptyList(), refreshStartVersion)
            if (generation == loadGeneration) _isRefreshing.value = false
            return
        }

        var refreshedConversations = conversations
        conversations.chunked(PREVIEW_REFRESH_BATCH_SIZE).forEachIndexed { batchIndex, batch ->
            val batchUpdates = coroutineScope {
                batch.map { conversation ->
                    async {
                        val latestMessage = repository.getLatestMessage(
                            token = token,
                            chatId = conversation.chatId,
                            chatType = conversation.chatType
                        ).getOrNull()
                        (conversation.chatType to conversation.chatId) to latestMessage
                    }
                }.awaitAll()
            }

            val latestByConversation = batchUpdates.toMap()
            refreshedConversations = refreshedConversations.map { conversation ->
                val latest = latestByConversation[conversation.chatType to conversation.chatId]
                    ?: return@map conversation
                listOf(conversation).withLatestMessage(
                    message = latest,
                    incrementUnread = false
                )?.singleOrNull() ?: conversation
            }
            publishConversations(refreshedConversations, refreshStartVersion)

            // The pull gesture completes as soon as the first visible batch is on screen. The
            // remaining batches continue in this coroutine and publish independently.
            if (batchIndex == 0 && generation == loadGeneration) {
                _isRefreshing.value = false
            }
        }

        if (generation == loadGeneration) _isRefreshing.value = false
    }

    private fun publishConversations(
        conversations: List<ConversationItem>,
        refreshStartVersion: Long
    ) {
        _uiState.update { state ->
            val displayed = if (state is ConversationUiState.Success) {
                mergeRefreshedConversations(
                    refreshed = conversations,
                    current = state.conversations,
                    protectedKeys = realtimePreviewVersionByConversation
                        .filterValues { it > refreshStartVersion }
                        .keys
                )
            } else {
                conversations
            }
            ConversationUiState.Success(
                conversations = displayed,
                stickyConversations = stickyConversations
            )
        }
        syncConversationCache()
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
