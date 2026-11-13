package fi.hospitalos.glasses

import fi.hospitalos.ward.Snapshot
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

/**
 * Screen → Meta Ray-Ban Display views.
 *
 * Mockup rules: one idea per screen, card at the bottom (status at the top), the rest black
 * (see-through), warning triangle on warnings, no small caption text for important info.
 * The display has no custom colors or scrolling; the map is a phone-drawn bitmap (SPEC §6).
 */
class Renderer(private val f: GlassesFlow) {
  companion object {
    /** Last reveal stage: everything visible. */
    const val FULL = 2

    /** Pause between reveal stages. Tune on hardware: too short and Bluetooth merges them. */
    const val STEP_MS = 160L

    /** Screens that arrive in stages (mockup Transitions tab): most important part first. */
    fun isStaged(screen: Screen) =
        screen is Screen.FallAlert ||
            screen is Screen.Incoming ||
            screen is Screen.PatientCard ||
            screen is Screen.NoteReview ||
            screen is Screen.Briefing ||
            screen is Screen.Home
  }

  /** [r] = reveal stage, 0..[FULL]. Non-staged screens ignore it. */
  fun ContentScope.render(screen: Screen, s: Snapshot?, r: Int = FULL) {
    when (screen) {
      Screen.Clear -> flexBox {}
      Screen.Home -> home(s, r)
      is Screen.PatientCard -> patientCard(screen, s, r)
      is Screen.Consent -> consent(screen)
      is Screen.Recording -> recording(screen)
      is Screen.RecordingMenu -> recordingMenu(screen, s)
      is Screen.Drafting -> status(IconName.STAR_CIRCLE_TRIANGLE_AI, "Preparing the draft note")
      is Screen.NoteReview -> noteReview(screen, s, r)
      is Screen.AskListening -> askListening(screen, s)
      is Screen.AskAnswer -> askAnswer(screen)
      is Screen.Outgoing -> outgoing(screen, s)
      is Screen.Incoming -> incoming(screen, s, r)
      is Screen.Briefing -> briefing(screen, s, r)
      Screen.StaffMap -> staffMap(s)
      Screen.FallAlert -> fallAlert(r)
      Screen.FallBriefing -> fallBriefing(s)
      Screen.Offline -> offline()
      is Screen.Toast -> status(if (screen.ok) IconName.CHECKMARK_CIRCLE else IconName.I_CIRCLE, screen.text)
    }
  }

  // ---------------------------------------------------------------- layout helpers

  private fun ContentScope.bottom(block: FlexBoxScope.() -> Unit) =
      flexBox(direction = Direction.COLUMN, alignment = Alignment.END, crossAlignment = Alignment.STRETCH, gap = 12, padding = 16, block = block)

  private fun FlexBoxScope.card(onClick: (() -> Unit)? = null, block: FlexBoxScope.() -> Unit) =
      flexBox(direction = Direction.COLUMN, gap = 8, padding = 20, background = FlexBoxBackground.CARD, onClick = onClick, block = block)

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

  // ---------------------------------------------------------------- home & patient

  /** Stages: ward card → first patient row → all rows. */
  private fun ContentScope.home(s: Snapshot?, r: Int) = bottom {
    card {
      text(GlassesFlow.WARD_NAME, style = TextStyle.HEADING)
      val open = s?.incoming?.size ?: 0
      text(if (open == 0) "No open requests" else "$open request${if (open > 1) "s" else ""} for you", style = TextStyle.BODY, color = TextColor.SECONDARY)
    }
    s?.patients?.take(if (r == 0) 0 else if (r == 1) 1 else Int.MAX_VALUE)?.forEach { p ->
      flexBox(direction = Direction.ROW, gap = 12, padding = 16, crossAlignment = Alignment.CENTER, background = FlexBoxBackground.CARD, onClick = { f.openPatient(p.id) }) {
        icon(name = IconName.BED_SIDE, style = IconStyle.FILLED)
        flexBox(flexGrow = 1f) { text(p.name, style = TextStyle.BODY) }
        text("Room ${p.room}", style = TextStyle.BODY, color = TextColor.SECONDARY)
      }
    }
  }

