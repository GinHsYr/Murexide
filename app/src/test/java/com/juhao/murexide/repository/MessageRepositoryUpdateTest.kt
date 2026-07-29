package com.juhao.murexide.repository

import com.juhao.murexide.proto.Msg
import com.juhao.murexide.proto.list_message_by_update_send
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageRepositoryUpdateTest {
    @Test
    fun `incremental request uses the visible message update cursor`() {
        val encoded = createListMessageByUpdateRequest(
            updateTime = 12_345L,
            chatId = "chat-id",
            chatType = 2,
            msgCount = 100
        ).encode()

        val request = list_message_by_update_send.ADAPTER.decode(encoded)

        assertEquals(12_345L, request.update_time)
        assertEquals("chat-id", request.chat_id)
        assertEquals(2L, request.chat_type)
        assertEquals(100L, request.msg_count)
    }

    @Test
    fun `message update cursor includes edits and recalls`() {
        val edited = Msg(
            msg_id = "edited",
            content_type = 1,
            send_time = 1_000L,
            edit_time = 2_000L
        ).toMessageItem(chatId = "chat-id", chatType = 2)
        val recalled = Msg(
            msg_id = "recalled",
            content_type = 1,
            send_time = 1_000L,
            msg_delete_time = 3_000L
        ).toMessageItem(chatId = "chat-id", chatType = 2)

        assertEquals(2_000L, edited.updateTimestamp)
        assertTrue(edited.isEdited)
        assertEquals(3_000L, recalled.updateTimestamp)
        assertTrue(recalled.isRecalled)
    }
}
