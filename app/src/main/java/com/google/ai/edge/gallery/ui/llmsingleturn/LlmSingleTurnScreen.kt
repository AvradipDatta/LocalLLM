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
import android.util.Base64
import com.google.api.services.gmail.model.Message
import java.util.*
import javax.mail.Message.RecipientType
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import java.io.ByteArrayOutputStream
import com.google.api.client.extensions.android.http.AndroidHttp
import android.app.AlertDialog
import java.io.PrintWriter
import java.io.StringWriter
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.withContext



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
                      AndroidHttp.newCompatibleTransport(),
                      GsonFactory.getDefaultInstance(),
                      credential
                    ).setApplicationName("LocalLLM").build()

                    val subject = "Workflow Detected"
                    val body = "A workflow was detected in your prompt."
                    val email = googleSignInAccount.email ?: throw IllegalStateException("Email not found")

                    var emailError: String? = null
                    val emailSent = withContext(Dispatchers.IO) {
                      try {
                        sendEmailToSelf(gmailService, email, subject, body)
                      } catch (e: Exception) {
                        emailError = Log.getStackTraceString(e)
                        false
                      }
                    }
                    val (telegramSuccess, telegramError) = sendTelegramMessage(context, "🚨 Workflow Detected!\nSent you a test email and Telegram message.")

                    if (emailSent && telegramSuccess) {
                      workflowResult = "✅ Workflow triggered!\nGmail + Telegram notifications sent successfully."
                    } else {
                      workflowError = buildString {
                        if (!emailSent) {
                          append("📧 Gmail failed:\n$emailError\n\n")
                        }
                        if (!telegramSuccess) {
                          append("📨 Telegram failed:\n$telegramError")
                        }
                      }
                    }

                  } catch (e: Exception) {
                    val errorText = Log.getStackTraceString(e)
                    workflowError = "Workflow crashed:\n\n$errorText"
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

suspend fun sendTelegramMessage(context: Context, message: String): Pair<Boolean, String?> {
  return withContext(Dispatchers.IO) {
    try {
      val client = OkHttpClient()
      val url =
        "https://api.telegram.org/bot8281461205:AAG7F6Je80ImQ4sd3jjBMXr8KPLu5Ixs8Nc/sendMessage?chat_id=5416836654&text=" +
                URLEncoder.encode(message, "UTF-8")
      val request = Request.Builder().url(url).get().build()
      val response = client.newCall(request).execute()

      if (!response.isSuccessful) {
        val errorBody = response.body?.string()
        val fullError = "Telegram error: HTTP ${response.code}\n$errorBody"
        saveErrorToFile(context, "telegram_error.txt", fullError)
        return@withContext Pair(false, fullError)
      }

      return@withContext Pair(true, null)
    } catch (e: Exception) {
      val errorText = Log.getStackTraceString(e)
      saveErrorToFile(context, "telegram_error.txt", errorText)
      return@withContext Pair(false, errorText)
    }
  }
}


fun saveErrorToFile(context: Context, fileName: String, content: String) {
  try {
    val file = File(context.getExternalFilesDir(null), fileName)
    FileOutputStream(file).use { output ->
      output.write(content.toByteArray())
    }
  } catch (e: Exception) {
    Log.e("FileSave", "Failed to write error file: ${e.message}")
  }
}


private fun showAlertDialog(context: Context, title: String, message: String) {
  AlertDialog.Builder(context)
    .setTitle(title)
    .setMessage(message)
    .setPositiveButton("OK", null)
    .create()
    .show()
}



fun sendEmailToSelf(gmailService: Gmail, recipientEmail: String, subject: String, body: String): Boolean {
  val props = Properties()
  val session = Session.getDefaultInstance(props, null)

  val email = MimeMessage(session).apply {
    setFrom(InternetAddress(recipientEmail))
    addRecipient(javax.mail.Message.RecipientType.TO, InternetAddress(recipientEmail))
    setSubject(subject)
    setText(body)
  }

  val buffer = ByteArrayOutputStream()
  email.writeTo(buffer)
  val encodedEmail = Base64.encodeToString(buffer.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)

  val message = Message().apply {
    raw = encodedEmail
  }


  // This will throw exception if anything goes wrong
  gmailService.users().messages().send("me", message).execute()
  return true
}


//temp
fun readErrorFile(context: Context, filename: String): String {
  return try {
    val file = File(context.filesDir, filename)
    if (file.exists()) file.readText()
    else "Error file not found."
  } catch (e: Exception) {
    "Error reading file: ${e.localizedMessage}"
  }
}



