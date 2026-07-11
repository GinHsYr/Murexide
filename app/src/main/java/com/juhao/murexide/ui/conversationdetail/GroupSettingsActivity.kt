package com.juhao.murexide.ui.conversationdetail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juhao.murexide.datastore.TokenStorage
import com.juhao.murexide.ui.theme.MurexideTheme
import kotlinx.coroutines.runBlocking

class GroupSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val groupId = intent.getStringExtra("group_id") ?: return finish()
        val groupName = intent.getStringExtra("group_name") ?: ""
        val groupAvatar = intent.getStringExtra("group_avatar") ?: ""

        val tokenStorage = TokenStorage(this)
        val token = runBlocking { tokenStorage.getToken() } ?: return finish()

        setContent {
            MurexideTheme {
                GroupSettingsScreen(
                    onBack = { finish() },
                    viewModel = viewModel(
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return GroupSettingsViewModel(
                                    token = token,
                                    groupId = groupId,
                                    fallbackName = groupName,
                                    fallbackAvatar = groupAvatar
                                ) as T
                            }
                        }
                    )
                )
            }
        }
    }

    companion object {
        fun start(
            context: Context,
            groupId: String,
            groupName: String,
            groupAvatar: String
        ) {
            val intent = Intent(context, GroupSettingsActivity::class.java).apply {
                putExtra("group_id", groupId)
                putExtra("group_name", groupName)
                putExtra("group_avatar", groupAvatar)
            }
            context.startActivity(intent)
        }
    }
}
