# HospitalOS — Spec

> Single source of truth for **what** we build and **how**. Change this file first, then the code.
> Rules for working in the repo and the decision log live in [CLAUDE.md](CLAUDE.md).

## 1. One-liner

**HospitalOS** connects every nurse's and doctor's Meta AI glasses into one hands-free ward network: talk to colleagues instantly, get alerts with a briefing while you walk, and have patient conversations logged for you, without touching a phone.

## 2. Problem & audience

**Who:** Ward nurses first (they do most of the moving, talking and documenting), doctors second.

**Problems:**
1. **Finding and reaching a colleague is slow.** You walk the corridor, phone around or shout. You don't know who is free or where they are.
2. **Remembering what was said is hard.** Care tasks run from minutes to hours. What the nurse advised (medication, instructions) and what the patient said gets written down later, from memory, or not at all.
3. **Alerts arrive without context.** You run to an emergency knowing the room number, not the patient.

**Why glasses, not a phone:** Hands are busy, gloved or must stay clean. The glasses are already on your face, see what you see (the patient's wristband) and hear what you hear (the conversation). Glanceable cards and audio don't interrupt care.

## 3. Scope: what we build

Priority: **P0 = must be in the demo.** P1 = add if time allows. Everything else = mention in the deck only.

| # | Feature | Priority |
|---|---|---|
| F1 | Nurse-to-nurse communication (presence, staff map, voice messages, call for help) | **P0** |
| F2 | Live alerts with a walking briefing | **P0** |
| F3 | Care conversation log (consent → capture → AI summary → nurse confirms) | **P0** |
| F4 | Wristband QR scan → patient card | P1 |
| — | EHR integration, real indoor positioning, shift handover reports, admin dashboard | Deck only |

### F1 — Nurse-to-nurse communication (P0)
- **Presence:** each wearer has a status: `available`, `busy`, `in-room <n>`, `responding`. Status is set by voice/gesture or automatically (e.g. `in-room` after a wristband scan).
- **Staff map:** a simple ward floor plan on the glasses showing each colleague as a dot, colored by status. Positions are **scripted** from `demo-data/` (no real indoor positioning).
- **Voice message (push-to-talk):** hold to record → sent to one colleague or to the whole ward → recipient hears it in their glasses and sees the transcript as a card.
- **Call for help:** one action ("Hey, I need help in room 12") → the nearest `available` colleague gets a request card → accept / decline → sender sees who's coming.
- **Live between two real pairs of glasses.** The two team phones run the app; messages go through the server. Other colleagues are simulated by the server.

### F2 — Live alerts with a walking briefing (P0)
- Alerts come from the server (scripted in the demo, e.g. "Fall detected, room 7").
- Routed to the nearest `available` staff member; shown as a full card + sound; accept / pass.
- On accept, the wearer gets a **briefing** while walking: patient name, age, main diagnosis, allergies, last medications, and the **latest care-log summaries from F3**. Read aloud (audio) and shown as swipeable cards (display).
- Other staff see that the alert is taken.

### F3 — Care conversation log (P0)
1. Nurse starts a log for the current patient (voice or gesture). The patient is selected by F4 scan or by voice ("log for room 12").
2. **Consent step:** the glasses prompt the nurse to ask the patient; the patient's verbal yes is captured and stored with the log. No consent → no recording.
3. Recording indicator is visible on the display for the whole capture (plus the glasses' own capture LED).
4. Audio is transcribed; on stop, the server asks Claude to turn it into a structured entry:
   - medications discussed / advised (name, dose, timing)
   - instructions given to the patient
   - what the patient reported (symptoms, concerns)
   - follow-ups for staff
5. The nurse reviews the summary card and **confirms or discards**. Only confirmed entries are saved. The raw transcript is deleted after confirmation (see §8).
6. Confirmed entries appear in the patient's record and in F2 briefings.

### F4 — Wristband QR scan (P1)
- Patient wristbands carry a QR code with a patient ID (we print fake ones from `demo-data/`).
- Looking at the wristband and triggering a capture decodes the QR on the phone → patient card on the glasses (name, age, allergies, meds due, latest log entries) and status becomes `in-room <n>`.

## 4. Demo script (fake scenario)

The demo is a **scripted fake ward**: two team members wear glasses and act as nurses; the rest of the ward (other staff, alerts, patient records) is played by the server from `demo-data/`. No real hospital systems.

| Beat | What happens | Features |
|---|---|---|
| 1 | Nurse Aino starts her shift; glasses show the ward status card and staff map | F1 |
| 2 | Aino enters room 12, looks at the patient's wristband → patient card appears | F4 |
| 3 | Aino explains a new medication schedule to the patient; asks consent, logs the conversation; summary card appears; she confirms | F3 |
| 4 | Aino needs a second pair of hands → "call for help" → nurse Ben (second glasses) gets the request, accepts; Aino sees "Ben is coming" | F1 |
| 5 | Scripted alert: fall in room 7 → routed to Ben (nearest available) → he accepts and gets the briefing while walking, including the log entry from beat 3 if it's the same patient | F2 + F3 |
| 6 | Failure moment: Aino's glasses disconnect → app shows a clear state, messages queue, reconnect resumes | Robustness |

Keep the full demo under **3 minutes**. Each beat must also work in **scripted mode** (server fakes the step) in case live hardware fails on stage.

## 5. Architecture

```
[Ray-Ban Display glasses]  ⇄ Bluetooth ⇄  [Android app (Kotlin, DAT SDK)]  ⇄ WebSocket ⇄  [Node/TS server]  →  Claude API
   camera, mic, speakers,                   UI cards, QR decode, audio,                    presence, routing, alerts,
   display, Neural Band                     transcription, offline queue                   scenario player, care log store
                                                                                              ↑
                                                                                         demo-data/ (JSON)
```

- **`app/` — Android companion (Kotlin).** Built on Meta Wearables Device Access Toolkit (DAT) **1.0.1 or later**, starting from Meta's `CameraAccess` / `DisplayAccess` samples. Owns everything device-side: session lifecycle, camera frames (QR), audio, display cards, inputs, offline queue. Developed in Android Studio on macOS.
- **`server/` — Node + TypeScript.** One WebSocket hub. Holds staff presence, routes messages and alerts, plays the scripted scenario, stores care log entries in memory/JSON, and calls the Claude API. **The Claude API key lives only here.**
- **`demo-data/`** — fake staff, patients, floor plan, wristband QR images, scenario timeline.
- **AI model:** Claude via the Anthropic API (`claude-sonnet-5-5` for summaries and briefings; fast and accurate enough).
- **Devices:** Ray-Ban Display is the primary target (the map, cards and alerts need a screen). Ray-Ban Meta Gen 2 gets an **audio-only fallback**: every card has a spoken version.

### Message types (server ⇄ app), JSON over WebSocket
`presence.update`, `staff.positions`, `msg.voice` (audio ref + transcript), `help.request`, `help.response`, `alert.new`, `alert.accept`, `alert.briefing`, `log.start`, `log.consent`, `log.stop`, `log.summary`, `log.confirm`, `patient.card`. Every message has `type`, `id`, `from`, `ts`, `payload`. Add new types here before using them.

## 6. Glasses platform: facts & gotchas

From Meta's DAT docs and our research ([research/notes/meta_dev_platform.md](research/notes/meta_dev_platform.md)):

- **Camera:** stream at 360p–720p, 2–30 fps over Bluetooth; lower resolution often looks *better* (less compression). For QR, grab single frames, don't stream continuously.
- **Display (Ray-Ban Display only):** 600×600, built with the SDK's layout API (FlexBox, Text, Image, Button, Icon). **Black = transparent.** Screen dims after 20 s, sleeps at 25 s.
- **Audio:** HFP is two-way mono 16 kHz. **Set up HFP before starting the camera stream** or audio can fail silently.
- **Beta features (fine in Developer Mode, not shippable):** on-glasses speech-to-text, Neural Band / button inputs, motion, "Hey Meta" app launch, standalone photos.
- **On-glasses speech-to-text stops after ~1 minute.** So F3's long conversations can't rely on it: capture audio on the phone and transcribe there or on the server.
- **Inputs stop working after the glasses are taken off and put back on.** The app must detect this and tell the user.
- **Tap (pause/resume) and tap-and-hold (stop) on the touchpad are reserved** and never reach our app.
- Only **one session** on the glasses at a time; only **one third-party app** registered in Developer Mode per pair.
- **Mock device kit:** develop without glasses using the phone camera as the glasses camera; it also simulates display, inputs and speech.
- Registration needs internet. DAT app won't install below 10% glasses battery.

## 7. Challenge requirements → how we handle them

| Requirement | Our handling |
|---|---|
| Permissions | Camera/mic/Bluetooth permission flow in the app with plain-language explanations; patient verbal consent for F3 |
| Errors | Every failure becomes a short card + spoken message ("Couldn't read wristband, try again closer") |
| Performance trade-offs | Single frames for QR instead of streaming; low resolution; summaries only on stop, not live |
| Connectivity | Outgoing messages and log entries queue offline and send on reconnect; UI shows offline state |
| Device state changes | Handle glasses disconnect, removed/put back on, hinge closed, low battery, thermal stop; each has a visible state |
| Accessibility | Every card has a spoken version (works on non-display glasses too); voice, gesture and phone fallback for every action; adjustable text size |
| Consent & safe data | See §8 |

## 8. Privacy, consent & data

- **Demo uses fake data only.** No real patient or staff data, ever, anywhere in the repo.
- **Patient consent** is required before any conversation log; the consent is stored with the entry.
- **Visible recording state** on the display for the whole capture.
- **Data minimization:** raw audio and transcripts are deleted once the nurse confirms or discards the summary; only the confirmed structured entry is kept.
- **No face recognition, no bystander identification.** Patients are identified only by their wristband.
- **Pilot path (for the deck):** a single ward pilot in a Finnish hospital, data kept within the hospital's own environment, later integration with the hospital's patient record system. Medical-device and GDPR health-data requirements are named as the next step, not solved in the demo.

## 9. Repo layout

```
README.md     pitch + how to run
CLAUDE.md     rules + decision log
SPEC.md       this file
app/          Android companion (Kotlin)
server/       Node/TS WebSocket server + Claude calls
demo-data/    fake staff, patients, floor plan, QR codes, scenario
research/     challenge brief, judging criteria, research report + notes
```

## 10. Open questions

Resolve these, then move the answer into the decision log in CLAUDE.md and delete it here.

- Speech-to-text for F1/F3: Android's on-device `SpeechRecognizer` (routed to the glasses mic over HFP) vs a cloud STT service on the server. Needs a quick test on real glasses.
- Where the server runs during the demo: team laptop on venue Wi-Fi vs a small hosted instance (Render/Fly). Venue Wi-Fi may block device-to-device traffic.
- How the wearer triggers actions: Neural Band gestures vs capture button vs voice, given the beta inputs.
