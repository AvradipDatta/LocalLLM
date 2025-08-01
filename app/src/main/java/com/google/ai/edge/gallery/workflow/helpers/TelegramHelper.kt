package com.google.ai.edge.gallery.workflow.helpers

object TelegramHelper {
    fun fetchMessages(filters: Map<String, String>): String {
        // Call Telegram Bot API to get messages (stub)
        return "[TELEGRAM] Sample telegram messages with filters: $filters"
    }

    fun sendMessage(content: String): String {
        // Use Telegram Bot API to send message
        return "[TELEGRAM] Sent: $content"
    }
}