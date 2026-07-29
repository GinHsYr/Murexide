package com.juhao.murexide.ui.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.juhao.murexide.data.DefaultEmoji
import com.juhao.murexide.data.DefaultEmojiCatalog
import com.juhao.murexide.ui.chat.components.BatchedDefaultEmojiText
import com.juhao.murexide.ui.chat.components.DefaultEmojiTextField
import com.juhao.murexide.ui.theme.MurexideTheme

/** Offline stress screen for hundreds of bundled default emoji occurrences. */
class DefaultEmojiBenchmarkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MurexideTheme {
                DefaultEmojiBenchmarkContent()
            }
        }
    }
}

@Composable
private fun DefaultEmojiBenchmarkContent() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val emojis = remember(context) { DefaultEmojiCatalog.load(context.assets) }
    val messages = remember(emojis) {
        List(6) { index ->
            index to buildString {
                repeat(160) { position ->
                    append(emojis[(position + index) % emojis.size].marker)
                    if (position % 18 == 17) append(' ')
                }
            }
        }
    }
    var editorValue by remember { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("default-emoji-benchmark-list")
                .background(MaterialTheme.colorScheme.surface),
            reverseLayout = true
        ) {
            items(messages, key = { it.first }, contentType = { "default_emoji_message" }) { (_, text) ->
                BenchmarkEmojiMessage(text = text, emojis = emojis)
            }
        }
        DefaultEmojiTextField(
            value = editorValue,
            mentions = emptyList(),
            emojis = emojis,
            enabled = true,
            textColor = MaterialTheme.colorScheme.onSurface,
            hintColor = MaterialTheme.colorScheme.onSurfaceVariant,
            textSizeSp = 16f,
            focusRequester = focusRequester,
            onValueChange = { value, _, _, _ -> editorValue = value },
            onFocused = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("default-emoji-benchmark-editor")
        )
    }
}

@Composable
private fun BenchmarkEmojiMessage(text: String, emojis: List<DefaultEmoji>) {
    BatchedDefaultEmojiText(
        text = text,
        timestampText = "12:34",
        emojis = emojis,
        bodyStyle = MaterialTheme.typography.bodyMedium,
        timestampStyle = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
    )
}
