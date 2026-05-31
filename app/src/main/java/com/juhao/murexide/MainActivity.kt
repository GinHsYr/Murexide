package com.juhao.murexide

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.res.stringResource
import com.juhao.murexide.datastore.TokenStorage
import com.juhao.murexide.ui.chat.ChatActivity
import com.juhao.murexide.ui.conversation.ConversationListScreen
import com.juhao.murexide.ui.theme.MurexideTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val tokenStorage = TokenStorage(this)

        lifecycleScope.launch {
            val token = tokenStorage.getToken()
            if (token == null) {
                LoginActivity.start(this@MainActivity)
                finish()
                return@launch
            }

            setContent {
                MurexideTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        MainScreen(token) {
                            lifecycleScope.launch {
                                tokenStorage.clearToken()
                                Toast.makeText(this@MainActivity, "已登出", Toast.LENGTH_SHORT).show()
                                LoginActivity.start(this@MainActivity)
                                finish()
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(token: String, onLogout: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("登出")
                    }
                }
            )
        }
    ) { paddingValues ->
        ConversationListScreen(
            token = token,
            onConversationClick = { currentChat ->
                ChatActivity.start(
                    context = context,
                    chatId = currentChat.chatId,
                    chatType = currentChat.chatType,
                    chatName = currentChat.displayName,
                    chatAvatar = currentChat.avatarUrl,
                )
            },
            modifier = Modifier.padding(paddingValues)
        )
    }
}