package com.google.ai.edge.gallery.workflow

import com.google.ai.edge.gallery.workflow.helpers.GmailHelper
import com.google.ai.edge.gallery.workflow.helpers.TelegramHelper
import com.google.ai.edge.gallery.workflow.helpers.LlmProcessor

object WorkflowExecutor {
    fun execute(workflow: WorkflowTask): String {
        val content = when (workflow.source) {
            SourceType.GMAIL -> GmailHelper.fetchEmails(workflow.filters)
            SourceType.TELEGRAM -> TelegramHelper.fetchMessages(workflow.filters)
        }

        val result = LlmProcessor.process(workflow.task, content)

        return when (workflow.destination) {
            DestinationType.GMAIL -> GmailHelper.sendEmail(result)
            DestinationType.TELEGRAM -> TelegramHelper.sendMessage(result)
        }
    }
}