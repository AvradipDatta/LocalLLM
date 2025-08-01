/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.ui.llmsingleturn

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.common.processLlmResponse
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.TASK_LLM_PROMPT_LAB
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageBenchmarkLlmResult
import com.google.ai.edge.gallery.ui.common.chat.Stat
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.gallery.ui.llmchat.LlmModelInstance
import com.google.api.services.gmail.Gmail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import javax.inject.Inject

private const val TAG = "AGLlmSingleTurnVM"
private const val TELEGRAM_BOT_TOKEN = "YOUR_TELEGRAM_BOT_TOKEN"
private const val TELEGRAM_CHAT_ID = "YOUR_TELEGRAM_CHAT_ID"

data class WorkflowTask(
  val source: String,
  val destination: String,
  val action: String
)

data class LlmSingleTurnUiState(
  val inProgress: Boolean = false,
  val preparing: Boolean = false,
  val responsesByModel: Map<String, Map<String, String>>,
  val benchmarkByModel: Map<String, Map<String, ChatMessageBenchmarkLlmResult>>,
  val selectedPromptTemplateType: PromptTemplateType = PromptTemplateType.entries[0],
  val promptResponse: String = "",
)

private val STATS = listOf(
  Stat(id = "time_to_first_token", label = "1st token", unit = "sec"),
  Stat(id = "prefill_speed", label = "Prefill speed", unit = "tokens/s"),
  Stat(id = "decode_speed", label = "Decode speed", unit = "tokens/s"),
  Stat(id = "latency", label = "Latency", unit = "sec"),
)

@HiltViewModel
class LlmSingleTurnViewModel @Inject constructor() : ViewModel() {
  private val _uiState = MutableStateFlow(createUiState(task = TASK_LLM_PROMPT_LAB))
  val uiState = _uiState.asStateFlow()

  fun generateResponse(model: Model, input: String) {
    viewModelScope.launch(Dispatchers.Default) {
      setInProgress(true)
      setPreparing(true)

      while (model.instance == null) {
        delay(100)
      }

      LlmChatModelHelper.resetSession(model = model)
      delay(500)

      val instance = model.instance as LlmModelInstance
      val prefillTokens = instance.session.sizeInTokens(input)

      var firstRun = true
      var timeToFirstToken = 0f
      var firstTokenTs = 0L
      var decodeTokens = 0
      var prefillSpeed = 0f
      var decodeSpeed: Float
      val start = System.currentTimeMillis()
      var response = ""
      var lastBenchmarkUpdateTs = 0L

      LlmChatModelHelper.runInference(
        model = model,
        input = input,
        resultListener = { partialResult, done ->
          val curTs = System.currentTimeMillis()

          if (firstRun) {
            setPreparing(false)
            firstTokenTs = System.currentTimeMillis()
            timeToFirstToken = (firstTokenTs - start) / 1000f
            prefillSpeed = prefillTokens / timeToFirstToken
            firstRun = false
          } else {
            decodeTokens++
          }

          response = processLlmResponse(response = "$response$partialResult")

          updateResponse(
            model = model,
            promptTemplateType = uiState.value.selectedPromptTemplateType,
            response = response,
          )

          if (curTs - lastBenchmarkUpdateTs > 200) {
            decodeSpeed = decodeTokens / ((curTs - firstTokenTs) / 1000f)
            if (decodeSpeed.isNaN()) decodeSpeed = 0f

            val benchmark = ChatMessageBenchmarkLlmResult(
              orderedStats = STATS,
              statValues = mutableMapOf(
                "prefill_speed" to prefillSpeed,
                "decode_speed" to decodeSpeed,
                "time_to_first_token" to timeToFirstToken,
                "latency" to (curTs - start).toFloat() / 1000f,
              ),
              running = !done,
              latencyMs = -1f,
            )
            updateBenchmark(
              model = model,
              promptTemplateType = uiState.value.selectedPromptTemplateType,
              benchmark = benchmark,
            )
            lastBenchmarkUpdateTs = curTs
          }

          if (done) setInProgress(false)
        },
        cleanUpListener = {
          setPreparing(false)
          setInProgress(false)
        },
      )
    }
  }

  fun checkIfWorkflow(prompt: String): Boolean {
    return prompt.contains("gmail", true) || prompt.contains("telegram", true)
  }

