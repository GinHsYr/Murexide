package com.juhao.murexide.ui.community.detail

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
import com.juhao.murexide.ui.theme.MurexideTheme

class PostDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val postId = resolvePostId()
        if (postId <= 0) {
            Toast.makeText(this, "无效的文章ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val viewModel: PostDetailViewModel by viewModels {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PostDetailViewModel(
                        accountStorage = AccountStorage(this@PostDetailActivity),
                        postId = postId
                    ) as T
                }
            }
        }

        setContent {
            MurexideTheme {
                PostDetailScreen(
                    onBackClick = { finish() },
                    viewModel = viewModel
                )
            }
        }
    }

    private fun resolvePostId(): Int {
        val extraId = intent.getIntExtra(EXTRA_POST_ID, -1)
        if (extraId > 0) return extraId

        intent.data?.getQueryParameter("id")?.toIntOrNull()?.let { return it }

        return -1
    }

    companion object {
        const val EXTRA_POST_ID = "post_id"

        fun start(context: Context, postId: Int) {
            val intent = Intent(context, PostDetailActivity::class.java).apply {
                putExtra(EXTRA_POST_ID, postId)
                if (context !is ComponentActivity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        }
    }
}
