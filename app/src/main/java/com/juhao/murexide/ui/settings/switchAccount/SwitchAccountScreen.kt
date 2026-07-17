package com.juhao.murexide.ui.settings.switchAccount

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.juhao.murexide.ui.components.StyledIconButton
import com.juhao.murexide.ui.components.StyledTopBar
import com.juhao.murexide.ui.theme.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Greeting(
    onBack: () -> Unit
) {
    val themeStyle by UiState.themeStyle

    val scrollBehavior = if (themeStyle == "md3") TopAppBarDefaults.pinnedScrollBehavior()
    else TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            StyledTopBar(
                title = { Text("切换账号") },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    StyledIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Text("还没做")
        }
    }
}