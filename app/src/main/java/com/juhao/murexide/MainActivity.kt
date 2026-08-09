package com.juhao.murexide

import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AppFilledIcons
import com.juhao.murexide.ui.icons.AnimatedNavigationSymbol

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.juhao.murexide.ui.chat.ChatActivity
import com.juhao.murexide.ui.contact.ContactListScreen
import com.juhao.murexide.ui.conversation.ConversationListScreen
import com.juhao.murexide.ui.conversation.CreationActivity
import com.juhao.murexide.ui.conversation.HomeSearchActivity
import com.juhao.murexide.ui.login.LoginActivity
import com.juhao.murexide.ui.mine.MineScreen
import com.juhao.murexide.ui.theme.MurexideTheme
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.data.ConversationItem
import com.juhao.murexide.data.ConversationKey
import com.juhao.murexide.data.unreadTotal
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.datastore.UserAccount
import com.juhao.murexide.data.local.LocalCache
import com.juhao.murexide.ui.chat.ChatScreen
import com.juhao.murexide.ui.chat.ChatViewModel
import com.juhao.murexide.ui.components.UnreadCountBadge
import com.juhao.murexide.ui.components.AccountQuickSwitchMenu
import com.juhao.murexide.ui.community.CommunityScreen
import com.juhao.murexide.ui.settings.SettingsActivity
import com.juhao.murexide.utils.getAppVersionInfo
import kotlinx.coroutines.delay
import androidx.compose.foundation.combinedClickable

private data class NavItem(
    val route: String,
    val title: String,
    val outlineIcon: ImageVector,
    val filledIcon: ImageVector,
)

private val navItems = listOf(
    NavItem("conversations", "消息", AppIcons.ChatBubble, AppFilledIcons.ChatBubble),
    NavItem("contacts", "通讯录", AppIcons.Contacts, AppFilledIcons.Contacts),
    NavItem("community", "社区", AppIcons.Group, AppFilledIcons.Group),
    NavItem("discover", "发现", AppIcons.Explore, AppFilledIcons.Explore),
    NavItem("mine", "我的", AppIcons.Person, AppFilledIcons.Person),
)

