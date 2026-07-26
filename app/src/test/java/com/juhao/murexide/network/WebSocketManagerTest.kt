package com.juhao.murexide.network

import com.juhao.murexide.proto.chat_ws_go.INFO
import com.juhao.murexide.proto.chat_ws_go.WsMsg
import com.juhao.murexide.proto.chat_ws_go.edit_message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSocketManagerTest {
    @Test
    fun editMessagePayload_decodesAsEditedMessage() {
        val event = decodeEditMessageEvent(editPayload(deleteTime = 0))

        assertTrue(event is WebSocketManager.WsEvent.EditMessage)
        val message = (event as WebSocketManager.WsEvent.EditMessage).message
        assertEquals("message-id", message.msgId)
        assertEquals("chat-id", message.chatId)
        assertEquals("updated", message.content)
        assertFalse(message.isRecalled)
        assertTrue(message.isEdited)
    }

    @Test
    fun recalledEditMessagePayload_decodesAsDeletedEvent() {
        val event = decodeEditMessageEvent(editPayload(deleteTime = 1_234))

        assertEquals(
            WebSocketManager.WsEvent.MessageDeleted("message-id"),
            event
        )
    }

    private fun editPayload(deleteTime: Long): ByteArray {
        val payload = edit_message(
            info = INFO(cmd = "edit_message"),
            data_ = edit_message.EditData(
                msg = WsMsg(
                    msg_id = "message-id",
                    chat_id = "chat-id",
                    chat_type = 2,
                    content = WsMsg.WsContent(text = "updated"),
                    content_type = 1,
                    timestamp = 1_000,
                    delete_time = deleteTime,
                    msg_seq = 42,
                    edit_time = 2_000
                )
            )
        )
        return edit_message.ADAPTER.encode(payload)
    }
}