  /** Stages: name + room → warning + latest info → buttons. */
  private fun ContentScope.patientCard(screen: Screen.PatientCard, s: Snapshot?, r: Int) {
    val p = s?.patient(screen.patientId) ?: return status(IconName.I_CIRCLE, "Patient not available")
    val latest = s.notesFor(p.id).firstOrNull()
    flexBox(direction = Direction.COLUMN, gap = 10, padding = 16, alignment = Alignment.END, crossAlignment = Alignment.STRETCH) {
      card {
        flexBox(direction = Direction.ROW, crossAlignment = Alignment.CENTER) {
          flexBox(flexGrow = 1f) { text(p.title, style = TextStyle.HEADING) }
          text("Room ${p.room}", style = TextStyle.BODY, color = TextColor.SECONDARY)
        }
        if (r >= 1) {
          p.warning?.let { warning(it) }
          if (latest != null) {
            text("${latest.time}  ${latest.planned.ifBlank { latest.reported }}", style = TextStyle.BODY)
          } else {
            text("Reported: ${p.reported}", style = TextStyle.BODY)
          }
        }
      }
      if (r >= 2) buttons {
        button("Record", style = ButtonStyle.PRIMARY, iconName = IconName.SPEECH_BUBBLE, onClick = { f.startConsent(p.id) })
        button("Ask", style = ButtonStyle.SECONDARY, iconName = IconName.STAR_CIRCLE_TRIANGLE_AI, onClick = { f.ask(p.id) })
        s.colleagues.firstOrNull()?.let { c ->
          button("Ask ${c.name}", style = ButtonStyle.SECONDARY, iconName = IconName.BULLHORN, onClick = { f.requestHelp(p.id, c.id) })
        }
        button("Back", style = ButtonStyle.SECONDARY, onClick = { f.home() })
      }
    }
  }

  private fun ContentScope.askListening(screen: Screen.AskListening, s: Snapshot?) = bottom {
    card {
      text(if (screen.heard.isBlank()) "Ask about ${s?.patient(screen.patientId)?.name ?: "the patient"}" else "\"${screen.heard}\"", style = TextStyle.HEADING)
      line(IconName.THREE_DOT_SPEECH_BUBBLE, "Listening", TextColor.SECONDARY)
    }
  }

  private fun ContentScope.askAnswer(screen: Screen.AskAnswer) = bottom {
    card {
      text(screen.title, style = TextStyle.BODY, color = TextColor.SECONDARY)
      text(screen.answer, style = TextStyle.HEADING)
      if (screen.source.isNotBlank()) line(IconName.I_CIRCLE, "From: ${screen.source}", TextColor.SECONDARY)
      buttons {
        button("Back", style = ButtonStyle.PRIMARY, onClick = { f.openPatient(screen.patientId) })
        button("Ask again", style = ButtonStyle.SECONDARY, onClick = { f.ask(screen.patientId) })
      }
    }
  }

  // ---------------------------------------------------------------- encounter

  private fun ContentScope.consent(screen: Screen.Consent) = bottom {
    card {
      text("Ask the patient:", style = TextStyle.BODY, color = TextColor.SECONDARY)
      text("\"Can I record this for your care notes?\"", style = TextStyle.HEADING)
      buttons {
        button("They agreed", style = ButtonStyle.PRIMARY, iconName = IconName.CHECKMARK, onClick = { f.consentGiven(screen.patientId) })
        button("No", style = ButtonStyle.SECONDARY, onClick = { f.consentRefused(screen.patientId) })
      }
    }
  }

  /** Small corner pill so the wearer's view stays clear during a long conversation. */
  private fun ContentScope.recording(screen: Screen.Recording) =
      flexBox(direction = Direction.COLUMN, alignment = Alignment.START, crossAlignment = Alignment.END, padding = 16) {
        flexBox(direction = Direction.ROW, gap = 8, padding = 12, crossAlignment = Alignment.CENTER, background = FlexBoxBackground.CARD, onClick = { f.recordingMenu(screen.patientId) }) {
          icon(name = IconName.SPEECH_BUBBLE, style = IconStyle.FILLED)
          val secs = ((System.currentTimeMillis() - screen.startedAt) / 1000).coerceAtLeast(0)
          text("%d:%02d".format(secs / 60, secs % 60), style = TextStyle.BODY)
        }
      }

  private fun ContentScope.recordingMenu(screen: Screen.RecordingMenu, s: Snapshot?) = bottom {
    card {
      text("Recording", style = TextStyle.HEADING)
      buttons {
        button("Stop", style = ButtonStyle.PRIMARY, iconName = IconName.CHECKMARK, onClick = { f.stopRecording(screen.patientId) })
        s?.colleagues?.firstOrNull()?.let { c ->
          button("Ask ${c.name}", style = ButtonStyle.SECONDARY, iconName = IconName.BULLHORN, onClick = { f.requestHelp(screen.patientId, c.id) })
        }
        button("Keep going", style = ButtonStyle.SECONDARY, onClick = { f.keepRecording(screen.patientId) })
      }
    }
  }

