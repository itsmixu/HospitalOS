package fi.hospitalos.glasses

import com.meta.wearable.dat.display.views.Alignment
import com.meta.wearable.dat.display.views.ButtonStyle
import com.meta.wearable.dat.display.views.ContentScope
import com.meta.wearable.dat.display.views.CornerRadius
import com.meta.wearable.dat.display.views.Direction
import com.meta.wearable.dat.display.views.FlexBoxBackground
import com.meta.wearable.dat.display.views.FlexBoxScope
import com.meta.wearable.dat.display.views.IconName
import com.meta.wearable.dat.display.views.IconStyle
import com.meta.wearable.dat.display.views.ImageSize
import com.meta.wearable.dat.display.views.TextColor
import com.meta.wearable.dat.display.views.TextStyle
import fi.hospitalos.ward.Snapshot

/**
 * Screen → Meta Ray-Ban Display views. Glasses-first rules: at most three actions per screen,
 * cards sit low on the lens, the rest stays black (see-through), warnings use the triangle icon,
 * and tapping a card's header goes back. No custom colors exist in the view API, so color (red
 * alert, map) is drawn as images (SPEC §6).
 */
class Renderer(private val f: GlassesFlow) {

  fun ContentScope.render(screen: Screen, s: Snapshot?) {
    when (screen) {
      Screen.Clear -> flexBox {}
      Screen.Home -> home(s)
      Screen.Patients -> patients(s)
      is Screen.PatientCard -> patientCard(screen, s)
      is Screen.Consent -> consent(screen)
      is Screen.Dictating -> listening("Speak your note", s?.patient(screen.patientId)?.name, onCancel = { f.cancelListening() })
      is Screen.Drafting -> status(IconName.STAR_CIRCLE_TRIANGLE_AI, "Preparing the draft note")
      is Screen.NoteReview -> noteReview(screen, s)
      Screen.PickColleague -> pickColleague(s)
      is Screen.SpeakMessage -> listening("Message to ${s?.name(screen.to) ?: screen.to}", null, onCancel = { f.cancelListening() })
      is Screen.ConfirmMessage -> confirmMessage(screen, s)
      Screen.Requests -> requests(s)
      is Screen.Outgoing -> outgoing(screen, s)
      is Screen.HelpSent -> helpSent(screen, s)
      is Screen.AssistantListening ->
          listening(
              "Listening",
              screen.patientId?.let { id -> s?.patient(id)?.let { "About ${it.name}, Room ${it.room}" } },
              hint = "Try: two nurses to my room",
              onCancel = { f.cancelListening() },
          )
      is Screen.AssistantThinking -> assistantThinking(screen)
      is Screen.AssistantAnswer -> assistantAnswer(screen)
      is Screen.Incoming -> incoming(screen, s)
      is Screen.Briefing -> briefing(screen, s)
      is Screen.MessageIn -> messageIn(screen, s)
      Screen.FallAlert -> fallAlert()
      Screen.FallBriefing -> fallBriefing(s)
      Screen.StaffMap -> staffMap(s)
      Screen.Offline -> offline()
      is Screen.Toast -> status(if (screen.ok) IconName.CHECKMARK_CIRCLE else IconName.I_CIRCLE, screen.text)
    }
  }

  // ---------------------------------------------------------------- layout helpers

  private fun ContentScope.bottom(block: FlexBoxScope.() -> Unit) =
      flexBox(direction = Direction.COLUMN, alignment = Alignment.END, crossAlignment = Alignment.STRETCH, gap = 10, padding = 16, block = block)

  private fun FlexBoxScope.card(onClick: (() -> Unit)? = null, block: FlexBoxScope.() -> Unit) =
      flexBox(direction = Direction.COLUMN, gap = 8, padding = 20, background = FlexBoxBackground.CARD, onClick = onClick, block = block)

  /** A tappable list row: icon, label, right-hand detail. */
  private fun FlexBoxScope.row(icon: IconName, label: String, detail: String? = null, onClick: () -> Unit) =
      flexBox(direction = Direction.ROW, gap = 12, padding = 16, crossAlignment = Alignment.CENTER, background = FlexBoxBackground.CARD, onClick = onClick) {
        icon(name = icon, style = IconStyle.FILLED)
        flexBox(flexGrow = 1f, flexShrink = 1f) { text(label, style = TextStyle.BODY) }
        detail?.let { text(it, style = TextStyle.BODY, color = TextColor.SECONDARY) }
      }

