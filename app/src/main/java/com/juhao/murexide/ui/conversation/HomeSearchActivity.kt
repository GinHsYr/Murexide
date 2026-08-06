package com.juhao.murexide.ui.conversation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewAnimationUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juhao.murexide.data.HomeSearchResult
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.ui.components.Avatar
import com.juhao.murexide.ui.conversationdetail.ConversationDetailActivity
import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.theme.MurexideTheme
import kotlin.math.hypot
import kotlin.math.max
import kotlinx.coroutines.launch

private const val REVEAL_DURATION_MILLIS = 300L
private const val EXTRA_REVEAL_CENTER_X = "reveal_center_x"
private const val EXTRA_REVEAL_CENTER_Y = "reveal_center_y"

class HomeSearchActivity : ComponentActivity() {
    private var openingReveal: Animator? = null
    private var isClosing = false
    private var shouldAnimateReveal = false
    private var revealCompleted by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        shouldAnimateReveal = savedInstanceState == null && hasRevealCenter()
        revealCompleted = !shouldAnimateReveal

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = closeWithReveal()
        })

        val tokenState = mutableStateOf<String?>(null)
        setContent {
            MurexideTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    val token = tokenState.value
                    if (token == null) {
                        LoadingScreen()
                    } else {
                        HomeSearchScreen(
                            onClose = ::closeWithReveal,
                            onResultClick = { result ->
                                ConversationDetailActivity.start(
                                    this@HomeSearchActivity,
                                    result.chatId,
                                    result.chatType,
                                    result.name,
                                    result.avatarUrl
                                )
                            },
                            requestInitialFocus = revealCompleted,
                            viewModel = viewModel(factory = object : ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : ViewModel> create(modelClass: Class<T>) =
                                    HomeSearchViewModel(token) as T
                            })
                        )
                    }
                }
            }
        }

        if (shouldAnimateReveal) startOpeningReveal()

        lifecycleScope.launch {
            val token = AccountStorage.getInstance(this@HomeSearchActivity).getCurrentToken()
            if (token == null) {
                Toast.makeText(this@HomeSearchActivity, "请先登录", Toast.LENGTH_SHORT).show()
                finishWithoutWindowAnimation()
                return@launch
            }
            tokenState.value = token
        }
    }

    private fun startOpeningReveal() {
        val decorView = window.decorView
        decorView.doOnPreDraw {
            if (isFinishing || isDestroyed || isClosing) return@doOnPreDraw
            val (centerX, centerY) = revealCenterIn(decorView) ?: run {
                revealCompleted = true
                return@doOnPreDraw
            }
            openingReveal = ViewAnimationUtils.createCircularReveal(
                decorView,
                centerX,
                centerY,
                0f,
                revealRadius(decorView, centerX, centerY)
            ).apply {
                duration = REVEAL_DURATION_MILLIS
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        revealCompleted = true
                        openingReveal = null
                    }
                })
                start()
            }
        }
    }

    private fun closeWithReveal() {
        if (isClosing) return
        isClosing = true
        openingReveal?.cancel()

        val decorView = window.decorView
        val center = revealCenterIn(decorView)
        if (center == null || !shouldAnimateReveal || decorView.width == 0 || decorView.height == 0) {
            finishWithoutWindowAnimation()
            return
        }

        val (centerX, centerY) = center
        ViewAnimationUtils.createCircularReveal(
            decorView,
            centerX,
            centerY,
            revealRadius(decorView, centerX, centerY),
            0f
        ).apply {
            duration = REVEAL_DURATION_MILLIS
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = finishWithoutWindowAnimation()
            })
            start()
        }
    }

    private fun hasRevealCenter(): Boolean =
        intent.hasExtra(EXTRA_REVEAL_CENTER_X) && intent.hasExtra(EXTRA_REVEAL_CENTER_Y)

    private fun revealCenterIn(view: View): Pair<Int, Int>? {
        if (!hasRevealCenter()) return null
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val centerX = intent.getIntExtra(EXTRA_REVEAL_CENTER_X, -1) - location[0]
        val centerY = intent.getIntExtra(EXTRA_REVEAL_CENTER_Y, -1) - location[1]
        if (centerX !in 0..view.width || centerY !in 0..view.height) return null
        return centerX to centerY
    }

    private fun revealRadius(view: View, centerX: Int, centerY: Int): Float = hypot(
        max(centerX, view.width - centerX).toDouble(),
        max(centerY, view.height - centerY).toDouble()
    ).toFloat()

    private fun finishWithoutWindowAnimation() {
        // Avoid composing a transparent activity's final frame with the underlying window.
        window.decorView.visibility = View.INVISIBLE
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        fun start(context: Context, revealCenter: IntOffset) {
            context.startActivity(
                Intent(context, HomeSearchActivity::class.java)
                    .putExtra(EXTRA_REVEAL_CENTER_X, revealCenter.x)
                    .putExtra(EXTRA_REVEAL_CENTER_Y, revealCenter.y)
            )
            (context as? Activity)?.overridePendingTransition(0, 0)
        }
    }
}

@Composable
fun LoadingScreen(modifier: Modifier = Modifier) = Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.Center
) { CircularProgressIndicator() }

@Composable
fun HomeSearchScreen(
    onClose: () -> Unit,
    onResultClick: (HomeSearchResult) -> Unit,
    requestInitialFocus: Boolean,
    viewModel: HomeSearchViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val focusRequester = androidx.compose.runtime.remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var isSearchFocused by androidx.compose.runtime.remember { mutableStateOf(false) }

    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Scaffold(
        topBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(64.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::updateQuery,
                        singleLine = true,
                        placeholder = { Text("搜索用户、群聊、机器人") },
                        leadingIcon = {
                            Icon(AppIcons.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier.graphicsLayer {
                                    alpha = if (requestInitialFocus) 1f else 0.7f
                                    scaleX = if (requestInitialFocus) 1f else 0.82f
                                    scaleY = if (requestInitialFocus) 1f else 0.82f
                                }
                            ) {
                                Icon(AppIcons.Close, contentDescription = "关闭搜索")
                            }
                        },
                        shape = RoundedCornerShape(28.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { isSearchFocused = it.isFocused }
                    )
                }
                HorizontalDivider(
                    color = if (isSearchFocused) {
                        MaterialTheme.colorScheme.outline
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                )
            }
        }
    ) { padding ->
        when {
            state.isLoading && state.results.isEmpty() -> LoadingScreen(Modifier.padding(padding))
            state.error != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(state.error!!)
                Spacer(Modifier.size(12.dp))
                TextButton(onClick = viewModel::retry) { Text("重试") }
            }
            state.results.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    AppIcons.Inbox,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (state.hasSearched) "未找到相关结果" else "暂无搜索内容",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                listOf(1 to "用户", 2 to "群聊", 3 to "机器人").forEach { (type, title) ->
                    val items = state.resultsFor(type)
                    if (items.isNotEmpty()) {
                        item {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(16.dp, 14.dp, 16.dp, 6.dp)
                            )
                        }
                        items(items, key = { "${it.chatType}:${it.chatId}" }) { result ->
                            SearchRow(result, onResultClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchRow(result: HomeSearchResult, onClick: (HomeSearchResult) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(result) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(url = result.avatarUrl, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(result.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (result.introduction.isNotBlank()) {
                Text(
                    result.introduction,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            if (result.chatType == 2) AppIcons.Group else if (result.chatType == 3) AppIcons.SmartToy else AppIcons.Person,
            contentDescription = null
        )
    }
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
}
