package com.juhao.murexide.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.juhao.murexide.MainActivity
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.datastore.UserAccount
import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.login.LoginActivity
import kotlinx.coroutines.launch

/** 主页“我的”导航项长按后展示的快捷账号切换菜单。 */
@Composable
fun AccountQuickSwitchMenu(
    expanded: Boolean,
    accounts: List<UserAccount>,
    currentAccountId: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accountStorage = AccountStorage.getInstance(context.applicationContext)

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        DropdownMenuItem(
            text = {
                Text(
                    text = "添加账号",
                    style = MaterialTheme.typography.titleSmall
                )
            },
            onClick = {
                onDismissRequest()
                LoginActivity.start(context, isAddMode = true)
            },
            leadingIcon = {
                Icon(AppIcons.Add, contentDescription = null)
            }
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        Column(
            modifier = Modifier
                .heightIn(max = 320.dp)
                .padding(vertical = 4.dp)
                .verticalScroll(rememberScrollState())
        ) {
            accounts.forEach { account ->
                val isCurrentAccount = account.id == currentAccountId
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            QuickSwitchAvatar(
                                account = account,
                                isCurrentAccount = isCurrentAccount
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = account.username,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "ID: ${account.id}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    onClick = {
                        onDismissRequest()
                        if (!isCurrentAccount) {
                            scope.launch {
                                accountStorage.switchAccount(account.id)
                                context.startActivity(
                                    Intent(context, MainActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun QuickSwitchAvatar(account: UserAccount, isCurrentAccount: Boolean) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(
                if (isCurrentAccount) {
                    Modifier
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(2.dp)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (account.avatar.isNotBlank()) {
            Avatar(url = account.avatar, size = if (isCurrentAccount) 36.dp else 40.dp)
        } else {
            Box(
                modifier = Modifier
                    .size(if (isCurrentAccount) 36.dp else 40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
