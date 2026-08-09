package com.juhao.murexide.ui.conversationdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.data.ConversationDetail
import com.juhao.murexide.data.ConversationDetailUiState
import com.juhao.murexide.data.GroupMember
import com.juhao.murexide.data.MessageItem
import com.juhao.murexide.data.local.LocalCache
import com.juhao.murexide.repository.ConversationDetailRepository
import com.juhao.murexide.repository.FriendRepository
import com.juhao.murexide.repository.GroupMemberRepository
import com.juhao.murexide.repository.MessageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State holder for the Telegram-style group profile. User and bot details keep using its base state. */
class ConversationDetailViewModel(
    private val token: String,
    private val chatId: String,
    private val chatType: Int,
    fallbackName: String = "",
    fallbackAvatar: String = "",
    private val repository: ConversationDetailRepository = ConversationDetailRepository(),
    private val friendRepository: FriendRepository = FriendRepository(),
    private val memberRepository: GroupMemberRepository = GroupMemberRepository(),
    private val messageRepository: MessageRepository = MessageRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ConversationDetailUiState(
            isLoading = true,
            detail = ConversationDetail(
                chatId = chatId,
                chatType = chatType,
                name = fallbackName,
                avatarUrl = fallbackAvatar
            )
        )
    )
    val uiState: StateFlow<ConversationDetailUiState> = _uiState.asStateFlow()

    init {
        loadDetail()
        checkAdded()
        if (chatType == 2) loadMembers()
    }

    fun loadDetail() {
        viewModelScope.launch {
            val cached = repository.getCachedDetail(chatId, chatType)
            if (cached != null) {
                _uiState.update { it.copy(isLoading = false, detail = cached, error = null) }
            } else {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }
            val accountId = LocalCache.currentAccountId()
            if (accountId != null &&
                LocalCache.isPayloadFresh(accountId, LocalCache.KIND_DETAIL, "$chatType:$chatId")
            ) return@launch

            repository.getDetail(token, chatId, chatType)
                .onSuccess { detail ->
                    _uiState.update { it.copy(isLoading = false, detail = detail, error = null) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message ?: "加载失败") }
                }
        }
    }

    private fun checkAdded() {
        viewModelScope.launch {
            friendRepository.isAdded(token, chatId, chatType)
                .onSuccess { added -> _uiState.update { it.copy(isAdded = added) } }
        }
    }

    fun addChat() {
        val state = _uiState.value
        if (state.isAdding) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAdding = true) }
            friendRepository.apply(token, chatId, chatType)
                .onSuccess { response ->
                    val detail = state.detail
                    when (response.code) {
                        1 -> _uiState.update {
                            val added = detail?.chatType == 3 || (detail?.chatType == 2 && detail.directJoin)
                            it.copy(
                                isAdding = false,
                                isAdded = if (added) true else it.isAdded,
                                message = if (added) "已加入群聊" else "已发送申请"
                            )
                        }
                        -9 -> _uiState.update { it.copy(isAdding = false, isAdded = true, message = "你已在群聊中") }
                        else -> _uiState.update { it.copy(isAdding = false, message = response.msg) }
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(isAdding = false, message = error.message ?: "添加失败") } }
        }
    }

    fun selectTab(index: Int) {
        if (index !in 0..2) return
        _uiState.update { it.copy(selectedTab = index) }
        if (index == 1 && _uiState.value.mediaMessages.isEmpty()) {
            loadMoreHistory()
        }
    }

    fun loadMembers(refresh: Boolean = false) {
        val current = _uiState.value
        if (chatType != 2 || current.isLoadingMembers || current.isLoadingMoreMembers ||
            (!refresh && !current.hasMoreMembers)
        ) return
        val page = if (refresh) 1 else current.membersPage
        _uiState.update {
            it.copy(
                isLoadingMembers = page == 1,
                isLoadingMoreMembers = page > 1,
                members = if (page == 1) emptyList() else it.members,
                membersPage = page,
                hasMoreMembers = true
            )
        }
        viewModelScope.launch {
            memberRepository.listMembers(token, chatId, page = page).onSuccess { pageItems ->
                _uiState.update {
                    val merged = (if (page == 1) emptyList() else it.members)
                        .plus(pageItems)
                        .distinctBy(GroupMember::userId)
                    it.copy(
                        members = merged,
                        isLoadingMembers = false,
                        isLoadingMoreMembers = false,
                        membersPage = page + 1,
                        hasMoreMembers = pageItems.size >= 50,
                        error = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoadingMembers = false,
                        isLoadingMoreMembers = false,
                        error = error.message ?: "成员加载失败",
                        message = error.message ?: "成员加载失败"
                    )
                }
            }
        }
    }

    /** Loads complete message pages until the active media/file tab receives a new item or history ends. */
    fun loadMoreHistory() {
        val initial = _uiState.value
        if (chatType != 2 || initial.isLoadingHistory || !initial.hasMoreHistory) return
        val selectedTab = initial.selectedTab
        if (selectedTab != 1) return
        _uiState.update { it.copy(isLoadingHistory = true) }
        viewModelScope.launch {
            var anchor = initial.historyAnchorMessageId
            var imageAnchor = initial.mediaImageAnchor
            var hasMore = initial.hasMoreHistory
            var media = initial.mediaMessages
            val existingCount = media.size

            if (imageAnchor == 0L) {
                while (hasMore && imageAnchor == 0L) {
                    val result = messageRepository.getMessageList(token, chatId, chatType, anchor)
                    val page = result.getOrElse { error ->
                        _uiState.update { it.copy(isLoadingHistory = false, message = error.message ?: "消息加载失败") }
                        return@launch
                    }
                    val image = page.firstOrNull {
                        !it.isRecalled && it.contentType == MessageItem.CONTENT_TYPE_IMAGE && it.msgSeq > 0L
                    }
                    imageAnchor = image?.msgSeq ?: 0L
                    val nextAnchor = page.lastOrNull()?.msgId?.takeIf { it.isNotBlank() }
                    hasMore = page.size >= HISTORY_PAGE_SIZE && nextAnchor != null && nextAnchor != anchor
                    anchor = nextAnchor
                }
            }

            if (imageAnchor == 0L) {
                _uiState.update { it.copy(isLoadingHistory = false, hasMoreHistory = false, historyAnchorMessageId = anchor) }
                return@launch
            }

            // The picture endpoint, not the bootstrap message page, determines picture pagination.
            hasMore = true
            while (hasMore && media.size == existingCount) {
                val result = messageRepository.getImageMessageList(
                    token = token,
                    chatId = chatId,
                    chatType = chatType,
                    imageId = imageAnchor,
                    earlierQuantities = HISTORY_PAGE_SIZE * 2,
                    latestQuantities = 0
                )
                val images = result.getOrElse { error ->
                    _uiState.update { it.copy(isLoadingHistory = false, message = error.message ?: "消息加载失败") }
                    return@launch
                }
                val nextImageAnchor = images.minOfOrNull { it.sequence } ?: 0L
                media = (media + images.map { image ->
                    MessageItem(
                        msgId = image.messageId,
                        senderId = "",
                        senderName = "",
                        senderAvatar = "",
                        contentType = MessageItem.CONTENT_TYPE_IMAGE,
                        timestamp = image.timestamp,
                        msgSeq = image.sequence,
                        direction = "left",
                        imageUrl = image.url
                    )
                }).distinctBy(MessageItem::msgId)
                    .sortedWith(compareByDescending<MessageItem> { it.timestamp }.thenByDescending { it.msgSeq })
                hasMore = images.size >= HISTORY_PAGE_SIZE && nextImageAnchor > 0L && nextImageAnchor < imageAnchor
                imageAnchor = nextImageAnchor
                _uiState.update {
                    it.copy(
                        mediaMessages = media,
                        historyAnchorMessageId = anchor,
                        mediaImageAnchor = imageAnchor,
                        hasMoreHistory = hasMore
                    )
                }
            }
            _uiState.update { it.copy(isLoadingHistory = false) }
        }
    }

    fun toggleMute() {
        val detail = _uiState.value.detail ?: return
        if (chatType != 2 || _uiState.value.isChangingMute) return
        val targetMuted = !detail.doNotDisturb
        _uiState.update { it.copy(isChangingMute = true, detail = detail.copy(doNotDisturb = targetMuted)) }
        viewModelScope.launch {
            friendRepository.setNoNotify(token, chatId, targetMuted).onSuccess {
                LocalCache.currentAccountId()?.let { accountId ->
                    LocalCache.setConversationMuted(accountId, chatId, chatType, targetMuted)
                }
                _uiState.update { it.copy(isChangingMute = false, message = if (targetMuted) "已静音" else "已取消静音") }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isChangingMute = false,
                        detail = detail,
                        message = error.message ?: "修改免打扰失败"
                    )
                }
            }
        }
    }

    fun leaveGroup() {
        if (chatType != 2 || _uiState.value.isLeaving) return
        _uiState.update { it.copy(isLeaving = true) }
        viewModelScope.launch {
            friendRepository.deleteFriend(token, chatId, type = 2).onSuccess {
                LocalCache.currentAccountId()?.let { accountId ->
                    LocalCache.removeConversation(accountId, chatId, chatType)
                }
                _uiState.update { it.copy(isLeaving = false, hasLeft = true) }
            }.onFailure { error ->
                _uiState.update { it.copy(isLeaving = false, message = error.message ?: "退出群聊失败") }
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    private companion object {
        const val HISTORY_PAGE_SIZE = 20
    }
}
