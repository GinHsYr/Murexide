package com.juhao.murexide.ui.conversationdetail

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.juhao.murexide.data.ConversationDetail
import com.juhao.murexide.data.GroupMember
import com.juhao.murexide.data.MessageItem
import com.juhao.murexide.ui.components.Avatar
import com.juhao.murexide.ui.components.CapsuleTabBar
import com.juhao.murexide.ui.components.MediaViewerPagination
import com.juhao.murexide.ui.components.imageMessagePreviewItem
import com.juhao.murexide.ui.components.showImageViewer
import com.juhao.murexide.ui.components.videoMessagePreviewItem
import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationDetailScreen(
    viewModel: ConversationDetailViewModel,
    onBack: () -> Unit,
    onEnterChat: (ConversationDetail) -> Unit,
    onEditGroup: (ConversationDetail) -> Unit = {},
    onOpenMember: (GroupMember) -> Unit = {},
    onManageMembers: (ConversationDetail) -> Unit = {},
    onLeaveGroup: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    var showMore by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbars.showSnackbar(message)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(state.hasLeft) {
        if (state.hasLeft) onLeaveGroup()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { AutoMirroredIcon(AppIcons.ArrowBack, "返回") }
                },
                actions = {
                val group = state.detail?.takeIf { it.chatType == 2 }
                if (group?.permissionLevel ?: 0 >= 2) {
                    IconButton(onClick = { onEditGroup(group!!) }) {
                        Icon(AppIcons.Edit, "编辑群聊")
                    }
                }
                Box {
                    IconButton(onClick = { showMore = true }) {
                        Icon(AppIcons.MoreVert, "更多")
                    }
                    DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                        DropdownMenuItem(
                            text = { Text("刷新") },
                            leadingIcon = { Icon(AppIcons.Refresh, null) },
                            onClick = {
                                showMore = false
                                viewModel.loadDetail()
                                if (group != null) viewModel.loadMembers(refresh = true)
                            }
                        )
                        if (group != null && group.permissionLevel >= 2) {
                            DropdownMenuItem(
                                text = { Text("管理成员") },
                                leadingIcon = { Icon(AppIcons.Group, null) },
                                onClick = { showMore = false; onManageMembers(group) }
                            )
                        }
                    }
                }
                }
            )
        }
    ) { padding ->
        val detail = state.detail
        when {
            detail == null && state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            detail == null -> ErrorContent(state.error ?: "加载失败", viewModel::loadDetail)
            detail.chatType == 2 -> GroupConversationDetail(
                modifier = Modifier.padding(padding),
                detail = detail,
                selectedTab = state.selectedTab,
                members = state.members,
                media = state.mediaMessages,
                isLoadingMembers = state.isLoadingMembers,
                isLoadingMoreMembers = state.isLoadingMoreMembers,
                hasMoreMembers = state.hasMoreMembers,
                isLoadingHistory = state.isLoadingHistory,
                hasMoreHistory = state.hasMoreHistory,
                isChangingMute = state.isChangingMute,
                isLeaving = state.isLeaving,
                onMessage = { onEnterChat(detail) },
                onMute = viewModel::toggleMute,
                onLeave = { showLeaveConfirm = true },
                onTabSelected = viewModel::selectTab,
                onLoadMembers = { viewModel.loadMembers() },
                onLoadHistory = viewModel::loadMoreHistory,
                onOpenMember = onOpenMember
            )
            else -> LegacyDetail(
                modifier = Modifier.padding(padding),
                detail = detail,
                isAdded = state.isAdded,
                isAdding = state.isAdding,
                onAdd = viewModel::addChat,
                onMessage = { onEnterChat(detail) }
            )
        }
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { if (!state.isLeaving) showLeaveConfirm = false },
            icon = { Icon(AppIcons.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("退出群聊") },
            text = { Text("确定要退出该群聊吗？") },
            confirmButton = {
                TextButton(
                    enabled = !state.isLeaving,
                    onClick = viewModel::leaveGroup
                ) {
                    if (state.isLeaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("退出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(enabled = !state.isLeaving, onClick = { showLeaveConfirm = false }) { Text("取消") }
            }
        )
    }

}

@Composable
private fun GroupConversationDetail(
    modifier: Modifier,
    detail: ConversationDetail,
    selectedTab: Int,
    members: List<GroupMember>,
    media: List<MessageItem>,
    isLoadingMembers: Boolean,
    isLoadingMoreMembers: Boolean,
    hasMoreMembers: Boolean,
    isLoadingHistory: Boolean,
    hasMoreHistory: Boolean,
    isChangingMute: Boolean,
    isLeaving: Boolean,
    onMessage: () -> Unit,
    onMute: () -> Unit,
    onLeave: () -> Unit,
    onTabSelected: (Int) -> Unit,
    onLoadMembers: () -> Unit,
    onLoadHistory: () -> Unit,
    onOpenMember: (GroupMember) -> Unit
) {
    var introductionExpanded by remember(detail.introduction) { mutableStateOf(false) }
    val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val mediaRows = media.chunked(3)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item(key = "header") {
            GroupHeader(
                detail = detail,
                onMessage = onMessage,
                onMute = onMute,
                onLeave = onLeave,
                isChangingMute = isChangingMute,
                isLeaving = isLeaving,
                introductionExpanded = introductionExpanded,
                onIntroductionClick = {
                    introductionExpanded = true
                }
            )
        }
        item(key = "tabs") {
            DetailCardSegment(cardColor = cardColor, isTop = true) {
                val labels = listOf("成员", "媒体", "群云盘")
                CapsuleTabBar(
                    tabs = labels,
                    selectedTabIndex = selectedTab,
                    onTabSelected = onTabSelected,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        when (selectedTab) {
            0 -> when {
                isLoadingMembers && members.isEmpty() -> item(key = "members-loading") {
                    DetailCardSegment(cardColor, isBottom = true, minHeight = 180.dp) { LoadingContent() }
                }
                members.isEmpty() -> item(key = "members-empty") {
                    DetailCardSegment(cardColor, isBottom = true, minHeight = 180.dp) { EmptyContent("暂无可展示的成员") }
                }
                else -> {
                    items(members, key = GroupMember::userId) { member ->
                        val isLast = member == members.last() && !hasMoreMembers
                        DetailCardSegment(cardColor, isBottom = isLast) {
                            MemberRow(member, onClick = { onOpenMember(member) })
                            if (!isLast) HorizontalDivider(Modifier.padding(start = 70.dp))
                        }
                    }
                    if (hasMoreMembers) item(key = "members-load-more") {
                        DetailCardSegment(cardColor, isBottom = true) {
                            LoadMoreRow(isLoadingMoreMembers, onLoadMembers)
                        }
                    }
                }
            }
            1 -> when {
                isLoadingHistory && media.isEmpty() -> item(key = "media-loading") {
                    DetailCardSegment(cardColor, isBottom = true, minHeight = 180.dp) { LoadingContent() }
                }
                media.isEmpty() && !hasMoreHistory -> item(key = "media-empty") {
                    DetailCardSegment(cardColor, isBottom = true, minHeight = 180.dp) { EmptyContent("暂无媒体") }
                }
                else -> {
                    items(mediaRows, key = { row -> row.joinToString(separator = ":") { it.msgId } }) { row ->
                        DetailCardSegment(cardColor, isBottom = row == mediaRows.last() && !hasMoreHistory) {
                            MediaRow(row, media, detail)
                        }
                    }
                    if (hasMoreHistory) item(key = "media-load-more") {
                        DetailCardSegment(cardColor, isBottom = true) {
                            LoadMoreRow(isLoadingHistory, onLoadHistory)
                        }
                    }
                }
            }
            else -> item(key = "cloud-drive") {
                DetailCardSegment(cardColor, isBottom = true, minHeight = 180.dp) { EmptyContent("群云盘功能即将推出") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupHeader(
    detail: ConversationDetail,
    onMessage: () -> Unit,
    onMute: () -> Unit,
    onLeave: () -> Unit,
    isChangingMute: Boolean,
    isLeaving: Boolean,
    introductionExpanded: Boolean,
    onIntroductionClick: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(4.dp))
        Avatar(url = detail.avatarUrl, size = 88.dp, canView = true)
        Spacer(Modifier.height(10.dp))
        Text(detail.name.ifBlank { "未知群聊" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("${detail.memberCount ?: 0} 位成员", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TelegramAction(Modifier.weight(1f), AppIcons.ChatBubbleOutline, "消息", onMessage)
            TelegramAction(
                Modifier.weight(1f),
                if (detail.doNotDisturb) AppIcons.NotificationsOff else AppIcons.Notifications,
                if (detail.doNotDisturb) "取消静音" else "静音",
                onMute,
                isChangingMute
            )
            TelegramAction(Modifier.weight(1f), AppIcons.Logout, "退出", onLeave, isLeaving, isDanger = true)
        }
        if (detail.introduction.isNotBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .combinedClickable(
                        onClick = onIntroductionClick,
                        onLongClick = {
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("群简介", detail.introduction)))
                                Toast.makeText(context, "简介已复制", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                IntroductionContent(
                    introduction = detail.introduction,
                    expanded = introductionExpanded,
                    onExpand = onIntroductionClick
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun IntroductionContent(
    introduction: String,
    expanded: Boolean,
    onExpand: () -> Unit
) {
    var hasOverflow by remember(introduction) { mutableStateOf(false) }
    val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(Modifier.padding(14.dp)) {
        Box {
            Text(
                text = introduction,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Clip,
                onTextLayout = { hasOverflow = it.hasVisualOverflow }
            )
            if (!expanded && hasOverflow) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .fillMaxWidth(.34f)
                        .height(26.dp)
                        .background(Brush.horizontalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, cardColor)))
                        .clickable(onClick = onExpand),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        "更多",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 2.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("简介", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun TelegramAction(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    loading: Boolean = false,
    isDanger: Boolean = false
) {
    Card(
        modifier = modifier.height(56.dp).clickable(enabled = !loading, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Icon(icon, null, tint = if (isDanger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(3.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (isDanger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun MemberRow(member: GroupMember, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(url = member.avatarUrl, size = 46.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(member.name.ifBlank { "未知用户" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    member.permissionLevel == 100 -> "群主"
                    member.permissionLevel >= 2 -> "管理员"
                    member.isGag -> "已禁言"
                    else -> "成员"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MediaRow(row: List<MessageItem>, media: List<MessageItem>, detail: ConversationDetail) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        row.forEach { message ->
            val url = if (message.contentType == MessageItem.CONTENT_TYPE_VIDEO) message.videoUrl else message.imageUrl
            AsyncImage(
                model = url,
                contentDescription = "媒体",
                contentScale = ContentScale.Crop,
                modifier = Modifier.weight(1f).aspectRatio(1f).clickable {
                    val previews = media.map { item ->
                        if (item.contentType == MessageItem.CONTENT_TYPE_VIDEO) {
                            videoMessagePreviewItem(item.videoUrl.orEmpty(), item.msgId, item.msgSeq)
                        } else imageMessagePreviewItem(item.imageUrl.orEmpty(), item.msgId, item.msgSeq)
                    }
                    showImageViewer(
                        context, previews, media.indexOfFirst { it.msgId == message.msgId },
                        MediaViewerPagination(detail.chatId, detail.chatType)
                    )
                }
            )
        }
        repeat(3 - row.size) { Spacer(Modifier.weight(1f).aspectRatio(1f)) }
    }
}

@Composable
private fun DetailCardSegment(
    cardColor: androidx.compose.ui.graphics.Color,
    isTop: Boolean = false,
    isBottom: Boolean = false,
    minHeight: androidx.compose.ui.unit.Dp? = null,
    content: @Composable () -> Unit
) {
    val shape = when {
        isTop -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        isBottom -> RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
        else -> RectangleShape
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = shape,
        color = cardColor
    ) {
        if (minHeight == null) {
            Column { content() }
        } else {
            Box(
                modifier = Modifier.height(minHeight),
                contentAlignment = Alignment.Center
            ) {
                content()
            }
        }
    }
}

@Composable
private fun LoadMoreRow(loading: Boolean, onLoadMore: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(12.dp).clickable(enabled = !loading, onClick = onLoadMore), Alignment.Center) {
        if (loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) else Text("加载更多", color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun LegacyDetail(
    modifier: Modifier,
    detail: ConversationDetail,
    isAdded: Boolean?,
    isAdding: Boolean,
    onAdd: () -> Unit,
    onMessage: () -> Unit
) {
    Column(modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Avatar(url = detail.avatarUrl, size = 88.dp, canView = true)
        Spacer(Modifier.height(14.dp))
        Text(detail.name.ifBlank { "未知" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (detail.introduction.isNotBlank()) Text(detail.introduction, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(20.dp))
        Button(onClick = if (isAdded == false) onAdd else onMessage, enabled = !isAdding, modifier = Modifier.fillMaxWidth()) {
            Text(if (isAdded == false) "添加" else "进入聊天")
        }
    }
}

@Composable
private fun LoadingContent() = Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

@Composable
private fun EmptyContent(text: String) = Box(Modifier.fillMaxSize(), Alignment.Center) { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable
private fun ErrorContent(message: String, retry: () -> Unit) = Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    Text(message, color = MaterialTheme.colorScheme.error)
    TextButton(onClick = retry) { Text("重试") }
}
