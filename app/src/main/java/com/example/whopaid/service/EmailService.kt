package com.example.whopaid.service

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Simple synchronous helper to send EmailJS requests via OkHttp.
 *
 * Note:
 * - EmailJS uses a public "user_id" (your EmailJS user ID) and service/template IDs.
 * - Keep in mind this is a public client key; for production you might want to proxy via a server.
 *
 * Usage (call from a background thread / coroutine IO dispatcher):
 *   EmailService.sendReminder(serviceId, templateId, userId, templateParams)
 *
 * Returns true if the HTTP call succeeded (2xx).
 */
object EmailService {
    private val client = OkHttpClient()
    private val gson = Gson()
    private const val ENDPOINT = "https://api.emailjs.com/api/v1.0/email/send"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    data class Payload(
        val service_id: String,
        val template_id: String,
        val user_id: String,
        val template_params: Map<String, Any>
    )

    fun sendReminder(
        serviceId: String,
        templateId: String,
        userId: String,
        templateParams: Map<String, Any>
    ): Boolean {
        val payload = Payload(
            service_id = serviceId,
            template_id = templateId,
            user_id = userId,
            template_params = templateParams
        )
        val json = gson.toJson(payload)
        val body = json.toRequestBody(JSON)
        val req = Request.Builder()
            .url(ENDPOINT)
            .post(body)
            .build()

        client.newCall(req).execute().use { resp ->
            return resp.isSuccessful
        }
    }
}
