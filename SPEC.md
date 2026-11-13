# HospitalOS — product and implementation plan

> Authoritative plan, revised 2026-11-14. This replaces earlier feature priorities; historical decisions remain in CLAUDE.md. Implementation lives in `prototype/`. A planned capability is not an implemented or validated capability.

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