  private fun FlexBoxScope.line(icon: IconName, text: String, color: TextColor = TextColor.PRIMARY) =
      flexBox(direction = Direction.ROW, gap = 8, crossAlignment = Alignment.CENTER) {
        icon(name = icon, style = IconStyle.FILLED)
        text(text, style = TextStyle.BODY, color = color, flexShrink = 1f)
      }

  private fun FlexBoxScope.warning(text: String) = line(IconName.EXCLAMATION_TRIANGLE, text)

  private fun FlexBoxScope.buttons(block: FlexBoxScope.() -> Unit) =
      flexBox(direction = Direction.ROW, gap = 8, alignment = Alignment.CENTER, crossAlignment = Alignment.CENTER, wrap = true, block = block)

  private fun ContentScope.status(icon: IconName, text: String) = bottom {
    flexBox(direction = Direction.ROW, gap = 10, padding = 16, alignment = Alignment.CENTER, crossAlignment = Alignment.CENTER, background = FlexBoxBackground.CARD) {
      icon(name = icon, style = IconStyle.FILLED)
      text(text, style = TextStyle.BODY, flexShrink = 1f)
    }
  }

  private fun statusLabel(status: String?) = when (status) {
    "free" -> "Free"
    "busy" -> "Busy"
    else -> ""
  }

  // ---------------------------------------------------------------- home

  /** Header (tap = toggle free/busy), three hubs, and the assistant. */
  private fun ContentScope.home(s: Snapshot?) = bottom {
    card(onClick = { f.toggleStatus() }) {
      flexBox(direction = Direction.ROW, crossAlignment = Alignment.CENTER) {
        flexBox(flexGrow = 1f) { text(s?.me?.name ?: GlassesFlow.WARD_NAME, style = TextStyle.HEADING) }
        line(if (s?.me?.status == "busy") IconName.BELL_OFF else IconName.CHECKMARK_CIRCLE, statusLabel(s?.me?.status))
      }
      text("${GlassesFlow.WARD_NAME} · tap to change status", style = TextStyle.BODY, color = TextColor.SECONDARY)
    }
    row(IconName.BED_SIDE, "Patients", s?.patients?.size?.toString()) { f.patients() }
    row(IconName.SPEECH_BUBBLE, "Message", s?.unreadMessages?.size?.takeIf { it > 0 }?.let { "$it new" }) { f.pickColleague() }
    val open = (s?.incoming?.size ?: 0) + (s?.outgoing?.count { it.status in setOf("sent", "delivered", "accepted") } ?: 0)
    row(IconName.BELL, "Requests", if (open > 0) "$open open" else null) { f.requests() }
    buttons { button("Ask assistant", style = ButtonStyle.PRIMARY, iconName = IconName.STAR_CIRCLE_TRIANGLE_AI, onClick = { f.assistant() }) }
  }

  // ---------------------------------------------------------------- patients & notes

  private fun ContentScope.patients(s: Snapshot?) = bottom {
    card(onClick = { f.home() }) { text("Patients", style = TextStyle.HEADING) }
    s?.patients?.forEach { p ->
      row(if (p.warning != null) IconName.EXCLAMATION_TRIANGLE else IconName.BED_SIDE, p.name, "Room ${p.room}") { f.openPatient(p.id) }
    }
  }

  /** Tap the card to go back. Three actions: Note, Get help, Ask. */
  private fun ContentScope.patientCard(screen: Screen.PatientCard, s: Snapshot?) {
    val p = s?.patient(screen.patientId) ?: return status(IconName.I_CIRCLE, "Patient not available")
    val latest = s.notesFor(p.id).firstOrNull()
    bottom {
      card(onClick = { f.patients() }) {
        flexBox(direction = Direction.ROW, crossAlignment = Alignment.CENTER) {
          flexBox(flexGrow = 1f) { text(p.title, style = TextStyle.HEADING) }
          text("Room ${p.room}", style = TextStyle.BODY, color = TextColor.SECONDARY)
        }
        p.warning?.let { warning(it) }
        if (latest != null) text("${latest.time}  ${latest.planned.ifBlank { latest.reported }}", style = TextStyle.BODY)
        else text("Reported: ${p.reported}", style = TextStyle.BODY)
      }
      buttons {
        button("Note", style = ButtonStyle.PRIMARY, iconName = IconName.PENCIL, onClick = { f.startNote(p.id) })
        button("Get help", style = ButtonStyle.SECONDARY, iconName = IconName.BULLHORN, onClick = { f.getHelp(p.id) })
        button("Ask", style = ButtonStyle.SECONDARY, iconName = IconName.STAR_CIRCLE_TRIANGLE_AI, onClick = { f.assistant() })
      }
    }
  }

