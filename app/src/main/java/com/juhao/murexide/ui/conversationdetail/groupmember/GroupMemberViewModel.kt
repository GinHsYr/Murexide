package com.juhao.murexide.ui.conversationdetail.groupmember

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.data.GroupMember
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.repository.GroupMemberRepository
import com.juhao.murexide.repository.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupMemberUiState(
    val members: List<GroupMember> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val currentPage: Int = 1,
    val pageSize: Int = 50,
    val error: String? = null,
    val isOwner: Boolean = false,
    val isAdmin: Boolean = false,
    val showKickConfirm: Boolean = false,
    val kickTarget: GroupMember? = null,
    val showGagDialog: Boolean = false,
    val gagTarget: GroupMember? = null,
    val showSetAdminConfirm: Boolean = false,
    val setAdminTarget: GroupMember? = null,
    val targetAdminStatus: Boolean = false,
    val showCancelAdminConfirm: Boolean = false,
    val cancelAdminTarget: GroupMember? = null,
    val isPerformingAction: Boolean = false,
)

class GroupMemberViewModel(
    private val groupId: String,
    myPermission: Int,
    context: Context
) : ViewModel() {

    private val repository = GroupMemberRepository()
    private val groupRepository = GroupRepository()
    private val accountStorage = AccountStorage(context)

    private val _uiState = MutableStateFlow(
        GroupMemberUiState(
            isOwner = myPermission == 100,
            isAdmin = myPermission >= 2
        )
    )
    val uiState: StateFlow<GroupMemberUiState> = _uiState.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    init {
        loadMembers()
    }

    fun loadMembers(reset: Boolean = true) {
        if (reset) {
            _uiState.update {
                it.copy(
                    members = emptyList(),
                    currentPage = 1,
                    hasMore = true,
                    isLoading = true,
                    error = null
                )
            }
        } else {
            _uiState.update { it.copy(isLoadingMore = true) }
        }

        viewModelScope.launch {
            val state = _uiState.value
            val result = repository.listMembers(
                token = getToken(),
                groupId = groupId,
                page = state.currentPage,
                size = state.pageSize
            )

            result.onSuccess { newMembers ->
                _uiState.update {
                    val allMembers = if (reset) newMembers else it.members + newMembers
                    it.copy(
                        members = allMembers,
                        isLoading = false,
                        isLoadingMore = false,
                        hasMore = newMembers.isNotEmpty(),
                        currentPage = if (reset) 2 else it.currentPage + 1,
                        error = null
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = e.message
                    )
                }
                showToast("加载失败: ${e.message}")
            }
        }
    }

    fun loadMore() {
        if (_uiState.value.hasMore && !_uiState.value.isLoadingMore) {
            loadMembers(reset = false)
        }
    }

    fun refresh() {
        loadMembers(reset = true)
    }

    fun kickMember(member: GroupMember) {
        if (member.permissionLevel == 100) {
            showToast("不能踢出群主")
            return
        }
        _uiState.update {
            it.copy(
                showKickConfirm = true,
                kickTarget = member
            )
        }
    }

    fun confirmKick() {
        val target = _uiState.value.kickTarget ?: return
        _uiState.update { it.copy(isPerformingAction = true) }

        viewModelScope.launch {
            val result = groupRepository.removeMember(
                token = getToken(),
                groupId = groupId,
                userId = target.userId
            )

            _uiState.update { it.copy(isPerformingAction = false, showKickConfirm = false, kickTarget = null) }

            result.onSuccess {
                showToast("已踢出 ${target.name}")
                // 从列表中移除
                _uiState.update {
                    it.copy(
                        members = it.members.filter { it.userId != target.userId }
                    )
                }
            }.onFailure { e ->
                showToast("踢出失败: ${e.message}")
            }
        }
    }

    fun showGagDialog(member: GroupMember) {
        if (member.permissionLevel == 100) {
            showToast("不能禁言群主")
            return
        }
        _uiState.update {
            it.copy(
                showGagDialog = true,
                gagTarget = member
            )
        }
    }

    fun confirmGag(duration: Int) {
        val target = _uiState.value.gagTarget ?: return
        _uiState.update { it.copy(isPerformingAction = true) }

        viewModelScope.launch {
            val result = groupRepository.gagMember(
                token = getToken(),
                groupId = groupId,
                userId = target.userId,
                gag = duration
            )

            _uiState.update { it.copy(isPerformingAction = false, showGagDialog = false, gagTarget = null) }

            result.onSuccess {
                val durationText = when (duration) {
                    0 -> "取消禁言"
                    600 -> "禁言10分钟"
                    3600 -> "禁言1小时"
                    21600 -> "禁言6小时"
                    43200 -> "禁言12小时"
                    1 -> "永久禁言"
                    else -> "已操作"
                }
                showToast("${target.name} $durationText")
                refresh()
            }.onFailure { e ->
                showToast("操作失败: ${e.message}")
            }
        }
    }

    fun showSetAdmin(member: GroupMember) {
        if (!_uiState.value.isOwner) {
            showToast("只有群主可以设置管理员")
            return
        }
        if (member.permissionLevel == 100) {
            showToast("群主本身就是管理员")
            return
        }
        if (member.permissionLevel == 2) {
            showToast("${member.name} 已经是管理员")
            return
        }
        _uiState.update {
            it.copy(
                showSetAdminConfirm = true,
                setAdminTarget = member,
                targetAdminStatus = true
            )
        }
    }

    fun showCancelAdmin(member: GroupMember) {
        if (!_uiState.value.isOwner) {
            showToast("只有群主可以取消管理员")
            return
        }
        if (member.permissionLevel == 100) {
            showToast("不能取消群主的管理员权限")
            return
        }
        if (member.permissionLevel != 2) {
            showToast("${member.name} 不是管理员")
            return
        }
        _uiState.update {
            it.copy(
                showCancelAdminConfirm = true,
                cancelAdminTarget = member
            )
        }
    }

    fun confirmSetAdmin() {
        val target = _uiState.value.setAdminTarget ?: return
        _uiState.update { it.copy(isPerformingAction = true) }

        viewModelScope.launch {
            val result = groupRepository.setAdmin(
                token = getToken(),
                groupId = groupId,
                userId = target.userId,
                isAdmin = true
            )

            _uiState.update {
                it.copy(
                    isPerformingAction = false,
                    showSetAdminConfirm = false,
                    setAdminTarget = null
                )
            }

            result.onSuccess {
                showToast("已设置 ${target.name} 为管理员")
                refresh()
            }.onFailure { e ->
                showToast("设置失败: ${e.message}")
            }
        }
    }

    fun confirmCancelAdmin() {
        val target = _uiState.value.cancelAdminTarget ?: return
        _uiState.update { it.copy(isPerformingAction = true) }

        viewModelScope.launch {
            val result = groupRepository.setAdmin(
                token = getToken(),
                groupId = groupId,
                userId = target.userId,
                isAdmin = false
            )

            _uiState.update {
                it.copy(
                    isPerformingAction = false,
                    showCancelAdminConfirm = false,
                    cancelAdminTarget = null
                )
            }

            result.onSuccess {
                showToast("已取消 ${target.name} 的管理员权限")
                refresh()
            }.onFailure { e ->
                showToast("操作失败: ${e.message}")
            }
        }
    }

    fun dismissDialogs() {
        _uiState.update {
            it.copy(
                showKickConfirm = false,
                kickTarget = null,
                showGagDialog = false,
                gagTarget = null,
                showSetAdminConfirm = false,
                setAdminTarget = null,
                showCancelAdminConfirm = false,
                cancelAdminTarget = null
            )
        }
    }

    private suspend fun getToken(): String {
        return accountStorage.getCurrentToken() ?: ""
    }

    private fun showToast(message: String) {
        _toastMessage.value = message
    }

    fun toastShown() {
        _toastMessage.value = null
    }

    companion object {
        fun provideFactory(
            groupId: String,
            myPermission: Int,
            context: Context
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return GroupMemberViewModel(groupId, myPermission, context) as T
            }
        }
    }
}