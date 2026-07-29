package com.juhao.murexide.repository

import com.juhao.murexide.data.ConversationItem
import com.juhao.murexide.data.MessageItem
import com.juhao.murexide.network.NetworkClient
import com.juhao.murexide.proto.conversation.ConversationListSend
import com.juhao.murexide.proto.conversation.ConversationList
import com.juhao.murexide.proto.list_message
import com.juhao.murexide.proto.list_message_send
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ConversationRepository {
    private val client = NetworkClient.okHttpClient
    private val baseUrl = NetworkClient.BASE_URL

    suspend fun getConversationList(token: String, md5: String = ""): Result<List<ConversationItem>> {
        return withContext(Dispatchers.IO) {
            try {
                // 构建 ProtoBuf 请求
                val requestProto = ConversationListSend(md5 = md5)
                val requestBody = requestProto.encode().toRequestBody("application/octet-stream".toMediaType())
                
                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/conversation/list")
                    .post(requestBody)
                    .header("token", token)
                    .build()
                
                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body.bytes()
                        val conversationList = ConversationList.ADAPTER.decode(responseBody)
                        
                        if (conversationList.status?.code == 1) {
                            val items = conversationList.data_.map { conversationData ->
                                ConversationItem(
                                    chatId = conversationData.chat_id,
                                    chatType = conversationData.chat_type.toInt(),
                                    name = conversationData.name,
                                    remark = conversationData.remark.takeIf { it.isNotEmpty() },
                                    chatContent = conversationData.chat_content.takeIf { it.isNotEmpty() } ?: "[消息]",
                                    timestampMs = conversationData.timestamp_ms,
                                    sendTimestamp = conversationData.send_timestamp
                                        .takeIf { it > 0 }
                                        ?: conversationData.timestamp_ms,
                                    unreadMessage = conversationData.unread_message.toInt(),
                                    at = conversationData.at.toInt(),
                                    avatarUrl = conversationData.avatar_url,
                                    doNotDisturb = conversationData.do_not_disturb.toInt(),
                                    certificationLevel = conversationData.certification_level.toInt()
                                )
                            }
                            
                            Result.success(items)
                        } else {
                            Result.failure(Exception(conversationList.status?.msg ?: "请求失败"))
                        }
                    } else {
                        Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getLatestMessage(
        token: String,
        chatId: String,
        chatType: Int
    ): Result<MessageItem?> {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = list_message_send(
                    msg_count = 1,
                    msg_id = "",
                    chat_type = chatType.toLong(),
                    chat_id = chatId
                ).encode().toRequestBody("application/octet-stream".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/msg/list-message")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure<MessageItem?>(
                            Exception("HTTP error: ${response.code}")
                        )
                    }

                    val messageList = list_message.ADAPTER.decode(response.body.bytes())
                    if (messageList.status?.code != 1) {
                        return@use Result.failure<MessageItem?>(
                            Exception(messageList.status?.msg ?: "获取消息预览失败")
                        )
                    }

                    Result.success(
                        messageList.msg
                            .firstOrNull()
                            ?.let { msg ->
                                msg.toMessageItem(chatId = chatId, chatType = chatType)
                            }
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