  /** Stages: title → note lines → Save/Discard. */
  private fun ContentScope.noteReview(screen: Screen.NoteReview, s: Snapshot?, r: Int) = bottom {
    val d = s?.drafts?.firstOrNull { it.id == screen.draftId }
    if (d == null) {
      card { text("Draft no longer available", style = TextStyle.BODY) }
      return@bottom
    }
    val done = s.tasks.filter { it.patientId == d.patientId && it.from == s.me.id && it.status == "completed" }
    card {
      text("Draft care note", style = TextStyle.HEADING)
      if (r >= 1) {
        if (d.reported.isNotBlank()) text("Reported: ${d.reported}", style = TextStyle.BODY)
        if (d.planned.isNotBlank()) text("Planned: ${d.planned}", style = TextStyle.BODY)
        done.forEach { text("Done by ${s.name(it.to)}: ${it.summary}", style = TextStyle.BODY) }
      }
      if (r >= 2) buttons {
        button("Save", style = ButtonStyle.PRIMARY, iconName = IconName.CHECKMARK, onClick = { f.saveNote(d.id) })
        button("Discard", style = ButtonStyle.SECONDARY, onClick = { f.discardNote(d.id) })
      }
    }
  }

  // ---------------------------------------------------------------- requests

  private fun ContentScope.outgoing(screen: Screen.Outgoing, s: Snapshot?) = bottom {
    val t = s?.tasks?.firstOrNull { it.id == screen.taskId }
    if (t == null) {
      card { line(IconName.BULLHORN, "Sending request…") }
      return@bottom
    }
    val who = s.name(t.to)
    when (t.status) {
      "accepted" ->
          card(onClick = { f.show(f.base()) }) {
            icon(name = IconName.PERSON_CIRCLE, style = IconStyle.FILLED)
            text("$who is coming", style = TextStyle.HEADING)
            text("Accepted your request", style = TextStyle.BODY, color = TextColor.SECONDARY)
          }
      "cancelled" ->
          card {
            text("$who can't help", style = TextStyle.HEADING)
            text("Ask someone else", style = TextStyle.BODY, color = TextColor.SECONDARY)
            buttons { button("OK", style = ButtonStyle.PRIMARY, onClick = { f.show(f.base()) }) }
          }
      else ->
          card {
            line(IconName.BULLHORN, "Asked $who for help")
            // Sent ≠ delivered ≠ accepted (SPEC F1): never imply someone is coming before they accept.
            text(if (t.status == "delivered") "$who has seen it" else "Not seen yet", style = TextStyle.HEADING)
            buttons {
              button("Cancel request", style = ButtonStyle.SECONDARY, onClick = { f.cancelRequest(t.id) })
              button("Hide", style = ButtonStyle.SECONDARY, onClick = { f.show(f.base()) })
            }
          }
    }
  }

  /** Stages: who + room → what they need → buttons. */
  private fun ContentScope.incoming(screen: Screen.Incoming, s: Snapshot?, r: Int) = bottom {
    val t = s?.tasks?.firstOrNull { it.id == screen.taskId } ?: return@bottom
    val p = s.patient(t.patientId)
    card {
      text("${s.name(t.from)} needs help", style = TextStyle.BODY)
      text(if (p != null) "Room ${p.room}" else "Room ${t.room ?: "?"}", style = TextStyle.HEADING)
      if (r >= 1) t.summary?.let { text("\"$it\"", style = TextStyle.BODY, color = TextColor.SECONDARY) }
      if (r >= 2) buttons {
        button("On my way", style = ButtonStyle.PRIMARY, iconName = IconName.CHECKMARK, onClick = { f.acceptRequest(t.id) })
        button("Can't", style = ButtonStyle.SECONDARY, onClick = { f.declineRequest(t.id) })
      }
    }
  }