  fun extractWorkflowDetails(prompt: String): WorkflowTask {
    return WorkflowTask(
      source = if (prompt.contains("gmail", true)) "gmail" else "telegram",
      destination = if (prompt.contains("telegram", true)) "telegram" else "gmail",
      action = if (prompt.contains("summarize", true)) "summarize" else "forward"
    )
  }

  fun executeWorkflow(task: WorkflowTask, gmailService: Gmail?, context: Context): String {
    return when (task.source to task.destination) {
      "gmail" to "telegram" -> {
        try {
          if (gmailService == null) return "Gmail not available. Please sign in."
          val emails = fetchGmailMessages(gmailService)
          val summary = emails.take(1000) + if (emails.length > 1000) "..." else ""
          val sent = sendTelegramMessage(summary)
          if (sent) "✅ Sent email summary to Telegram." else "❌ Failed to send to Telegram."
        } catch (e: Exception) {
          Log.e(TAG, "Gmail->Telegram failed", e)
          "❌ Workflow failed: ${e.message}"
        }
      }
      else -> "⚠️ Workflow not yet supported for ${task.source} to ${task.destination}"
    }
  }

  private fun fetchGmailMessages(gmailService: Gmail): String {
    val listRequest = gmailService.users().messages().list("me").setMaxResults(10)
    val messages = listRequest.execute().messages ?: return "No emails."
    val sb = StringBuilder()
    for (msg in messages) {
      val message = gmailService.users().messages().get("me", msg.id).setFormat("full").execute()
      sb.append(message.snippet).append("\n\n")
    }
    return sb.toString()
  }

  private fun sendTelegramMessage(message: String): Boolean {
    return try {
      val client = OkHttpClient()
      val url = "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendMessage?chat_id=$TELEGRAM_CHAT_ID&text=${URLEncoder.encode(message, "UTF-8")}"
      val request = Request.Builder().url(url).get().build()
      val response = client.newCall(request).execute()
      response.isSuccessful
    } catch (e: Exception) {
      Log.e(TAG, "Telegram error", e)
      false
    }
  }

  fun selectPromptTemplate(model: Model, promptTemplateType: PromptTemplateType) {
    updateResponse(model, promptTemplateType, "")
    _uiState.update {
      it.copy(selectedPromptTemplateType = promptTemplateType)
    }
  }

  fun stopResponse(model: Model) {
    viewModelScope.launch(Dispatchers.Default) {
      setInProgress(false)
      (model.instance as? LlmModelInstance)?.session?.cancelGenerateResponseAsync()
    }
  }

  fun setInProgress(value: Boolean) {
    _uiState.update { it.copy(inProgress = value) }
  }

  fun setPreparing(value: Boolean) {
    _uiState.update { it.copy(preparing = value) }
  }

  fun updateResponse(model: Model, promptTemplateType: PromptTemplateType, response: String) {
    _uiState.update {
      val modelResponses = it.responsesByModel[model.name]?.toMutableMap() ?: mutableMapOf()
      modelResponses[promptTemplateType.label] = response
      val newResponses = it.responsesByModel.toMutableMap()
      newResponses[model.name] = modelResponses
      it.copy(responsesByModel = newResponses)
    }
  }

  fun updateBenchmark(model: Model, promptTemplateType: PromptTemplateType, benchmark: ChatMessageBenchmarkLlmResult) {
    _uiState.update {
      val modelBenchmarks = it.benchmarkByModel[model.name]?.toMutableMap() ?: mutableMapOf()
      modelBenchmarks[promptTemplateType.label] = benchmark
      val newBenchmarks = it.benchmarkByModel.toMutableMap()
      newBenchmarks[model.name] = modelBenchmarks
      it.copy(benchmarkByModel = newBenchmarks)
    }
  }

  private fun createUiState(task: Task): LlmSingleTurnUiState {
    val responsesByModel = mutableMapOf<String, Map<String, String>>()
    val benchmarkByModel = mutableMapOf<String, Map<String, ChatMessageBenchmarkLlmResult>>()
    for (model in task.models) {
      responsesByModel[model.name] = mutableMapOf()
      benchmarkByModel[model.name] = mutableMapOf()
    }
    return LlmSingleTurnUiState(
      responsesByModel = responsesByModel,
      benchmarkByModel = benchmarkByModel,
    )
  }
}
