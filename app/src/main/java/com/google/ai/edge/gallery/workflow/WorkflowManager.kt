package com.google.ai.edge.gallery.workflow

data class WorkflowRequest(
    val isWorkflow: Boolean,
    val action: String,
    val source: String,
    val destination: String
)

object WorkflowManager {
    fun parsePrompt(prompt: String): WorkflowRequest {
        val lower = prompt.lowercase()

        val source = when {
            "from telegram" in lower || "telegram" in lower -> "telegram"
            "from gmail" in lower || "gmail" in lower || "mail" in lower -> "gmail"
            else -> "unknown"
        }

        val destination = when {
            "to telegram" in lower -> "telegram"
            "to gmail" in lower || "to email" in lower -> "gmail"
            else -> "unknown"
        }

        val action = when {
            "summarize" in lower -> "summarize"
            "translate" in lower -> "translate"
            "find" in lower -> "find"
            else -> "chat"
        }

        val isWorkflow = (source != "unknown" && destination != "unknown")

        return WorkflowRequest(
            isWorkflow = isWorkflow,
            action = action,
            source = source,
            destination = destination
        )
    }

    fun executeWorkflow(request: WorkflowRequest, prompt: String): String {
        return "✅ Workflow Triggered:\nAction: ${request.action}\nSource: ${request.source}\nDestination: ${request.destination}\nPrompt: $prompt"
    }
}
