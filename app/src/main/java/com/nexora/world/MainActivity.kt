package com.nexora.world

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Android entry point: local 3D city plus optional authenticated social connection. */
class MainActivity : Activity(), NexoraRealtimeClient.Listener {
    private lateinit var gameView: Nexora3DView
    private lateinit var statusView: TextView
    private lateinit var preferences: android.content.SharedPreferences
    private val remotePlayers = LinkedHashMap<String, NexoraRemotePlayer>()
    @Volatile private var realtime: NexoraRealtimeClient? = null
    private var serverUrl = ""
    private var playerId: String? = null
    private var username = "Explorer"

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )

        preferences = getSharedPreferences("nexora_social_profile", Context.MODE_PRIVATE)
        serverUrl = preferences.getString("server_url", "") ?: ""
        username = preferences.getString("username", "Explorer") ?: "Explorer"
        playerId = preferences.getString("player_id", null)

        RemoteGameConfig.refresh(this)
        gameView = Nexora3DView(this)
        gameView.setNetworkEnabled(false)
        gameView.setLocalPlayerId(playerId)
        gameView.setLocalPositionListener { x, y -> realtime?.sendMovement(x, y) }

        val root = FrameLayout(this)
        root.addView(gameView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        root.addView(createSocialPanel(), FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        ).apply { setMargins(0, 14, 14, 0) })
        setContentView(root)

        val token = NexoraSessionStore.loadToken(this)
        if (!token.isNullOrBlank() && serverUrl.isNotBlank() && !playerId.isNullOrBlank()) {
            connectRealtime(token)
        } else {
            root.post { showAuthDialog() }
        }
    }

    private fun createSocialPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10, 7, 10, 7)
            setBackgroundColor(0xCC111D2D.toInt())
        }
        statusView = TextView(this).apply {
            text = "غير متصل — يمكنك اللعب محلياً أو تسجيل الدخول"
            setTextColor(0xFFEAF4FF.toInt())
            textSize = 10f
            maxLines = 2
        }
        panel.addView(statusView)
        panel.addView(buttonRow(
            actionButton("الحساب") { showAuthDialog() },
            actionButton("إنشاء") { realtime?.createPrivateRoom("غرفة NEXORA", 8) ?: offlineNotice() },
            actionButton("انضمام") { showCodeDialog() }
        ))
        panel.addView(buttonRow(
            actionButton("العامة") { realtime?.requestPublicRooms() ?: offlineNotice() },
            actionButton("مغادرة") { realtime?.leaveRoom() ?: offlineNotice() },
            actionButton("محادثة") { showChatDialog() }
        ))
        return panel
    }

    private fun buttonRow(vararg buttons: Button) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        buttons.forEach { addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = 4
        }) }
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 10f
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        minimumWidth = 0
        setPadding(7, 2, 7, 2)
        setOnClickListener { action() }
    }

    private fun offlineNotice() {
        Toast.makeText(this, "سجّل الدخول واتصل بالخادم أولاً", Toast.LENGTH_SHORT).show()
    }

    private fun showAuthDialog() {
        if (isFinishing) return
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 8, 22, 0)
        }
        val serverField = EditText(this).apply {
            hint = "عنوان الخادم HTTPS"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            setText(serverUrl.ifBlank { "https://your-server.example" })
        }
        val usernameField = EditText(this).apply {
            hint = "اسم المستخدم"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            setText(username.takeIf { it != "Explorer" }.orEmpty())
        }
        val passwordField = EditText(this).apply {
            hint = "كلمة المرور"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        val note = TextView(this).apply {
            text = "على الهاتف الحقيقي استخدم عنوان خادم منشوراً. 10.0.2.2 مخصص لمحاكي Android."
            textSize = 11f
            setTextColor(0xFF667788.toInt())
        }
        form.addView(serverField)
        form.addView(usernameField)
        form.addView(passwordField)
        form.addView(note)

        val dialog = AlertDialog.Builder(this)
            .setTitle("حساب NEXORA WORLD")
            .setView(form)
            .setPositiveButton("دخول", null)
            .setNeutralButton("إنشاء حساب", null)
            .setNegativeButton("لاحقاً", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                submitAuth(serverField.text.toString(), usernameField.text.toString(), passwordField.text.toString(), false, dialog)
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                submitAuth(serverField.text.toString(), usernameField.text.toString(), passwordField.text.toString(), true, dialog)
            }
        }
        dialog.show()
    }

    private fun submitAuth(
        endpoint: String,
        name: String,
        password: String,
        createAccount: Boolean,
        dialog: AlertDialog
    ) {
        if (name.trim().isBlank() || password.isBlank()) {
            Toast.makeText(this, "أدخل اسم المستخدم وكلمة المرور", Toast.LENGTH_SHORT).show()
            return
        }
        val normalizedUrl = endpoint.trim().trimEnd('/')
        serverUrl = normalizedUrl
        username = name.trim()
        preferences.edit()
            .putString("server_url", serverUrl)
            .putString("username", username)
            .apply()
        dialog.dismiss()
        statusView.text = "جارٍ الاتصال بخادم الحسابات..."
        Thread {
            try {
                val result = NexoraAuthApi.authenticate(normalizedUrl, name, password, createAccount)
                NexoraSessionStore.saveToken(applicationContext, result.token)
                runOnUiThread {
                    serverUrl = normalizedUrl
                    username = result.username
                    playerId = result.playerId
                    preferences.edit()
                        .putString("server_url", serverUrl)
                        .putString("username", username)
                        .putString("player_id", playerId)
                        .apply()
                    gameView.setLocalPlayerId(playerId)
                    connectRealtime(result.token)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    statusView.text = "فشل تسجيل الدخول"
                    Toast.makeText(this, error.localizedMessage ?: "تعذر تسجيل الدخول", Toast.LENGTH_LONG).show()
                    showAuthDialog()
                }
            }
        }.start()
    }

    private fun connectRealtime(token: String) {
        realtime?.close()
        gameView.setNetworkEnabled(false)
        remotePlayers.clear()
        gameView.setRemotePlayers(emptyList())
        val client = NexoraRealtimeClient(serverUrl, token)
        client.listener = this
        realtime = client
        client.connect()
    }

    private fun showCodeDialog() {
        promptText("الانضمام إلى غرفة خاصة", "أدخل رمز الدعوة") { code ->
            if (code.isNotBlank()) realtime?.joinPrivateRoom(code) ?: offlineNotice()
        }
    }

    private fun showChatDialog() {
        promptText("رسالة الغرفة", "اكتب رسالتك") { message ->
            if (message.isNotBlank()) realtime?.sendChat(message) ?: offlineNotice()
        }
    }

    private fun promptText(title: String, hint: String, submit: (String) -> Unit) {
        val field = EditText(this).apply {
            this.hint = hint
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(field)
            .setPositiveButton("إرسال") { _, _ -> submit(field.text.toString()) }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showInviteCode(code: String) {
        AlertDialog.Builder(this)
            .setTitle("تم إنشاء الغرفة الخاصة")
            .setMessage("رمز الدعوة:\n" + code + "\n\nأرسله إلى أصدقائك للانضمام.")
            .setPositiveButton("نسخ الرمز") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("NEXORA invite code", code))
                Toast.makeText(this, "تم نسخ رمز الدعوة", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun showPublicRooms(rooms: List<NexoraRoomInfo>) {
        val message = rooms.joinToString("\n") {
            it.name + " — " + it.playerCount + "/" + it.maxPlayers
        }.ifBlank { "لا توجد غرف عامة حالياً" }
        AlertDialog.Builder(this)
            .setTitle("الغرف العامة")
            .setMessage(message)
            .setPositiveButton("حسناً", null)
            .show()
    }

    private fun updateRemotePlayers() {
        gameView.setRemotePlayers(remotePlayers.values.toList())
    }

    override fun onConnectionChanged(connected: Boolean) {
        runOnUiThread {
            if (!connected) {
                gameView.setNetworkEnabled(false)
                remotePlayers.clear()
                updateRemotePlayers()
                statusView.text = "انقطع الاتصال — جارٍ إعادة المحاولة"
            } else {
                statusView.text = "متصل بالخادم — جارٍ مزامنة العالم"
            }
        }
    }

    override fun onWorldSnapshot(players: List<NexoraRemotePlayer>) {
        runOnUiThread {
            val local = players.firstOrNull { it.id == playerId }
            if (local != null) {
                gameView.setLocalPlayerId(local.id)
                gameView.applyAuthoritativePosition(local.x, local.y)
                gameView.setNetworkEnabled(true)
            }
            remotePlayers.clear()
            players.filter { it.id != playerId }.forEach { remotePlayers[it.id] = it }
            updateRemotePlayers()
        }
    }

    override fun onPlayerJoined(player: NexoraRemotePlayer) {
        runOnUiThread {
            if (player.id != playerId) {
                remotePlayers[player.id] = player
                updateRemotePlayers()
            }
        }
    }

    override fun onPlayerMoved(player: NexoraRemotePlayer) {
        runOnUiThread {
            if (player.id != playerId) {
                remotePlayers[player.id] = player
                updateRemotePlayers()
            }
        }
    }

    override fun onPlayerLeft(playerId: String) {
        runOnUiThread {
            remotePlayers.remove(playerId)
            updateRemotePlayers()
        }
    }

    override fun onPlayerCorrected(player: NexoraRemotePlayer) {
        runOnUiThread {
            if (player.id == playerId) gameView.applyAuthoritativePosition(player.x, player.y)
        }
    }

    override fun onRoomState(room: NexoraRoomInfo) {
        runOnUiThread {
            statusView.text = room.name + " — " + room.playerCount + "/" + room.maxPlayers
        }
    }

    override fun onRoomCreated(room: NexoraRoomInfo, inviteCode: String) {
        runOnUiThread {
            statusView.text = "الغرفة " + room.name + " جاهزة"
            showInviteCode(inviteCode)
        }
    }

    override fun onRoomJoined(room: NexoraRoomInfo) {
        runOnUiThread {
            statusView.text = "انضممت إلى " + room.name
            Toast.makeText(this, "تم الانضمام إلى الغرفة", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPublicRooms(rooms: List<NexoraRoomInfo>) {
        runOnUiThread { showPublicRooms(rooms) }
    }

    override fun onChatMessage(message: NexoraChatMessage) {
        runOnUiThread {
            statusView.text = message.username + ": " + message.message
        }
    }

    override fun onRoomHostChanged(hostId: String) {
        runOnUiThread {
            statusView.text = if (hostId == playerId) "أصبحت مدير الغرفة" else "تم نقل إدارة الغرفة"
        }
    }

    override fun onRoomClosed(roomId: String) {
        runOnUiThread {
            statusView.text = "أُغلقت الغرفة الخاصة"
        }
    }

    override fun onServerError(code: String, message: String) {
        runOnUiThread {
            statusView.text = message
            if (code == "CONNECT_ERROR" && message.contains("token", ignoreCase = true)) {
                NexoraSessionStore.clearToken(this)
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::gameView.isInitialized) gameView.resumeWorld()
    }

    override fun onPause() {
        if (::gameView.isInitialized) gameView.pauseWorld()
        super.onPause()
    }

    override fun onDestroy() {
        realtime?.close()
        realtime = null
        super.onDestroy()
    }
}