private const val TAB_TRANSITION_DURATION_MILLIS = 300

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabSlideDirection():
    AnimatedContentTransitionScope.SlideDirection {
    val initialIndex = navItems.indexOfFirst { it.route == initialState.destination.route }
    val targetIndex = navItems.indexOfFirst { it.route == targetState.destination.route }
    return if (targetIndex > initialIndex) {
        AnimatedContentTransitionScope.SlideDirection.Left
    } else {
        AnimatedContentTransitionScope.SlideDirection.Right
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val accountStorage = AccountStorage.getInstance(this)

        lifecycleScope.launch {
            val account = accountStorage.getCurrentUserInfo()
            if (account == null || account.token.isEmpty()) {
                LoginActivity.start(this@MainActivity)
                finish()
                return@launch
            }

            // Main content and its ViewModels may be created before the Application-level
            // DataStore collector emits. Bind the already validated account synchronously.
            LocalCache.setActiveAccount(account.id)

            setContent {
                MurexideTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        MainScreen(account)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(account: UserAccount) {
    val token = account.token
    val navController = rememberNavController()
    val context = LocalContext.current
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var previousRoute by remember { mutableStateOf<String?>(null) }
    var blockContentInput by remember { mutableStateOf(false) }

    LaunchedEffect(currentRoute) {
        val routeChanged = previousRoute != null && previousRoute != currentRoute
        previousRoute = currentRoute
        if (routeChanged) {
            blockContentInput = true
            delay(TAB_TRANSITION_DURATION_MILLIS.toLong())
            blockContentInput = false
        }
    }
    
    val settingsStorage = remember { SettingsStorage(context) }
    val bigScreenEnabled by settingsStorage.bigScreenFlow.collectAsState(initial = true)
    
    val isBigScreen = LocalConfiguration.current.screenWidthDp >= 600

    var currentConversation by remember { mutableStateOf<ConversationItem?>(null) }
    val cachedConversations by LocalCache.observeConversations(account.id).collectAsState(initial = emptyList())
    val unreadCount = cachedConversations.unreadTotal(
        currentConversation?.takeIf { isBigScreen && bigScreenEnabled }
            ?.let { ConversationKey(it.chatId, it.chatType) }
    )

    var isContactNewMessagesVisible by remember { mutableStateOf(false) }
    
    var showDevTip by remember { mutableStateOf(context.getAppVersionInfo().commitHash == "dev") }
    var showAccountMenu by remember { mutableStateOf(false) }
    val accountStorage = remember(context.applicationContext) {
        AccountStorage.getInstance(context.applicationContext)
    }
    val loggedInAccounts by accountStorage.userAccountsFlow.collectAsState(initial = emptyList())

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
    
    if (showDevTip) {
        AlertDialog(
            onDismissRequest = { showDevTip = false },
            icon = { Icon(AppIcons.Info, contentDescription = null) },
            title = { Text("正在使用开发版本") },
            text = { Text("你正在使用内部开发版本，可能出现问题。如果发现问题，可在交流群反馈。此弹窗会在每次应用启动时弹出。此版本仅为测试使用，不能作为日用版本，请及时更新至正式版或快照版。开发版本的检查更新不可用。") },
            confirmButton = {
                TextButton(onClick = { showDevTip = false }) {
                    Text("了解")
                }
            }
        )
    }

    NavigationSuiteScaffold(
        layoutType = when {
            bigScreenEnabled && isBigScreen -> NavigationSuiteType.NavigationRail
            currentRoute == "contacts" && isContactNewMessagesVisible -> NavigationSuiteType.None
            else -> NavigationSuiteType.NavigationBar
        },
        navigationSuiteItems = {
            navItems.forEach { item ->
                val selected = currentRoute == item.route
                item(
                    icon = {
                        if (item.route == "mine") {
                            Box(
                                modifier = Modifier.combinedClickable(
                                    onClick = { navigateTo(item.route) },
                                    onLongClick = { showAccountMenu = true },
                                    onLongClickLabel = "打开账号菜单"
                                )
                            ) {
                                AnimatedNavigationSymbol(
                                    outlineIcon = item.outlineIcon,
                                    filledIcon = item.filledIcon,
                                    selected = selected,
                                    contentDescription = item.title,
                                )
                                AccountQuickSwitchMenu(
                                    expanded = showAccountMenu,
                                    accounts = loggedInAccounts,
                                    currentAccountId = account.id,
                                    onDismissRequest = { showAccountMenu = false }
                                )
                            }
                        } else if (item.route == "conversations") {
                            BadgedBox(badge = { UnreadCountBadge(unreadCount) }) {
                                AnimatedNavigationSymbol(
                                    outlineIcon = item.outlineIcon,
                                    filledIcon = item.filledIcon,
                                    selected = selected,
                                    contentDescription = item.title,
                                )
                            }
                        } else {
                            AnimatedNavigationSymbol(
                                outlineIcon = item.outlineIcon,
                                filledIcon = item.filledIcon,
                                selected = selected,
                                contentDescription = item.title,
                            )
                        }
                    },
                    label = {
                        AnimatedVisibility(
                            visible = selected,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Text(item.title)
                        }
                    },
                    selected = selected,
                    onClick = {
                        navigateTo(item.route)
                    }
                )
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = "conversations",
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(blockContentInput) {
                    if (blockContentInput) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial).changes.forEach {
                                    it.consume()
                                }
                            }
                        }
                    }
                },
            enterTransition = {
                slideIntoContainer(
                    towards = tabSlideDirection(),
                    animationSpec = tween(TAB_TRANSITION_DURATION_MILLIS)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = tabSlideDirection(),
                    animationSpec = tween(TAB_TRANSITION_DURATION_MILLIS)
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = tabSlideDirection(),
                    animationSpec = tween(TAB_TRANSITION_DURATION_MILLIS)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = tabSlideDirection(),
                    animationSpec = tween(TAB_TRANSITION_DURATION_MILLIS)
                )
            }
        ) {
            composable("conversations") {
                Row(modifier = Modifier.fillMaxSize()) {
                    ConversationListScreen(
                        modifier = Modifier
                            .weight(if (isBigScreen && bigScreenEnabled) 0.4f else 1f)
                            .fillMaxHeight(),
                        token = token,
                        accountId = account.id,
                        bigScreenMode = isBigScreen && bigScreenEnabled,
                        currentConversation = if (isBigScreen && bigScreenEnabled) currentConversation else null,
                        onConversationClick = { conversation ->
                            if (isBigScreen && bigScreenEnabled) {
                                currentConversation = conversation
                            } else {
                                ChatActivity.start(
                                    context = context,
                                    chatId = conversation.chatId,
                                    chatType = conversation.chatType,
                                    chatName = conversation.displayName,
                                    chatAvatar = conversation.avatarUrl,
                                )
                            }
                        },
                        onSearchClick = { origin -> HomeSearchActivity.start(context, origin) },
                        onCreateClick = { kind -> CreationActivity.start(context, kind) }
                    )

                    if (isBigScreen && bigScreenEnabled) {
                        if (currentConversation != null) {
                            BackHandler {
                                currentConversation = null
                            }
                            key(currentConversation!!.chatId) {
                                ChatScreen(
                                    modifier = Modifier
                                        .weight(0.7f)
                                        .fillMaxHeight(),
                                    chatAvatar = currentConversation!!.avatarUrl,
                                    chatName = currentConversation!!.name,
                                    chatType = currentConversation!!.chatType,
                                    chatId = currentConversation!!.chatId,
                                    onBackClick = { currentConversation = null },
                                    onOpenConversation = { target ->
                                        currentConversation = target.toConversationItem()
                                    },
                                    bigScreenMode = true,
                                    backUnreadCount = unreadCount,
                                    viewModel = viewModel(
                                        key = "chat_" + currentConversation!!.chatId,
                                        factory = object : ViewModelProvider.Factory {
                                            @Suppress("UNCHECKED_CAST")
                                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                                return ChatViewModel(
                                                    token = token,
                                                    chatId = currentConversation!!.chatId,
                                                    chatType = currentConversation!!.chatType,
                                                    currentUserId = account.id,
                                                    currentUserName = account.username,
                                                    currentUserAvatar = account.avatar
                                                ) as T
                                            }
                                        }
                                    )
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.weight(0.7f).fillMaxHeight(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = AppIcons.ChatBubble,
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp).alpha(0.6f)
                                )
                            }
                        }
                    }
                }
            }

            composable("contacts") {
                ContactListScreen(
                    token = token,
                    onNewMessagesVisibilityChanged = { isVisible ->
                        isContactNewMessagesVisible = isVisible
                    },
                    onContactClick = { contact ->
                        ChatActivity.start(
                            context = context,
                            chatId = contact.chatId,
                            chatType = contact.chatType,
                            chatName = contact.remark ?: contact.name,
                            chatAvatar = contact.avatarUrl
                        )
                    }
                )
            }

            composable("community") {
                CommunityScreen(
                    token = token
                )
            }
            
            composable("discover") {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("发现") },
                            actions = {
                                IconButton(onClick = { /* TODO: 搜索 */ }) {
                                    Icon(AppIcons.Search, contentDescription = "搜索")
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(top = 16.dp),
                    ) {
                        Text("发现")
                    }
                }
            }

            composable("mine") {
                MineScreen(
                    token = token,
                    onSettingsClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }
                )
            }
        }
    }
}
