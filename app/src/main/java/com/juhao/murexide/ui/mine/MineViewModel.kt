package com.juhao.murexide.ui.mine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.repository.AuthRepository
import com.juhao.murexide.repository.ConversationDetailRepository
import com.juhao.murexide.repository.UserInfo
import com.juhao.murexide.data.SaveUserDataRequest
import com.juhao.murexide.data.UserProfileData
import com.juhao.murexide.utils.QiniuUploader
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class MineUiState {
    object Loading : MineUiState()
    data class Success(
        val userInfo: UserInfo,
        val onlineDay: Int? = null,
        val continuousOnlineDay: Int? = null,
        val introduction: String = "",
        val userProfile: UserProfileData? = null,
        val isUploadingAvatar: Boolean = false,
        val uploadProgress: Float = 0f
    ) : MineUiState()
    data class Error(val message: String) : MineUiState()
}

class MineViewModel(
    private val token: String,
    private val repository: AuthRepository = AuthRepository(),
    private val detailRepository: ConversationDetailRepository = ConversationDetailRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<MineUiState>(MineUiState.Loading)
    val uiState: StateFlow<MineUiState> = _uiState

    private val _eventFlow = MutableSharedFlow<MineEvent>()
    val eventFlow: SharedFlow<MineEvent> = _eventFlow

    sealed class MineEvent {
        data class ShowToast(val message: String) : MineEvent()
        object ProfileUpdated : MineEvent()
    }

    init {
        loadUserInfo()
    }

    fun loadUserInfo() {
        viewModelScope.launch {
            _uiState.value = MineUiState.Loading

            repository.getUserInfo(token).onSuccess { userInfo ->
                _uiState.value = MineUiState.Success(userInfo)

                detailRepository.getDetail(token, userInfo.id, 1).onSuccess { detail ->
                    val current = _uiState.value
                    if (current is MineUiState.Success) {
                        _uiState.value = current.copy(
                            onlineDay = detail.onlineDay,
                            continuousOnlineDay = detail.continuousOnlineDay,
                            introduction = detail.introduction
                        )
                    }
                }

                repository.getUserData(token).onSuccess { profile ->
                    val current = _uiState.value
                    if (current is MineUiState.Success) {
                        _uiState.value = current.copy(userProfile = profile)
                    }
                }
            }.onFailure { error ->
                _uiState.value = MineUiState.Error(error.message ?: "获取用户信息失败")
            }
        }
    }

    fun updateNickname(nickname: String) {
        viewModelScope.launch {
            repository.editNickname(token, nickname).onSuccess {
                loadUserInfo()
            }
        }
    }

    /** 上传并修改头像 */
    fun uploadAndChangeAvatar(context: Context, uri: Uri) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is MineUiState.Success) {
                _uiState.value = currentState.copy(isUploadingAvatar = true, uploadProgress = 0f)
            }

            val uploader = QiniuUploader(
                context = context,
                userToken = token,
                enableWebp = true
            )
            
            uploader.uploadFromUri(
                context = context, 
                uri = uri,
                onProgress = { percent ->
                    val state = _uiState.value
                    if (state is MineUiState.Success) {
                        _uiState.value = state.copy(uploadProgress = percent)
                    }
                }
            ).onSuccess { response ->
                val avatarUrl = "https://chat-img.jwznb.com/${response.key}"
                repository.editAvatar(token, avatarUrl).onSuccess {
                    _eventFlow.emit(MineEvent.ShowToast("头像修改成功"))
                    loadUserInfo()
                }.onFailure { error ->
                    _eventFlow.emit(MineEvent.ShowToast(error.message ?: "修改头像失败"))
                    val state = _uiState.value
                    if (state is MineUiState.Success) {
                        _uiState.value = state.copy(isUploadingAvatar = false)
                    }
                }
            }.onFailure { error ->
                _eventFlow.emit(MineEvent.ShowToast("上传失败: ${error.message}"))
                val state = _uiState.value
                if (state is MineUiState.Success) {
                    _uiState.value = state.copy(isUploadingAvatar = false)
                }
            }
        }
    }

    /** 修改个人资料 */
    fun updateProfile(
        introduction: String,
        gender: Int,
        birthday: Long,
        province: String,
        city: String,
        district: String,
        locationCode: String
    ) {
        viewModelScope.launch {
            val request = SaveUserDataRequest(
                introduction = introduction,
                gender = gender,
                birthday = birthday,
                province = province,
                city = city,
                district = district,
                locationCode = locationCode
            )
            repository.saveUserData(token, request).onSuccess {
                _eventFlow.emit(MineEvent.ShowToast("个人资料修改成功"))
                _eventFlow.emit(MineEvent.ProfileUpdated)
                loadUserInfo()
            }.onFailure { error ->
                _eventFlow.emit(MineEvent.ShowToast(error.message ?: "修改个人资料失败"))
            }
        }
    }
}
