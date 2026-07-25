package com.juhao.murexide.utils

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.juhao.murexide.data.MentionToken

object MentionUtils {
    data class EditResult(
        val value: TextFieldValue,
        val mentions: List<MentionToken>,
        val insertedText: String,
        val insertPos: Int
    )

    data class InsertResult(
        val text: String,
        val mentions: List<MentionToken>
    )

    fun validMentions(text: String, mentions: List<MentionToken>): List<MentionToken> {
        val result = mutableListOf<MentionToken>()
        var previousEnd = -1
        mentions.sortedBy { it.start }.forEach { mention ->
            val isValidRange = mention.start >= 0 &&
                mention.endExclusive == mention.start + mention.displayText.length &&
                mention.endExclusive <= text.length
            if (
                isValidRange &&
                mention.start >= previousEnd &&
                text.regionMatches(
                    thisOffset = mention.start,
                    other = mention.displayText,
                    otherOffset = 0,
                    length = mention.displayText.length
                )
            ) {
                result += mention
                previousEnd = mention.endExclusive
            }
        }
        return result
    }

    fun mentionedUserIds(text: String, mentions: List<MentionToken>): List<String> {
        return validMentions(text, mentions).map { it.userId }.distinct()
    }

    fun insertMention(
        text: String,
        mentions: List<MentionToken>,
        userId: String,
        displayName: String,
        triggerPos: Int = -1
    ): InsertResult {
        require(displayName.isNotEmpty())
        val currentMentions = validMentions(text, mentions)
        val replacesTrigger = triggerPos in text.indices && text[triggerPos] == '@'
        val insertAt = if (replacesTrigger) triggerPos else text.length
        val replaceEnd = if (replacesTrigger) triggerPos + 1 else insertAt
        val displayText = "@$displayName"
        val insertedText = "$displayText "
        val newText = text.substring(0, insertAt) + insertedText + text.substring(replaceEnd)
        val shiftedMentions = transformMentions(
            mentions = currentMentions,
            editStart = insertAt,
            editEnd = replaceEnd,
            insertedLength = insertedText.length
        )
        val newMention = MentionToken(
            userId = userId,
            displayName = displayName,
            start = insertAt,
            endExclusive = insertAt + displayText.length
        )
        return InsertResult(
            text = newText,
            mentions = (shiftedMentions + newMention).sortedBy { it.start }
        )
    }

    fun processEdit(
        old: TextFieldValue,
        new: TextFieldValue,
        mentions: List<MentionToken>
    ): EditResult {
        val currentMentions = validMentions(old.text, mentions)
        if (old.text == new.text) {
            return EditResult(
                new.copy(selection = clampSelection(new.text, new.selection, currentMentions)),
                currentMentions,
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

        if (delStart == delEnd) {
            for (mention in currentMentions) {
                if (delStart > mention.start && delStart < mention.endExclusive) {
                    val insertAt = mention.endExclusive
                    val result = oldText.substring(0, insertAt) + inserted + oldText.substring(insertAt)
                    val updatedMentions = transformMentions(
                        currentMentions,
                        insertAt,
                        insertAt,
                        inserted.length
                    )
                    return EditResult(
                        TextFieldValue(result, TextRange(insertAt + inserted.length)),
                        updatedMentions,
                        inserted,
                        insertAt
                    )
                }
            }
        }

        var editStart = delStart
        var editEnd = delEnd
        if (delStart < delEnd) {
            var expanded: Boolean
            do {
                expanded = false
                currentMentions.forEach { mention ->
                    val intersects = editStart < mention.endExclusive && editEnd > mention.start
                    val fullyCovered = editStart <= mention.start && editEnd >= mention.endExclusive
                    if (intersects && !fullyCovered) {
                        val expandedStart = minOf(editStart, mention.start)
                        val expandedEnd = maxOf(editEnd, mention.endExclusive)
                        if (expandedStart != editStart || expandedEnd != editEnd) {
                            editStart = expandedStart
                            editEnd = expandedEnd
                            expanded = true
                        }
                    }
                }
            } while (expanded)
        }

        val resultText = oldText.substring(0, editStart) + inserted + oldText.substring(editEnd)
        val updatedMentions = transformMentions(
            currentMentions,
            editStart,
            editEnd,
            inserted.length
        )
        val selection = if (editStart == delStart && editEnd == delEnd) {
            new.selection
        } else {
            TextRange(editStart + inserted.length)
        }
        return EditResult(
            TextFieldValue(
                resultText,
                clampSelection(resultText, selection, updatedMentions)
            ),
            updatedMentions,
            inserted,
            editStart
        )
    }

    private fun transformMentions(
        mentions: List<MentionToken>,
        editStart: Int,
        editEnd: Int,
        insertedLength: Int
    ): List<MentionToken> {
        val offset = insertedLength - (editEnd - editStart)
        return mentions.mapNotNull { mention ->
            when {
                mention.endExclusive <= editStart -> mention
                mention.start >= editEnd -> mention.copy(
                    start = mention.start + offset,
                    endExclusive = mention.endExclusive + offset
                )
                else -> null
            }
        }.sortedBy { it.start }
    }

    private fun clampSelection(
        text: String,
        selection: TextRange,
        mentions: List<MentionToken>
    ): TextRange {
        val validMentions = validMentions(text, mentions)
        if (validMentions.isEmpty()) return selection

        fun clampPos(p: Int): Int {
            for (mention in validMentions) {
                if (p > mention.start && p < mention.endExclusive) {
                    return if (p - mention.start < mention.endExclusive - p) {
                        mention.start
                    } else {
                        mention.endExclusive
                    }
                }
            }
            return p
        }
        return TextRange(clampPos(selection.start), clampPos(selection.end))
    }
}
