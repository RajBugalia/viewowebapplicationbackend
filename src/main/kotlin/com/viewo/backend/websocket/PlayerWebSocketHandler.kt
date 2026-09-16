package com.viewo.backend.websocket

import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

@Component
class PlayerWebSocketHandler : TextWebSocketHandler() {
    
    private val activeSessions = ConcurrentHashMap<String, WebSocketSession>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        activeSessions[session.id] = session
        println("New player connected: ${session.id}")
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        println("Message from ${session.id}: ${message.payload}")
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: org.springframework.web.socket.CloseStatus) {
        activeSessions.remove(session.id)
        println("Player disconnected: ${session.id}")
    }
    
    fun sendCampaignToAll(campaignJson: String) {
        activeSessions.values.forEach { it.sendMessage(TextMessage(campaignJson)) }
    }
}
