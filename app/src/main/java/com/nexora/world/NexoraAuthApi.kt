package com.nexora.world

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class NexoraAuthResult(
    val token: String,
    val playerId: String,
    val username: String
)

/** Small REST client for the backend register/login endpoints. Call from a worker thread. */
object NexoraAuthApi {
    fun authenticate(
        serverUrl: String,
        username: String,
        password: String,
        createAccount: Boolean
    ): NexoraAuthResult {
        val base = serverUrl.trim().trimEnd('/')
        require(base.startsWith("https://") || base.startsWith("http://")) {
            "أدخل عنوان الخادم كاملاً ويبدأ بـ http:// أو https://"
        }
        val endpoint = if (createAccount) "register" else "login"
        val connection = (URL("$base/api/auth/$endpoint").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }

        try {
            val body = JSONObject()
                .put("username", username.trim())
                .put("password", password)
                .toString()
                .toByteArray(Charsets.UTF_8)
            connection.outputStream.use { it.write(body) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val response = runCatching { JSONObject(responseText) }.getOrNull()
            if (status !in 200..299 || response == null) {
                val errorValue = response?.opt("error")
                val message = when (errorValue) {
                    is JSONObject -> errorValue.optString("message", "")
                    is String -> errorValue
                    else -> ""
                }.ifBlank { "تعذر تسجيل الدخول (HTTP $status)" }
                throw IOException(message)
            }

            val user = response.optJSONObject("user")
                ?: throw IOException("استجابة الخادم لا تحتوي على بيانات الحساب")
            val token = response.optString("token")
            val playerId = user.optString("id")
            val returnedName = user.optString("username", username.trim())
            if (token.isBlank() || playerId.isBlank()) {
                throw IOException("استجابة الخادم غير مكتملة")
            }
            return NexoraAuthResult(token, playerId, returnedName)
        } finally {
            connection.disconnect()
        }
    }
}
