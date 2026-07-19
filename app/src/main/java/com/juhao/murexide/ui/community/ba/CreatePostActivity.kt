package com.juhao.murexide.ui.community.ba

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.ui.community.detail.PostDetailActivity
import com.juhao.murexide.ui.theme.MurexideTheme

class CreatePostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val baId = intent.getIntExtra(EXTRA_BA_ID, -1)
        val baName = intent.getStringExtra(EXTRA_BA_NAME) ?: ""
        if (baId <= 0) {
            Toast.makeText(this, "无效的分区ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val viewModel: CreatePostViewModel by viewModels {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CreatePostViewModel(
                        accountStorage = AccountStorage(this@CreatePostActivity),
                        baId = baId
                    ) as T
                }
            }
        }

        setContent {
            MurexideTheme {
                CreatePostScreen(
                    baName = baName,
                    onClose = { finish() },
                    onPublished = { postId ->
                        Toast.makeText(this, "发布成功", Toast.LENGTH_SHORT).show()
                        if (postId > 0) {
                            PostDetailActivity.start(this, postId)
                        }
                        finish()
                    },
                    viewModel = viewModel
                )
            }
        }
    }

    companion object {
        const val EXTRA_BA_ID = "ba_id"
        const val EXTRA_BA_NAME = "ba_name"

        fun start(context: Context, baId: Int, baName: String) {
            val intent = Intent(context, CreatePostActivity::class.java).apply {
                putExtra(EXTRA_BA_ID, baId)
                putExtra(EXTRA_BA_NAME, baName)
                if (context !is ComponentActivity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        }
    }
}
