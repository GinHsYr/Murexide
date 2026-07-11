package com.juhao.murexide.ui.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.data.BaItem
import com.juhao.murexide.data.PostItem
import com.juhao.murexide.repository.CommunityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CommunityViewModel(
    token: String
) : ViewModel() {

    private val repository = CommunityRepository(token)

    private companion object {
        const val PAGE_SIZE = 20
    }

    private val _uiState = MutableStateFlow(CommunityUiState())
    val uiState: StateFlow<CommunityUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        loadBaList()
        loadPosts()
    }

    private fun loadBaList() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingBa = true)
            val result = repository.getBaList()
            result.onSuccess { list ->
                _uiState.value = _uiState.value.copy(
                    baList = list,
                    isLoadingBa = false
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoadingBa = false,
                    error = e.message
                )
            }
        }
    }

    fun loadPosts(baId: Int? = null) {
        viewModelScope.launch {
            val targetBaId = if (baId != null && baId > 0) baId else 0
            // 加载第一页并重置分页状态
            _uiState.value = _uiState.value.copy(
                isLoadingPosts = true,
                currentBaId = targetBaId,
                currentPage = 1
            )

            val result = if (targetBaId > 0) {
                repository.getPostList(targetBaId, size = PAGE_SIZE, page = 1)
            } else {
                repository.getRecommendPosts(size = PAGE_SIZE, page = 1)
            }

            result.onSuccess { data ->
                _uiState.value = _uiState.value.copy(
                    posts = data.posts,
                    total = data.total,
                    hasMore = data.posts.size < data.total && data.posts.isNotEmpty(),
                    isLoadingPosts = false
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoadingPosts = false,
                    hasMore = false,
                    error = e.message
                )
            }
        }
    }

    fun loadMorePosts() {
        val state = _uiState.value
        // 并发/边界保护
        if (state.isLoadingPosts || state.isLoadingMore || !state.hasMore) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            val nextPage = state.currentPage + 1
            val baId = state.currentBaId

            val result = if (baId > 0) {
                repository.getPostList(baId, size = PAGE_SIZE, page = nextPage)
            } else {
                repository.getRecommendPosts(size = PAGE_SIZE, page = nextPage)
            }

            result.onSuccess { data ->
                val merged = _uiState.value.posts + data.posts
                _uiState.value = _uiState.value.copy(
                    posts = merged,
                    total = data.total,
                    currentPage = nextPage,
                    hasMore = data.posts.isNotEmpty() && merged.size < data.total,
                    isLoadingMore = false
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    error = e.message
                )
            }
        }
    }

    fun toggleLike(postId: Int) {
        viewModelScope.launch {
            val result = repository.toggleLike(postId)
            result.onSuccess {
                updatePostLike(postId)
            }
        }
    }

    fun toggleCollect(postId: Int) {
        viewModelScope.launch {
            val result = repository.toggleCollect(postId)
            result.onSuccess {
                updatePostCollect(postId)
            }
        }
    }

    private fun updatePostLike(postId: Int) {
        val updatedPosts = _uiState.value.posts.map { post ->
            if (post.id == postId) {
                val isLiked = post.isLiked == "0"
                post.copy(
                    isLiked = if (isLiked) "1" else "0",
                    likeNum = if (isLiked) post.likeNum + 1 else post.likeNum - 1
                )
            } else post
        }
        _uiState.value = _uiState.value.copy(posts = updatedPosts)
    }

    private fun updatePostCollect(postId: Int) {
        val updatedPosts = _uiState.value.posts.map { post ->
            if (post.id == postId) {
                val isCollected = post.isCollected == 0
                post.copy(
                    isCollected = if (isCollected) 1 else 0,
                    collectNum = if (isCollected) post.collectNum + 1 else post.collectNum - 1
                )
            } else post
        }
        _uiState.value = _uiState.value.copy(posts = updatedPosts)
    }

    fun selectBa(baId: Int) {
        val currentId = _uiState.value.currentBaId
        if (currentId != baId) {
            loadPosts(baId)
        }
    }

    fun refresh() {
        val currentId = _uiState.value.currentBaId
        if (currentId == 0) {
            loadPosts()
        } else {
            loadPosts(currentId)
        }
    }
}

data class CommunityUiState(
    val baList: List<BaItem> = emptyList(),
    val posts: List<PostItem> = emptyList(),
    val isLoadingBa: Boolean = false,
    val isLoadingPosts: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val currentBaId: Int = 0,
    val currentPage: Int = 1,
    val total: Int = 0,
    val hasMore: Boolean = false
)