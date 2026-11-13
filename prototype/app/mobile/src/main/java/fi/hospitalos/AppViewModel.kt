package fi.hospitalos

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fi.hospitalos.glasses.GlassesFlow
import fi.hospitalos.glasses.GlassesLink
import fi.hospitalos.glasses.HeadTracker
import fi.hospitalos.ward.WardClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import org.json.JSONObject

class AppViewModel(app: Application) : AndroidViewModel(app) {
  private val prefs = app.getSharedPreferences("hospitalos", Context.MODE_PRIVATE)

  val ward = WardClient()
  lateinit var flow: GlassesFlow
    private set

  val glasses = GlassesLink(viewModelScope, HeadTracker(app, viewModelScope), onDisplayReady = { flow.render() })

  init {
    flow = GlassesFlow(viewModelScope, ward, glasses)
  }

  /** One stream of short messages for the phone's snackbar. */
  private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val notices: SharedFlow<String> = _notices.asSharedFlow()

  init {
    viewModelScope.launch { merge(ward.messages, glasses.messages).collect { _notices.emit(it) } }
    // Reconnect straight away when we already know the server and token.
    if (token.isNotBlank()) ward.connect(serverUrl, token)
  }

  var serverUrl: String
    get() = prefs.getString("url", null) ?: "ws://127.0.0.1:8787"
    set(v) = prefs.edit().putString("url", v).apply()

  /** Synthetic demo token (never a real credential). Remembered so reconnects are one tap. */
  var token: String
    get() = prefs.getString("token", null) ?: ""
    set(v) = prefs.edit().putString("token", v).apply()

  fun connect(url: String, token: String) {
    serverUrl = url
    this.token = token
    ward.connect(url, token)
  }

  fun bluetoothGranted() = glasses.initialize(getApplication())

  fun register(activity: Activity) = glasses.register(activity)

  fun unregister(activity: Activity) = glasses.unregister(activity)

  // Phone-side actions that don't need the glasses screen.
  fun send(type: String, payload: JSONObject) = ward.send(type, payload)

  override fun onCleared() {
    glasses.stopDisplay()
    ward.close()
  }
}
