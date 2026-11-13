package fi.hospitalos.ward

import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

enum class Link { OFFLINE, CONNECTING, ONLINE }

/**
 * WebSocket connection to the HospitalOS ward server (prototype/server).
 * Actions are never replayed automatically after a disconnect (SPEC F1).
 */
class WardClient {
  private val http = OkHttpClient.Builder().pingInterval(15, TimeUnit.SECONDS).build()
  private var socket: WebSocket? = null

  private val _link = MutableStateFlow(Link.OFFLINE)
  val link: StateFlow<Link> = _link.asStateFlow()

  private val _snapshot = MutableStateFlow<Snapshot?>(null)
  val snapshot: StateFlow<Snapshot?> = _snapshot.asStateFlow()

  /** Server errors and connection problems, for the phone UI. */
  private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val messages: SharedFlow<String> = _messages.asSharedFlow()

  /** Replies from the voice assistant, only ever for this user's own questions. */
  private val _replies = MutableSharedFlow<AssistantReply>(extraBufferCapacity = 4)
  val replies: SharedFlow<AssistantReply> = _replies.asSharedFlow()

  /** Ask the server-side assistant (Claude, or the offline rule parser). */
  fun ask(text: String, patientId: String?) =
      send("assistant.ask", JSONObject().put("text", text).apply { if (patientId != null) put("patientId", patientId) })

  fun connect(url: String, token: String) {
    socket?.close(1000, "Reconnect")
    _link.value = Link.CONNECTING
    val request =
        try {
          Request.Builder().url(url.trim()).build()
        } catch (e: IllegalArgumentException) {
          _link.value = Link.OFFLINE
          _messages.tryEmit("That server address isn't valid")
          return
        }
    socket =
        http.newWebSocket(
            request,
            object : WebSocketListener() {
              override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(JSONObject().put("type", "auth").put("token", token.trim()).toString())
              }

              override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket !== socket) return
                val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (msg.optString("type")) {
                  "snapshot" -> {
                    _snapshot.value = Snapshot.parse(msg.getJSONObject("payload"))
                    _link.value = Link.ONLINE
                  }
                  "error" -> _messages.tryEmit(msg.optString("message", "Server error"))
                  "assistant.reply" -> {
                    val p = msg.optJSONObject("payload") ?: return
                    val acts = p.optJSONArray("actions")
                    _replies.tryEmit(
                        AssistantReply(
                            heard = p.optString("heard"),
                            say = p.optString("say"),
                            openPatientId =
                                (0 until (acts?.length() ?: 0))
                                    .mapNotNull { acts?.optJSONObject(it) }
                                    .firstOrNull { it.optString("type") == "open_patient" }
                                    ?.optString("patientId"),
                            usedClaude = p.optString("source") == "claude",
                        ),
                    )
                  }
                }
              }

              override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (webSocket !== socket) return
                _link.value = Link.OFFLINE
                _messages.tryEmit("Ward server unreachable. Nothing was resent.")
              }

              override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (webSocket !== socket) return
                _link.value = Link.OFFLINE
                if (reason.isNotBlank() && code != 1000) _messages.tryEmit(reason)
              }
            },
        )
  }

  fun disconnect() {
    socket?.close(1000, "Bye")
    socket = null
    _link.value = Link.OFFLINE
  }

  /** Send a command. Returns false (and says why) when offline; never queued or replayed. */
  fun send(type: String, payload: JSONObject = JSONObject()): Boolean {
    val ok =
        _link.value == Link.ONLINE &&
            socket?.send(
                JSONObject()
                    .put("id", UUID.randomUUID().toString())
                    .put("type", type)
                    .put("payload", payload)
                    .toString(),
            ) == true
    if (!ok) _messages.tryEmit("Offline: not sent")
    return ok
  }

  fun close() {
    disconnect()
    http.dispatcher.executorService.shutdown()
  }
}

/** What the assistant understood and did. [say] is shown on the lens. */
data class AssistantReply(
    val heard: String,
    val say: String,
    val openPatientId: String?,
    val usedClaude: Boolean,
)
