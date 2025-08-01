package com.google.ai.edge.gallery.ui.llmsingleturn

import android.content.Context
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.TASK_LLM_PROMPT_LAB
import com.google.ai.edge.gallery.ui.common.ErrorDialog
import com.google.ai.edge.gallery.ui.common.ModelPageAppBar
import com.google.ai.edge.gallery.ui.common.chat.ModelDownloadStatusInfoPanel
import com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.ui.theme.customColors
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import androidx.compose.ui.unit.dp


private const val TAG = "AGLlmSingleTurnScreen"

// Your actual telegram bot credentials here
private const val TELEGRAM_BOT_TOKEN = "YOUR_TELEGRAM_BOT_TOKEN"
private const val TELEGRAM_CHAT_ID = "YOUR_TELEGRAM_CHAT_ID"

/** Navigation destination data */
object LlmSingleTurnDestination {
  const val route = "LlmSingleTurnRoute"
}

@Composable
fun LlmSingleTurnScreen(
  modelManagerViewModel: ModelManagerViewModel,
  navigateUp: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: LlmSingleTurnViewModel,
) {
  val task = TASK_LLM_PROMPT_LAB
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val uiState by viewModel.uiState.collectAsState()
  val selectedModel = modelManagerUiState.selectedModel
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  var navigatingUp by remember { mutableStateOf(false) }
  var showErrorDialog by remember { mutableStateOf(false) }

  // Workflow state
  var workflowRunning by remember { mutableStateOf(false) }
  var workflowError by remember { mutableStateOf<String?>(null) }
  var workflowResult by remember { mutableStateOf<String?>(null) }

  // Get currently signed-in Google account from your existing shared state
  // For example, if you store it in ModelManagerViewModel or another singleton, fetch it here
  // Replace this line with your actual way of obtaining GoogleSignInAccount from your existing code:
  val googleAccount: GoogleSignInAccount? = modelManagerViewModel.getSignedInGoogleAccount()

  val handleNavigateUp = {
    navigatingUp = true
    navigateUp()
    scope.launch(Dispatchers.Default) {
      for (model in task.models) {
        modelManagerViewModel.cleanupModel(task = task, model = model)
      }
    }
  }

  BackHandler { handleNavigateUp() }

  val curDownloadStatus = modelManagerUiState.modelDownloadStatus[selectedModel.name]
  LaunchedEffect(curDownloadStatus, selectedModel.name) {
    if (!navigatingUp && curDownloadStatus?.status == ModelDownloadStatusType.SUCCEEDED) {
      Log.d(TAG, "Initializing model '${selectedModel.name}'")
      modelManagerViewModel.initializeModel(context, task = task, model = selectedModel)
    }
  }

  val modelInitializationStatus = modelManagerUiState.modelInitializationStatus[selectedModel.name]
  LaunchedEffect(modelInitializationStatus) {
    showErrorDialog = modelInitializationStatus?.status == ModelInitializationStatusType.ERROR
  }

  Scaffold(
    modifier = modifier,
    topBar = {
      ModelPageAppBar(
        task = task,
        model = selectedModel,
        modelManagerViewModel = modelManagerViewModel,
        inProgress = uiState.inProgress,
        modelPreparing = uiState.preparing,
        onConfigChanged = { _, _ -> },
        onBackClicked = { handleNavigateUp() },
        onModelSelected = { newSelectedModel ->
          scope.launch(Dispatchers.Default) {
            modelManagerViewModel.cleanupModel(task = task, model = selectedModel)
            modelManagerViewModel.selectModel(model = newSelectedModel)
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier =
        Modifier.padding(
          top = innerPadding.calculateTopPadding(),
          start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
          end = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
        )
    ) {
      ModelDownloadStatusInfoPanel(
        model = selectedModel,
        task = task,
        modelManagerViewModel = modelManagerViewModel,
      )

      val modelDownloaded = curDownloadStatus?.status == ModelDownloadStatusType.SUCCEEDED

      Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier.weight(1f).alpha(if (modelDownloaded) 1.0f else 0.0f)
      ) {
        VerticalSplitView(
          modifier = Modifier.fillMaxSize(),
          topView = {
            PromptTemplatesPanel(
              model = selectedModel,
              viewModel = viewModel,
              modelManagerViewModel = modelManagerViewModel,
              onSend = { fullPrompt ->
                if (workflowRunning) {
                  Log.w(TAG, "Workflow already running, ignoring new prompt")
                  return@PromptTemplatesPanel
                }
                scope.launch {
                  workflowRunning = true
                  workflowError = null
                  workflowResult = null

                  val isWorkflowPrompt = viewModel.checkIfWorkflow(fullPrompt)

                  if (!isWorkflowPrompt) {
                    // Not a workflow, normal LLM response
                    viewModel.generateResponse(model = selectedModel, input = fullPrompt)
                    workflowRunning = false
                    return@launch
                  }

                  // Workflow detected — extract details
                  val workflow = viewModel.extractWorkflowDetails(fullPrompt)

                  if (googleAccount == null) {
                    workflowError = "Please sign in with Google to use Gmail features."
                    workflowRunning = false
                    return@launch
                  }

                  try {
                    // Fetch Gmail emails using your existing gmailService creation method:
                    val gmailService = modelManagerViewModel.getGmailService(context, googleAccount)

                    val emailsText = fetchGmailMessages(gmailService)

                    // Summarize or process emails here (replace with your LLM or logic)
                    val summary = if (emailsText.length > 1000) emailsText.substring(0, 1000) + "..." else emailsText

                    // Send summary to Telegram
                    val telegramSuccess = sendTelegramMessage(summary)

                    if (telegramSuccess) {
                      workflowResult = "Workflow succeeded: Email summary sent to Telegram."
                    } else {
                      workflowError = "Failed to send message to Telegram."
                    }
                  } catch (e: Exception) {
                    workflowError = "Workflow failed: ${e.localizedMessage ?: "unknown error"}"
                  }

                  workflowRunning = false
                }
              },
              onStopButtonClicked = { model -> viewModel.stopResponse(model = model) },
              modifier = Modifier.fillMaxSize(),
            )
          },
          bottomView = {
            Box(
              contentAlignment = Alignment.BottomCenter,
              modifier = Modifier.fillMaxSize().background(MaterialTheme.customColors.agentBubbleBgColor)
            ) {
              ResponsePanel(
                model = selectedModel,
                viewModel = viewModel,
                modelManagerViewModel = modelManagerViewModel,
                modifier = Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding()),
              )
            }
          }
        )
      }

      if (workflowRunning) {
        Box(
          modifier = Modifier.fillMaxWidth().padding(8.dp),
          contentAlignment = Alignment.Center
        ) {
          CircularProgressIndicator()
        }
      }

      workflowError?.let { errorMsg ->
        ErrorDialog(error = errorMsg, onDismiss = { workflowError = null })
      }

      workflowResult?.let { resultMsg ->
        AlertDialog(
          onDismissRequest = { workflowResult = null },
          confirmButton = {
            TextButton(onClick = { workflowResult = null }) {
              Text("OK")
            }
          },
          title = { Text("Workflow Result") },
          text = { Text(resultMsg) }
        )
      }

      if (showErrorDialog) {
        ErrorDialog(
          error = modelInitializationStatus?.error ?: "",
          onDismiss = { showErrorDialog = false },
        )
      }
    }
  }
}

// Fetch recent Gmail messages snippet text (reuse your existing logic here)
private fun fetchGmailMessages(gmailService: com.google.api.services.gmail.Gmail): String {
  val user = "me"
  val listRequest = gmailService.users().messages().list(user).setMaxResults(10)
  val response = listRequest.execute()
  val messages = response.messages ?: return "No emails found."
  val sb = StringBuilder()
  for (msg in messages) {
    val message = gmailService.users().messages().get(user, msg.id).setFormat("full").execute()
    sb.append(message.snippet).append("\n\n")
  }
  return sb.toString()
}

// Send Telegram message via HTTP GET
private fun sendTelegramMessage(message: String): Boolean {
  return try {
    val client = OkHttpClient()
    val url =
      "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendMessage?chat_id=$TELEGRAM_CHAT_ID&text=${URLEncoder.encode(message, "UTF-8")}"
    val request = Request.Builder().url(url).get().build()
    val response = client.newCall(request).execute()
    response.isSuccessful
  } catch (e: Exception) {
    Log.e(TAG, "Telegram send error", e)
    false
  }
}