  /** Only task-relevant, permitted info, with its source (SPEC F2). */
  /** Stages: patient + task → warning and report → Done/Hide. */
  private fun ContentScope.briefing(screen: Screen.Briefing, s: Snapshot?, r: Int) = bottom {
    val t = s?.tasks?.firstOrNull { it.id == screen.taskId } ?: return@bottom
    val p = s.patient(t.patientId)
    card {
      text(p?.title ?: "Room ${t.room}", style = TextStyle.HEADING)
      text("Room ${p?.room ?: t.room}  ·  ${t.summary ?: "Assistance"}", style = TextStyle.BODY, color = TextColor.SECONDARY)
      if (r >= 1) {
        p?.warning?.let { warning(it) }
        p?.let { text("Patient reported: ${it.reported}", style = TextStyle.BODY) }
      }
      if (r >= 2) buttons {
        button("Done", style = ButtonStyle.PRIMARY, iconName = IconName.CHECKMARK, onClick = { f.completeRequest(t.id) })
        button("Hide", style = ButtonStyle.SECONDARY, onClick = { f.clear() })
      }
    }
  }

  // ---------------------------------------------------------------- demo extras (simulated)

  private fun ContentScope.staffMap(s: Snapshot?) =
      flexBox(direction = Direction.COLUMN, gap = 10, padding = 16, crossAlignment = Alignment.STRETCH) {
        text("Staff map (simulated)", style = TextStyle.HEADING)
        val staff = SimulatedPositions.staff(s?.me?.name ?: "You", s?.colleagues?.map { it.name } ?: listOf("Ben"))
        image(bitmap = WardMap.staffMap(staff), sizePreset = ImageSize.FILL, cornerRadius = CornerRadius.MEDIUM)
        buttons { button("Close", style = ButtonStyle.SECONDARY, onClick = { f.show(f.base()) }) }
      }

  /** Red banner is static, so draw it once. */
  private val fallBanner by lazy { WardMap.alertBanner("Fall in Room 7") }

  /**
   * Stages: red alert banner → heading-up map + direction → Accept/Pass.
   * The map turns with the wearer's head (HeadTracker); the plan itself is simulated.
   */
  private fun ContentScope.fallAlert(r: Int) =
      flexBox(direction = Direction.COLUMN, gap = 10, padding = 16, crossAlignment = Alignment.STRETCH) {
        image(bitmap = fallBanner, sizePreset = ImageSize.FILL, cornerRadius = CornerRadius.MEDIUM)
        if (r >= 1) {
          val turn = f.turnDegrees
          image(bitmap = WardMap.headingUpMap(SimulatedPositions.me, 7, turn), sizePreset = ImageSize.FILL, cornerRadius = CornerRadius.MEDIUM)
          text(WardMap.direction(SimulatedPositions.me, 7, turn), style = TextStyle.HEADING, alignSelf = Alignment.CENTER)
        }
        if (r >= 2) buttons {
          button("Accept", style = ButtonStyle.PRIMARY, iconName = IconName.CHECKMARK, onClick = { f.acceptFall() })
          button("Pass", style = ButtonStyle.SECONDARY, onClick = { f.passFall() })
        }
      }

  private fun ContentScope.fallBriefing(s: Snapshot?) {
    val p = s?.patients?.firstOrNull { it.room == "7" }
    flexBox(direction = Direction.COLUMN, crossAlignment = Alignment.STRETCH, padding = 16) {
      flexBox(direction = Direction.ROW, alignment = Alignment.END) {
        // Live direction from head tracking instead of a fixed "2nd door left".
        val dir = WardMap.direction(SimulatedPositions.me, 7, f.turnDegrees)
        flexBox(direction = Direction.ROW, gap = 8, padding = 12, crossAlignment = Alignment.CENTER, background = FlexBoxBackground.CARD) {
          text(dir, style = TextStyle.BODY)
          icon(
              name =
                  when (dir) {
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
      card(onClick = { f.show(f.base()) }) {
        text(p?.title ?: "Room 7", style = TextStyle.HEADING)
        p?.summary?.let { text(it, style = TextStyle.BODY, color = TextColor.SECONDARY) }
        p?.warning?.let { warning(it) }
      }
    }
  }

  private fun ContentScope.offline() =
      flexBox(direction = Direction.COLUMN, alignment = Alignment.START, crossAlignment = Alignment.CENTER, padding = 16) {
        flexBox(direction = Direction.ROW, gap = 8, padding = 12, crossAlignment = Alignment.CENTER, background = FlexBoxBackground.CARD) {
          icon(name = IconName.EXCLAMATION_CIRCLE, style = IconStyle.FILLED)
          text("Offline. Nothing is sent", style = TextStyle.BODY)
        }
      }
}