  private fun ContentScope.consent(screen: Screen.Consent) = bottom {
    card {
      text("Ask the patient:", style = TextStyle.BODY, color = TextColor.SECONDARY)
      text("\"Can I note this for your care record?\"", style = TextStyle.HEADING)
      buttons {
        button("They agreed", style = ButtonStyle.PRIMARY, iconName = IconName.CHECKMARK, onClick = { f.consentGiven(screen.patientId) })
        button("No", style = ButtonStyle.SECONDARY, onClick = { f.consentRefused(screen.patientId) })
      }
    }
  }

  /** Shared listening screen: live words as they're recognised. */
  private fun ContentScope.listening(title: String, subtitle: String?, hint: String? = null, onCancel: () -> Unit) = bottom {
    val heard = f.heardSoFar.value
    card {
      line(IconName.THREE_DOT_SPEECH_BUBBLE, title)
      subtitle?.let { text(it, style = TextStyle.BODY, color = TextColor.SECONDARY) }
      when {
        heard.isNotBlank() -> text("\"$heard\"", style = TextStyle.HEADING)
        hint != null -> text(hint, style = TextStyle.BODY, color = TextColor.SECONDARY)
        else -> text("Speak now", style = TextStyle.BODY, color = TextColor.SECONDARY)
      }
      buttons { button("Cancel", style = ButtonStyle.SECONDARY, onClick = onCancel) }
    }
  }

  private fun ContentScope.noteReview(screen: Screen.NoteReview, s: Snapshot?) = bottom {
    val d = s?.drafts?.firstOrNull { it.id == screen.draftId }
    if (d == null) {
      card { text("Draft no longer available", style = TextStyle.BODY) }
      return@bottom
    }
    val done = s.tasks.filter { it.patientId == d.patientId && it.from == s.me.id && it.status == "completed" }
    card {
      text("Draft note", style = TextStyle.HEADING)
      if (d.reported.isNotBlank()) text("Reported: ${d.reported}", style = TextStyle.BODY)
      if (d.planned.isNotBlank()) text(d.planned, style = TextStyle.BODY)
      done.forEach { text("Done by ${s.name(it.to)}: ${it.summary}", style = TextStyle.BODY, color = TextColor.SECONDARY) }
      buttons {
        button("Save", style = ButtonStyle.PRIMARY, iconName = IconName.CHECKMARK, onClick = { f.saveNote(d.id) })
        button("Discard", style = ButtonStyle.SECONDARY, onClick = { f.discardNote(d.id) })
      }
    }
  }

  // ---------------------------------------------------------------- messages

  private fun ContentScope.pickColleague(s: Snapshot?) = bottom {
    card(onClick = { f.home() }) { text("Message who?", style = TextStyle.HEADING) }
    s?.colleagues?.forEach { c -> row(IconName.PERSON_CIRCLE, c.name, statusLabel(c.status)) { f.speakMessage(c.id) } }
  }

  private fun ContentScope.confirmMessage(screen: Screen.ConfirmMessage, s: Snapshot?) = bottom {
    card {
      text("To ${s?.name(screen.to) ?: screen.to}", style = TextStyle.BODY, color = TextColor.SECONDARY)
      text("\"${screen.text}\"", style = TextStyle.HEADING)
      buttons {
        button("Send", style = ButtonStyle.PRIMARY, iconName = IconName.PAPER_AIRPLANE, onClick = { f.sendMessage(screen.to, screen.text) })
        button("Again", style = ButtonStyle.SECONDARY, onClick = { f.speakMessage(screen.to) })
      }
    }
  }

  private fun ContentScope.messageIn(screen: Screen.MessageIn, s: Snapshot?) = bottom {
    val m = s?.messages?.firstOrNull { it.id == screen.messageId } ?: return@bottom
    card {
      line(IconName.SPEECH_BUBBLE, "Message from ${s.name(m.from)}")
      text(m.text, style = TextStyle.HEADING)
      buttons {
        button("OK", style = ButtonStyle.PRIMARY, iconName = IconName.CHECKMARK, onClick = { f.markRead(m.id) })
        button("Reply", style = ButtonStyle.SECONDARY, onClick = { f.reply(m.id) })
      }
    }
  }

  // ---------------------------------------------------------------- requests

