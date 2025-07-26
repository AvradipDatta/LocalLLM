package com.google.ai.edge.gallery.ui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.ai.edge.gallery.GalleryTopAppBar
import com.google.ai.edge.gallery.data.AppBarAction
import com.google.ai.edge.gallery.data.AppBarActionType
import com.google.ai.edge.gallery.util.callWhatsAppWebhook
import com.google.ai.edge.gallery.util.sendEmail
import com.google.ai.edge.gallery.util.sendTelegram
import com.google.mediapipe.tasks.genai.llminference.LlmInference

import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryWorkflowScreen(onBack: () -> Unit) {
    var sourceText by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("Telegram") }
    var isProcessing by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.padding(16.dp)) {
        GalleryTopAppBar(
            title = "Send Summary",
            leftAction = AppBarAction(AppBarActionType.NAVIGATE_UP, onBack)
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text("Enter text or paste your email/message content:")
        OutlinedTextField(
            value = sourceText,
            onValueChange = { sourceText = it },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text("Choose destination:")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("Telegram", "Email", "WhatsApp").forEach {
                Button(
                    onClick = { destination = it },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (destination == it) MaterialTheme.colorScheme.primary else Color.LightGray
                    )
                ) {
                    Text(it)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
//        Button(
//            onClick = {
//                isProcessing = true
//                coroutineScope.launch {
//                    val modelPath = getModelPath(context) // implement your own logic
//                    val llm = LlmInference.create(
//                        context,
//                        LlmInference.LlmInferenceOptions.builder()
//                            .setModelPath(modelPath)
//                            .setMaxTokens(512)
//                            .setTemperature(0.7f)
//                            .build()
//                    )
//                    llm.generateResponseAsync(sourceText, object : ResultListener {
//                        override fun onResult(result: String?, done: Boolean) {
//                            if (done && result != null) {
//                                summary = result
//                                isProcessing = false
//                                when (destination) {
//                                    "Telegram" -> sendTelegram("<BOT_TOKEN>", "<CHAT_ID>", result)
//                                    "Email" -> sendEmail("to@example.com", "Summary", result)
//                                    "WhatsApp" -> callWhatsAppWebhook(result)
//                                }
//                            }
//                        }
//                    })
//                }
//            },
//            enabled = sourceText.isNotEmpty() && !isProcessing
//        ) {
//            Text("Send Summary")
//        }

        if (summary.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Summary Result:", style = MaterialTheme.typography.titleMedium)
            Text(summary)
        }
    }
}

// Dummy path resolver — replace this with real model path logic
fun getModelPath(context: Context): String {
    return "path/to/your/model.task" // Place your LLM model file here
}
