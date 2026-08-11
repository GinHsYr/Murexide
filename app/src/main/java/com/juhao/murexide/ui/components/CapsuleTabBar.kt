package com.juhao.murexide.ui.components

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.juhao.murexide.ui.theme.PurpleDarkColorScheme
import com.juhao.murexide.ui.theme.PurpleLightColorScheme
import com.juhao.murexide.ui.theme.UiState

@Composable
fun CapsuleTabBar(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tabs.isEmpty()) return

    val selectedIndex = selectedTabIndex.coerceIn(tabs.indices)
    val selectedIndicatorColor = if (UiState.themeColor.value == "WHITE") {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val horizontalInset = 6.dp
    val selectionMotion = tween<androidx.compose.ui.unit.Dp>(
        durationMillis = 260,
        easing = FastOutSlowInEasing
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = horizontalInset, vertical = 6.dp)
    ) {
        val tabWidth = maxWidth / tabs.size
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * selectedIndex,
            animationSpec = selectionMotion,
            label = "capsule tab indicator offset"
        )

        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(tabWidth)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(selectedIndicatorColor)
        )

        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            tabs.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val textColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(durationMillis = 180),
                    label = "capsule tab text color"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onTabSelected(index) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CapsuleTabBarPreviewContent() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    Surface(color = MaterialTheme.colorScheme.surface) {
        CapsuleTabBar(
            tabs = listOf("成员", "媒体", "群云盘"),
            selectedTabIndex = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(name = "Light", showBackground = true, widthDp = 420)
@Composable
private fun CapsuleTabBarLightPreview() {
    MaterialExpressiveTheme(colorScheme = PurpleLightColorScheme) {
        CapsuleTabBarPreviewContent()
    }
}

@Preview(
    name = "Dark",
    showBackground = true,
    widthDp = 420,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun CapsuleTabBarDarkPreview() {
    MaterialExpressiveTheme(colorScheme = PurpleDarkColorScheme) {
        CapsuleTabBarPreviewContent()
    }
}
