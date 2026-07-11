package com.juhao.murexide.data

/**
 * 会话详情统一数据模型（用户 / 群聊 / 机器人）
 */
data class ConversationDetail(
    val chatId: String,
    val chatType: Int,
    val name: String,
    val avatarUrl: String,
    val introduction: String = "",
    // 群聊
    val groupId: String? = null,
    val memberCount: Long? = null,
    val ownerId: String? = null,
    val groupCode: String? = null,
    val categoryName: String? = null,
    val categoryId: Long? = null,
    val myGroupNickname: String? = null,
    val isPrivate: Boolean = false,
    val doNotDisturb: Boolean = false,
    // 群聊设置（权限与开关）
    val permissionLevel: Int = 0,      // 群主 100 / 管理员 2 / 普通 0
    val directJoin: Boolean = false,   // 进群免审核
    val historyMsg: Boolean = false,   // 新成员可见历史消息
    val hideGroupMembers: Boolean = false, // 隐藏群成员
    // 用户
    val nameId: Long? = null,
    val registerTime: String? = null,
    val lastActiveTime: String? = null,
    val onlineDay: Int? = null,
    val continuousOnlineDay: Int? = null,
    val ipGeo: String? = null,
    val isVip: Boolean = false,
    val gender: Int = 3,
    // 机器人
    val createBy: String? = null,
    val createTime: Long? = null,
    val usageCount: Long? = null,
    val isStop: Boolean = false
)

data class ConversationDetailUiState(
    val isLoading: Boolean = true,
    val detail: ConversationDetail? = null,
    val error: String? = null
)
