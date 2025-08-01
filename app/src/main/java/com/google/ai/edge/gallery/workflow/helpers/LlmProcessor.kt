package com.google.ai.edge.gallery.workflow.helpers

object LlmProcessor {
    fun process(task: String, content: String): String {
        return when (task.lowercase()) {
            "summarize" -> "[SUMMARY]: $content"
            "translate" -> "[TRANSLATION]: $content"
            else -> "[RAW]: $content"
        }
    }
}