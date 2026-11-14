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
    p.text("warning"), p.text("reported"),p.text("admissionReason"),p.strings("allergies"),
    (p["medications"] as? List<*>)?.mapNotNull{v->val m=v as? Map<*,*>?:return@mapNotNull null;Medication(m.text("name")?:return@mapNotNull null,m.text("dose")?:return@mapNotNull null,m.text("route")?:return@mapNotNull null,m.text("schedule")?:return@mapNotNull null,m.text("prescribedBy")?:return@mapNotNull null,m.instant("prescribedAt")?:return@mapNotNull null)}?:emptyList(),
    (p["careHistory"] as? List<*>)?.mapNotNull{v->val e=v as? Map<*,*>?:return@mapNotNull null;CareEvent(e.text("kind")?:return@mapNotNull null,e.text("description")?:return@mapNotNull null,e.instant("at")?:return@mapNotNull null,e.text("author")?:return@mapNotNull null)}?:emptyList(),
  ) }
  fun emergency(e:Map<*,*>):Emergency?{
    val r=e["isbar"] as? Map<*,*>;val report=r?.let{val fields=listOf("identify","situation","background","assessment","recommendation").map{k->it.text(k)?.takeIf{t->t.isNotBlank()&&t.length<=240}?:return@let null};Isbar(fields[0],fields[1],fields[2],fields[3],fields[4])}
    if(e.text("kind")!="code-blue")return null
    return Emergency(e.text("id")?:return null,e.text("from")?:return null,e.text("room")?:return null,e.text("status")?.takeIf{it in setOf("active","resolved")}?:return null,e.number("revision")?.takeIf{it>0}?:return null,e.instant("createdAt")?:return null,e.instant("updatedAt")?:return null,e.text("response")?.takeIf{it in setOf("pending","acknowledged","declined")}?:return null,report,e["briefingUnavailable"]==true)
  }
}
