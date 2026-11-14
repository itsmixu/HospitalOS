package fi.hospitalos.handsfree

/** Captured before recognition starts. A final result cannot act on a different card. */
data class SpeechWindow(val card: Card, val rendered: Boolean, val ready: Boolean)
enum class SpeechRoute { WAKE, COMMAND, STOP, IGNORE, STALE }
object SpeechGate {
  fun route(text: String, started: SpeechWindow?, current: SpeechWindow, confidence: Float = -1f): SpeechRoute {
    if(text.trim().trimEnd('.', '!', '?').equals("stop listening", true)) return SpeechRoute.STOP
    if(started == null || started != current || !current.ready || !current.rendered) return SpeechRoute.STALE
    if(!confidence.isFinite() || (confidence != -1f && confidence !in 0.6f..1f)) return SpeechRoute.IGNORE
    val clean=normalize(text).trimEnd('.', '!', '?')
    if(clean.isBlank() || clean.length>200) return SpeechRoute.IGNORE
    if(Regex("(hey[, ]+lumi|(hey )?hospital)", RegexOption.IGNORE_CASE).matches(clean)) return if(current.card.microphone) SpeechRoute.IGNORE else SpeechRoute.WAKE
    return if(current.card.microphone) SpeechRoute.COMMAND else SpeechRoute.IGNORE
  }
  fun normalize(text:String):String {
    val clean=text.trim().trimEnd('.', '!', '?')
    return Regex("select patient demo ([ab])",RegexOption.IGNORE_CASE).matchEntire(clean)?.let{"select patient demo-${it.groupValues[1].lowercase()}"} ?: text.trim()
  }
}
