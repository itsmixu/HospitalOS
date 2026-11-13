# HospitalOS — Spec

> Single source of truth for **what** we build and **how**. Change this file first, then the code.
> Rules for working in the repo and the decision log live in [CLAUDE.md](CLAUDE.md).

## 1. One-liner

**HospitalOS lets nurses coordinate care and prepare patient notes hands-free.** A request for help carries relevant patient context to the responding colleague, while a privacy layer controls what is recorded, processed and shared.

## 2. Problem & audience

**Who:** Ward nurses first, doctors second.

**Problems:** Finding available help takes time; a responding colleague needs context; patient conversations and completed care must be documented without reconstructing them later from memory.

**Core experience:** Patient encounter → request assistance → colleague accepts and receives a briefing → explicit care update → reviewed draft note.

**Why glasses:** Hands-free interaction at the bedside, a polished Display interface with glanceable patient cards, task status, briefings and draft-note previews, plus useful spoken acknowledgements. First-person camera value still needs testing: compare optional wristband selection with capturing a selected instrument reading. Retain the useful option after testing; neither is a proven differentiator or a prerequisite for the core workflow.

**Positioning:** A connected bedside workflow with explicit acknowledgement and controlled information sharing. Do not claim that voice communication or ambient notes alone are novel, or that the prototype replaces a hospital information system.

## 3. Scope: what we build

Priority: **P0 = must be in the Display demo.** P1 = add only if it does not delay the Display workflow, device reliability or rehearsal. Everything else = deck only.

**Device scope:** Meta Ray-Ban Display is the main demo and development target. Build and polish its full bedside workflow first. Basic non-display (Gen 2) audio support is P1 only: assistance requests, acknowledgements and encounter capture start/stop. Detailed patient/briefing and note review uses the companion phone on non-display devices. There is no requirement for feature parity or a spoken version of every card. Share patient context, permissions and task state in the existing app/backend; do not build a second application. If Gen 2 support causes delays, defer it and label it planned rather than working.

| # | Feature | Priority |
|---|---|---|
| F1 | Direct colleague messages and acknowledged assistance requests | **P0** |
| F2 | Relevant patient briefing when a colleague accepts a request | **P0** |
| F3 | Consented conversation capture + explicit care updates → reviewed draft note | **P0** |
| F4 | Optional wristband QR scan → patient selection/card | P1 |
| — | Basic Gen 2 audio support (requests, acknowledgements, encounter capture); phone for detailed review | P1 only if no delay |
| F5 | Explicit privacy routing and visible recipient-specific outputs | **P0** |
| — | Staff map with labelled simulated positions; separate simulated alert scenario | P1 |
| — | EHR integration, real indoor positioning, autonomous staffing, shift reports, production admin dashboard | Deck only |

### Patient context (P0; QR is optional)
- Carry the stable patient ID from an existing task, or select a patient explicitly by voice/phone and confirm identifying details before starting an encounter.
- A room number may narrow a search but never identifies a patient on its own. Tasks, encounters and notes carry patient IDs, not just room labels.
- Keep the selected patient visible/on-demand through audio; require confirmation when switching. Never silently attach an update to a different patient.
- Selection does not grant access. The demo uses explicit staff assignments and permissions; a future integration must use the hospital's access controls.

### F1 — Communication and assistance (P0)
- **Presence:** declared `available`, `busy`, `in-room <n>`, or `responding`, set through supported controls. Scanning a wristband is not proof of physical room location.
- **Direct message:** deliberately record/send to a selected colleague. Use a supported voice/gesture trigger or phone fallback; do not assume reserved touchpad gestures work.
- **Request help:** create a patient-linked routine assistance task. Route using declared availability and assignment, not simulated proximity. Recipient accepts/declines; sender hears who accepted.
- **State:** distinguish queued/sent, delivered, accepted and completed. Delivery requires recipient acknowledgement; acceptance names the responding colleague. Completion requires an explicit staff update. Neither delivery nor acceptance proves care occurred.
- **Disconnected recipient:** show undelivered/unacknowledged status. Reconnect reconciles state using message IDs; it must not duplicate tasks or silently revive cancelled/stale requests. The sender reviews pending requests before resending.
- One real Display pair is required for the main demo; a second Display pair is preferred. A phone/browser client can act as the second caregiver without requiring Gen 2 support. Other staff are simulated.
- Optional staff map positions are clearly labelled simulated. Existing emergency channels remain authoritative; this prototype demonstrates routine assistance.

### F2 — Briefing on acceptance (P0)
- Accepting an assistance request opens a brief, task-relevant patient briefing drawn from the synthetic record and confirmed F3 entries.
- Include only permitted, relevant information; do not automatically read the full patient history, medication list or diagnoses. Preserve source attribution such as "patient reported".
- Offer a short audio acknowledgement, with clinical detail available on deliberate request or display so it is not automatically spoken into a shared room.
- The ward overview shows task status/ownership, not the clinical briefing. A separate simulated fall/emergency alert is P1, not needed for the core demo and not evidence of real fall detection.

