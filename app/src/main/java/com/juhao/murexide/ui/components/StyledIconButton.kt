package com.juhao.murexide.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.juhao.murexide.ui.theme.ThemeState

@Composable
fun StyledIconButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val themeStyle by ThemeState.themeStyle
    
    if (themeStyle == "md3") {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
        ) {
            content()
        }
    } else {
        FilledTonalIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
        ) {
            content()
        }
    }
}