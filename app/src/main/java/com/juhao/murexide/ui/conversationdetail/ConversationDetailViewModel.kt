package com.juhao.murexide.ui.conversationdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.data.ConversationDetail
import com.juhao.murexide.data.ConversationDetailUiState
import com.juhao.murexide.data.GroupMember
import com.juhao.murexide.data.MessageItem
import com.juhao.murexide.data.local.LocalCache
import com.juhao.murexide.repository.CommunityRepository
import com.juhao.murexide.repository.ConversationDetailRepository
import com.juhao.murexide.repository.FriendRepository
import com.juhao.murexide.repository.GroupMemberRepository
import com.juhao.murexide.repository.MessageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State holder for the Telegram-style conversation profile. */
class ConversationDetailViewModel(
    private val token: String,
    private val chatId: String,
    private val chatType: Int,
    fallbackName: String = "",
    fallbackAvatar: String = "",
    private val repository: ConversationDetailRepository = ConversationDetailRepository(),
    private val friendRepository: FriendRepository = FriendRepository(),
    private val memberRepository: GroupMemberRepository = GroupMemberRepository(),
    private val messageRepository: MessageRepository = MessageRepository(),
    private val communityRepository: CommunityRepository = CommunityRepository(token)
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
        if (chatType == 1) {
            loadCreatedBoards()
        }
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
        val maxTab = if (chatType == 2) 2 else 0
        if (index !in 0..maxTab) return
        _uiState.update { it.copy(selectedTab = index) }
        val mediaTab = if (chatType == 2) 1 else 0
        if (index == mediaTab && _uiState.value.mediaMessages.isEmpty()) {
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

    /** Loads history pages until the active media tab receives a new image or video, or history ends. */
    fun loadMoreHistory() {
        val initial = _uiState.value
        if (chatType !in 1..2 || initial.isLoadingHistory || !initial.hasMoreHistory) return
        val mediaTab = if (chatType == 2) 1 else 0
        if (initial.selectedTab != mediaTab) return
        _uiState.update { it.copy(isLoadingHistory = true) }
        viewModelScope.launch {
            var anchor = initial.historyAnchorMessageId
            var hasMore = initial.hasMoreHistory
            var media = initial.mediaMessages
            val existingCount = media.size

            while (hasMore && media.size == existingCount) {
                val result = messageRepository.getMessageList(
                    token = token,
                    chatId = chatId,
                    chatType = chatType,
                    msgId = anchor
                )
                val page = result.getOrElse { error ->
                    _uiState.update { it.copy(isLoadingHistory = false, message = error.message ?: "消息加载失败") }
                    return@launch
                }
                media = (media + page.filter { message ->
                    !message.isRecalled && message.contentType in setOf(
                        MessageItem.CONTENT_TYPE_IMAGE,
                        MessageItem.CONTENT_TYPE_VIDEO
                    )
                }).distinctBy(MessageItem::msgId)
                    .sortedWith(compareByDescending<MessageItem> { it.timestamp }.thenByDescending { it.msgSeq })
                val nextAnchor = page.lastOrNull()?.msgId?.takeIf { it.isNotBlank() }
                hasMore = page.size >= HISTORY_PAGE_SIZE && nextAnchor != null && nextAnchor != anchor
                anchor = nextAnchor
                _uiState.update {
                    it.copy(
                        mediaMessages = media,
                        historyAnchorMessageId = anchor,
                        hasMoreHistory = hasMore
                    )
                }
            }
            _uiState.update { it.copy(isLoadingHistory = false) }
        }
    }

    fun loadCreatedBoards() {
        val current = _uiState.value
        if (chatType != 1 || current.isLoadingCreatedBoards || current.hasLoadedCreatedBoards) return
        _uiState.update { it.copy(isLoadingCreatedBoards = true) }
        viewModelScope.launch {
            communityRepository.getBaListByCreate(chatId)
                .onSuccess { boards ->
                    _uiState.update {
                        it.copy(
                            createdBoards = boards,
                            isLoadingCreatedBoards = false,
                            hasLoadedCreatedBoards = true
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingCreatedBoards = false,
                            message = error.message ?: "获取创建的板块失败"
                        )
                    }
                }
        }
    }

    fun toggleMute() {
        val detail = _uiState.value.detail ?: return
        if (chatType !in 1..2 || _uiState.value.isChangingMute) return
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
