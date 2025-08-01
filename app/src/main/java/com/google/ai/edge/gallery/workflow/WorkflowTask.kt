package com.google.ai.edge.gallery.workflow

data class WorkflowTask(
    val task: String,
    val source: SourceType,
    val destination: DestinationType,
    val filters: Map<String, String>
)

enum class SourceType {
    GMAIL, TELEGRAM
}

enum class DestinationType {
    GMAIL, TELEGRAM
}
