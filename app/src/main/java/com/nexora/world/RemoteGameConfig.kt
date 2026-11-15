package com.nexora.world

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/** Loads safe gameplay tuning remotely; native code changes still require an APK update. */
object RemoteGameConfig {
    private const val CONFIG_URL = "https://raw.githubusercontent.com/dyabalaskndrduab-commits/NEXORA-WORLD/main/live-config.json"
    @Volatile var moveSpeed = 4.0f
    @Volatile var acceleration = 11.0f
    @Volatile var deceleration = 7.5f
    @Volatile var turnResponsiveness = 12.0f
    @Volatile var walkCycleBase = 7.0f
    @Volatile var walkCycleSpeed = 2.0f
    @Volatile var cameraFollow = 5.5f

    fun refresh(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences("nexora_live_config", Context.MODE_PRIVATE)
        applyJson(prefs.getString("cached_json", null))
        thread(name = "nexora-config") {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(CONFIG_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 7000
                    readTimeout = 7000
                    requestMethod = "GET"
                    setRequestProperty("Cache-Control", "no-cache")
                }
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val parsed = JSONObject(body)
                    if (parsed.optInt("schemaVersion", 0) == 1) {
                        prefs.edit().putString("cached_json", body).apply()
                        applyJson(body)
                    }
                }
            } catch (e: Exception) {
                Log.w("NEXORA_CONFIG", "Using last known movement settings", e)
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun applyJson(raw: String?) {
        if (raw.isNullOrBlank()) return
        try {
            val json = JSONObject(raw)
            if (json.optInt("schemaVersion", 0) != 1) return
            val movement = json.optJSONObject("movement") ?: return
            moveSpeed = movement.optDouble("moveSpeed", moveSpeed.toDouble()).toFloat().coerceIn(1f, 7f)
            acceleration = movement.optDouble("acceleration", acceleration.toDouble()).toFloat().coerceIn(2f, 20f)
            deceleration = movement.optDouble("deceleration", deceleration.toDouble()).toFloat().coerceIn(2f, 16f)
            turnResponsiveness = movement.optDouble("turnResponsiveness", turnResponsiveness.toDouble()).toFloat().coerceIn(3f, 22f)
            walkCycleBase = movement.optDouble("walkCycleBase", walkCycleBase.toDouble()).toFloat().coerceIn(2f, 12f)
            walkCycleSpeed = movement.optDouble("walkCycleSpeed", walkCycleSpeed.toDouble()).toFloat().coerceIn(0f, 5f)
            cameraFollow = movement.optDouble("cameraFollow", cameraFollow.toDouble()).toFloat().coerceIn(1f, 12f)
        } catch (e: Exception) {
            Log.w("NEXORA_CONFIG", "Invalid remote config ignored", e)
        }
    }
}
