package com.juhao.murexide.utils

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

object MentionUtils {
    data class EditResult(
        val value: TextFieldValue,
        val insertedText: String,
        val insertPos: Int
    )

    fun mentionSpans(text: String, names: Collection<String>): List<Pair<Int, Int>> {
        val spans = mutableListOf<Pair<Int, Int>>()
        for (name in names) {
            if (name.isEmpty()) continue
            val target = "@$name"
            var idx = text.indexOf(target)
            while (idx >= 0) {
                spans += idx to (idx + target.length)
                idx = text.indexOf(target, idx + 1)
            }
        }
        return spans.sortedBy { it.first }
    }

    fun processEdit(
        old: TextFieldValue,
        new: TextFieldValue,
        names: Collection<String>
    ): EditResult {
        if (old.text == new.text) {
            return EditResult(
                new.copy(selection = clampSelection(new.text, new.selection, names)),
                "", -1
            )
        }

        val oldText = old.text
        val newText = new.text

        var prefix = 0
        val minLen = minOf(oldText.length, newText.length)
        while (prefix < minLen && oldText[prefix] == newText[prefix]) prefix++
        var suffix = 0
        while (suffix < minLen - prefix &&
            oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]
        ) suffix++
        val delStart = prefix
        val delEnd = oldText.length - suffix
        val inserted = newText.substring(prefix, newText.length - suffix)

        val spans = mentionSpans(oldText, names)

        if (delStart == delEnd) {
            for ((s, e) in spans) {
                if (delStart in (s + 1)..<e) {
                    val result = oldText.substring(0, e) + inserted + oldText.substring(e)
                    return EditResult(
                        TextFieldValue(result, TextRange(e + inserted.length)),
                        inserted, e
                    )
                }
            }
            return EditResult(
                new.copy(selection = clampSelection(newText, new.selection, names)),
                inserted, delStart
            )
        }

        var s2 = delStart
        var e2 = delEnd
        var expanded = false
        for ((s, e) in spans) {
            val intersects = delStart < e && delEnd > s
            val fullyCovered = delStart <= s && delEnd >= e
            if (intersects && !fullyCovered) {
                if (s < s2) { s2 = s; expanded = true }
                if (e > e2) { e2 = e; expanded = true }
            }
        }
        if (expanded) {
            val result = oldText.substring(0, s2) + inserted + oldText.substring(e2)
            return EditResult(
                TextFieldValue(result, TextRange(s2 + inserted.length)),
                inserted, s2
            )
        }
        return EditResult(
            new.copy(selection = clampSelection(newText, new.selection, names)),
            inserted, delStart
        )
    }

    private fun clampSelection(
        text: String,
        selection: TextRange,
        names: Collection<String>
    ): TextRange {
        val spans = mentionSpans(text, names)
        if (spans.isEmpty()) return selection

        fun clampPos(p: Int): Int {
            for ((s, e) in spans) {
                if (p in (s + 1)..<e) {
                    return if (p - s < e - p) s else e
                }
            }
            return p
        }
        return TextRange(clampPos(selection.start), clampPos(selection.end))
    }
}
