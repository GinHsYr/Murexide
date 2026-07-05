package com.juhao.murexide.ui.settings.appearance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.juhao.murexide.ui.theme.MurexideTheme

class AppearanceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MurexideTheme {
                AppearanceScreen(
                    onBack = { finish() },
                )
            }
        }
    }
}