package fi.hospitalos.handsfree

import fi.hospitalos.handsfree.capture.Reading
import java.time.Instant

data class RequestAttachment(
  val id: String,
  val patientId: String,
  val patientName: String,
  val encounterId: String,
  val values: Reading,
  val pressureUnit: String,
  val pulseUnit: String,
  val capturedAt: String,
  val corrected: List<String>,
  val source: String,
) {
  fun readingText() = "SYS ${values.systolic} / DIA ${values.diastolic} $pressureUnit\nPulse ${values.pulse} $pulseUnit"
}

/** Platform-independent mapping of the server payload used by the real display bridge. */
object WardSnapshot {
  private fun Map<*, *>.text(key: String) = this[key] as? String
  private fun Map<*, *>.number(key: String): Int? = (this[key] as? Number)?.let {
    val d = it.toDouble()
    if (d.isFinite() && d == it.toInt().toDouble()) it.toInt() else null
  }
  private fun Map<*, *>.strings(key: String) = (this[key] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
  private fun Map<*, *>.instant(key: String) = text(key)?.takeIf { runCatching { Instant.parse(it) }.isSuccess }

  fun attachment(value: Any?): RequestAttachment? {
    val a = value as? Map<*, *> ?: return null
    val v = a["values"] as? Map<*, *> ?: return null
    val u = a["units"] as? Map<*, *> ?: return null
    val values = listOf("systolic", "diastolic", "pulse").map { key ->
      v.number(key)?.takeIf { it in 0..999 } ?: return null
    }
    val corrected = a["corrected"] as? List<*> ?: return null
    if (corrected.any { it !in setOf("systolic", "diastolic", "pulse") } || corrected.distinct().size != corrected.size) return null
    if (u.text("pressure") != "mmHg" || u.text("pulse") != "bpm" || a["measurementTime"] != null) return null
    val source = a.text("source")?.takeIf { it in setOf("injected-camera", "dat-camera") } ?: return null
    return RequestAttachment(
      a.text("id") ?: return null, a.text("patientId") ?: return null,
      a.text("patientName") ?: return null, a.text("encounterId") ?: return null,
      Reading(values[0], values[1], values[2]), "mmHg", "bpm", a.instant("capturedAt") ?: return null,
      corrected.filterIsInstance<String>(), source,
    )
  }

  fun request(t: Map<*, *>): Request? {
    val a = attachment(t["attachment"])
    return Request(
      t.text("id") ?: return null, t.text("from") ?: return null, t.text("to") ?: return null,
      t.text("room") ?: return null, t.text("text") ?: return null, t.text("status") ?: return null,
      t.number("revision") ?: return null, t.number("required") ?: 0, t.strings("acceptedBy"), a,
      t["attachmentUnavailable"] == true || (t["attachment"] != null && a == null),
    )
  }

  fun patient(p: Map<*, *>): Patient? { return Patient(
    p.text("id") ?: return null, p.text("name") ?: return null,
    p.text("encounterId") ?: return null, p.text("room") ?: return null,
    p.text("warning"), p.text("reported"),
  ) }
}
