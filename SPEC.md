# HospitalOS — product and implementation plan

> Authoritative plan, revised 2026-11-14. **The hands-free revision in §16 supersedes conflicting interaction and build priorities below for `handsfree/`; `prototype/` remains Miko’s preserved version.** This replaces earlier feature priorities; historical decisions remain in CLAUDE.md. Implementation lives in `prototype/`. A planned capability is not an implemented or validated capability.

## 1. Product decision

**HospitalOS helps nurses request and acknowledge help without leaving the bedside, and capture a visible instrument reading for immediate review on their glasses.**

Communication is the core product. Bedside observation capture is the focused first-person-camera feature. They work independently and connect when a nurse deliberately attaches a confirmed observation to a request. The product does not diagnose, recommend treatment, infer medication administration or replace emergency communication.

**Primary audience:** nurses on a ward with repeated requests for practical assistance and some equipment readings that still require manual entry. Start with one ward and one supported instrument display, not every hospital, profession or device. Confirm the need with actual nurses before asserting daily value. Communication-only users should never need to open a patient encounter or activate the camera.

**Device:** Meta Ray-Ban Display first. One real pair plus a responder browser/phone is the minimum honest hardware demo; two Display pairs are preferred. Basic Gen 2 audio is optional only if it does not delay the Display workflow or rehearsal. QR remains optional.

**Success:** a wearer can send a request, see who accepted, capture and confirm a reading, and complete the task without taking their phone out during the demonstrated flow. The phone is still used for setup, detailed corrections and administrative review.

## 2. Why glasses — and where they are not better

| Work | Advantage to prove | Existing alternative / limit |
|---|---|---|
| Request help while handling equipment or assisting a patient | Short voice action plus a quiet visual confirmation of recipient and response | A headset or communication badge can handle audio. Do not claim communication alone needs glasses. |
| Receive a task and its context | Glance at a short card without holding a separate screen or hearing clinical detail aloud | Phones are used in healthcare. The claim is reduced interruptions, not that phones are prohibited. |
| Capture a visible instrument reading | First-person camera plus immediate visual verification while hands remain available | Phone cameras can do OCR; compare interaction effort. Connected monitors already export readings and should use their native integration. |
| Hand off a confirmed observation | Same patient context and provenance travel with a deliberate request | EHR and communication integrations remain necessary for real deployment. |

The differentiator is the complete **see → verify → share with an acknowledged colleague** interaction, rather than generic transcription, a new EHR, or an always-on camera. First-person view is head direction, not eye tracking. No spatially anchored AR or gaze selection is assumed.

Pilot value hypotheses: fewer device-handling interruptions, less retyping, faster acknowledgement, and fewer missing task owners. These must be measured, not presented as achieved savings. If existing ward tools outperform the glasses, narrow the target setting or do not deploy.

## 3. Current baseline: what is actually built

At plan revision the repo contains:
- Local TypeScript/WebSocket ward server, synthetic fixtures, per-session demo tokens and server-filtered snapshots.
- Browser responder with patient selection, typed encounters, requests, separate delivery/acceptance/completion and reviewed deterministic notes.
- Kotlin Android shell with DAT registration/session code, a hard-coded synthetic request button and a basic Display status card. It is not yet a full glasses workflow.
- Six passing foundation checks and a previously successful Android debug build. These do not validate real glasses interaction, clinical suitability, OCR or speech.

Missing: actual on-glasses actions, device-tested microphone/camera routing, observations pipeline, short-message capture, request expiry/presence, robust server-enforced selection, durable task recovery and production identity/integration.

Important existing gaps to fix: `patient.select` currently checks access but does not retain confirmed session context; snapshots expose all assigned-patient records rather than task-minimal context; delivery acknowledgement is manual in the development UI; task acceptance can currently bypass delivery; state is lost on server restart. Do not hide these behind polished screens.

## 4. Scope and order of cuts

| Priority | Deliverable | Boundaries |
|---|---|---|
| P0 | Complete Display request/response/task screens | A named colleague; routine assistance; explicit send, accept, decline, cancel and complete |
| P0 | Basic nurse-to-nurse messages | Short dictated text, reviewed before send; local speech recognition; no simultaneous live call |
| P0 | Delivery, presence and reconnect truth | Delivery means client received it; acceptance means person accepted; never conflate either with care |
| P0 | One supported observation format | Deliberate camera capture, numeric/label/unit extraction, reject uncertainty, nurse review, explicit save |
| P0 | Patient/encounter binding and minimal sharing | Patient selection without mandatory QR; confirmed observations only attached by choice |
| P0 | Privacy controls and truthful offline demo | Camera/mic off by default, no external content processing in default mode, permitted fields only |
| P0 | Simulation coverage and live hardware rehearsal | Same business logic for real and replay inputs; disclose substitutions |
| P1 | Replay original short voice clip | Only if transport and retention are reliable; otherwise send reviewed text |
| P1 | Second monitor profile, QR, second glasses pair, Gen 2 audio | Only after P0 is stable; no required dependency on any of these |
| Later | Long ambient conversations, generated narrative notes, live calls, maps, automatic assignment, translation | Do not spend remaining hackathon time here |
| Pilot | EHR write-back, hospital identity, durable audit, fleet management and clinical validation | Defined in §13, not claimed by the hackathon |

Retain the existing typed note workflow as a development/review tool. The demonstration record is a deterministic structured entry from verified observations and explicit task updates. External LLMs are not a dependency. No automatic alarm, escalation decision, diagnosis, dosing or treatment interpretation.

## 5. Intended real-use interaction

### Shift setup (phone; once, not every task)

Sign in, select/confirm assigned ward, connect glasses, check permissions/battery, choose language and quiet mode. For the demo use explicit synthetic identities; for a hospital use managed identity. Show availability controls: Available / Busy / Off duty. Automatically mark a disconnected session Unreachable, never Available. Pairing and developer setup occur before the stage demo and are disclosed.

### A. Communication without patient data

1. Deliberately activate **Help** or **Message** using the verified glasses input. Short voice input begins only after activation; no custom always-listening wake word.
2. Choose a colleague from a short list, or speak a constrained command such as “Ask Ben for assistance in room twelve.” Resolve only against the roster; ambiguous names require selection. Room-only requests are allowed if no patient context is attached.
3. Show recipient, room, task and any attachment; **Send / Edit / Cancel**. Speech recognition cannot send an unreviewed request.
4. After Send: “Sending” → “Received by Ben's device” → “Ben accepted.” If offline or timed out, show that explicitly and offer Cancel / Choose another colleague / existing hospital channel.
5. Recipient receives a short non-clinical cue and a card: requester, location, task, **Accept / Decline / Details**. No forced spoken clinical briefing. Routine notifications queue without obscuring an active observation confirmation.
6. Acceptance claims ownership atomically. If routing to more than one recipient is added later, first valid acceptance wins unless a requested headcount is explicitly supported. Hackathon scope is one assisting colleague.
7. **Complete** is an explicit action by the assigned responder; it is never inferred from arrival, proximity, silence or a disconnected device. Sender sees completion. Cancellation and decline stay distinct for audit and display.

Short messages use Delivered status and optional explicit acknowledgement; they are not actionable care requests unless deliberately created as a task. “Sent” means server acknowledgement, not socket.send() returning true.

### B. Bedside observation capture

1. Select and confirm a synthetic patient from an assigned task or short patient list. Display two identifying attributes; room is context, not identity. A patient switch clears unconfirmed readings and requests confirmation. Optional wristband capture is a shortcut, not access permission.
2. Select **Capture reading**, explain the capture to the simulated patient and confirm the demo permission step. Camera remains off until this action. Use one supported blood-pressure-style layout (SYS/DIA in mmHg and pulse in bpm); choose the actual profile after the initial optical test.
3. Brief camera session: a fixed aiming guide and “Hold steady” cue. Detect/crop the supported display; read several frames. Stop after a usable candidate or a short timeout. Do not continuously stream the ward.
4. Display the proposed values, labels, units, patient and capture time. Where supported show the cropped source image on a second card; otherwise provide it on the phone for uncertain readings. **Confirm / Retry / Cancel**. Do not auto-confirm because a value seems medically plausible.
5. Confirm creates an immutable confirmed observation with provenance. An explicitly corrected value is labelled manual correction, never “camera verified.” Corrections create a new version; old confirmations cannot silently change.
6. **Save locally / Request help / Done**. Sharing requires a deliberate attachment selection; an old observation is labelled with its capture time, never presented as live monitoring. Changing the selected patient invalidates an unsubmitted attachment.
7. A confirmed observation may join a reviewed structured note; it is not “filed in the EHR” until a future connector receives a success acknowledgement.