### F3 — Encounter and care record (P0)
1. Confirm the selected patient and explicitly start the consent flow. The nurse asks permission before recording and records the consent decision in the app; refusal leaves capture off. Do not record a pre-consent conversation just to obtain consent evidence.
2. Start/stop capture explicitly. Show recording state and provide spoken start/stop confirmation. Verify device indicators on the actual hardware; do not assume the camera LED indicates audio-only capture.
3. Transcribe the synthetic encounter and create a draft with separate fields for **patient-reported information**, **advice/instructions**, **planned actions**, **staff-confirmed completed actions**, and **follow-ups**.
4. Explicit care updates attach to the same patient/task. "We will help you walk" remains planned; a medication discussion is not administration. The model must not infer completed care from speech about intentions or a camera image.
5. Nurse reviews, corrects and confirms or discards the draft. Only reviewed entries enter the simulated patient record; unreviewed drafts do not feed F2 briefings.
6. Apply §8 retention and access rules. Record supporting source references while the draft is being reviewed; do not imply deleted transcripts remain available afterward.

### F4 — Optional wristband QR (P1)
- Printed fake wristbands contain a patient ID. Deliberate capture decodes the QR on the phone, followed by patient confirmation and an authorised card.
- QR scanning is a selection convenience, not authentication, permission, the main selling point, or a mandatory step in the demo.
- Evaluate selected instrument-reading capture as an alternative camera experiment. It requires nurse verification and must not expand the P0 scope or claim clinical measurement accuracy.

### F5 — Privacy routing (P0)
- Server-enforced, explicit access rules determine allowed recipients and fields; the language model does not decide permissions.
- Prepare different outputs for the assigned caregiver, ward overview and authorised patient record (see §8).
- A small demo view displays the actual outgoing payloads and denied requests. This is a verification aid, not a production admin dashboard.

## 4. Demo script (fake scenario)

One coherent encounter in a **simulated ward**, using synthetic patients, assigned staff and no real hospital systems. One real Display wearer plus a phone/browser responder is sufficient; use two Display pairs if available. Show request status, patient context, the briefing and draft-note preview on the Display. Complete detailed editing/review on the companion phone if needed. Gen 2 is optional and never a prerequisite for rehearsal.

| Beat | What happens | Features |
|---|---|---|
| 1 | Aino explicitly selects and confirms a simulated patient, then starts a consented encounter; QR may be used if ready | Patient context, F3; optional F4 |
| 2 | Patient reports a concern; Aino records it and requests routine assistance for that patient | F1, F3 |
| 3 | Ben accepts, Aino receives acknowledgement, and Ben requests the relevant patient briefing | F1, F2 |
| 4 | Ben explicitly confirms the completed task; the update retains patient ID and staff ownership | F1, F3 |
| 5 | A draft care note distinguishes reported, planned and completed information; Aino reviews it | F3 |
| 6 | Show ward task status beside the authorised clinical output and an unauthorised request being denied | F5 |

Include one short failure: disconnect Ben before request delivery. Aino sees an unacknowledged request, never "Ben is coming". Reconnect/reconcile before proceeding to acceptance.

Keep the demo under **3 minutes**. Every beat also has a clearly labelled scripted fallback; disclose simulated events and hardware substitutions. A staff map and separate alert are optional and should not distract from the complete workflow.

**Acceptance checks:** finish and rehearse the full Display workflow without requiring Gen 2 support; complete the workflow without QR; no delivery implies acceptance; no planned action becomes completed; context stays on the selected patient; an unauthorised account cannot obtain clinical fields; confirm/discard handles raw-data deletion; disconnect/retry does not duplicate a task. Measure request-to-delivery latency on the actual setup.

## 5. Architecture

```
[Ray-Ban Display glasses]  ⇄ Bluetooth ⇄  [Android app (Kotlin, DAT SDK)]  ⇄ WebSocket ⇄  [Node/TS server]  →  Claude API
   camera, mic, speakers,                   UI cards, QR decode, audio,                    presence, routing, alerts,
   display, Neural Band                     transcription, offline queue                   scenario player, care log store
                                                                                              ↑
                                                                                         demo-data/ (JSON)
```