  private fun taskStatus(status: String) = when (status) {
    "sent" -> "Not seen yet"
    "delivered" -> "Seen"
    "accepted" -> "Coming"
    "completed" -> "Done"
    "cancelled" -> "Can't come"
    else -> status
  }

  private fun ContentScope.requests(s: Snapshot?) = bottom {
    card(onClick = { f.home() }) { text("Requests", style = TextStyle.HEADING) }
    val incoming = s?.incoming.orEmpty()
    val outgoing = s?.outgoing?.filter { it.status in setOf("sent", "delivered", "accepted") }.orEmpty()
    incoming.take(2).forEach { t ->
      row(IconName.BELL, "${s!!.name(t.from)} · Room ${s.patient(t.patientId)?.room ?: t.room}", taskStatus(t.status)) { f.showIncoming(t.id) }
    }
    outgoing.take(2).forEach { t ->
      row(IconName.BULLHORN, "To ${s!!.name(t.to)}", taskStatus(t.status)) { f.showOutgoing(t.id) }
    }
    if (incoming.isEmpty() && outgoing.isEmpty()) card { text("No open requests", style = TextStyle.BODY, color = TextColor.SECONDARY) }
  }

  private fun ContentScope.outgoing(screen: Screen.Outgoing, s: Snapshot?) = bottom {
    val t = s?.tasks?.firstOrNull { it.id == screen.taskId }
    if (t == null) {
      card { line(IconName.BULLHORN, "Sending request…") }
      return@bottom
    }
    val who = s.name(t.to)
    card(onClick = { f.resume() }) {
      line(IconName.BULLHORN, "Asked $who for help")
      // Sent ≠ delivered ≠ accepted (SPEC F1): never imply someone is coming before they accept.
      text(
          when (t.status) {
            "accepted" -> "$who is coming"
            "delivered" -> "$who has seen it"
            "cancelled" -> "$who can't come"
            else -> "Not seen yet"
          },
          style = TextStyle.HEADING,
      )
      if (t.status in setOf("sent", "delivered")) buttons { button("Cancel request", style = ButtonStyle.SECONDARY, onClick = { f.cancelRequest(t.id) }) }
    }
  }

  private fun ContentScope.helpSent(screen: Screen.HelpSent, s: Snapshot?) = bottom {
    val room = s?.patient(screen.patientId)?.room ?: "?"
    card(onClick = { f.openPatient(screen.patientId) }) {
      line(IconName.BULLHORN, "Help for Room $room")
      screen.taskIds.mapNotNull { id -> s?.tasks?.firstOrNull { it.id == id } }.forEach { t ->
        flexBox(direction = Direction.ROW, crossAlignment = Alignment.CENTER) {
          flexBox(flexGrow = 1f) { text(s!!.name(t.to), style = TextStyle.HEADING) }
          text(taskStatus(t.status), style = TextStyle.BODY)
        }
      }
    }
  }

  private fun ContentScope.incoming(screen: Screen.Incoming, s: Snapshot?) = bottom {
    val t = s?.tasks?.firstOrNull { it.id == screen.taskId } ?: return@bottom
    val p = s.patient(t.patientId)
    card {
      text("${s.name(t.from)} needs help", style = TextStyle.BODY)
      text(if (p != null) "Room ${p.room}" else "Room ${t.room ?: "?"}", style = TextStyle.HEADING)
      t.summary?.let { text("\"$it\"", style = TextStyle.BODY, color = TextColor.SECONDARY) }
      buttons {
        button("On my way", style = ButtonStyle.PRIMARY, iconName = IconName.CHECKMARK, onClick = { f.acceptRequest(t.id) })
        button("Can't", style = ButtonStyle.SECONDARY, onClick = { f.declineRequest(t.id) })
      }
    }
  }

  /** Only task-relevant, permitted info, with its source (SPEC F2). */
  private fun ContentScope.briefing(screen: Screen.Briefing, s: Snapshot?) = bottom {
    val t = s?.tasks?.firstOrNull { it.id == screen.taskId } ?: return@bottom
    val p = s.patient(t.patientId)
    card {
      text(p?.title ?: "Room ${t.room}", style = TextStyle.HEADING)
      text("Room ${p?.room ?: t.room}  ·  ${t.summary ?: "Assistance"}", style = TextStyle.BODY, color = TextColor.SECONDARY)
      p?.warning?.let { warning(it) }
      p?.let { text("Patient reported: ${it.reported}", style = TextStyle.BODY) }
      buttons {
        button("Done", style = ButtonStyle.PRIMARY, iconName = IconName.CHECKMARK, onClick = { f.completeRequest(t.id) })
        button("Later", style = ButtonStyle.SECONDARY, onClick = { f.resume() })
      }
    }
  }