### Display design rules

- Home has three choices: **Help**, **Messages**, **Capture**. Task inbox/status is reachable without navigating through a patient record.
- One decision per card, two or three actions, short readable text. Do not mirror the browser dashboard or put JSON/technical states in the wearer UI.
- Maintain a compact patient banner in patient-bound screens, but omit names from generic notifications and the ward overview.
- Quiet by default; sound indicates an event, never automatically reads symptoms/names. Open-ear speakers are not confidential. Speech controls have visual/button alternatives; don't rely only on colour.
- Capture mode unmistakably shows Camera on / Listening / Stopped. Screen sleep must not silently change task or capture state. Cancel/stop always available.
- Confirmations happen standing safely at the bedside, not while walking. No arrows over the physical world, gaze assumptions or dense scrolling during movement.

## 6. Implementation architecture: local first

```text
Meta Display glasses (camera / microphone / Display / supported input)
                       ↕ DAT + Android audio route, verified on actual hardware
Managed Android companion
  Display presenter + interaction state machine
  explicit capture controller → temporary frames → local crop/OCR/profile parser
  deliberate short audio → local ASR / restricted intent parser → user review
  session context + local encrypted outbox + credential store
                       ↕ authenticated WSS, hospital/local network
Ward service (Node/TypeScript)
  identity/assignment checks → atomic tasks/messages → recipient-specific projections
  confirmed observations + draft/review state → audit + persistence
                       ↕ later: approved EHR interface / hospital messaging connector
```

**Where computation runs:** glasses are sensors/output, not an assumed general-purpose AI computer. Start with bundled ML Kit Latin OCR on Android; model bundling avoids a first-use download. Google states ML Kit processes input on-device but documents metrics/diagnostics separately; evaluate telemetry and merged permissions. If hospital policy cannot accept it, replace the adapter with a self-hosted/offline OCR engine, accepting revalidation cost. Do not claim all ML Kit network traffic is absent.

**Speech:** first test Android's explicitly on-device recognition availability and selected language; never silently fall back to a cloud recognition service. Alternative: whisper.cpp on the controlled demo laptop/ward server, with model downloaded in advance. Local-server speech is local-network processing, not phone-only. Use a multilingual model for Finnish; English-only models do not prove Finnish support. Start the demo in one declared language and test names/numbers/noise. If neither local recognizer is reliable, use presets/Display controls and label free dictation unavailable; do not claim working voice messaging.

**AI boundaries:** OCR and speech recognition are bounded perception tools. Intent parsing uses a restricted schema and roster matching. Values, access decisions, request lifecycle and completion come from deterministic code. Do not use a generative model to guess obscured digits, assign a patient, interpret clinical significance or decide access. Optional future summarisation consumes only reviewed text and cannot change verified numbers.

**Local network:** use the existing Node server on the team laptop, Android phones on a controlled router/hotspot or USB reverse. Prefer authenticated TLS/WSS on a LAN; cleartext remains restricted to USB/emulator synthetic development. Validate certificates on the phones; never disable certificate checks to make the demo work. Demo identities use short-lived tokens entered on the phone. Provision models and register devices before venue connectivity tests. No public deployment or internet exposure is required.

### Proposed modules and ownership boundaries

All paths below are under `prototype/`; create these as implementation advances rather than empty scaffolding.

| Module | Responsibility | Test seam |
|---|---|---|
| `app/.../ui/` | Display cards and companion correction/review | Fake presenter event replay |
| `app/.../session/` | Registration, capabilities, lifecycle and input | MockDeviceKit + hardware checklist |
| `app/.../capture/` | Permission-gated camera/mic; bounded buffers | Recorded synthetic frames/audio |
| `app/.../observation/` | Profile detection, crop, OCR, stability, review candidate | Shared labelled fixtures and rejected cases |
| `app/.../speech/` | Local recognizer adapter and schema-based intent | Recorded phrases with expected intents |
| `app/.../transport/` | WSS, auth, acks, timeouts, outbox and reconnection | Fault-injected local service |
| `server/src/` | Authoritative task/message/observation state and permissions | Existing tests extended with concurrency/restart cases |
| `demo-data/` | Synthetic identities, monitor generator, fixture labels, replay | Frozen holdout and reproducible seed |

Refactor the current single Activity into lifecycle-owned state and presenters; keep the activity as wiring. Cancel old session collectors and socket callbacks on replacement; a stale connection must not overwrite the current connection's state. No hard-coded “request Ben for p1” controls in the finished wearer flow.

## 7. Observation pipeline: the technical feature

1. **Scope device support:** choose one layout with identifiable labels/units. For the hackathon generate a visible simulated monitor with randomized values on a second screen; the camera must read pixels. Never feed hidden values directly to the OCR pipeline. If a real device is available, collect separate fixtures at actual working distance and label its results separately.
2. **Acquire:** short bounded stream; no all-day recording. Tag capture ID, patient ID, context version, time and profile version at start. Reject results arriving after context changes or cancellation.
3. **Locate:** fixed guide plus profile anchors; rectify perspective if useful. Crop before any local-network transmission. Unknown layout, missing unit/label, excessive glare, blur or clipped digit means Retry, not a partial saved record.
4. **Recognise:** bundled phone OCR baseline, followed by a profile-specific parser. Segment-display digits may need a dedicated decoder; test this before adding another general model.
5. **Temporal check:** require the same complete tuple from three separate frames over a short stable interval. This reduces transient errors but is not three independent votes or a clinical confidence guarantee; systematic errors can repeat. Stop within five seconds and request repositioning if unstable.
6. **Verify:** display exact value/unit tuple for nurse confirmation. Syntactic format checks detect malformed captures; do not reject unusual but visible readings using a “normal patient” range or substitute a normal value. No unit conversion in the first profile.
7. **Save/share:** persist confirmed numbers, source kind, reviewer, capture time, confirmation time and versions. Raw images remain temporary; keep clinical measurement time unknown if the display does not establish it. “Captured now” does not prove the device measured now.

**Candidate data:** `captureId`, `patientId`, `encounterId`, `contextVersion`, `profileId/version`, `capturedAt`, `displayedMeasurementTime?`, labelled values/units, frame-agreement/quality flags, `reviewState`, `reviewerId`, `confirmedAt`, optional manual-correction provenance. Unconfirmed candidates never enter colleague briefings or records.

**Evaluation:** use at least 40 labelled synthetic scene cases (clear, dim, angled, glare, occluded, changing, missing labels, unsupported layouts), with a held-out split fixed before tuning. Measure complete-tuple exact accuracy, wrong proposals, rejection rate, useful-read coverage and latency. Count a capture attempt as a case, not every correlated frame as a new sample. Report numbers honestly; all-reject is not success. A projected-screen demo does not validate physical medical displays.

## 8. Authoritative state and contracts

Extend, do not silently break, the current `{id,type,payload}` protocol. Version it before Android/browser divergence. Derive actor from authenticated session; client `from`, role or patient ID is never authority. All patient-bound operations include patient/encounter/context version and are checked server-side. For a generic staff request, patientId/encounterId are absent and room is only a location label; it cannot retrieve clinical data. Attaching an observation upgrades it to a patient-bound operation and requires explicit confirmed context and access checks.

**Tasks:** draft (client) → queued locally → server accepted → delivered → accepted → completed. Alternatives: declined, cancelled, expired. Server accepted is the current compatibility equivalent of `sent`; migrate deliberately. Use monotonically increasing revisions, atomic transitions and request IDs scoped to actor. Persist idempotency outcomes; hashing content is not itself durable deduplication. A cancelled/expired request cannot be revived by an offline client. Treat two recipients racing as a real concurrency test.

**Presence:** available/busy/off-duty plus online/unreachable are separate. Heartbeat timeout indicates reachability, not staffing availability. Choose a demonstrative request expiry (e.g. 60 seconds), show it, and make production timing ward-configured. Timeout never implies the task was handled; offer existing communication routes. A request is never called an emergency alert.

**Observation:** capturing → candidate → confirmed → saved locally → submitted to connector → acknowledged by EHR (last two are later). Rejected/discarded/expired are terminal. Pending captures expire after 30 seconds or patient change. The prototype ends at saved locally and labels it accordingly.

**Messages:** recording → transcribing → review → sent → delivered; read/acknowledged only after explicit recipient action. Text is primary; optional audio expires under a separate retention rule. Do not replay it aloud automatically.

