package com.juhao.murexide.ui.chat

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.datastore.UserAccount
import com.juhao.murexide.ui.theme.MurexideTheme
import kotlinx.coroutines.launch

class ChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val chatId = intent.getStringExtra("chat_id") ?: return finish()
        val chatType = intent.getIntExtra("chat_type", 1)
        val chatName = intent.getStringExtra("chat_name") ?: ""
        val chatAvatar = intent.getStringExtra("chat_avatar") ?: ""

        val accountStorage = AccountStorage.getInstance(this)
        val accountState = mutableStateOf<UserAccount?>(null)

        setContent {
            MurexideTheme {
                val account = accountState.value
                if (account == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    ChatScreen(
                        chatType = chatType,
                        chatName = chatName,
                        chatId = chatId,
                        chatAvatar = chatAvatar,
                        onBackClick = { finish() },
                        viewModel = viewModel(
                            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                    return ChatViewModel(
                                        token = account.token,
                                        chatId = chatId,
                                        chatType = chatType,
                                        currentUserId = account.id,
                                        currentUserName = account.username,
                                        currentUserAvatar = account.avatar
                                    ) as T
                                }
                            }
                        )
                    )
                }
            }
        }

        lifecycleScope.launch {
            val account = runCatching { accountStorage.getCurrentUserInfo() }.getOrNull()
            if (account == null || account.token.isEmpty()) {
                finish()
                return@launch
            }
            accountState.value = account
        }
    }

    companion object {
        fun start(
            context: Context,
            chatId: String,
            chatType: Int,
            chatName: String,
            chatAvatar: String
        ) {
            val intent = android.content.Intent(context, ChatActivity::class.java).apply {
                putExtra("chat_id", chatId)
                putExtra("chat_type", chatType)
                putExtra("chat_name", chatName)
                putExtra("chat_avatar", chatAvatar)
            }
            context.startActivity(intent)
        }
    }
}
