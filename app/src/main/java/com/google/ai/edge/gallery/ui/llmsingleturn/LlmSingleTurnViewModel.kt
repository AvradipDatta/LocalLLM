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

import android.util.Log
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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.widget.Toast
import android.content.Context
import com.google.ai.edge.gallery.workflow.WorkflowParser
import com.google.ai.edge.gallery.workflow.WorkflowExecutor



private const val TAG = "AGLlmSingleTurnVM"

data class WorkflowTask(
  val source: String,
  val destination: String,
  val action: String
)


data class LlmSingleTurnUiState(
  /** Indicates whether the runtime is currently processing a message. */
  val inProgress: Boolean = false,

  /**
   * Indicates whether the model is preparing (before outputting any result and after initializing).
   */
  val preparing: Boolean = false,

  // model -> <template label -> response>
  val responsesByModel: Map<String, Map<String, String>>,

  // model -> <template label -> benchmark result>
  val benchmarkByModel: Map<String, Map<String, ChatMessageBenchmarkLlmResult>>,

  /** Selected prompt template type. */
  val selectedPromptTemplateType: PromptTemplateType = PromptTemplateType.entries[0],

  //promt response line
  val promptResponse: String = "", // ✅ You need to add this line if it's missing

)

private val STATS =
  listOf(
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

      // Wait for instance to be initialized.
      while (model.instance == null) {
        delay(100)
      }

      LlmChatModelHelper.resetSession(model = model)
      delay(500)

      //new code snippet
      val workflow = WorkflowParser.parse(input)
      if (workflow != null) {
        val response = WorkflowExecutor.execute(workflow)
        _uiState.update { it.copy(promptResponse = response) }
        setInProgress(false)
        setPreparing(false)
        return@launch
      }


      // Run inference.
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

          // Incrementally update the streamed partial results.
          response = processLlmResponse(response = "$response$partialResult")

          // Update response.
          updateResponse(
            model = model,
            promptTemplateType = uiState.value.selectedPromptTemplateType,
            response = response,
          )

          // Update benchmark (with throttling).
          if (curTs - lastBenchmarkUpdateTs > 200) {
            decodeSpeed = decodeTokens / ((curTs - firstTokenTs) / 1000f)
            if (decodeSpeed.isNaN()) {
              decodeSpeed = 0f
            }
            val benchmark =
              ChatMessageBenchmarkLlmResult(
                orderedStats = STATS,
                statValues =
                  mutableMapOf(
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

          if (done) {
            setInProgress(false)
          }
        },
        cleanUpListener = {
          setPreparing(false)
          setInProgress(false)
        },
      )
    }
  }

  fun selectPromptTemplate(model: Model, promptTemplateType: PromptTemplateType) {
    Log.d(TAG, "selecting prompt template: ${promptTemplateType.label}")

    // Clear response.
    updateResponse(model = model, promptTemplateType = promptTemplateType, response = "")

    this._uiState.update {
      this.uiState.value.copy(selectedPromptTemplateType = promptTemplateType)
    }
  }

  fun setInProgress(inProgress: Boolean) {
    _uiState.update { _uiState.value.copy(inProgress = inProgress) }
  }

  fun setPreparing(preparing: Boolean) {
    _uiState.update { _uiState.value.copy(preparing = preparing) }
  }

  fun updateResponse(model: Model, promptTemplateType: PromptTemplateType, response: String) {
    _uiState.update { currentState ->
      val currentResponses = currentState.responsesByModel
      val modelResponses = currentResponses[model.name]?.toMutableMap() ?: mutableMapOf()
      modelResponses[promptTemplateType.label] = response
      val newResponses = currentResponses.toMutableMap()
      newResponses[model.name] = modelResponses
      currentState.copy(responsesByModel = newResponses)
    }
  }

  fun updateBenchmark(
    model: Model,
    promptTemplateType: PromptTemplateType,
    benchmark: ChatMessageBenchmarkLlmResult,
  ) {
    _uiState.update { currentState ->
      val currentBenchmark = currentState.benchmarkByModel
      val modelBenchmarks = currentBenchmark[model.name]?.toMutableMap() ?: mutableMapOf()
      modelBenchmarks[promptTemplateType.label] = benchmark
      val newBenchmarks = currentBenchmark.toMutableMap()
      newBenchmarks[model.name] = modelBenchmarks
      currentState.copy(benchmarkByModel = newBenchmarks)
    }
  }

  fun stopResponse(model: Model) {
    Log.d(TAG, "Stopping response for model ${model.name}...")
    viewModelScope.launch(Dispatchers.Default) {
      setInProgress(false)
      val instance = model.instance as LlmModelInstance
      instance.session.cancelGenerateResponseAsync()
    }
  }

  private fun createUiState(task: Task): LlmSingleTurnUiState {
    val responsesByModel: MutableMap<String, Map<String, String>> = mutableMapOf()
    val benchmarkByModel: MutableMap<String, Map<String, ChatMessageBenchmarkLlmResult>> =
      mutableMapOf()
    for (model in task.models) {
      responsesByModel[model.name] = mutableMapOf()
      benchmarkByModel[model.name] = mutableMapOf()
    }
    return LlmSingleTurnUiState(
      responsesByModel = responsesByModel,
      benchmarkByModel = benchmarkByModel,
    )
  }

  fun checkIfWorkflow(prompt: String): Boolean {
    return prompt.lowercase().contains("gmail") || prompt.lowercase().contains("telegram")
  }

  fun extractWorkflowDetails(prompt: String): WorkflowTask {
    // Let LLM infer workflow details. This is mocked. You can later call your LLM here.
    return WorkflowTask(
      source = if (prompt.contains("gmail", ignoreCase = true)) "gmail" else "telegram",
      destination = if (prompt.contains("telegram", ignoreCase = true)) "telegram" else "gmail",
      action = if (prompt.contains("summarize", ignoreCase = true)) "summarize" else "forward"
    )
  }

  fun executeWorkflow(task: WorkflowTask): String {
    Log.d("Workflow", "Source: ${task.source}, Destination: ${task.destination}, Action: ${task.action}")
    when (task.source to task.destination) {
      "gmail" to "gmail" -> { /* read from Gmail, process, send to Gmail */ }
      "gmail" to "telegram" -> { /* read from Gmail, process, send to Telegram */ }
      "telegram" to "gmail" -> { /* read Telegram msg, send to Gmail */ }
      "telegram" to "telegram" -> { /* process & forward inside Telegram */ }
    }
    return "Workflow detected!\nAction: ${task.action}\nFrom: ${task.source}\nTo: ${task.destination}"
  }

}