**Implemented 2026-11-14 (branch `feat/animations`):** `presence.update {status: free|busy}`; `msg.send {to, text}` and `msg.read {messageId}` (snapshot `messages` = only the user's own); `help.auto {patientId, count, summary}` (server picks free colleagues assigned to the patient); `assistant.ask {text, patientId?}` → `assistant.reply {heard, say, actions, source}` sent only to the speaker — Claude or the rule parser maps speech to the commands above, executed as the speaker through the normal permission checks.

**New commands, planned:** `session.context.confirm`, `message.received`, `message.ack`, `help.expire`, `help.briefing.get`, `observation.propose`, `observation.confirm`, `observation.discard`, `observation.attach`. Keep existing commands behind compatibility handlers until both clients migrate. Build response schemas too, not only incoming validation.

**Briefings:** project fields for the requesting/assigned staff and active task; request details only on deliberate open. Replace broad full-record snapshots with generic inbox status plus separately authorised patient/task reads. Observations share only after confirmation and user selection. A ward observer receives task ID, room, status and ownership, not names, symptoms, free-text messages or instrument values. Changing permissions must affect live sockets, reconnects and cached screens.

**Storage:** SQLite on the local server for tasks, messages, confirmed observations, revisions and audit metadata. Use transactions for state transitions and idempotency; startup restores pending states accurately. Android Keystore-backed secrets and encrypted local pending drafts/outbox; no patient data in shared external storage. The demo uses synthetic data, but persistence/restart correctness is a release gate. Hospital storage and backup policies come later.

## 9. Privacy design and explicit limits

| Data | Default processing / recipients | Retention target for prototype |
|---|---|---|
| Full camera frames | Phone memory only; never ward broadcast or cloud | Bounded capture buffer; release on stop/cancel within 10 seconds; no gallery files |
| Cropped evidence | Phone-only review by default | Delete at confirmation/discard, or 30-second candidate expiry |
| Short command audio | Phone; optionally explicitly configured ward-local recognizer | Delete after transcript review/send/discard; maximum 2 minutes for abandoned commands |
| Reviewed message text | Named authorised recipient and sender | Synthetic demo session, with explicit reset and expiry policy; pilot governed by hospital policy |
| Confirmed observation | Assigned patient context; attached only by choice | Synthetic local database until explicit reset; real clinical retention determined by hospital |
| Audit | Actor, action, resource ID, revision, time, outcome; no raw media | Synthetic reset policy; production access/retention controlled separately |

“No raw content stored” means no intentional file/database/log copy. It is not a promise of forensic erasure from managed-language memory, OS caches or vendor components. Check temp files, request/error logs, backups and crash reports; include failed and cancelled flows.

Camera and mic off by default. No ambient corridor listening, facial recognition, worker tracking or automatic room detection. Use short camera capture only after the user intentionally starts it. A fixed crop cannot guarantee that bystanders never enter the sensor's view, so source frames stay local and temporary. Staff can stop capture immediately, and a patient refusal takes the workflow to manual entry without pressure.

The prototype uses simulated permission/consent steps. For real care, the hospital establishes the lawful basis, notices, patient choices, workforce policies and any consent requirements; a recorded “yes” is not a universal legal solution. Kanta access requires appropriate grounds and rights, not merely possession of glasses. SDK analytics/crash-reporting opt-outs are already configured; audit Meta AI app/device traffic and all OCR/ASR dependencies separately. Do not claim control of the glasses firmware, vendor telemetry or other apps.

**Local-first acceptance test:** preload models and register devices, preserve local Wi-Fi/Bluetooth but block external internet. Run both workflows, capture network destinations and inspect payload paths. Report app content routing separately from residual system/vendor traffic. Distinguish WAN loss (local service may still work) from LAN loss (colleague communication cannot work). No silent switch to cloud ASR/LLM. Signed-in device provisioning and updates can still require internet.

## 10. Build sequence and simulation gates

Effort ranges are rough focused engineering hours, not promised delivery dates. Dependencies and pass/fail criteria determine order. Existing code is reused. One developer should execute sequentially; owners below describe responsibilities, not additional staffing commitments. Expect roughly 20–30 focused engineering hours including integration/rehearsal; if less time remains, use the cut rules rather than claim finished features.

| Step | Work / responsible role | Check before advancing | Estimate |
|---|---|---|---|
| G0 | Developer + nurse/mentor: confirm one routine assistance scenario, monitor support and platform viability | Ask a nurse to walk through current practice; test real Display rendering, button callback, camera view of a monitor at natural distance, mic route and capability coexistence. Record firmware/SDK/device. No claims from simulator alone. | 1–2 h |
| G1 | Backend: state contracts, persisted tasks/context, minimal projections, fake clock/network | Simulation: unauthorised IDs, patient switch, malformed command, duplicate send, accept race, decline/cancel/expire, server restart and stale reconnect. No incorrect ownership or cross-patient write. | 3–4 h |
| G2 | Android: three core screens plus inbox/status using fake transport first | Replay incoming request while capturing, long text, screen sleep, no devices, lost connection, permission denied. Then real glasses A → browser B → A acceptance/completion with no phone touch during A's flow. | 3–4 h |
| G3 | Android/local ASR: deliberate activation, short command, recipient resolution, review/send | At least 20 recorded phrases across two speakers plus ambiguous names, background speech and silence. No command sends without confirmation. Then real glasses mic test; label phone mic fallback honestly. | 2–4 h |
| G4 | Vision: single profile, frame fixtures, crop/OCR/parser/stability/review | Freeze held-out scene set before tuning; verify failure rejection and no guessing. Then actual camera capture of randomized monitor values, with no data connection from generator to extractor. | 3–5 h |
| G5 | Integration: bind confirmed observation to patient/task and review entry | Simulation: late OCR after patient switch, repeated confirm, wrong encounter, cancel during inference, manual correction, old reading and denied attachment. Live: capture → confirm → request → accept → complete → saved local record. | 2–3 h |
| G6 | Privacy/reliability: offline, permission revocation, lifecycle, restart and soak | Egress test, raw-data cleanup, unauthorised recipient test; remove/re-wear glasses, phone background, Wi-Fi drop, reconnect. A 30-minute trial with bursts of 20 tasks; no stale confirmations or hidden recording. | 2–3 h |
| G7 | Team/nurse: usability pass and demo freeze | New user completes after a short explanation; compare phone/badge baseline; three consecutive clean rehearsals with judge-chosen readings and one failure. Freeze features. | 2–3 h |

Do simulation at every boundary, not only at the end. Inject fake frame/audio/network adapters into the same state machine and parser used live. Do not make a separate “demo success” implementation. Fixtures and UI must label **replay**, **simulated monitor**, **phone camera** and **live glasses** distinctly.

**Hardware fallback rules:** if camera+Display cannot coexist, capture a brief burst, stop the camera and then render review; explicitly validate this sequence. If local speech is slow, use short presets and retain reviewed dictated message input only where measured reliable. If optics cannot read an actual device, demonstrate a supported simulated monitor and disclose the limit; don't promote it as device-validated. If Display inputs fail, phone controls are a development fallback, not evidence of the hands-free milestone.

**Cut order:** maps/live calls → original-audio playback → long dictation/free-form notes → second monitor → QR/Gen 2. Do not cut privacy checks, truthful acknowledgement, patient binding, explicit observation confirmation or a working camera demonstration and still call the full plan complete. If one essential hardware gate fails, present the reduced prototype honestly with the blocker and next step.

### Release targets (engineering targets, not measured results)

- Established-session request delivery and response update: p95 under 2 seconds on the test local network, measured over at least 30 events; report both latency and loss, including cold/reconnect separately.
- Short local command transcription: target under 3 seconds after speech ends; no silent wrong-recipient sends in the test corpus.
- Supported optical capture: candidate within 5 seconds; at least 90% useful exact-tuple capture on clear held-out scenes; no incorrect proposal in deliberately unreadable/unsupported fixtures. Report wrong-proposal rate separately; passing a small test is not a clinical accuracy guarantee.
- No cross-patient/unauthorised output or duplicate task in defined contract tests. All writes require valid state transitions.
- Main wearer completes the rehearsed interaction without touching a phone. A one-pair demo can use the browser for the responder.
- Capture stops reliably on cancel/permission loss/session loss; stored raw content cleaned under the defined limits. A 30-minute soak measures heat/battery and interruptions, not all-shift endurance.

## 11. Three-minute demonstration

1. **0:00–0:25:** Aino requests routine assistance from Ben on Display. Show confirmed recipient and room; no camera is needed for communication.
2. **0:25–0:45:** Ben accepts on a second Display or labelled browser responder. Aino sees the named acceptance. Explain that received, accepted and completed are separate states.
3. **0:45–1:30:** Aino confirms the synthetic patient; a judge changes values on a visibly simulated monitor. Deliberately capture through glasses, review labels/units and confirm. Show a short obscured-digit failure/retry if timing permits.
4. **1:30–2:05:** Attach the confirmed observation to the existing task. Ben opens the relevant briefing and explicitly completes the assistance. Display “saved locally”, not “filed in hospital record”.
5. **2:05–2:35:** Disconnect responder before another request; show pending/unreachable, never invented acceptance. Switch to ward observer view: no clinical fields.
6. **2:35–3:00:** Show local processing/network evidence and pilot path. State remaining constraints: one supported profile, simulated data, no EHR, hardware coverage and language tested.

Keep a labelled replay available for venue failure, with no claim it is live. Never use a fake error-catch that only works for a preselected wrong digit. Measure the actual displayed values chosen by the judge.

## 12. Evidence to collect and user validation

Before polishing, ask at least one nurse: What assistance requests repeat daily? How are they acknowledged now? Which readings still need manual entry? Is patient information visible/readable in their working conditions? When would glasses distract or breach privacy? What PPE/cleaning rules apply? These are discovery questions, not evidence of demand until answered.

Test with two or three people unfamiliar with the interface (prefer nurses if available). Ask them to send/cancel/accept a task, reject a bad reading and recover from a disconnect. Count device touches, time, mistakes, clarification requests and perceived interruption. Compare against a phone workflow and, where available, their existing badge. A tiny hackathon usability exercise is directional, not a clinical study.

Store synthetic result summaries and fixture manifests under `prototype/demo-data/`; describe results/limitations in the existing README/SPEC rather than new planning documents. Keep evaluation scenes separate from tuning scenes. Capture exact versions, sample sizes, device model, distance, lighting, network and language so the deck cannot imply universal performance.

## 13. From hackathon to actual hospital use

Realistic first customer: one ward with an identified manual workflow and a clinical sponsor. Product integrates with existing systems; hospital adoption is not “install the APK and give everyone glasses”. Deployment gates are sequential:

| Stage / outside hackathon | Required work and why | Exit evidence / decision owner |
|---|---|---|
| 1. Platform and intended-use feasibility | Read applicable Meta developer terms/AUP with mentor; establish permitted healthcare use, production distribution, supported APIs/firmware, account management and telemetry controls. Developer Mode is not a production distribution plan. | Vendor/IT written feasibility; if unsuitable, keep hardware adapter portable and evaluate hospital-approved hardware. Current AUP page requires login; its terms have not been verified here. |
| 2. Workflow co-design | Ward nurse lead selects routine use case and baseline; assess interruptions, screen readability, cleaning, prescription lenses, PPE fit, batteries/charging, infection-control approval and shared-device logout. | Approved workflow and device handling instructions; occupational/infection-control review; no operating-room/PPE-safety claim without evidence. |
| 3. Legal, data and intended purpose | Hospital DPO/security and qualified regulatory lead assess controller/processor roles, lawful basis, DPIA need, contracts, retention, EU transfers and medical-device/software classification. No-diagnosis wording alone does not establish exemption. | Written applicability/classification decision, required certification/conformity route and risk management plan before clinical deployment. |
| 4. Secure managed deployment | Managed Android fleet, hospital identity/roles, assignment feed, secure enrolment, revocation/lost-device controls, WSS/TLS, network segmentation, audit, backups, monitoring and versioned releases. Ward-local ASR if phones cannot meet performance/policy. | Security test, recovery exercise, traffic inventory, access review, support owner and approved release process. |
| 5. Clinical system connection | EHR vendor-approved interface; explicit patient/encounter mapping, units/codes, author/provenance, idempotent write-back and success/error acknowledgement. FHIR Observation/Task are candidate mappings, not proof that a local EHR permits write access. Integrate existing communication/nurse-call systems rather than replacing emergency channels. | Vendor sandbox and reconciliation tests; hospital-approved integration. Determine applicable Finnish information-system requirements; direct Kanta connectivity is not assumed. |
| 6. Supervised shadow trial | Run beside current practice, no clinical write-back; compare device-reading accuracy, communication outcomes, staff workload, patient acceptance and failure handling. Validate real displays across lighting/angles and Finnish/Swedish as needed. | Protocol-defined accuracy/usability/privacy criteria signed off by clinical safety/IT owners; stop on wrong-patient association or unsafe distraction. |
| 7. Limited real-care pilot | Enable only approved routine tasks and reviewed write-back, with training, supervision, incident reporting, rollback and normal workflow always available. | Monitored pilot outcomes versus baseline; expand only on demonstrated benefit and reliability. |
| 8. Scale | Multi-ward identity/assignment support, HA local services, shift-long endurance, fleet cleaning/charging, multilingual evaluation, support SLA and lifecycle/security updates. | Sustainable operating cost, support capacity and ward-level evidence; no universal rollout on hackathon results. |

**EHR ownership:** records remain in the hospital's clinical system; HospitalOS is an interaction/capture client. Retention of clinical records follows approved obligations; our demo's deletion rules must not erase production records. Preserve audit metadata while minimising raw media.

**Clinical-risk boundary:** transcribing a displayed reading can still affect care even with human review. Regulatory classification depends on intended purpose and implementation. No autonomous risk score, treatment suggestions, dosage calculation or alarms are in scope. If later added, reassess classification, validation and governance before development claims.

**Economic reality:** sell only where avoided manual effort exceeds glasses/phone fleet, integration, support and training cost. Measure per-round savings and reliability before proposing a price or ROI. Prefer direct monitor integration where already available. If a headset plus phone serves a ward better, do not force glasses.

## 14. External evidence and unresolved facts

Primary references checked 2026-11-14; statements above distinguish documented capability from proposed implementation.

- [Meta Android DAT source and samples](https://github.com/meta/meta-wearables-dat-android): SDK integration and sample lifecycle. Pin the tested dependency and record actual device capability results.
- [Meta developer acceptable-use policy](https://wearables.developer.meta.com/acceptable-use-policy): login required when checked; healthcare permissions/restrictions remain an explicit mentor/vendor check, not assumed approval.
- [ML Kit Android OCR](https://developers.google.com/ml-kit/vision/text-recognition/v2/android): bundled-model option supports immediate availability; image quality matters. [ML Kit terms/privacy](https://developers.google.com/ml-kit/terms) distinguish on-device input processing from metrics collection.
- [whisper.cpp](https://github.com/ggml-org/whisper.cpp): local inference and Android/Mac support make it a candidate, not a guarantee of Finnish accuracy or phone latency.
- [Kanta patient data access](https://www.kanta.fi/en/professionals/patient-data-repository): use requires a legitimate care need and appropriate access. [Certification and key requirements](https://www.kanta.fi/en/system-developers/certification-and-key-requirements): assess applicable information-system obligations with the hospital/vendor.
- [Fimea intended purpose and classification](https://fimea.fi/en/medical-devices/placing-a-medical-device-on-the-market/intended-purpose-and-classification) and [EU software guidance](https://health.ec.europa.eu/system/files/2025-06/mdcg_2019_11_en.pdf): classification requires an intended-purpose assessment; clinical review alone is not proof of exemption.
- [Microsoft nursing documentation](https://support.microsoft.com/en-us/dragon-copilot/nurses/overview-of-the-dragon-copilot-experience-for-nurses): ambient nursing capture already exists; it is not our novelty claim.
- [Baxter connected vitals workflow](https://www.hillrom.com/content/dam/hillrom-aem/us/en/marketing/products/connex-spot-monitor/documents/US-FLC45-220030-EN-V2_Vitals-Oracle-Cerner-Connectivity_Sell-Sheet-LR.pdf): connected devices can already transfer readings; target the remaining manual gap.

Open decisions for G0: exact loan hardware and simultaneous capabilities; monitor profile and working distance; named nurse validation contact; available focused engineering hours; language; input gesture; local audio route; local-network topology; permitted SDK use. Resolve each with evidence, update CLAUDE.md, and adjust scope explicitly rather than silently substituting a simulated feature.

## Local glasses preview (2026-11-14)

A debug-only Android preview activity uses Meta DAT MockDeviceKit and its native display preview view to render the existing HospitalOS Renderer and route clicks into the existing flow. It connects to the synthetic ward; it is a UI simulator, not optical, microphone, camera or physical-gesture validation. The release app excludes the preview activity and mock dependency. No live hardware session is required. An explicit debug `chromePreview` launch flag starts the official SDK test server on Android port 9000; localhost ADB forwarding connects it to Meta’s Chrome extension. Closing the preview stops the server.

## 16. Hands-free revision — separate implementation, 2026-11-14

### Product and separation

Build nurse-to-nurse communication plus one verified bedside instrument-reading feature. The wearer performs the complete core loop without phone, temple, wristband, or other touch after shift setup. **Voice is primary input, the display is quiet verification, optional deliberate head gestures handle short decisions.** Head movement is never a substitute for clinical review. Maintain voice alternatives for restricted neck mobility, dizziness, noise and user preference; if no reliable input is available, pause safely and use the existing ward channel. No claim that gestures are forbidden in every hospital: validate the actual ward and tasks.

`prototype/` is preserved. New implementation lives in `handsfree/`, initially forked from the existing source, with Android package `fi.hospitalos.handsfree`, separate build output, loopback server port 8790, and separate state. Reuse Miko’s DAT setup, card design, local server experience and simulator bridge; keep inherited code available but route the new launcher through the new controller. Do not route new speech through the old assistant or cloud fallback. No overwrite, migration or push to main as part of this first implementation.

### Interaction contract

- Idle: clear view or small status; no mandatory home-menu navigation. A visual incoming alert does not turn on the mic or arm a head gesture.
- Activate through approved Meta Voice Invocations ("Hey Meta, start HospitalOS" with the actual approved spoken name). App configuration/approval and cold/warm launch, locked phone, WAN-off behavior are G0 hardware gates; a simulator Wake event is a labelled substitute, not proof. Do not continuously restart speech as an all-day custom wake detector.
- After activation, bounded listening (12 seconds for a command; 8 seconds for a response), explicit listening/closed state, stop on timeout/cancel/error/device loss. Finite follow-up windows may start inside an intentionally initiated interaction. No automatic microphone opening for unrelated notifications. Speech source must be shown truthfully; no silent phone/cloud switch.
- Generic assistance: "Ask Ben to bring a hoist to room twelve" → local parse → recipient/room/action review → "send request" or deliberate nod → server acknowledgement → device delivered → accepted → explicitly completed. Patient record access is not required for a generic room request; a room cannot grant access or identify a patient.
- Incoming: passive "Ben requests help · Room 12" → wearer activates and says "review request" → one request fixed on screen → nod / "accept request" or shake / "decline request". Never apply yes/no to a different alert that arrived mid-confirmation. Queue routine interruptions; expire/review if the target changes.
- Messages: "Tell Ben I am busy" → reviewed exact text + recipient → "send message" / nod. Never interpret words inside dictated message content as top-level commands. Explicit cancel available before sending; unsupported/negated/ambiguous commands do not mutate state.
- Completion: "complete request" resolves only one accepted incoming task (otherwise ask which). Show task and require "confirm completion" or deliberate nod. No auto-completion on arrival/silence/disconnect. Decline, cancel and expiry remain distinct.
- Status: exact "set me busy" / "set me available", reviewed then confirmed. Declared availability and reachability remain separate. Never auto-route to disconnected staff as if available.
- Observation (later implementation gate): identify/confirm patient, "capture reading", bounded camera capture → exact labels/values/units/source time → explicit review → "save reading" (nod only after deliberate review, if validated) → optional attach/share. Reject ambiguity, late OCR, wrong context and uncertain digits; never guess. Short structured notes are secondary, and statements must not all be labelled planned care.

### Head confirmations: limited, optional and testable

Use **two deliberate nod cycles** for yes, and a deliberate left-right-return shake for no, only within an 8-second visible confirmation prompt. This is a starting recognizer design to evaluate with nurses, not a validated movement prescription. Arming follows an explicit wearer action (command/review), never mere notification arrival. Require a stable neutral interval, reject mixed-axis/large/too-fast motion, nonmonotonic timestamps, sensor gaps, phone/wrist motion and motion outside the prompt. Consume once, add cooldown, and bind to prompt ID, task ID/revision and session. A new prompt resets the detector. Timeout cancels a draft or closes review; silence never accepts. Walking/bending/conversation nods need held-out physical tests before enabling real head input. Phone IMU is never presented as glasses head motion.

First simulator implementation uses labelled calibrated pitch/yaw traces through the same detector. Real DAT Motion wiring requires axis/sign/units calibration, glasses source checks, stationary gating, start/error/pause handling and physical false-activation evidence. Do not infer these from a turning-map implementation.

### Local processing, state and privacy

- Small state-aware exact command grammar for common tasks. Unknown text asks for clarification; no broad keyword matching. Free-text is restricted to deliberate message/request fields. A future small local model may propose an intent, never execute or determine access. Fast common actions do not wait for Gemma.
- Every mutation has a client command ID and correlated server response. Show Sending until ack; a socket enqueue is not success. Server enforces recipient, role, task revision and valid transition. Do not label delivery Seen. Pending confirmations invalidate on disconnect, expiry or task revision change; reconnection does not replay an unconfirmed command. Unknown outcome after send asks for reconciliation, not blind resend.
- Task/message payloads go only to participants; observer gets bounded room/status/ownership metadata. Patient records require explicit care context. Cloud assistant route is disabled in the new server. No external AI dependencies in the active workflow, no clinical text/audio/model-result logs. Legacy code in the copied folder is not evidence of an enabled feature.
- Track live connections/heartbeats separately from availability; remove reachability when the last connection closes. Exact sender/recipient identities from authenticated session. Same-origin browser sockets, bounded input, loopback synthetic tokens, no public exposure. Production uses managed identity and WSS, not demo tokens.
- Current first milestone may remain in memory, labelled volatile; persistence, durable idempotency and restart recovery remain required before a reliable pilot. SQLite plus transactions/revisions, encrypted Android pending data, retention/access/audit policies come before real care. Do not imply local memory is an EHR.
- Meta speech/voice launch are currently experimental development/beta capabilities; production-channel support and vendor approval are deployment gates. On-device ASR does not prove vendor wake-word/account/telemetry traffic stays local; test WAN-off and inspect destinations. Retain §13 hospital deployment gates: platform permission, nurse/infection-control/PPE assessment, legal/security/regulatory intended-purpose review, managed fleet/identity, approved EHR integration, shadow trial, supervised pilot, rollback and support. No clinical-readiness or production-availability claim.

### Build sequence with checks at the right points

| Gate | Implementation | Required evidence before moving on |
|---|---|---|
| HF0 | Preserve original; separate identity/server; confirm Meta voice-launch/Speech/Motion access with mentor | Original file hashes unchanged; independent builds; no physical activation claim until cold/warm/locked-phone test succeeds |
| HF1 — first implementation | Pure local interaction controller, restricted parser, prompt-scoped head classifier; communication server with revisions/acks/reachability; official DAT renderer preview | Tests for negation, quoted message/status collision, unknown/ambiguous recipient, idle speech, timeout, late/duplicate confirmation, cancelled/stale request, unrelated/wrist/phone motion, drift/mixed axes, queued interruption and offline handling |
| HF2 — first simulator loop | Synthetic Wake/transcript/motion input into actual Android controller; official Chrome glasses preview; second nurse client | Request → draft → nod → ack → receipt → accept → complete; message review/send; negative shake/cancel; no task before confirm; no replay on reconnect. Label injected speech/head motion clearly. No hidden operator portrayed as a hands-free wearer |
| HF3 | Approved voice-launch adapter; actual glasses ASR; finite follow-ups; real Motion calibration | 20+ utterances across speakers, noise, negation and names; activation/recovery without touch; physical nod/shake vs walking/bending/look-around; count false activations, misses and latency, no claims from 10 model prompts |
| HF4 | One optical profile and confirmed patient context | ≥40 held-out scenes; actual glasses camera sees randomized screen pixels; exact tuple/rejection tests; patient switch/late-result tests; every save reviewed |
| HF5 | Persistence, privacy/lifecycle and conflict handling | Restart/reconcile and duplicate-command tests; WAN-off vs ward-link loss; sleep/fold/revoke/battery; no raw-content logs; no cloud fallback; 30-minute soak |
| HF6 | Nurse usability, comparison and rehearsals | Entire communication + capture demo without phone/temple/band touch after setup; quiet/voice-only alternatives; compare current phone/badge workflow; three rehearsals with failure recovery |

Target post-transcription response under 300 ms for local control and p95 server acknowledgement under 2 s on the local network. These are engineering targets, not measured results. Gesture thresholds and response windows are provisional and must be adjustable and physically evaluated. First work finishes HF1/HF2 as far as actual evidence permits, not the entire hospital product.

### Scope cuts and acceptance

Pause maps, fall detection/alerts, navigation arrows, broad record Q&A, all-day recording, generated narratives, translation, multiple-reader OCR, QR and Gen 2 until core communication plus one optical capture works. Keep Miko’s implementations in the preserved version for later reuse. No mandatory neural band or temple actions in the new core. New incoming requests do not steal the current confirmation. If voice activation is unavailable on the hardware, report a prototype limitation and use an explicitly labelled simulator/startup substitute; do not present it as solved.

The technical differentiator is bounded voice → exact visual review → explicit head/voice confirmation → acknowledged colleague action, plus first-person capture/verification. Communication alone can be served by a headset; the visual review and camera feature must demonstrate additional value.

### First implementation evidence and remaining work — 2026-11-14

HF0 separation and the first HF1/HF2 communication slice are implemented in `handsfree/` on `feat/handsfree-workflow`; **the hardware portion of HF0/HF3 remains open**. The pure Kotlin `HandsFreeEngine`/`HeadGestureDetector` run inside the new Android debug activity. A loopback ward server validates commands and revisions; no assistant/model is imported by its new entry point. Only final transcripts inside an active session can propose actions. Confirmation additionally waits for DAT to accept the exact review card. A request's incoming receipt is acknowledged by the client after its snapshot is received with display ready; this is not proof the wearer saw it.

Implemented commands: named request, outgoing reviewed message, review incoming request (optionally by room), accept/decline, complete, cancel draft, check latest requests. Rooms 1–99 accept digits or English number words. Unknown/ambiguous requests close the input window with a rephrase instruction; clarification dialogue and selecting between multiple same-room tasks are not implemented. No patient context is needed or granted for these generic requests. Availability commands, task expiry, incoming-message read/reply, patient records and observer mode remain planned. The first server has reachability but no automatic matching/routing.

Evidence: 26 Android unit tests; 21 server tests (9 new, 12 preserved); TypeScript check; debug APK and release Kotlin compile. The official Chrome Meta simulator rendered the running Android controller through DAT MockDeviceKit, with synthetic final transcripts and calibrated motion traces. Verified both outgoing/incoming request loops through completion, message confirmation, single-nod non-action/timeout and shake cancellation. This is **not** a live microphone, wake-word, raw IMU, optical or clinical test. It does not establish the latency targets or a hospital false-activation rate. README contains reproducible commands and test steps.

The physical launcher is deliberately a setup/limitations screen for this first milestone; only the debug preview currently connects this controller to a display. Inherited adapters remain copied for reuse but are not invoked. The real wake/Speech/Motion adapters, continuous sensor sampling, physical motion calibration and cooldown tuning are HF3 work. No microphone is opened by the new simulation. Debug transcript injection crosses localhost solely as labelled synthetic test data. Production hardware adapters will produce transcripts locally and send only reviewed structured ward commands; vendor traffic still requires verification.

First server state and command idempotency are volatile and capacity-bounded. Reconnect cancels drafts and never automatically replays commands. `check requests` supports manual reconciliation while that server session survives; durable recovery across restart, explicit pending-command reconciliation, hardware background/fold/revoke handling and a 30-minute soak remain HF5 gates. Simulated device loss fails closed; relaunch is currently needed. Do not describe these remaining gates as passed. No real patient data, cloud deployment or external publication was performed.

### Bedside capture implementation plan — 2026-11-14

This extends HF4 in `handsfree/` only; preserve the existing uncommitted prototype and do not push. Order: (1) verify DAT camera/display capability lifecycle; (2) implement pixel-only offline profile and frozen synthetic evaluation; (3) confirmed patient/encounter revision, bounded acquisition and exact review/correction controller; (4) authorized idempotent synthetic observations and separate participant-only attachment; (5) integrate official DAT preview, adversarial/regression checks and reproducible rehearsal.

Use DAT 1.0.1 `addCamera` alongside `addDisplay` on the same STARTED session. Stop/detach the camera without stopping the display. Inspect actual compiled API and upstream samples; physical concurrency/optics remains a gate. The official API website requires login in this environment; public upstream AGENTS/CHANGELOG and the sibling SDK provide the accessible reference.

First optical profile: **HOS-BP-1**, a deliberately synthetic axis-aligned rectangular monitor with SYS / DIA / PULSE labels, mmHg / bpm units, three seven-segment digit slots per row and visible border. A bundled deterministic glyph/segment reader consumes image pixels only, checks full labels/units/border and rejects nonbinary/uncertain strokes. No medical ranges, hidden fixture values, filenames, inferred digits, general medical images or cloud fallback. Three agreeing distinct frames over at least 400 ms, bounded to five seconds; timestamps describe capture, not measurement. Freeze development/held-out seeds before evaluating. Report narrow synthetic coverage, including rejected images, separately from physical-camera readiness.

Patient selection by full synthetic name or explicit patient identifier; review name plus ID, then `confirm patient`. Server issues a session-bound context revision. Changing patient, cancelling, suspension/disconnect or context revision invalidates capture/review. Capture is armed only after its camera-active card is accepted by DAT. Exact tuple review supports `correct systolic/diastolic/pulse <digits>`, `retry`, `cancel`, and `save reading`. Corrections create a new prompt and retain original extracted values plus changed fields. Save requires the current visible prompt; no offline queue or automatic retry. Unknown observation outcomes use the voice-accessible authored saved-record snapshot, with immutable capture IDs retained in records; they never produce a new save automatically. Communication commands additionally support actor-scoped receipt reconciliation.

Synthetic observation records are persisted by the concurrent local SQLite reliability implementation (or memory-only with `HF_DB_PATH=:memory:`), explicitly **not an EHR**, with actor, patient/encounter/context, immutable capture ID, captured/reviewed/saved times, profile/version, units, original tuple and correction provenance. Authoritative assignment checks and session context apply; duplicate capture IDs are idempotent and conflicting payloads rejected. Sharing creates a separately reviewed nurse request containing only that saved observation and patient identifiers needed by its two participants. No unrelated records, raw image, or patient data in generic requests. Incoming communication cannot replace an active capture or review.

Images stay in memory and are released after each frame and on every exit. Synthetic evaluation PNGs are test assets, not retained captures. DAT/account/transport diagnostics and Android/OS memory are outside an application-level zero-retention guarantee; SDK analytics/crash reporting opt-outs remain set. Hardware Voice Invocations, microphone and Motion are still separate gates; preview injects speech and camera pixels explicitly. Update README with exact evidence and remaining gates after integration.

### Core reliability pass — 2026-11-14 (software implemented; simulation evidence below)

Keep camera ownership separate. Before additional chart/QR features, harden software-only communication: durable local task/message/confirmed-observation state; actor-scoped command receipts across reconnect/restart; explicit reconciliation without replay; bounded request expiry; multiple-request selection and recipient-facing status; queued interruptions; simulated device recovery and malformed-input checks. Continue to label hardware wake/speech/Motion as unverified.

Use Node's built-in SQLite with an atomic transaction containing the ward snapshot and command receipt. Default local database lives in ignored `handsfree/.local/`, owner-readable/writable only. No new external package, cloud route or image persistence. This is unencrypted synthetic-demo storage, not approved clinical storage. Persist reviewed records and communication history, never microphone audio, images, active patient context, raw recognition transcripts or unconfirmed drafts. On corruption/write failure fail closed rather than silently starting a blank ward. Success acknowledgements follow commit. Read-only command lookup is actor-bound, including for a new session; lookup never executes a command. Patient confirmation is session-scoped and must be repeated after reconnect. Test restart, lost acknowledgement, duplicate/conflicting commands, outsider access, transaction failure and stale task updates before wiring into the active server.

Core reliability contract now being verified: unaccepted requests expire after two minutes; expiry never implies someone responded, and accepted work stays open until explicit completion. Voice can qualify review/completion/cancellation by room and colleague; ambiguous matches never choose automatically. `set me busy/available` requires `confirm status`, independent of live reachability. `read message [from name]` opens the oldest matching unread message; `reply <text>` reviews the recipient and content; `close message` explicitly marks it opened. Reply context ends on timeout/cancel/disconnect/new activation. Routine alerts cannot replace an active communication or camera review. `check outcome` and reconnect use actor-bound `command.status` lookup only. The preview stores just the opaque unresolved communication command ID across process death; no command replay or draft persistence. Device readiness is separate from an open socket; an unavailable headset must not accept mutations or advertise itself reachable.

Profile hardening before final evaluation: HOS-BP-1 requires **three visibly rendered digits in every row**, including literal leading zeroes (`080` is read as 80). A completely erased leading digit must not be mistaken for a shorter number. The initial variable-width synthetic trial is superseded. Final evaluation seeds are development 161115 and held-out 918274, fixed before running the revised profile; 150 development and 180 held-out scenes include an additional fully erased digit category. Earlier generated PNGs are retained but excluded by the final manifest. This change came from code review of the missing-digit ambiguity, not tuning against the new holdout.

### Bedside implementation evidence — 2026-11-14

The integrated synthetic HF4 software slice is implemented: local frame acquisition/normalization/glyph extraction/three-frame agreement, server-confirmed patient context, exact review and corrections, guarded explicit save, separately reviewed participant-only attachment, and injected-input official DAT display/camera rehearsals. README records reproducible commands, the frozen final 330-scene evaluation and runtime fault suites. Final heldout: 40/40 clear tuples exact, 140/140 adverse rejected, zero wrong proposals; 22.2% accepted coverage over the intentionally adverse-heavy full heldout, not a real optical accuracy claim. Patient switching, superseded encounter/assignment, cancellation, late results, review refresh, retry, offline no-queue, denied/unavailable camera and duplicate confirmation are checked. Camera/display coexistence works in MockDeviceKit on a single session; physical HF4, HF3 voice/motion and hospital deployment remain open. The physical launcher is still a setup/limitations surface, while the complete no-touch command sequence is exercised with injected speech in debug preview.


Core integration contract: the debug preview advertises reachability only when its authenticated connection, DAT display, foreground activity and local recovery storage are ready. Simulated device restoration re-enables fresh commands but never restores a discarded review. On background/device loss, input/capture/gesture jobs stop and patient context clears. This foreground-only debug policy is not the eventual production background service. Shared observation requests hand off to the ordinary request status after acknowledgement, so the wearer sees delivery, acceptance, expiry and completion without a stale bedside “awaiting response” card. A later review always takes precedence over asynchronous status updates. Exiting bedside mode restores microphone-off communication state.


Core simulation acceptance checkpoint: 69 Android / 42 server checks pass, plus the installed Android communication rehearsal and direct-frame failure suite. Save/share passes with both direct pixels and official DAT mock camera, including live recipient acceptance displayed through DAT. An intermittent bounded clear-capture timeout recovered on a fresh attempt without restart; retain this limitation and investigate display/acquisition timing before claiming repeatability under load. No physical HF3/HF4, 30-minute soak or hospital-readiness gate is marked passed.


### Live computer microphone demo — 2026-11-14

User-authorized next step: replace typed transcript injection with a real computer microphone, leaving original prototype untouched. Add an explicit, five-minute opt-in browser microphone session on localhost. Browser captures mono PCM, separates short utterances locally, and sends audio only to the local ward process for offline whisper.cpp English recognition. A standalone “Hospital” opens the existing command window; recognition alone cannot bypass exact review or confirmation. No browser cloud SpeechRecognition fallback. Audio is transient in memory; no audio/transcript logging or recording. Stop releases tracks; hiding the page, disconnect, errors and session expiry stop capture. Microphone availability is labelled separately from command-window activity. A transcription belonging to an older prompt/state is discarded. This is computer-microphone demo input, not glasses voice activation or a phone-native implementation. Test known synthetic speech and non-speech, prompt changes, cancellation, backend failure and actual browser permission/capture startup; do not claim human recognition accuracy without a human test.


Physical Display bridge checkpoint: user now has Display glasses and connected Android phone. Add Bluetooth permission → Meta AI registration → explicit physical Display session to the separate hands-free debug build. Reuse the same controller and DAT rendering acceptance gate, with USB-local ward connection and computer-microphone speech. Physical mode never enables MockDeviceKit; camera remains explicitly injected for this checkpoint. Do not claim physical Motion, real optical capture, native glasses activation or locked-phone/background operation. Keep the test activity foreground.

Registration checkpoint: the hands-free setup activity must expose its unique `hospitalos-handsfree` callback scheme and retain a single activity instance. Local builds use Meta developer registration; Developer Mode must be enabled for the linked glasses in Meta AI, independently of Android USB debugging. A link error is not proof of successful registration; require REGISTERED before starting the display.

### Hardware preparation while devices provision — 2026-11-14

Next software slice: connect physical mode exclusively to `DatCameraSource` (never substitute a synthetic frame on physical failure); expose camera/microphone permissions as deliberate setup actions; add a separate bounded DAT Speech adapter without the inherited cloud-capable phone fallback or transcript logging. The developer test offers explicit 60-second voice opt-in and standalone “Hospital” activation within that session, with exact rendered-card binding and explicit stop. This is not approved Hey Meta cold-launch integration or production ambient listening. Speech is experimental per the installed SDK guidance; capability availability and vendor processing still require hardware validation. Keep computer speech available as a separately selected input, never mix it with DAT speech. Simulation must exercise the same adapter via MockDeviceKit transcriptions. Start the five-second acquisition budget only after the camera indicator is accepted, with a separate bounded display wait and no deadline extension on repeated acceptance. Foreground-only lifecycle remains explicit; background service and physical motion calibration are separate work.

Hardware-preparation evidence: physical debug mode now selects only the DAT camera source; setup has explicit capability permission buttons. The new bounded DAT Speech adapter is integrated and passes a real SDK MockDeviceKit transcription/camera/display rehearsal, including device loss and explicit restart. 80 Android and 48 server tests pass; builds/TypeScript pass. The computer-recognizer audio rehearsal completed request/acceptance and patient capture/save/share after restoring the local decoder's fallback. Earlier rejected audio/timing attempts and remaining hardware limitations are recorded in README; these checks do not close physical HF3/HF4, background-service, motion-calibration or deployment gates. No clinical or physical speech-accuracy claim.

2026-11-14 — User requests a simpler physical demo: Ben sends one request, an idle wearer automatically sees its exact review, deliberate single nod accepts. Add experimental glasses-only DAT Motion gyro input, stationary baseline and prompt binding; never use phone or band sensors. Physical axis direction, noise/false acceptance and capability availability require wearer testing. Existing DAT glasses speech remains opt-in via phone setup; no computer microphone needed for nod acceptance.

Physical startup waits up to 30 seconds for REGISTERED plus connected, display-capable device metadata before creating a session. Timeout directs the wearer to Meta AI connection and Developer Mode; restarting the Android app is not evidence that glasses discovery succeeded.

2026-11-14 — Wearer reports physical nod acceptance works on the connected glasses. This is one successful live test, not a false-activation benchmark. Replace primary speech activation prompt with “Hey nurse”; retain standalone Hospital alias. Require exact phrase (allow transcription comma), never bare nurse or phrase embedded in conversation. Compare physical recognition before asserting improvement; 60-second explicit speech opt-in remains.

2026-11-14 — User selects Lumi as the system name and “Hey Lumi” as activation, replacing the uninstalled Hey nurse proposal. Exact phrase plus optional transcription comma; standalone Hospital remains a compatibility alias. Physical recognition reliability is still being evaluated.

2026-11-14 — Demo desk connects Aino control and Ben responder automatically, shows separate ward/display/responder/input states, reconnects without replaying commands, clears stale display cards and offers request/message actions, recent request responses, bedside instructions and saved records. Computer microphone and injected controls are explicitly labelled troubleshooting tools. Bluetooth pairing, permissions and session restart stay on the phone.

2026-11-14 — Quiet physical display requested: clearDisplay while idle; new incoming requests or message notices and explicit Hey Lumi activation reveal content. Uncommitted command expiry clears the display silently; result notices close after six seconds. Physical head shake dismisses without accepting, declining or marking messages read. Pending writes/reconciliation cannot be discarded by dismissal. Microphone opt-in remains bounded and is separate from display visibility; no claim of background wake after speech session ends.

2026-11-14 — User explicitly requests continuous listening: remove the 60-second cap from opt-in DAT glasses speech, retaining manual stop, foreground/readiness and disconnect cancellation. This is continuously renewed SDK recognition, not a verified low-power custom wake detector or a claim of phone-local vendor processing. Physical communication review/command windows become 20 seconds and notices 15 seconds; bedside safety deadlines remain unchanged. Locked-phone/background operation and battery/endurance remain to validate.

2026-11-14 — Physical shake failed a live test; replace wide cross-centre pattern with bounded relative turn-and-return dismissal, tested independently from nod acceptance. User requests nod-to-open messages; message notification has exact display/prompt gating, shake dismisses without marking read, and unread message remains accessible by voice. Hardware retest pending.

2026-11-14 — User selects simulated monitor on another screen for physical camera validation. Add standalone /monitor canvas with HOS-BP-1 optical glyphs, changeable values and full-screen mode. No socket or expected-value feed from this page to the reader. Physical extraction remains unverified until a real capture succeeds; no real medical-device accuracy claim.

Physical test: nod-to-open message confirmed by wearer; shake dismissal still failed. Revised shake adapter projects angular velocity onto measured gravity to identify yaw without fixed mounting-axis assumptions, rejecting unavailable/implausible gravity. Further hardware test required.

- 2026-11-14 — Physical shake dismissal: wearer confirmed notification dismissal but not open-message dismissal. Start the bounded gesture timer at movement onset, not card appearance, so reading pauses cannot split a turn-and-return; reset the shake accumulator after nod-to-open. Regression covers a shake near the former idle deadline. Hardware retest required.

- 2026-11-14 — Replace unreliable physical shake dismissal with user-requested left-ear-toward-shoulder tilt: neutral for 300 ms, 18–40 degrees left held for 350 ms with low motion. Exact rendered-card gating and pending-write protection remain. Nod actions unchanged; simulated shake remains a test input. Left-axis sign and usability require hardware validation.

- 2026-11-14 — User authorizes consistent physical controls: left tilt dismisses/back/later without declining or submitting; right tilt opens a message or confirms the explicitly labelled reviewed action (including patient, save and share reviews). Physical nod input removed. Each held tilt requires fresh upright rearming and exact rendered prompt; pending writes remain protected. Voice and labelled legacy simulation inputs remain. Both directions require wearer validation.

### Reviewed dictation and manual patient notes — 2026-11-14

User requests reliable arbitrary short messages and manual patient-record statements alongside camera capture. This chat owns `HandsFreeEngine`, `BedsideController`, speech adapters, message/note server protocol/persistence/tests and demo desk; hardware chat retains PreviewActivity, physical tilt adapter, phone install and shared ward runtime; camera chat retains capture files. No shared server/phone restart from this work.

Messages: support explicit recipient selection (`message Ben`) then short free dictation, plus existing one-utterance `tell Ben ...`; review exact text before send, permit replacement, preserve punctuation/numbers, reject oversize content without truncating. Message content that resembles a command is content inside dictation mode. Reviewed messages already persist in the local synthetic SQLite store; return message ID/time and show them to their participants.

Manual notes: only after confirmed assigned patient/encounter, `add patient note` then dictate, or `save to patient records that ...`. Display patient identity and verbatim note, then explicit `save note` or labelled right-tilt confirmation. Left dismiss never saves. Bound short content to 160 characters for a complete lens review; longer text must be rephrased, never silently shortened. Edits invalidate prior review. Preserve dose/time text exactly as recognized; do not normalize medication names, infer date/time zone, verify administration, or generate a care recommendation. Store a labelled author-reported manual note, with review/save timestamps and no inferred event timestamp, immutable draft ID and durable actor-bound command receipt. No camera/AI provenance for manual notes. Reconnect clears confirmed patient binding and never replays a save. Author-only assigned-patient snapshot, no automatic sharing; synthetic local records, not EHR write-back.

- 2026-11-14 — Physical tilt diagnostic run: live GLASSES samples and controller transitions showed left dismissal of message notice, right opening message, then left closing opened message. Both signs work in this run; not a false-activation or usability benchmark. Exact gates remain: neutral within 8 degrees for 300 ms, tilt 18–40 degrees held 350 ms, low angular speed, fresh rendered card. Temporary local debug telemetry includes motion vectors only, no audio or patient text.


### Optical camera follow-up — 2026-11-14

User requested finalization of physical capture against the standalone synthetic HOS-BP-1 screen. Camera work will replace exact axis-aligned normalization with bounded quadrilateral rectification and conservative stroke-interior sampling. Require a complete single panel, sufficient optical resolution and contrast, all labels/units and three unambiguous visible digits per row. Keep three-frame consensus and existing context/confirmation/retention guards; no expected-value channel or new OCR/cloud dependency. Add a separately seeded perspective/resampling optical simulation suite alongside the frozen adversarial evaluation. Passing transformed synthetic scenes does not close the real glasses optical gate. Capture chat owns capture files/tests; testing chat owns activity/phone/runtime, reliability chat owns engine/controller/server.

Optical follow-up evidence: original 330-scene regression retained. Additional perspective/JPEG/exposure development: 34/36 clear exact, 24/24 adverse rejected; frozen optical holdout: 38/40 clear exact, 80/80 adverse rejected, zero wrong proposals. Two clear rejects in each split are retained; no certainty relaxation after holdout. Geometry bounded to a single continuous quadrilateral, minimum resolution/contrast and <=25-degree roll; three-frame and exact patient/value confirmation guards unchanged. These are synthetic optical transforms, not real captured photographs. Real physical capture still requires testing by the wearer on the installed build.

Manual-note/message slice is implemented and released for integration: server protocol/durable storage/participant message history/author-only note snapshots; two-step message dictation and editable reviews; confirmed patient-bound verbatim note review, right-tilt action label, durable pending-save recovery; final-segment speech buffering with unchanged activation policy. 54 server tests and 13 new Android dictation tests pass; scoped controller checks, debug build, release Kotlin and TypeScript compile pass. Full combined camera tests still need the capture owner’s fixes. Hardware chat owns the SavedNote snapshot hook and combined build/install; this chat has not modified PreviewActivity, physical tilt, camera files or the active phone/shared server runtime.

- 2026-11-14 — Add reviewed request.broadcast: request N nurses to a room, sent to every connected available colleague except sender at send time. Server owns capacity and distinct acceptances, excludes busy/offline staff, closes unfilled invitations on quota/expiry; sender sees count/names. No patient content broadcast. Fewer recipients than requested is explicit, not fabricated staffing. Optional patient QR identifies only, requires same assigned-patient review; ZXing core 3.5.3 added for on-phone QR decoding, no network decoding or access grant.

- 2026-11-14 — Broadcast + optional QR integration verified: 122 Android and 59 server tests pass. SDK-mock-speech rehearsal routed one two-nurse request to Ben/Sara with 1/2 then 2/2 confirmed. Generated QR camera pixels decoded to patient review, required confirmation, then permitted bound BP capture. These are simulator checks, not physical QR optics. Demo now has Demo/Blood pressure/Patient QR tabs and labelled simulated Sara; updated APK installed on physical phone. Current hardware blocker: Meta AI openChannel failures awaiting reconnect, before Display discovery.

2026-11-14 — Wake troubleshooting: debug phone screen shows transient SDK speech partial/final status, confidence and route; wake-mode text is limited to 80 characters in memory only, with no transcript/audio logging. Command/dictation content is not included. This diagnoses physical activation failures without bypassing speech confidence or exact-card confirmation gates.

2026-11-14 — Physical wake diagnostic observed exact Hey lumi at confidence 1.0 reported as partial without activation. Permit exact standalone activation from a partial only at confidence >=0.9, with current ready/rendered prompt binding and no active command microphone. This may only open Listening; commands, dictation and record writes remain final-only and reviewed. One partial wake per recognition window; changed prompts invalidate the old result.

2026-11-14 — On controller startup, the first message snapshot establishes saved inbox history without replaying its notification cards. Messages remain unread and accessible through Read message; subsequent new IDs still notify. Reconnecting the existing controller does not repeat previously offered IDs. No records are deleted or marked read by this suppression.

2026-11-14 — SDK partial-only command workaround: latest hypothesis at confidence >=0.9 unchanged for 2 seconds may propose input only in listening/dictating-message/dictating-note. Mixed final+partial content is excluded. No partial fallback on review/message/ready cards: sending, acceptance, saving and confirmations require final speech or an exact-card right tilt. Stop recognition before proposing; reject changed windows. This is a stability heuristic, not SDK-final transcription; user reviews draft text.

2026-11-14 — Speech recovery refinement: stable-draft quiet time follows unchanged words, not confidence-score fluctuations; minimum confidence is 0.6, matching the existing final-speech gate. Exact partial wake uses that threshold and may refresh only the empty listening screen with a new prompt ID. Review, dictation and pending writes cannot be interrupted by partial wake. All partial proposals still require explicit reviewed confirmation.

2026-11-14 — User requests Listening remain visible: physical quiet-mode empty command window no longer auto-expires. Left tilt, cancel, foreground/device loss still exit. Refresh the same listening content every five seconds while glasses voice is active; the installed Display API has no keep-awake setting, so hardware visibility remains a live verification. Review/write/capture deadlines remain unchanged.

2026-11-14 — Keep physical message dictation visible until input or explicit back/cancel, with no 30-second controller dismissal; refresh dictation content as for Listening. Empty SDK transcription events do not overwrite words, reset stability time or block finalized text. Nonempty unfinished partials still block final-only commitment; reviewed draft fallback remains separate.

### Monitor presentation — 2026-11-14

Restyle `/monitor` as a physical bedside NIBP device with a recessed display, casing, power indicator, collapsed value controls and full-screen presentation. Preserve the exact 480×360 HOS-BP-1 optical drawing and current configured values. Start/Stop runs a three-second explicitly simulated measurement, covering the old reading during acquisition and displaying the configured values afterward; no cuff, patient or record service is connected. The camera layout remains unchanged, but physical optics still require testing.