- **`app/` — Android companion (Kotlin).** Built on Meta Wearables Device Access Toolkit (DAT) **1.0.1 or later**, starting from Meta's `CameraAccess` / `DisplayAccess` samples. Owns everything device-side: session lifecycle, camera frames (QR), audio, display cards, inputs, offline queue. Developed in Android Studio on macOS.
- **`server/` — Node + TypeScript.** One WebSocket hub. Holds staff presence and patient-linked task state, enforces recipient permissions, routes messages and briefings, plays the scenario, stores reviewed care entries, and calls the Claude API with synthetic data. **The Claude API key lives only here.**
- **`demo-data/`** — fake staff, patients, floor plan, wristband QR images, scenario timeline.
- **AI model:** Claude via the Anthropic API for summaries and briefings. Verify the available model identifier and measure latency before choosing it; do not assume accuracy or performance.
- **Devices:** Meta Ray-Ban Display is the main demo target. Basic Gen 2 audio support is optional P1 and is dropped if it delays Display delivery. Keep one shared app/backend; use the phone for detailed review on non-display devices. Sensitive details are not automatically read aloud; open-ear audio is not a confidential channel. One Display pair plus a phone/browser responder is sufficient.

### Message types (server ⇄ app), JSON over WebSocket
Core message types: `presence.update`, `msg.voice`, `message.delivered`, `help.request`, `help.response`, `help.complete`, `help.cancel`, `help.briefing`, `patient.select`, `patient.confirm`, `patient.card`, `log.consent`, `log.start`, `log.stop`, `log.summary`, `log.confirm`, `log.discard`, `care.update`, `access.denied`. Optional: `staff.positions`, `alert.new`, `alert.accept`, `alert.briefing`.

Every message has `type`, `id`, `from`, `ts`, `payload`; patient/task events also have stable `patientId`/`taskId` and encounter events an `encounterId`. The server derives sender identity from the authenticated session, checks assignments/access, and filters outgoing payloads before delivery. Client-supplied identity or patient ID is not authorisation. Reconcile task versions and deduplicate IDs on reconnect. Add new types here before using them.

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
| Performance trade-offs | Camera capture only for optional selected actions; summaries on stop; measure delivery and summary latency |
| Connectivity | Show offline/undelivered/unacknowledged states separately; reconcile on reconnect and review pending requests before resending; no duplicate or stale tasks |
| Device state changes | Handle glasses disconnect, removed/put back on, hinge closed, low battery, thermal stop; each has a visible state |
| Accessibility | Prioritise readable Display cards, adjustable text size where supported, concise acknowledgements and supported voice/gesture controls with phone fallback. Full audio parity is not required; basic Gen 2 support is optional and must not delay Display work. |
| Consent & safe data | See §8 |

## 8. Privacy, consent & data

- **Fake data only.** No real patient or staff data in the demo or repository.
- **Capture controls:** explicit encounter start/stop, consent decision before recording, clear capture state and no background ward recording. Staff can stop capture immediately.
- **Explicit demo access policy:** the requesting and assigned accepting caregivers may receive permitted clinical context for their task. The ward overview receives room/task/status/ownership only, without patient name, symptoms, diagnoses or transcript. Only assigned staff may review/write the patient record; unrelated accounts are denied. These are prototype rules, not a claim about the exact permissions of a Finnish hospital.
- **Server-side filtering:** construct recipient-specific payloads before sending them. Hiding fields in the UI is insufficient. Apply the same checks to voice messages, briefings, record requests and reconnect queues. Avoid sending raw encounter audio to colleagues.
- **External processing:** synthetic encounter text may be sent to Claude for summarisation; specify any external transcription service too. Show what is sent. This is not hospital-local processing, and local deletion does not prove deletion from external providers.
- **Retention:** delete app/server raw audio and transcripts after confirmation or discard, including temporary and retry copies; retain only reviewed structured notes and minimal consent/task metadata. Define cleanup for abandoned sessions before the demo. Do not log raw clinical payloads outside the deliberately shown synthetic-data inspection view.
- **Demonstrate privacy:** show the actual caregiver, ward and record outputs plus an unrelated account's denied request. Removing names is not a guarantee of anonymisation.
- **Patient identification:** confirmed task context, explicit selection or optional wristband QR. No face recognition or bystander identification. Identification never grants permission.
- **Scope of claims:** this layer governs our app/server data flow, not Meta firmware, other apps or all device telemetry. No production privacy, security, regulatory or clinical-safety claim follows from the demo.
- **Pilot path (deck):** validate with nurses, then consider a single Finnish ward with hospital-controlled processing, identity/access integration and EHR integration. Appropriate privacy, security and clinical reviews remain future work.

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

- Which first-person camera interaction actually saves effort: optional wristband selection or a selected instrument reading? Test both briefly; QR stays optional and neither is a proven differentiator.
- Confirm available Claude model, transcription data destination, abandoned-session cleanup and actual device indicators before demo claims.

- Prove the Display sample, input and audio path on loan hardware first. Smoke-test Gen 2 only if available without delaying Display; document which optional functions actually work.
