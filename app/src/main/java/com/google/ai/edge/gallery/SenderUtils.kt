package com.google.ai.edge.gallery.util

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

fun sendTelegram(botToken: String, chatId: String, message: String) {
    val url = "https://api.telegram.org/bot$botToken/sendMessage"
    val requestBody = FormBody.Builder()
        .add("chat_id", chatId)
        .add("text", message)
        .build()

    val request = Request.Builder()
        .url(url)
        .post(requestBody)
        .build()

    OkHttpClient().newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("SenderUtils", "Telegram send failed: ${e.message}")
        }

        override fun onResponse(call: Call, response: Response) {
            Log.d("SenderUtils", "Telegram response: ${response.code}")
        }
    })
}

fun callWhatsAppWebhook(message: String) {
    val url = "https://your-server.com/send-whatsapp" // Your backend API URL
    val json = JSONObject().put("text", message).toString()

    val body = json.toRequestBody("application/json".toMediaType())
    val request = Request.Builder()
        .url(url)
        .post(body)
        .build()

    OkHttpClient().newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("SenderUtils", "WhatsApp send failed: ${e.message}")
        }

        override fun onResponse(call: Call, response: Response) {
            Log.d("SenderUtils", "WhatsApp response: ${response.code}")
        }
    })
}

fun sendEmail(to: String, subject: String, body: String) {
    // For production use: implement email via backend or SMTP lib
    Log.d("SenderUtils", "Sending email to $to with subject $subject and body:\n$body")
    // Stub only – implement real email sending securely on backend
}
