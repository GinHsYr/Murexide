package com.juhao.murexide.ui.conversationdetail.groupmember

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juhao.murexide.ui.theme.MurexideTheme

class GroupMemberActivity : ComponentActivity() {
    companion object {
        const val EXTRA_GROUP_ID = "group_id"
        const val EXTRA_MY_PERMISSION = "my_permission"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val groupId = intent.getStringExtra(EXTRA_GROUP_ID) ?: ""
        val myPermission = intent.getIntExtra(EXTRA_MY_PERMISSION, 0)

        setContent {
            MurexideTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current

                    val viewModel: GroupMemberViewModel = viewModel(
                        factory = GroupMemberViewModel.provideFactory(
                            groupId = groupId,
                            myPermission = myPermission,
                            context = context
                        )
                    )
                    GroupMemberScreen(
                        viewModel = viewModel,
                        onBackClick = { finish() }
                    )
                }
            }
        }
    }
}