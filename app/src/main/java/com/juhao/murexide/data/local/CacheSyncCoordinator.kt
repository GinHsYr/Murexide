package com.juhao.murexide.data.local

import com.juhao.murexide.network.WebSocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Persists complete real-time messages even while no chat screen is in the foreground. */
class CacheSyncCoordinator(
    private val webSocketManager: WebSocketManager = WebSocketManager.getInstance()
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            webSocketManager.messageFlow.collect { event ->
                val accountId = LocalCache.currentAccountId() ?: return@collect
                when (event) {
                    is WebSocketManager.WsEvent.NewMessage -> {
                        LocalCache.cacheMessages(accountId, listOf(event.message))
                        LocalCache.applyNewMessageToConversation(
                            accountId = accountId,
                            message = event.message,
                            incrementUnread = !event.message.isMine
                        )
                    }
                    is WebSocketManager.WsEvent.LocalMessageSent,
                    is WebSocketManager.WsEvent.LatestMessageResolved -> {
                        val message = when (event) {
                            is WebSocketManager.WsEvent.LocalMessageSent -> event.message
                            is WebSocketManager.WsEvent.LatestMessageResolved -> event.message
                            else -> return@collect
                        }
                        LocalCache.cacheMessages(accountId, listOf(message))
                        LocalCache.applyNewMessageToConversation(
                            accountId = accountId,
                            message = message,
                            incrementUnread = false
                        )
                    }
                    else -> Unit
                }
            }
        }
    }
}
