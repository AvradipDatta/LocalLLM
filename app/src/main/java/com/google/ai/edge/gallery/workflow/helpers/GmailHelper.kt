package com.google.ai.edge.gallery.workflow.helpers

object GmailHelper {
    fun fetchEmails(filters: Map<String, String>): String {
        // Call Gmail API with filters
        return "[GMAIL] Sample email contents based on filter: $filters"
    }

    fun sendEmail(content: String): String {
        // Use Gmail API to send content as email
        return "[GMAIL] Sent: $content"
    }
}
