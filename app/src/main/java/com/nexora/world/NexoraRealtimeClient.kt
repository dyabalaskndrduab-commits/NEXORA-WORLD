package com.nexora.world

import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONArray
import org.json.JSONObject

/** Socket.IO client for the server-authoritative room protocol in backend/src/socket-rooms.js. */
class NexoraRealtimeClient(
    private val serverUrl: String,
    private val token: String
) {
    interface Listener {
        fun onConnectionChanged(connected: Boolean)
        fun onWorldSnapshot(players: List<NexoraRemotePlayer>)
        fun onPlayerJoined(player: NexoraRemotePlayer)
        fun onPlayerMoved(player: NexoraRemotePlayer)
        fun onPlayerLeft(playerId: String)
        fun onPlayerCorrected(player: NexoraRemotePlayer)
        fun onRoomState(room: NexoraRoomInfo)
        fun onRoomCreated(room: NexoraRoomInfo, inviteCode: String)
        fun onRoomJoined(room: NexoraRoomInfo)
        fun onPublicRooms(rooms: List<NexoraRoomInfo>)
        fun onChatMessage(message: NexoraChatMessage)
        fun onRoomHostChanged(hostId: String)
        fun onRoomClosed(roomId: String)
        fun onServerError(code: String, message: String)
    }

    @Volatile
    var listener: Listener? = null

    @Volatile
    private var socket: Socket? = null

    val isConnected: Boolean
        get() = socket?.connected() == true

    fun connect() {
        if (isConnected) return
        try {
            val options = IO.Options().apply {
                forceNew = true
                reconnection = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = 1_000
                reconnectionDelayMax = 8_000
                timeout = 10_000
                auth = hashMapOf("token" to token)
            }
            val active = IO.socket(serverUrl, options)
            socket = active
            registerEvents(active)
            active.connect()
        } catch (error: Exception) {
            listener?.onConnectionChanged(false)
            listener?.onServerError("CONNECTION_FAILED", error.localizedMessage ?: "تعذر إنشاء الاتصال")
        }
    }

    private fun registerEvents(active: Socket) {
        active.on(Socket.EVENT_CONNECT) {
            listener?.onConnectionChanged(true)
        }
        active.on(Socket.EVENT_DISCONNECT) {
            listener?.onConnectionChanged(false)
        }
        active.on(Socket.EVENT_CONNECT_ERROR) { args ->
            listener?.onConnectionChanged(false)
            listener?.onServerError("CONNECT_ERROR", args.firstOrNull()?.toString() ?: "تعذر الوصول إلى الخادم")
        }
        active.on("world:snapshot") { args ->
            listener?.onWorldSnapshot(parsePlayers(args.firstOrNull()))
        }
        active.on("player:joined") { args ->
            parsePlayer(args.firstOrNull())?.let { listener?.onPlayerJoined(it) }
        }
        active.on("player:moved") { args ->
            parsePlayer(args.firstOrNull())?.let { listener?.onPlayerMoved(it) }
        }
        active.on("player:left") { args ->
            val data = args.firstOrNull() as? JSONObject
            val id = data?.optString("id").orEmpty()
            if (id.isNotBlank()) listener?.onPlayerLeft(id)
        }
        active.on("player:corrected") { args ->
            parsePlayer(args.firstOrNull())?.let { listener?.onPlayerCorrected(it) }
        }
        active.on("room:state") { args ->
            parseRoom(args.firstOrNull())?.let { listener?.onRoomState(it) }
        }
        active.on("room:created") { args ->
            val data = args.firstOrNull() as? JSONObject
            if (data != null) {
                val room = parseRoom(data)
                val code = data.optString("code")
                if (room != null && code.isNotBlank()) listener?.onRoomCreated(room, code)
            }
        }
        active.on("room:joined") { args ->
            parseRoom(args.firstOrNull())?.let { listener?.onRoomJoined(it) }
        }
        active.on("rooms:list") { args ->
            listener?.onPublicRooms(parseRooms(args.firstOrNull()))
        }
        active.on("chat:message") { args ->
            val data = args.firstOrNull() as? JSONObject
            if (data != null) {
                listener?.onChatMessage(
                    NexoraChatMessage(
                        playerId = data.optString("playerId"),
                        username = data.optString("username", "Explorer"),
                        message = data.optString("message"),
                        sentAt = data.optLong("sentAt")
                    )
                )
            }
        }
        active.on("room:host_changed") { args ->
            val data = args.firstOrNull() as? JSONObject
            val hostId = data?.optString("hostId").orEmpty()
            if (hostId.isNotBlank()) listener?.onRoomHostChanged(hostId)
        }
        active.on("room:closed") { args ->
            val data = args.firstOrNull() as? JSONObject
            val roomId = data?.optString("roomId").orEmpty()
            if (roomId.isNotBlank()) listener?.onRoomClosed(roomId)
        }
        active.on("server:error") { args ->
            val data = args.firstOrNull() as? JSONObject
            listener?.onServerError(
                data?.optString("code", "SERVER_ERROR") ?: "SERVER_ERROR",
                data?.optString("message", "رفض الخادم تنفيذ الطلب") ?: "رفض الخادم تنفيذ الطلب"
            )
        }
    }

    fun createPrivateRoom(name: String, maxPlayers: Int = 8) {
        emit("room:create", JSONObject().put("name", name).put("maxPlayers", maxPlayers))
    }

    fun joinPrivateRoom(code: String) {
        emit("room:join", JSONObject().put("code", code.trim().uppercase()))
    }

    fun leaveRoom() = emit("room:leave")

    fun requestPublicRooms() = emit("rooms:list")

    fun sendMovement(x: Float, y: Float) {
        emit("player:move", JSONObject().put("x", x.toDouble()).put("y", y.toDouble()))
    }

    fun sendChat(message: String) {
        val cleaned = message.trim().take(240)
        if (cleaned.isNotEmpty()) emit("chat:message", JSONObject().put("message", cleaned))
    }

    private fun emit(event: String, payload: JSONObject? = null) {
        val active = socket ?: return
        if (!active.connected()) return
        if (payload == null) active.emit(event) else active.emit(event, payload)
    }

    fun close() {
        socket?.off()
        socket?.disconnect()
        socket?.close()
        socket = null
    }

    private fun parsePlayer(value: Any?): NexoraRemotePlayer? {
        val data = value as? JSONObject ?: return null
        val id = data.optString("id")
        if (id.isBlank()) return null
        return NexoraRemotePlayer(
            id = id,
            username = data.optString("username", "Explorer"),
            x = data.optDouble("x", 0.5).toFloat(),
            y = data.optDouble("y", 0.66).toFloat(),
            roomId = data.optString("roomId")
        )
    }

    private fun parsePlayers(value: Any?): List<NexoraRemotePlayer> {
        val array = value as? JSONArray ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                parsePlayer(array.optJSONObject(index))?.let(::add)
            }
        }
    }

    private fun parseRoom(value: Any?): NexoraRoomInfo? {
        val data = value as? JSONObject ?: return null
        val id = data.optString("id")
        if (id.isBlank()) return null
        return NexoraRoomInfo(
            id = id,
            name = data.optString("name", "NEXORA Room"),
            visibility = data.optString("visibility", "public"),
            maxPlayers = data.optInt("maxPlayers", 100),
            playerCount = data.optInt("playerCount", 0),
            hostId = data.optString("hostId").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    private fun parseRooms(value: Any?): List<NexoraRoomInfo> {
        val array = value as? JSONArray ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                parseRoom(array.optJSONObject(index))?.let(::add)
            }
        }
    }
}

data class NexoraRemotePlayer(
    val id: String,
    val username: String,
    val x: Float,
    val y: Float,
    val roomId: String
)

data class NexoraRoomInfo(
    val id: String,
    val name: String,
    val visibility: String,
    val maxPlayers: Int,
    val playerCount: Int,
    val hostId: String?
)

data class NexoraChatMessage(
    val playerId: String,
    val username: String,
    val message: String,
    val sentAt: Long
)
