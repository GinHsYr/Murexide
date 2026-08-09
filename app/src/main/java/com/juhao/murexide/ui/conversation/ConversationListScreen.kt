package com.juhao.murexide.ui.conversation

import com.juhao.murexide.ui.icons.AppIcons

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.juhao.murexide.R
import com.juhao.murexide.data.ConversationItem
import com.juhao.murexide.data.StickyItem
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.ui.theme.UiState
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    modifier: Modifier = Modifier,
    token: String,
    accountId: String,
    bigScreenMode: Boolean,
    onConversationClick: (ConversationItem) -> Unit,
    onSearchClick: (IntOffset) -> Unit = {},
    onCreateClick: (CreationKind) -> Unit = {},
    currentConversation: ConversationItem? = null,
    viewModel: ConversationViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = "conversation_$accountId",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ConversationViewModel(token, accountId) as T
            }
        }
    )
) {
    val context = LocalContext.current
    val themeColor by UiState.themeColor
    val listContainerColor = if (themeColor == "WHITE") {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surface
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val uiState by viewModel.uiState.collectAsState()
    val isWsConnected by viewModel.isWsConnected.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    DisposableEffect(viewModel) {
        viewModel.setForegroundSyncEnabled(true)
        onDispose { viewModel.setForegroundSyncEnabled(false) }
    }

    val settingsStorage = remember { SettingsStorage(context) }
    var showSticky by remember { mutableStateOf(false) }
    var showCreateMenu by remember { mutableStateOf(false) }
    var searchButtonCenter by remember { mutableStateOf<IntOffset?>(null) }

    LaunchedEffect(Unit) {
        showSticky = settingsStorage.getShowSticky()
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.app_name),
                            maxLines = 1
                        )
                        if (!isWsConnected) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Surface(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.error
                            ) {}
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(
                        onClick = { onSearchClick(searchButtonCenter ?: IntOffset.Zero) },
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            val position = coordinates.positionInWindow()
                            searchButtonCenter = IntOffset(
                                x = (position.x + coordinates.size.width / 2f).roundToInt(),
                                y = (position.y + coordinates.size.height / 2f).roundToInt()
                            )
                        }
                    ) {
                        Icon(AppIcons.Search, contentDescription = "搜索")
                    }
                    Box {
                        StyledIconButton(onClick = { showCreateMenu = true }) {
                            Icon(AppIcons.Add, contentDescription = "创建")
                        }
                        DropdownMenu(expanded = showCreateMenu, onDismissRequest = { showCreateMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("创建群聊") },
                                onClick = { showCreateMenu = false; onCreateClick(CreationKind.GROUP) },
                                leadingIcon = { Icon(AppIcons.Group, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("创建机器人") },
                                onClick = { showCreateMenu = false; onCreateClick(CreationKind.BOT) },
                                leadingIcon = { Icon(AppIcons.SmartToy, contentDescription = null) }
                            )
                        }
                    }
                }
            )
        }
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(it),
        ) {
            val state = uiState
            if (state is ConversationUiState.Success) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(listContainerColor)
                ) {
                    if (showSticky && state.stickyConversations.isNotEmpty()) {
                        item {
                            StickyConversationSection(
                                stickyItems = state.stickyConversations,
                                onStickyClick = { sticky ->
                                    onConversationClick(
                                        ConversationItem(
                                            chatId = sticky.chatId,
                                            chatType = sticky.chatType,
                                            name = sticky.chatName,
                                            chatContent = "",
                                            timestampMs = 0,
                                            avatarUrl = sticky.avatarUrl
                                        )
                                    )
                                }
                            )
                        }
                    }

                    if (state.conversations.isEmpty() && state.stickyConversations.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillParentMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("暂无会话")
                            }
                        }
                    } else {
                        items(
                            items = state.conversations,
                            key = { item -> "${item.chatType}:${item.chatId}" }
                        ) { conversation ->
                            ConversationItem(
                                conversation = conversation,
                                isSelected = currentConversation?.chatId == conversation.chatId &&
                                    currentConversation.chatType == conversation.chatType &&
                                    bigScreenMode,
                                onClick = {
                                    viewModel.clearUnread(conversation.chatId, conversation.chatType)
                                    onConversationClick(conversation)
                                }
                            )
                        }
                    }
                }
            } else if (state is ConversationUiState.Error) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("加载失败: ${state.message}")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.refresh() }) {
                            Text("重试")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StickyConversationSection(
    stickyItems: List<StickyItem>,
    onStickyClick: (StickyItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            stickyItems.forEach { item ->
                StickyItemView(
                    item = item,
                    onClick = { onStickyClick(item) }
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun StickyItemView(
    item: StickyItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(64.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Avatar(
            url = item.avatarUrl,
            size = 42.dp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.chatName,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ConversationItem(
    conversation: ConversationItem,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    val themeColor by UiState.themeColor
    val listItemColor = if (themeColor == "WHITE") {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            else
                listItemColor
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            url = conversation.avatarUrl,
            size = 52.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = formatTime(conversation.timestampMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.chatContent,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (conversation.doNotDisturb == 1) {
                    Icon(
                        imageVector = AppIcons.NotificationsOff,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }

                if (conversation.hasUnread || conversation.isAtMentioned) {
                    Spacer(modifier = Modifier.width(8.dp))

                    Badges(
                        doNotDisturb = conversation.doNotDisturb == 1,
                        hasUnread = conversation.hasUnread,
                        isAtMentioned = conversation.isAtMentioned,
                        unreadCount = conversation.unreadMessage
                    )
                }
            }
        }
    }
}

@Composable
private fun Badges(
    doNotDisturb: Boolean,
    hasUnread: Boolean,
    isAtMentioned: Boolean,
    unreadCount: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (isAtMentioned) {
            Badge(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ) {
                Text("@", style = MaterialTheme.typography.labelSmall)
            }
        }

        if (hasUnread) {
            Badge(
                containerColor = if (doNotDisturb) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                contentColor = if (doNotDisturb) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
            ) {
                AnimatedContent(
                    targetState = unreadCount,
                    transitionSpec = {
                        if (targetState < initialState) {
                            slideInVertically(
                                initialOffsetY = { fullHeight -> fullHeight },
                                animationSpec = tween(200)
                            ) togetherWith slideOutVertically(
                                targetOffsetY = { fullHeight -> -fullHeight },
                                animationSpec = tween(200)
                            )
                        } else {
                            slideInVertically(
                                initialOffsetY = { fullHeight -> -fullHeight },
                                animationSpec = tween(200)
                            ) togetherWith slideOutVertically(
                                targetOffsetY = { fullHeight -> fullHeight },
                                animationSpec = tween(200)
                            )
                        }
                    },
                    label = "unread_count"
                ) { count ->
                    Text("$count", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun formatTime(timestampMs: Long): String {
    if (timestampMs <= 0) return ""

    val date = Date(timestampMs)
    val now = Date()

    val todayCalendar = Calendar.getInstance().apply {
        time = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val dateCalendar = Calendar.getInstance().apply {
        time = date
    }

    return when {
        date.after(todayCalendar.time) -> {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        }

        dateCalendar.get(Calendar.YEAR) == todayCalendar.get(Calendar.YEAR) -> {
            SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(date)
        }

        else -> {
            SimpleDateFormat("yyyy/M/d HH:mm", Locale.getDefault()).format(date)
        }
    }
}
