package com.juhao.murexide.ui.chat.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.juhao.murexide.data.DefaultEmoji
import com.juhao.murexide.data.ExpressionItem
import com.juhao.murexide.data.StickerItem
import com.juhao.murexide.data.StickerPack
import com.juhao.murexide.data.resolveStickerUrl
import kotlinx.coroutines.launch

private const val DEFAULT_EMOJI_COLUMNS = 8

@Composable
fun EmojiPanel(
    defaultEmojis: List<DefaultEmoji>,
    recentDefaultEmojis: List<DefaultEmoji>,
    expressions: List<ExpressionItem>,
    stickerPacks: List<StickerPack>,
    isLoading: Boolean,
    onExpressionClick: (ExpressionItem) -> Unit,
    onStickerItemClick: (StickerItem) -> Unit,
    onDefaultEmojiClick: (DefaultEmoji) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabTitles = remember(stickerPacks) {
        buildList {
            add("默认")
            add("收藏")
            stickerPacks.forEach { add(it.name) }
        }
    }

    val pagerState = rememberPagerState(pageCount = { tabTitles.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier.fillMaxWidth().navigationBarsPadding()
    ) {
        PrimaryScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            edgePadding = 4.dp,
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = {
                        Text(
                            text = title,
                            maxLines = 1,
                            fontSize = 13.sp
                        )
                    }
                )
            }
        }

        // Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            if (page == 0) {
                DefaultEmojiGridPage(
                    emojis = defaultEmojis,
                    recentEmojis = recentDefaultEmojis,
                    onItemClick = onDefaultEmojiClick
                )
            } else if (isLoading && page == 1 && expressions.isEmpty() && stickerPacks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else if (page == 1) {
                ExpressionGridPage(
                    expressions = expressions,
                    onItemClick = onExpressionClick
                )
            } else {
                val packIndex = page - 2
                if (packIndex in stickerPacks.indices) {
                    StickerPackGridPage(
                        items = stickerPacks[packIndex].stickerItems,
                        onItemClick = onStickerItemClick
                    )
                }
            }
        }
    }
}

@Composable
private fun DefaultEmojiGridPage(
    emojis: List<DefaultEmoji>,
    recentEmojis: List<DefaultEmoji>,
    onItemClick: (DefaultEmoji) -> Unit
) {
    if (emojis.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "暂无默认表情",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(DEFAULT_EMOJI_COLUMNS),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (recentEmojis.isNotEmpty()) {
            item(
                key = "recent_default_emojis",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                RecentDefaultEmojiBar(
                    emojis = recentEmojis,
                    onItemClick = onItemClick
                )
            }
        }

        items(
            count = emojis.size,
            key = { index -> "default_${emojis[index].name}" },
            contentType = { "default_emoji" }
        ) { index ->
            val emoji = emojis[index]
            DefaultEmojiItem(
                emoji = emoji,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                onClick = { onItemClick(emoji) }
            )
        }
    }
}

@Composable
private fun RecentDefaultEmojiBar(
    emojis: List<DefaultEmoji>,
    onItemClick: (DefaultEmoji) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val spacing = 4.dp
        val itemSize = (maxWidth - spacing * (DEFAULT_EMOJI_COLUMNS - 1)) /
            DEFAULT_EMOJI_COLUMNS

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "最近使用",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemSize),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                items(
                    count = emojis.size,
                    key = { index -> emojis[index].name },
                    contentType = { "recent_default_emoji" }
                ) { index ->
                    val emoji = emojis[index]
                    DefaultEmojiItem(
                        emoji = emoji,
                        modifier = Modifier.size(itemSize),
                        onClick = { onItemClick(emoji) }
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun DefaultEmojiItem(
    emoji: DefaultEmoji,
    modifier: Modifier,
    contentPadding: androidx.compose.ui.unit.Dp = 10.dp,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // 统一缩略图尺寸，避免为同一资源创建大量近似尺寸缓存项。
    val targetHeightPx = with(density) { 40.dp.roundToPx() }.coerceAtLeast(1)
    val bitmap = rememberDefaultEmojiBitmap(context, emoji, targetHeightPx)

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null && !bitmap.isRecycled) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = emoji.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            // 固定尺寸占位，避免 Coil 在大量本地资源上创建请求和解码线程。
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { }
        }
    }
}

@Composable
private fun ExpressionGridPage(
    expressions: List<ExpressionItem>,
    onItemClick: (ExpressionItem) -> Unit
) {
    if (expressions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "暂无收藏表情",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        return
    }
    EmojiGrid(
        count = expressions.size,
        contentPadding = PaddingValues(8.dp)
    ) { index ->
        val item = expressions[index]
        EmojiGridItem(
            url = resolveStickerUrl(item.url),
            onClick = { onItemClick(item) }
        )
    }
}

@Composable
private fun StickerPackGridPage(
    items: List<StickerItem>,
    onItemClick: (StickerItem) -> Unit
) {
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "暂无表情",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        return
    }
    EmojiGrid(
        count = items.size,
        contentPadding = PaddingValues(8.dp)
    ) { index ->
        val item = items[index]
        EmojiGridItem(
            url = resolveStickerUrl(item.url),
            name = item.name,
            onClick = { onItemClick(item) }
        )
    }
}

/** 通用表情网格 */
@Composable
private fun EmojiGrid(
    count: Int,
    modifier: Modifier = Modifier,
    columns: Int = 4,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable (index: Int) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(
            count = count,
            key = { index -> "remote_emoji_$index" },
            contentType = { "remote_emoji" }
        ) { index ->
            content(index)
        }
    }
}

@Composable
private fun EmojiGridItem(
    url: String?,
    name: String? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageRequest = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .apply {
                if (url?.contains("jwznb.com") == true) {
                    setHeader("Referer", "https://myapp.jwznb.com")
                }
            }
            .crossfade(true)
            .build()
    }
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            modifier = Modifier.size(80.dp).clip(MaterialTheme.shapes.extraSmall),
            contentScale = ContentScale.Fit
        )
        name?.let {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
