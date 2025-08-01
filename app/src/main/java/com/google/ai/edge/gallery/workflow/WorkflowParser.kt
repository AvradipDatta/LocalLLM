package com.google.ai.edge.gallery.workflow

import org.json.JSONObject
import com.google.ai.edge.gallery.workflow.WorkflowTask
import com.google.ai.edge.gallery.workflow.SourceType
import com.google.ai.edge.gallery.workflow.DestinationType

object WorkflowParser {
    fun parse(prompt: String): WorkflowTask? {
        // In a real implementation, use your local LLM to parse the prompt into structured JSON
        return try {
            val json = JSONObject(runLocalLlmParser(prompt))
            val task = json.getString("task")
            val source = SourceType.valueOf(json.getString("source").uppercase())
            val destination = DestinationType.valueOf(json.getString("destination").uppercase())
            val filters = mutableMapOf<String, String>()
            val jsonFilters = json.getJSONObject("filters")
            for (key in jsonFilters.keys()) {
                filters[key] = jsonFilters.getString(key)
            }
            WorkflowTask(task, source, destination, filters)
        } catch (e: Exception) {
            null
        }
    }

    private fun runLocalLlmParser(prompt: String): String {
        // This is placeholder logic
        // Replace this with call to local LLM that returns a JSON string
        return """
        {
          \"task\": \"summarize\",
          \"source\": \"gmail\",
          \"destination\": \"telegram\",
          \"filters\": {
            \"time\": \"today\"
          }
        }
        """.trimIndent()
    }
}