  // ---------------------------------------------------------------- assistant

  private fun ContentScope.assistantThinking(screen: Screen.AssistantThinking) = bottom {
    card {
      text("\"${screen.heard}\"", style = TextStyle.BODY, color = TextColor.SECONDARY)
      line(IconName.STAR_CIRCLE_TRIANGLE_AI, "Working on it…")
    }
  }

  private fun ContentScope.assistantAnswer(screen: Screen.AssistantAnswer) = bottom {
    card(onClick = { f.resume() }) {
      text("\"${screen.heard}\"", style = TextStyle.BODY, color = TextColor.SECONDARY)
      line(IconName.STAR_CIRCLE_TRIANGLE_AI, screen.say)
    }
  }

  // ---------------------------------------------------------------- alerts & extras

  private val fallBanner by lazy { WardMap.alertBanner("Fall in Room 7") }

  /** Red banner + heading-up map that turns with your head + direction + Accept/Pass. */
  private fun ContentScope.fallAlert() =
      flexBox(direction = Direction.COLUMN, gap = 10, padding = 16, crossAlignment = Alignment.STRETCH) {
        image(bitmap = fallBanner, sizePreset = ImageSize.FILL, cornerRadius = CornerRadius.MEDIUM)
        val turn = f.turnDegrees
        image(bitmap = WardMap.headingUpMap(SimulatedPositions.me, 7, turn), sizePreset = ImageSize.FILL, cornerRadius = CornerRadius.MEDIUM)
        text(WardMap.direction(SimulatedPositions.me, 7, turn), style = TextStyle.HEADING, alignSelf = Alignment.CENTER)
        buttons {
          button("Accept", style = ButtonStyle.PRIMARY, iconName = IconName.CHECKMARK, onClick = { f.acceptFall() })
          button("Pass", style = ButtonStyle.SECONDARY, onClick = { f.passFall() })
        }
      }

  private fun ContentScope.fallBriefing(s: Snapshot?) {
    val p = s?.patients?.firstOrNull { it.room == "7" }
    flexBox(direction = Direction.COLUMN, crossAlignment = Alignment.STRETCH, padding = 16) {
      val dir = WardMap.direction(SimulatedPositions.me, 7, f.turnDegrees)
      flexBox(direction = Direction.ROW, alignment = Alignment.END) {
        flexBox(direction = Direction.ROW, gap = 8, padding = 12, crossAlignment = Alignment.CENTER, background = FlexBoxBackground.CARD) {
          text(dir, style = TextStyle.BODY)
          icon(
              name = when (dir) {
                "Turn left" -> IconName.ARROW_LEFT
                "Turn right" -> IconName.ARROW_RIGHT
                "Turn around" -> IconName.ARROW_U_LEFT
                else -> IconName.CARET_UP
              },
              style = IconStyle.FILLED,
          )
        }
      }
      flexBox(flexGrow = 1f) {}
      card(onClick = { f.resume() }) {
        text(p?.title ?: "Room 7", style = TextStyle.HEADING)
        p?.summary?.let { text(it, style = TextStyle.BODY, color = TextColor.SECONDARY) }
        p?.warning?.let { warning(it) }
      }
    }
  }

  private fun ContentScope.staffMap(s: Snapshot?) =
      flexBox(direction = Direction.COLUMN, gap = 10, padding = 16, crossAlignment = Alignment.STRETCH) {
        text("Staff map (simulated)", style = TextStyle.HEADING)
        val staff = SimulatedPositions.staff(s?.me?.name ?: "You", s?.colleagues?.map { it.name } ?: listOf("Ben"))
        image(bitmap = WardMap.staffMap(staff), sizePreset = ImageSize.FILL, cornerRadius = CornerRadius.MEDIUM)
        buttons { button("Close", style = ButtonStyle.SECONDARY, onClick = { f.home() }) }
      }

  private fun ContentScope.offline() =
      flexBox(direction = Direction.COLUMN, alignment = Alignment.START, crossAlignment = Alignment.CENTER, padding = 16) {
        flexBox(direction = Direction.ROW, gap = 8, padding = 12, crossAlignment = Alignment.CENTER, background = FlexBoxBackground.CARD) {
          icon(name = IconName.EXCLAMATION_CIRCLE, style = IconStyle.FILLED)
          text("Offline. Nothing is sent", style = TextStyle.BODY)
        }
      }
}
