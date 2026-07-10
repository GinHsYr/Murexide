package com.juhao.murexide.ui.theme

import androidx.compose.runtime.mutableStateOf

object ThemeState {
    var themeMode = mutableStateOf("system")
    var themeStyle = mutableStateOf("md3")
    var themeColor = mutableStateOf("DYNAMIC")
    var squareAvatar = mutableStateOf(false)
}