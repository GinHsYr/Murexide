package com.juhao.murexide.ui.components.litehtml

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.juhao.murexide.ui.components.LiteHtmlContent
import com.juhao.murexide.ui.theme.MurexideTheme

/** Deterministic offline workload used only by the benchmark build type. */
class LiteHtmlBenchmarkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MurexideTheme {
                val messages = remember { List(100, ::benchmarkHtml) }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("litehtml-benchmark-list")
                ) {
                    items(
                        items = messages,
                        key = { it.first },
                        contentType = { "html" }
                    ) { (_, html) ->
                        LiteHtmlContent(
                            htmlContent = html,
                            backgroundColor = MaterialTheme.colorScheme.surface
                        )
                    }
                }
            }
        }
    }
}

private fun benchmarkHtml(index: Int): Pair<Int, String> = index to """
    <section style="padding:8px;margin:4px;border:1px solid #808080;border-radius:8px">
      <h3>HTML message $index 😄</h3>
      <p><strong>litehtml</strong> renders this paragraph with an
        <a href="https://example.com/message/$index">interactive link</a>.</p>
      <blockquote>Static HTML/CSS benchmark content with 中文、Emoji and wrapping text.</blockquote>
      <table>
        <thead><tr><th>Column A</th><th>Column B</th></tr></thead>
        <tbody>
          <tr><td>${index * 2}</td><td><code>value-$index</code></td></tr>
          <tr><td>${index * 3}</td><td>long wrapping cell content for layout work</td></tr>
        </tbody>
      </table>
      <ul><li>first item</li><li>second item</li><li>third item</li></ul>
    </section>
""".trimIndent()
