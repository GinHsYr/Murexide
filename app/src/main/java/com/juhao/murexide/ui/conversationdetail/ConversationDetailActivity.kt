package com.juhao.murexide.ui.conversationdetail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.ui.chat.ChatActivity
import com.juhao.murexide.ui.theme.MurexideTheme
import kotlinx.coroutines.runBlocking

/**
 * 会话详情页面。同时处理 yunhu://chat-add?id=xxx&type=user 协议链接。
 */
class ConversationDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val chatId = resolveChatId()
        if (chatId.isNullOrEmpty()) {
            Toast.makeText(this, "无效的会话链接", Toast.LENGTH_SHORT).show()
            return finish()
        }
        val chatType = resolveChatType()
        val chatName = intent.getStringExtra("chat_name") ?: ""
        val chatAvatar = intent.getStringExtra("chat_avatar") ?: ""

        val accountStorage = AccountStorage(this)
        val token = runBlocking { accountStorage.getCurrentToken() }
        if (token == null) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show()
            return finish()
        }

        setContent {
            MurexideTheme {
                ConversationDetailScreen(
                    onBack = { finish() },
                    onEnterChat = { detail ->
                        ChatActivity.start(
                            context = this,
                            chatId = detail.chatId,
                            chatType = detail.chatType,
                            chatName = detail.name,
                            chatAvatar = detail.avatarUrl
                        )
                        finish()
                    },
                    viewModel = viewModel(
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return ConversationDetailViewModel(
                                    token = token,
                                    chatId = chatId,
                                    chatType = chatType,
                                    fallbackName = chatName,
                                    fallbackAvatar = chatAvatar
                                ) as T
                            }
                        }
                    )
                )
            }
        }
    }

    private fun resolveChatId(): String? {
        intent.getStringExtra("chat_id")?.takeIf { it.isNotEmpty() }?.let { return it }
        // yunhu://chat-add?id=xxx&type=user
        return intent.data?.getQueryParameter("id")?.takeIf { it.isNotEmpty() }
    }

    private fun resolveChatType(): Int {
        if (intent.hasExtra("chat_type")) {
            val t = intent.getIntExtra("chat_type", 1)
            if (t in 1..3) return t
        }
        // scheme 里的 type 为 user/group/bot 文本
        return when (intent.data?.getQueryParameter("type")) {
            "group" -> 2
            "bot" -> 3
            else -> 1
        }
    }

    companion object {
        fun start(
            context: Context,
            chatId: String,
            chatType: Int,
            chatName: String = "",
            chatAvatar: String = ""
        ) {
            val intent = Intent(context, ConversationDetailActivity::class.java).apply {
                putExtra("chat_id", chatId)
                putExtra("chat_type", chatType)
                putExtra("chat_name", chatName)
                putExtra("chat_avatar", chatAvatar)
                if (context !is ComponentActivity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        }
    }
}
