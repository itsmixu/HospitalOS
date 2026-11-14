# HospitalOS

## Isolated next version: briefings and voice navigation

Branch `feat/handsfree-briefing-navigation` implements the first software slice of the next-steps plan in `SPEC.md`. It is based on committed hands-free version `bdec78b`; concurrent speech/hardware changes in the active test checkout are intentionally not included. Development and build output live in the separate `HospitalOS-next` worktree. This slice has not been installed on the shared phone or connected to the active simulator/ward.

New wearer commands, after enabling voice and activating Lumi as usual:

| Intent | Commands |
|---|---|
| Inspect an incoming request | `Show details` → `Next` / `Previous` → `Back` to a fresh acceptance review |
| Browse work | `Show requests`, `Show accepted requests`, `Show request history`, `Show all requests` |
| Find people | `Show patients`, `Who's available?` → `Next` / `Previous` → `Open` |
| Inspect a confirmed patient | `Show patient details`, `Show latest note`, `Show latest reading` |
| Browse messages | `Show messages` → `Open`; `Back` leaves unread, `Close message` explicitly marks an incoming message opened |
| Learn available commands | `What can I say?` |
| Dictate a message | `Send Ben a message` → dictate exact text → review → `Send message` |
| Request assistance | `Ask Ben for help in room twelve`; with confirmed patient context, `Get help here` reviews a one-nurse room-only broadcast |

Request details preserve the shared observation's exact values/units, patient identifiers, timestamp, correction fields and source. Capture time is not measurement freshness. Current patient assignment and encounter are checked for patient warnings and stored observations/notes; revoked request attachments are explicitly unavailable. Notes stay author-only. New detail/list screens are paged, use stable item IDs, and expire after a bounded command window. Returning from request details never reuses an old confirmation prompt.

Physical list controls use right tilt to open the exact rendered item and left tilt to go back. Those new interactions and their display layout still require hardware validation. This branch retains foreground-only session operation: phone locking/background support and the two-glasses physical rehearsal are the next unimplemented stages, not passed gates.

Software verification on 2026-11-14: **152 Android tests passed (30 new briefing/navigation checks)**; **62 server tests passed (3 new recipient-projection checks)**; TypeScript checks, debug APK assembly and release Kotlin compilation passed. Tests cover mapped attachment fidelity, review/accept/complete, revoked assignment/encounter, stale prompts, navigation changes, message read semantics, contextual help, dictation collisions, capture interruption and the existing regression suites. No new runtime or model dependency was added. These are software checks; no new hardware or optical-success claim is made.

Display-first nurse-to-nurse communication, with a bedside observation capture feature. Junction 2026, Meta challenge.

**Core:** request help or send a short reviewed message, see who received/accepted it, and explicitly complete the task. **Camera feature:** look at one supported instrument display, verify the extracted reading on the glasses, then optionally share it with the assigned colleague. No diagnosis or automatic clinical escalation.

- Display is the main demo; one pair plus a browser responder is sufficient, two Display pairs preferred.
- QR and Gen 2 audio remain optional and must not delay the main workflow.
- Local-first target: OCR on the phone; short speech recognition on-device or on a controlled local computer; no external AI required for the core demo.
- Communication can work without a patient encounter or camera. Patient-specific observations require explicit identification, review and permitted sharing.
- Long ambient recording and generative care notes are deferred. Structured records come from confirmed observations and staff updates.

**Current status:** the existing `prototype/` contains a working synthetic browser workflow and a buildable Android Display shell. It does **not yet** implement the full Display interaction, local speech, observation OCR, persistence or production identity. The new plan describes work to do, not functionality already delivered.

**Implementation plan:** [SPEC.md](SPEC.md) contains the complete product scope, interaction design, local-processing architecture, staged implementation and simulation gates, demo acceptance targets and hospital deployment path. Start with **§10 G0: hardware/workflow feasibility**, then build through G1–G7. [CLAUDE.md](CLAUDE.md) records the updated decisions.

**Hospital readiness:** the prototype uses synthetic data. A real deployment needs platform-use approval, nurse co-design, security/regulatory assessment, managed identity/devices, EHR integration and a supervised pilot; see SPEC §13. Existing connected monitors should use their direct integrations rather than camera transcription.

## Repo

| Folder | What |
|---|---|
| `prototype/app/` | Android companion app (Kotlin, Meta Wearables Device Access Toolkit) |
| `prototype/server/` | Local TypeScript/WebSocket ward service and browser responder |
| `prototype/demo-data/` | Fake staff, patients, floor plan, wristband QR codes, scenario |
| `research/` | Challenge brief, judging criteria, smart-glasses research |

## Setup

1. **Meta Android toolkit and samples:** official upstream <https://github.com/meta/meta-wearables-dat-android>, downloaded beside this repository at `../meta-wearables-dat-android` (checkout `f4371bc9f90249d51eb07d7f8a61e8d829294f16`). Do not commit upstream code here. Open `../meta-wearables-dat-android/samples/DisplayAccess` in Android Studio as the Display-first starting point; `samples/CameraAccess` is the optional camera reference. The Display sample pins DAT **1.0.1** and resolves its SDK libraries from Maven Central during Gradle sync; cloning alone does not download all build dependencies.
   - Upstream prerequisites: Android Studio Narwhal (2025.1.1)+, JDK 17+, Android SDK 36+. Use Android Studio's bundled JDK for Gradle.
   - Add your Wearables Developer Center application ID and client token to the sample's local `local.properties` (`mwdat_application_id`, `mwdat_client_token`); do not commit credentials.
   - Sync/build the sample, then test its display, input and audio paths on loan hardware before adapting it into `prototype/app/`. Download is verified; build, registration and hardware operation are not yet verified.
2. **Glasses:** enable Developer Mode in the Meta AI app (tap the app version 5 times). Without glasses, use the SDK's mock device kit.
3. **Secrets:** the optional environment template is `prototype/.env.example`. The current deterministic demo requires no AI key; reserve `ANTHROPIC_API_KEY` for later integration.
4. Use the development instructions below for the HospitalOS app and local ward server.

## Run the development foundation

From the repository root (Node 22+):

```sh
cd prototype
npm ci
npm run check
npm test
npm run dev
```

Open `http://127.0.0.1:8787`. The server prints fresh synthetic-only tokens for Aino, Ben and the ward observer. Open three browser tabs and connect each with its respective token. Tokens change on restart; all state is in memory. The server binds only to loopback by default. It does not load `.env` automatically; set `HOST`/`PORT` in the process environment if needed. Keep this demo off public networks.

1. As Aino, select and confirm Demo Patient A, check simulated consent and start a typed encounter.
2. Enter a patient report and planned action. Request Ben for assistance.
3. In Ben's tab, acknowledge delivery, accept, then explicitly confirm completion. Each transition is independent.
4. As Aino, prepare a draft, review/edit its fields and confirm the note. Observer sees room/task status without patient details. The payload panel shows exactly what that client received.
5. Disconnect Ben and make another request: it remains sent, never accepted. Reconnect to obtain current state. No client automatically replays requests.

This is typed/deterministic simulation, not ambient recording or an AI-generated clinical note. Drafts expire after 30 minutes; confirm/discard removes draft data server-side. Use synthetic information only. Access-denied controls remain visible in the development client so permission checks can be exercised.

## Android development

Open `prototype/app/` in Android Studio. Use its bundled JDK and Android SDK 36. The local, git-ignored `prototype/app/local.properties` contains the Android SDK path and DAT developer-mode IDs (`0`); production IDs belong in that local file, never Git. The public wrapper files come from Meta's sample; our application is independent.

From the repository root:

```sh
cd prototype/app
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :mobile:assembleDebug
```

The debug APK is `prototype/app/mobile/build/outputs/apk/debug/mobile-debug.apk`. For a USB-connected Android phone, from the repository root:

```sh
adb reverse tcp:8787 tcp:8787
adb install -r prototype/app/mobile/build/outputs/apk/debug/mobile-debug.apk
```

Use `ws://127.0.0.1:8787` in the phone app after USB reverse; the Android emulator uses `ws://10.0.2.2:8787`. Debug builds alone permit cleartext local traffic; this is not a production network configuration. SDK analytics/crash-reporting opt-outs are configured, without claiming device-wide privacy.

**Rehearsal tokens:** start the server with `DEMO_TOKENS=fixed npm run dev` to get stable tokens `aino-demo`, `ben-demo`, `observer-demo` (loopback only). Without it, tokens are random per restart, as before.

### Voice assistant — on-phone Gemma first

The assistant's "brain" runs **on the phone**: Gemma via Google's MediaPipe LLM Inference (`tasks-genai`). It turns the transcript into one JSON action, which the phone validates against ids the user may see and sends as a normal ward command — so the server's permission checks still apply, and only that command leaves the phone.

The model isn't in the APK (Gemma license + size). Download a MediaPipe `.task` model after accepting the Gemma terms — **demo default: `gemma-3n-E2B-it-int4.task`** (~3 GB, from Hugging Face `google/gemma-3n-E2B-it-litert-preview`; 10/10 on the built-in test, ~11–14 s per request). `gemma3-1b-it-int4.task` (~550 MB, `litert-community/Gemma3-1B-IT`) is ~3× faster but 7/10. The app loads the largest `.task` in the folder; rename the other to `.off`. Stage → **Test Gemma** reruns the 10-phrase test — then:

```sh
adb push gemma-3n-E2B-it-int4.task /sdcard/Android/data/fi.hospitalos.demo/files/
```

and tap **Load model** on the Stage tab. If no model is loaded, or Gemma returns nothing usable, the request falls back to the server assistant below.

### Server assistant fallback (Claude or rules)

Put an Anthropic API key in `prototype/.env` (git-ignored; copy `.env.example`): `ANTHROPIC_API_KEY=...`. `npm run dev` loads it. The key stays on the laptop server, never on the phone.

Speech is transcribed **on the glasses** (Meta DAT Speech, beta; allow the microphone in Meta AI via Connect → Voice). Meta has no language model, so the text goes to the server, where Claude (`claude-opus-5-5`, low effort) turns it into tool calls — request help (N free colleagues for "my room"), send a message, set status, open a patient, or answer from the patient's own record. **Every action runs through `ward.handle()` as the speaker**, so assignments and permissions are enforced by the server, not the model. Without a key (or if the API fails) a small rule parser handles the demo phrases: "two nurses to my room", "tell Ben …", "set me busy", "what did the patient report".

### The glasses (Meta Ray-Ban Display) — glasses-first

| Hub | Flow |
|---|---|
| Home | Your name + free/busy (tap to toggle) · Patients · Message · Requests · **Ask assistant** |
| Patients | List → card (tap card = back) → **Note** (consent → speak → draft → Save/Discard) · **Get help** (server picks a free assigned colleague, live status) · **Ask** (assistant with this patient as "my room") |
| Message | Pick colleague (shows free/busy) → speak → confirm → Send |
| Requests | Open requests, both directions, with real status (sent ≠ seen ≠ coming ≠ done, SPEC F1) |
| Interruptions | Incoming help request, new message, fall alert (red banner, map turns with your head) — pop up over anything, then return you to where you were |
| Offline | "Offline. Nothing is sent"; no automatic replay on reconnect |

### The phone app (stage remote only)

- **Connect** — five steps: ward server, Bluetooth, Meta AI, glasses display, voice (glasses mic in Meta AI; phone mic as backup).
- **Stage** — live mirror of the lens + live transcript, jump buttons, the assistant (Talk, or type a command as a stage fallback), demo events (fall alert, reset direction, staff map). Colleagues' requests/messages come from the browser at `http://127.0.0.1:8787` (log in as `ben-demo`).

Code: `mobile/src/main/java/fi/hospitalos/` — `ward/` (model + WebSocket client), `glasses/` (SDK link, voice, head tracking, screen flow, renderer, ward map), `ui/` (Compose phone screens). Server: `server/src/core.ts` (ward rules), `server/src/assistant.ts` (Claude + rule fallback).

**Simulated:** staff map positions and the fall itself. Spoken notes are saved as plans/observations; completed care still only comes from colleagues' explicit "Done".

## Next implementation steps

Follow the dependency-ordered implementation plan in [SPEC §10](SPEC.md#10-build-sequence-and-simulation-gates):

1. G0: actual Display/input/camera/microphone checks and nurse workflow validation.
2. G1–G2: persisted task/context contracts, simulation checks and a complete on-glasses communication loop.
3. G3–G4: local short speech and one supported observation parser, with recorded fixtures and held-out failure cases before hardware integration.
4. G5–G7: join confirmed observations to requests, verify privacy/offline recovery, run usability checks and rehearse the live demo.

The browser instructions above describe the existing typed foundation. They are not evidence that the new vision/speech milestones already work. Proposed time/accuracy targets in SPEC are acceptance goals, not measured results.

## Verified setup (2026-11-13)

- `npm run check`: passed.
- `npm test`: six checks passed, including real WebSocket auth/routing/reconnect, recipient filtering, explicit completion, consent/review, idempotent retry and encounter isolation.
- `npm audit --audit-level=high`: no reported vulnerabilities at setup time.
- `:mobile:assembleDebug`: passed using Android Studio's bundled Java 21 and SDK 36; debug APK generated.

## Verified on hardware (2026-11-14, Windows)

- Built on Windows, installed on an Android phone over USB with `adb reverse tcp:8787 tcp:8787`, connected to the local ward as Aino, registered with Meta AI (Developer Mode), and **Start Display showed the HospitalOS status card on real Meta Ray-Ban Display glasses**.
- Gotcha: newer Android Studio bundles **Java 25**, which Gradle 8.14 can't run on (build fails with just `25.0.4`). Point `JAVA_HOME` at a JDK 17/21 instead, e.g. on Windows `JAVA_HOME="$HOME/.jdks/jbr-21.0.11" ./gradlew :mobile:assembleDebug`.

## Preview the actual glasses UI locally

Debug builds include an official Meta DAT display simulator. It uses the same `Renderer`, screen state and click callbacks as the real glasses; the Stage tab's text summary is not the visual preview. The mock dependency and preview activity are excluded from release builds.

Start the synthetic ward server and configure the Android app's ward URL/token first. Then, with the emulator running and the debug APK installed:

```sh
adb shell am force-stop fi.hospitalos.demo
adb shell am start -n fi.hospitalos.demo/fi.hospitalos.preview.GlassesPreviewActivity
```

Click controls inside the square display to navigate. Home returns to the main glasses screen; Demo alert opens the existing simulated fall alert. The typed input beneath the display substitutes for voice. Use the browser responder as a second nurse. Close preview before returning to a real glasses session.

For the current Mac rehearsal, the server is at `http://127.0.0.1:8788`, and `adb reverse tcp:8787 tcp:8788` connects the emulator app's `ws://127.0.0.1:8787` to it. Use synthetic `aino-demo` / `ben-demo` rehearsal tokens only with `DEMO_TOKENS=fixed`. The preview does not prove real optics, brightness, microphone, head tracking, or camera behavior. Black is the simulator's transparent-area representation.

Meta's separate [Chrome simulator](https://chromewebstore.google.com/detail/jpjlmmodokemlepklkdbimceggpbjcll) supports Web Apps and DAT, but requires installing its browser extension. The in-app preview does not require that extension.

To use the official Chrome simulator with this same live Android renderer:

```sh
adb shell am force-stop fi.hospitalos.demo
adb shell am start -n fi.hospitalos.demo/fi.hospitalos.preview.GlassesPreviewActivity --ez chromePreview true
adb forward tcp:9000 tcp:9000
```

Open `http://127.0.0.1:9000/` in **Chrome**, then click the Meta Ray-Ban Display Simulator extension. The extension must be installed and enabled; the Codex in-app browser is not a substitute. Its background/brightness controls approximate see-through appearance, not real optical calibration. The SDK preview server runs only when explicitly requested by the debug launch flag and stops when the preview activity closes. Android port forwarding listens on the local computer; do not expose it to the network.

Verified locally on 2026-11-14: debug APK build, release Kotlin compilation (without the mock dependency), simulated Display STARTED, and Chrome clicks Home → Patients → Demo Patient A showing the existing patient card. Sensor and real-hardware behavior were not retested by this preview change.

## Hands-free version (separate work in progress)

`prototype/` remains Miko’s version. `handsfree/` is the separately launched voice/head-confirmation implementation; see SPEC §16 for the revised plan and validation gates. The new package is `fi.hospitalos.handsfree`; the ward runs on loopback port 8790. Synthetic inputs in a simulator are not proof of real speech, head gesture or wake-word operation.

### Run the hands-free milestone

The new launcher intentionally shows a setup/limitations screen on a physical phone. The working first slice is the **debug-only official DAT preview**, driven by the same local Kotlin controller that the future hardware adapters will call. It never starts the inherited assistant, phone speech fallback or map tracker.

```sh
cd handsfree
npm ci
ENABLE_SIMULATION=1 npm run dev
# In another terminal, from handsfree/app (JDK 17/21):
./gradlew :mobile:testDebugUnitTest :mobile:assembleDebug
adb install -r mobile/build/outputs/apk/debug/mobile-debug.apk
adb reverse tcp:8790 tcp:8790
adb shell pm grant fi.hospitalos.handsfree android.permission.BLUETOOTH_CONNECT
adb shell am start -n fi.hospitalos.handsfree/fi.hospitalos.handsfree.HandsFreePreviewActivity
adb forward tcp:9000 tcp:9001
```

Open **Chrome** at `http://127.0.0.1:9000/` for Meta's installed simulator extension; open `http://127.0.0.1:8790/` for the labelled synthetic test controls. The Android preview server is on device port 9001. In this session the extension attached on host port 9000 but not 9001; forwarding 9000 → 9001 resolves that. This temporarily switches the Chrome preview away from Miko's app; restore `adb forward tcp:9000 tcp:9000` to view his running preview again. Neither app's files or account are migrated.

1. In the lab, Connect tester (Aino), then Connect Ben. The lab is a **tester/responder**, not a proposed wearer interface.
2. Simulate wake; inject `Ask Ben to bring the hoist to room twelve`. Confirm that the lens shows recipient, room and task and **no request exists yet**. Double nod within eight seconds. The lab injects a time series through the actual detector, not an `accept=true` shortcut.
3. Ben's responder receives the request. Click Accept as Ben; Aino sees `Ben is coming`. Mark complete as Ben; the task becomes completed.
4. Ben asks Aino for help in room 7. On Aino: wake → `review request` → double nod (or `accept request`) → wake → `complete request` → `confirm completion`. No wearer-side display button, neural-band or temple action is needed.
5. Repeat a request with Shake head: Cancelled, nothing sent. A single nod must not send; let eight seconds pass and check timeout. Test `Tell Ben I'm busy` → `send message`: it is message content, not a presence change. Wake → `check requests` shows up to the latest three requests.

The eight-second review starts when the draft is prepared; confirmation only arms after DAT accepts that exact card for display. Simulator input is active only with `ENABLE_SIMULATION=1`, is limited to the same authenticated synthetic account, and cannot directly mutate ward records. Real physical display visibility/optics still require testing. The first grammar supports named colleagues, rooms 1–99 (digits or English words), exact review/send/complete/cancel phrases; it is intentionally not open-ended conversational AI.

The server binds only to loopback, uses synthetic demo tokens and loses state when restarted. Do not use real patient data. A restart or reconnect does not resend a command; an uncertain outcome must be checked, and durable reconciliation remains a later gate. `Simulate device loss` closes the controller; relaunch the preview to resume this first milestone. Physical recovery is not implemented. Closing/reopening the lab requires reconnecting the tester/Ben.

### Verified first milestone — 2026-11-14

- `npm run check` and `npm test` passed in `handsfree/`: **21 server tests**, including 9 new hands-free tests and 12 inherited regression tests. New transport tests exercise authentication, participant filtering, simulation enabled/disabled, and denial of ward mutations from tester clients.
- Android debug build, release Kotlin compilation and **26 local controller/gesture tests** passed. These cover final-only speech, negation, message-command collision, spoken room numbers, unrendered review, correlated acknowledgements, stale requests, disconnect, ambiguous requests, timeout, no replay, single nod, wrong prompt/source, mixed-axis/walking/gapped/out-of-order input and completion/decline.
- **Actual Android controller + Meta DAT mock display + installed Chrome simulator**: outgoing request/nod/delivery/Ben acceptance/completion; incoming review/nod/explicit completion; spoken-number conversion; single-nod non-action and timeout; shake cancellation; voice-confirmed message; final request status. Both tasks were visibly completed. Synthetic inputs substituted for wearer speech and physical head motion throughout. Screens were inspected in the official simulator, not only in the lab mirror.
- Original `prototype/` source files were compared against a before-work SHA-256 manifest and preserved. Earlier uncommitted preview changes were already present before this fork.

Next: wire approved Voice Invocations and DAT Speech to the controller; calibrate physical glasses Motion; test interruption, denied permission, folded/removed glasses and background/reconnect recovery. Then implement the confirmed-patient, single-instrument camera capture/review loop. Busy/free commands, incoming message read/reply, task expiry, observer UI, durable storage and hospital deployment remain planned, not implemented in this milestone. See SPEC §16 for acceptance gates.

### Bedside capture workflow — hands-free integrated prototype

Implemented in `handsfree/`: **confirm synthetic patient → bounded camera capture → local pixel extraction → exact review/correction → explicit save → optional separately reviewed request attachment**. `prototype/` is preserved. This is a synthetic record system, **not an EHR**. Voice, activation and head motion are injected in the debug preview; the physical launcher still lists the unfinished hardware adapters.

The supported instrument is **HOS-BP-1/v1**, a synthetic 480 × 360 panel with a complete rectangular border, visible SYS/DIA/PULSE and mmHg/bpm labels, and three visible seven-segment digits per field. Leading zeroes are drawn explicitly (`080` → 80). One axis-aligned panel may be located inside a larger image; rotation, perspective, other fonts, other instruments and real medical displays are unvalidated and may be rejected. A missing digit is not treated as a blank leading slot. No expected fixture value, filename, metadata, clinical range or language model supplies extraction results.

The pure Kotlin pipeline is `CameraSource → owned grayscale PixelFrame → PanelProcessor → MonitorReader → CapturePipeline → BedsideController → ward command`. Both direct injected frames and official DAT camera photos enter this same processing/review path. Three complete agreeing frames at distinct timestamps spanning at least 400 ms are required. A rejected frame resets agreement. Acquisition starts only after DAT accepts the camera-active card and ends within five seconds; success, cancel, timeout, context change, disconnect and suspension stop it. A result can only enter the review for its original capture ID and confirmed patient/encounter revision. `capturedAt` is the phone timestamp at the start of that bounded capture attempt, not a sensor exposure timestamp or instrument measurement time.

#### Run and rehearse

From the repository root, use Node >=22.16 and JDK 17/21:

```sh
cd handsfree
npm ci
npm run check
npm test
ENABLE_SIMULATION=1 npm run dev
# Optional isolated/disposable server: HF_DB_PATH=:memory: ENABLE_SIMULATION=1 npm run dev
```

In another terminal:

```sh
cd handsfree/app
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :mobile:testDebugUnitTest :mobile:assembleDebug :mobile:compileReleaseKotlin
adb install -r mobile/build/outputs/apk/debug/mobile-debug.apk
adb reverse tcp:8790 tcp:8790
adb shell pm grant fi.hospitalos.handsfree android.permission.BLUETOOTH_CONNECT
adb shell am force-stop fi.hospitalos.handsfree
adb shell am start -n fi.hospitalos.handsfree/fi.hospitalos.handsfree.HandsFreePreviewActivity
adb forward tcp:9000 tcp:9001
```

Open the installed official Meta simulator in Chrome at `http://127.0.0.1:9000/` and the test lab at `http://127.0.0.1:8790/`. Connect the Aino tester and Ben responder. Camera settings choose a synthetic seed and a clear/adverse image scenario. The controls are an explicitly labelled test driver, **not a claim of live speech or a hidden hands-free operator**.

1. Simulate wake, then inject `select patient demo-a` (or `select patient Demo Patient A`). The display presents the name and patient ID; a room alone cannot select a patient. Say `confirm patient` while that exact review is visible.
2. Say `capture reading`. The camera-active indicator appears before acquisition. After a complete stable extraction, the display shows patient, SYS/DIA in mmHg, pulse in bpm and **capture time**. Measurement time/freshness remains unknown.
3. Say `correct systolic 141`, `correct diastolic 82` or `correct pulse 77` to change a field. This creates a new exact review and labels the reading manually corrected; an earlier prompt cannot authorize it. Corrections use digits in this constrained transcript grammar. `retry` discards the candidate and acquires again; `cancel` discards it and clears patient context.
4. Say `save reading`, or use the existing prompt-bound double nod, after reviewing the exact values. The card remains pending until the ward acknowledges. Duplicate confirmation does not create another observation. Saving while offline is blocked; nothing queues or automatically resends. `check saved readings` provides a voice-accessible view of the latest authored saved reading after an uncertain outcome.
5. Sharing is optional: `share reading with Ben` → inspect Ben, patient, exact values/time and the request text → `send attachment`. Ben receives a separate request with only that observation. Sara sees no task or observation. Ben must be assigned to that patient. Incoming requests cannot steal the current observation confirmation.

Confirmation prompts last eight seconds; wake opens a fresh command window after a timeout. No clinical significance, current measurement, care completion or EHR write is inferred.

A repeatable integration driver injects the same inputs into the **running Android controller**, checks the actual DAT-accepted prompts and server records, and never sends a save/share command directly:

```sh
cd handsfree
node --import tsx server/test/rehearse-bedside.ts
node --import tsx server/test/rehearse-bedside-failures.ts
# Optional pause on the saved display, then Enter to continue:
INSPECT_SAVED=1 HOLD=1 node --import tsx server/test/rehearse-bedside.ts
```

To exercise the official DAT camera capability together with the display, restart only the hands-free app with:

```sh
adb shell am force-stop fi.hospitalos.handsfree
adb shell am start -n fi.hospitalos.handsfree/fi.hospitalos.handsfree.HandsFreePreviewActivity --ez datCamera true
# Run rehearse-bedside.ts again. Force-stop and relaunch without the flag for the direct-injection fault suite.
```

This mode uses `MockDeviceKit` photo/feed URIs, `addCamera`, `stream.start`, `capturePhoto`, and camera teardown on the **same STARTED session** that owns `addDisplay`. The photos are generated synthetic images; this is camera/display **SDK integration evidence**, not physical camera or optical validation. The debug feed MP4 is synthetic and only supplies the mock streaming lifecycle; extraction reads the selected mock photo pixels. Stopping the camera keeps the display/session attached.

#### Local processing, records and privacy

The only new app dependency for this workflow is official `com.meta.wearable:mwdat-camera:1.0.1`. The optical glyph/segment reader is bundled Kotlin code, with no OCR model download, general OCR SDK telemetry or cloud fallback. Python/Pillow and FFmpeg are fixture-generation tools, not runtime app dependencies. Inherited Gemma/assistant files are not invoked by the hands-free capture path.

The concurrent reliability implementation persists synthetic observations, tasks and command receipts atomically in local SQLite by default (`handsfree/.local/handsfree.sqlite`; `HF_DB_PATH` overrides it). Context confirmation remains connection-bound and is never restored from disk. Saved observations include authenticated reviewer, patient/encounter/revision, immutable capture ID, original and reviewed values, exact units, corrections, profile/source, capture/review/save timestamps, and null measurement time. They contain **no image**. Authoritative assignment checks protect saving and sharing; command and capture idempotency prevent duplicate records. This store does not have production encryption, EHR integration or a hospital identity/access policy.

Owned grayscale buffers are cleared after each frame; camera bitmaps are released, and no hardware-captured image is written to disk or sent to the ward. The official mock-camera adapter requires URI inputs, so **generated synthetic fixtures only** are briefly written to the debug cache and removed in cleanup; process termination can leave these synthetic fixture files until overwritten/app-cache removal. Frozen evaluation PNGs and the synthetic mock MP4 are intentionally retained test assets. Raw clinical text and image contents are not logged by the new pipeline. SDK/OS-owned buffers cannot be promised to be securely zeroed by application code. Existing DAT analytics and crash-reporting opt-outs are set, but Meta registration/account, device transport, OS diagnostics and firmware data flows still require vendor/network review. The loopback lab relays synthetic transcripts/cards only to the matching authenticated test actor; it is disabled unless `ENABLE_SIMULATION=1`.

#### Evidence and limits

The final fixed-slot evaluation is reproducible with `python3 handsfree/demo-data/monitor/generate.py` followed by `:mobile:testDebugUnitTest`. Seeds were frozen before evaluating the revised profile: development **161115**, held-out **918274**. The manifest records every image hash and expected tuple separately from extraction. The test feeds three copies of each rendered scene at distinct timestamps through the real processing/consensus pipeline; this tests stable scenes, not independent camera noise. Separate tests cover changing/rejected frames and duplicate timestamps.

| Final evaluation | Cases | Exact tuples proposed | Wrong tuples proposed | Rejected |
|---|---:|---:|---:|---:|
| Development clear | 80 | 80 | 0 | 0 |
| Development adverse/unsupported | 70 | 0 | 0 | 70 |
| Held-out clear | 40 | 40 | 0 | 0 |
| Held-out adverse/unsupported | 140 | 0 | 0 | 140 |

The held-out adverse set has 20 cases each of blur, glare, cropping, missing labels, ambiguous strokes, unsupported title/layout and fully erased digit. Across **all 180 held-out cases**, useful-read coverage is **22.2%**, rejection **77.8%**, and wrong proposals **0/180**; accepted clear tuples match **40/40**. These are deliberately narrow synthetic results, not a real-display accuracy estimate. The initial variable-width trial was superseded after code review found that an erased leading digit could resemble a blank slot; final fixed-slot images use a fresh holdout seed. Earlier images remain outside the final manifest. No pixel reader can detect every corruption that turns one fully valid visible digit into another; user verification and physical validation remain necessary.

Detailed counts are in `handsfree/demo-data/monitor/evaluation.csv`; test sources are `BedsideControllerTest.kt`, `MonitorReaderTest.kt`, and `server/test/observations.test.ts`. Official simulator rehearsal scripts live beside the server tests. The final run evidence below records the actual executed checks rather than hardware promises.

Official capability evidence checked 2026-11-14: [upstream Android guidance](https://github.com/meta/meta-wearables-dat-android/blob/main/AGENTS.md), [DAT changelog](https://github.com/meta/meta-wearables-dat-android/blob/main/CHANGELOG.md), and sibling checkout `f4371bc9f90249d51eb07d7f8a61e8d829294f16`, especially CameraAccess camera lifecycle/permission/photo handling. The official hosted API reference required login; compiled 1.0.1 APIs and the official mock run provided the accessible integration check.

Remaining gates: physical camera/display coexistence and camera indicator behavior; actual randomized screen photographed through glasses at working distances/lighting; lens orientation, blur/glare and optical false-read tests; approved Voice Invocations, actual glasses microphone/local transcription and calibrated Motion; no-touch cold/warm/locked-phone activation; permission revocation/fold/doff/battery/background recovery; WAN-off/vendor-traffic inspection; retention/encryption/managed hospital identity; platform healthcare/distribution approval, hospital privacy/security/intended-purpose review, nurse usability and supervised validation, and a separately authorized EHR connector. No real patient, diagnosis, production deployment or real-glasses validation is claimed.

### Core reliability update — current hands-free behavior

The active `handsfree/` server now saves synthetic requests, messages, declared availability, confirmed observations and command outcomes in **local SQLite**. The default file is `handsfree/.local/handsfree.sqlite`, ignored by Git and restricted to its owner. Run with Node 22.16 or newer (verified here on 26.3). `HF_DB_PATH=:memory:` intentionally disables persistence for disposable tests. The database is not encrypted or an approved clinical record store; keep all demo data synthetic. Patient confirmation, image frames and unconfirmed drafts are never restored from it.

A successful response follows the database commit. Restarting does not erase accepted tasks. Reconnect asks for the previous command's recorded outcome instead of resending it; an unknown result stays explicit. Only the authenticated sender can look up their own command. The preview retains an opaque unresolved communication command ID across app restart, not its text or patient details. After a server restart, confirm the patient again before a new observation. Invalid/unwritable storage stops actions rather than silently opening an empty ward.

Additional exact voice phrases (after activation):

| Action | Example |
|---|---|
| Select one incoming request | `review request in room twelve from Ben` |
| Complete one accepted request | `complete request in room twelve from Ben`, then `confirm completion` |
| Cancel your unanswered request | `cancel request in room twelve to Ben`, then `confirm cancellation` |
| Set availability | `set me busy` or `set me available`, then `confirm status` |
| Read an incoming message | `read message` or `read message from Ben` |
| Reply during its open window | `reply I am on my way`, then `send message` |
| Finish reading | `close message` |
| Recover an uncertain send | `check outcome` (a read-only lookup) |

Named requests can still be sent to a connected colleague marked busy; the review flags that status. Availability is not connectivity. Unaccepted requests expire after two minutes; accepted work requires explicit completion. Ambiguous request selection never guesses. Message replies lose their recipient context on timeout, cancel, disconnect or a new activation. Incoming alerts wait behind active dictation/review/capture.

Tests include actual server process crash/restart, stale revisions, conflicting duplicates, participant filtering, SQLite transaction rollback, malformed authentication, and 100 complete request lifecycles with a database reopen. These checks establish software behavior in a synthetic environment, not clinical reliability or physical speech/gesture accuracy. The hardware activation/Speech/Motion and production background-service gates remain open.

Capture verification executed on 2026-11-14: **69 Android tests passed** (26 inherited controller/gesture, 19 bedside, 3 pixel/consensus, 21 communication reliability), debug APK assembled and release Kotlin compiled. The final server run passed **42 tests**, including observation authorization/idempotency and durable restart tests. The live direct-frame fault driver passed blur, glare, crop, missing label, ambiguous digit, simulated permission denial, unavailable camera, patient switching/late frames, cancellation and retry, with no observations saved. Complete final direct-frame and DAT mock-camera rehearsals passed correction, save/duplicate confirmation, separate attachment, participant filtering and Ben acceptance, including the glasses changing to “Ben is coming.” TypeScript checks also passed. Speech and camera inputs were injected throughout. One final clear-frame attempt after the fault suite hit the bounded five-second timeout and saved nothing; an immediate retry passed without restarting the app or server. The cause is unconfirmed and timing reliability remains an open gate. Camera-active and sent-to-Ben cards were visually inspected in the official Chrome simulator. The final multi-line saved card was also visually verified: patient, exact values/units, capture time, unknown freshness, correction label and next actions all fit. Screenshots: `handsfree/demo-data/monitor/simulator-saved.png` and `handsfree/demo-data/monitor/simulator-accepted.png`. Detailed machine-readable evidence is in `handsfree/demo-data/monitor/verification.json`. The 45 pre-existing prototype source/config files matched the before-work SHA-256 manifest exactly. No push was performed.


### Repeatable core rehearsal

With the local server and Android debug preview running, run:

```sh
cd handsfree
node --import tsx server/test/rehearse-core.ts
```

This drives the actual Android controller using labelled simulated speech/motion. It checks a named outgoing request through delivery, acceptance and completion; single-nod non-action; recipient/room-qualified incoming acceptance and completion; message reading and reviewed replies; busy/available status; device loss, restored reachability and rejection of stale confirmations. It creates synthetic rehearsal tasks/messages in the local demo store. The test lab also offers Ben-to-Aino messages and explicit device restoration.

Reachability now requires authenticated ward connection, DAT display readiness, foreground debug activity and working local recovery storage. A socket alone does not mean a nurse can receive requests. Backgrounding this debug app stops input and capture; a hospital-ready background service remains a separate hardware/lifecycle gate. Returning from bedside mode always closes its listening window. A shared reading now follows the ordinary live request status, including **“Ben is coming”** after explicit acceptance. Saving a reading still never implies that another nurse has accepted it.


Final core verification, 2026-11-14: **69 Android tests and 42 server tests passed**, TypeScript checked, debug APK assembled and release Kotlin compiled. The actual Android/DAT communication rehearsal and camera fault driver passed. Both direct-frame and DAT mock-camera save/share rehearsals passed, including the live **Ben is coming** card on glasses. The official Chrome simulator was visually checked for full saved-record content and final acceptance; see `handsfree/demo-data/monitor/simulator-accepted.png`. All 45 original prototype source/config hashes remain unchanged.

One direct-frame clear capture after the fault suite reached its bounded five-second timeout, saved nothing, and passed on the next attempt without restarting. The cause is not established; delayed display acceptance consuming part of the acquisition window is a plausible timing factor. Do not describe this as a zero-timeout or long-duration soak result. Actual voice activation, local microphone transcription, physical head motion, locked-phone/background operation and clinical deployment remain unverified in this fork.

### Hardware-preparation update — 2026-11-14 (supersedes earlier adapter status)

The separate `handsfree/` debug build now has actual DAT camera and Speech wiring. **Physical mode never uses generated camera frames:** it checks the camera permission and reads through the same live DAT session as Display. A denial, unsupported capability or failed capture produces an error, with no synthetic fallback. The HOS-BP-1 reader is still a narrow synthetic-profile reader; successful optical extraction on physical glasses remains unverified.

Setup is Bluetooth → Meta AI registration (Developer Mode required) → optional Camera/Microphone permission → Start glasses display. The unique `hospitalos-handsfree` callback is configured. Permission alone does not start a sensor. The physical test screen mirrors the current card and displays session errors rather than showing “Connecting” after a successful connection. Keep USB attached and this test screen foreground: locked-phone/background operation is still outstanding.

**Glasses voice:** after permission/setup, choose **Enable glasses voice · 60 seconds** once on the phone. Say **Hospital**, pause until the listening cue, then one command. Each final transcript is bound to the exact DAT-accepted card at recognition start; partial results, changed/unrendered reviews and low reported confidence cannot execute commands. Short recognition turns restart only inside the explicitly enabled 60-second demo session. **Stop listening**, the Stop button, expiry, errors or device loss end it; reconnect does not reopen the microphone. A Preparing microphone cue distinguishes rearming from readiness. Computer input and DAT Speech are mutually exclusive. The adapter uses Meta's experimental Speech capability, with no automatic Android/cloud recognition fallback and no application transcript logging. Hardware speech availability, language/accuracy and vendor processing must still be validated. This bounded demo activation is not Hey Meta/Voice Invocations or a production all-shift wake-word implementation.

**Computer voice:** run these commands from `handsfree/` on this Mac:

```sh
sh server/speech/setup-macos.sh
ENABLE_SIMULATION=1 ENABLE_LOCAL_SPEECH=1 npm run dev
```

Open `http://127.0.0.1:8790`, connect the tester, then explicitly enable the computer microphone and grant browser permission. Say **Hospital**, pause for Listening, then a command; confirmations are separate utterances. The session stops after five minutes, on Stop listening, page hiding/disconnection or recognition failure. Audio goes only to the loopback ward process and offline whisper.cpp English model over an authenticated endpoint; memory buffers are cleared, no live recording is written and there is no cloud speech fallback. Model installation requires a download once. This is laptop processing, not phone-only processing. Retained `.local/speech-check/` audio is generated synthetic test speech, never microphone recordings.

**Timing:** the camera indicator has an eight-second maximum display wait. Its first accepted rendering starts a five-second acquisition budget. Repeated render acceptance cannot extend it. The speech adapter waits for its new SDK recognition turn before claiming readiness. Display-send failures retry at a bounded interval; draft/confirmation gates remain authoritative.

Verification after this update: **80 Android unit tests and 48 server tests pass**, TypeScript check, debug APK and release Kotlin compile succeed. `rehearse-sdk-speech.ts` passed official MockDeviceKit speech partial/final handling → request review/send → colleague acceptance → patient confirmation → same-session DAT mock-camera extraction → reviewed save, plus spoken stop/late-result rejection and device loss/no automatic microphone restart. This injects SDK transcriptions, not microphone audio. `rehearse-voice.ts` separately passed generated English audio through the actual offline recognizer and Android controller through request acceptance and patient capture/save/share, without typed-command substitution; the final successful run's transcription calls were about 0.85–2.57 seconds, excluding speaking and end-of-utterance detection. Browser microphone permission/capture startup was also exercised previously. These are not human/noisy-ward accuracy tests.

Earlier audio attempts exposed decoder repetition (fixed by restoring whisper's local decoder fallback) and one stale review rejection; neither submitted the rejected action. The SDK rehearsal also exposed a turn-transition timing race; an explicit per-card readiness signal now gates speech cues and the rehearsal. Do not claim three consecutive fault-free physical rehearsals or a completed soak. Physical optics/audio, device folding/removal, phone locking/background service, real head-motion calibration and approved voice launch remain open.

Reproduce against a separately launched emulator ward with `HF_PORT=8791 HF_DB_PATH=:memory: ENABLE_SIMULATION=1 ENABLE_LOCAL_SPEECH=1 npm run dev`, USB reverse for 8791, and the preview activity's `wardPort=8791` / `datCamera=true` extras. Then run **sequentially**:

```sh
HF_URL=ws://127.0.0.1:8791 node --import tsx server/test/rehearse-sdk-speech.ts
HF_HTTP=http://127.0.0.1:8791 node --import tsx server/test/rehearse-voice.ts
HF_URL=ws://127.0.0.1:8791 node --import tsx server/test/rehearse-core.ts
```

The audio rehearsal needs macOS `say`, ffmpeg and the local whisper setup. All data remains synthetic. The physical phone uses the normal ward on 8790, isolated from these destructive-in-memory test resets. Miko's original 45 source/config file hashes remain unchanged.

Final regression after disconnect-ordering fix: the full communication rehearsal and SDK speech/camera rehearsal both pass on the final APK. The phone reported an authenticated, foreground, DAT-ready physical headset on the normal ward before installation of this update; this establishes app-side display connection readiness, not wearer-verified optics or physical audio/camera success.

### Physical synthetic-screen optics follow-up — 2026-11-14

The HOS-BP-1 reader now locates one complete four-sided border and applies a projective warp before reading. It accepts modest roll/perspective, resampling and global exposure changes. It requires sufficient visible resolution (at least 300 × 220 pixels along the detected border in the analysis image), complete margins, continuous edges and adequate contrast; roll beyond 25 degrees is unsupported. Stroke interiors tolerate antialiasing at printed edges, but every segment must remain clearly dark or light. Labels, units, all three visible digits per row and three-frame agreement remain mandatory. No digit repair, expected-value channel, cloud fallback or new runtime dependency was added.

An additional independent synthetic optical suite uses frozen seeds **113708** (development) and **672941** (held-out), chosen before evaluation. It applies perspective, ±12-degree roll, screen scaling, JPEG compression and exposure changes to randomized screen values. Development: **34/36 clear exact**, 2 clear rejected, **24/24 adverse rejected**, zero wrong. Untouched optical holdout: **38/40 clear exact**, 2 clear rejected, **80/80 adverse rejected**, zero wrong. Thus optical held-out clear acceptance is **95%**, overall acceptance **31.7%**, rejection **68.3%** across 120 intentionally adverse-heavy scenes. No threshold was changed after inspecting that holdout. The original 330-image suite still passes unchanged (all 120 clear exact, all 210 adverse rejected). These are transformed synthetic images, not photographs or a physical-camera accuracy estimate. Each evaluation scene is repeated at three timestamps, not three independently noisy exposures.

Reproduce with `python3 handsfree/demo-data/monitor/generate_optical.py` (Pillow only) then the existing Android unit-test command. The original generator remains `generate.py`; both retain separate manifests/oracles outside the decoder. Detailed results: `handsfree/demo-data/monitor/optical-development-evaluation.csv` and `optical-heldout-evaluation.csv`.

For the next real-glasses test, open the local ward's `/monitor` page on another screen, use full screen, show the complete border with a light margin, keep the screen large in view and minimize glare. Choose new values independently on that page; it sends no expected values to the app. Confirm the synthetic patient, say “capture reading,” and compare every displayed value and unit with the screen before explicitly saving. A rejection is expected when resolution, focus or lighting is inadequate. Camera/display transport, actual photographed extraction, repeatability, the five-second acquisition budget and physical save/share still need a wearer-run validation of this updated slice; no phone install or real-camera success is claimed by the optical unit tests.

### Lumi short-message dictation and manual notes — 2026-11-14

Glasses voice now accepts **“Message Ben”**, then a short freely dictated message. It shows the exact recognized content and recipient; **“Send message”** or the labelled right tilt sends only that rendered review. **“Edit message”** starts replacement dictation; **“Replace message …”** supplies a replacement directly. Existing “Tell Ben …” and “Reply …” continue to work. Punctuation and numbers are preserved, command-like words inside dictation stay message content, and content over 160 characters is rejected without truncation. The server returns a durable message ID and send timestamp. Demo desk shows participant-only saved message history, supports a custom Ben message and explicit marking opened.

Confirm a synthetic patient first, then say **“Save to patient records that I gave 400mg of ibuprofen at 12:00.”** Alternatively say **“Add patient note”**, then dictate. Review patient ID and exact text before **“Save note”** / right tilt. **“Edit note”** or **“Replace note …”** invalidates the old review. Left dismissal or cancellation saves nothing. These are author-reported manual notes: the application does not verify administration, normalize dose or drug names, or infer an event date/time zone. It retains the time expression in verbatim text and stores review/save timestamps separately; `eventTime` remains null. Maximum 160 characters keeps the complete short note reviewable; long notes require rephrasing. Notes are private to their author with current patient assignment and are not automatically attached or shared.

`note.save` requires the current confirmed patient/encounter/context revision, an immutable draft ID and review time. The local SQLite transaction stores the note and actor-bound command receipt together. Duplicate/conflicting submissions, unauthorized/stale patient binding and malformed provenance are checked. Pending note saves use the same opaque-ID recovery and read-only command-status lookup as communication; reconnect never replays a write. Existing database snapshots without notes load with an empty note collection. The glasses’ **“Check saved notes”** uses the `SavedNote` snapshot hook; the hardware chat owns that final Preview wiring and phone install.

DAT speech now collects finalized segments until an 850 ms quiet gap rather than stopping at the first finalized fragment. Cumulative finals are deduplicated, unfinished partial results cannot commit, oversize content is not truncated, and every result remains bound to its original displayed card. This is a segmentation improvement, not evidence of improved physical recognition accuracy. Existing continuous user-enabled Hey Lumi listening and manual stop remain; no new cloud recognizer or model dependency.

Validation for this slice: **54/54 server tests pass**, including six manual-note durability/authorization/idempotency/provenance tests; **13 new Android dictation/note/buffer tests pass**, and the scoped controller/speech/bedside test build plus debug assembly and release Kotlin compile pass. TypeScript and demo-page script syntax checks pass. A concurrent full Android run had three failures in capture-owned MonitorReaderTest (frozen evaluation, bad-frame agreement, temporal consensus); combined camera validation is assigned to the camera/hardware chats. No phone installation or shared server restart was performed by this slice, and it has not yet been tested as a complete physical dictation flow.

### Guided Lumi experience (local, unpublished)

`handsfree/web-demo/` now opens the guided **blood-pressure / medication / Code Blue** walkthrough. The visitor scans the patient's wristband, holds right to confirm, holds left to decline Ben's request, captures 145/95 with pulse 88, dictates the video’s exact medication note, explicitly saves both reviewed records, then accepts the emergency and reads ISBAR while moving toward Room 8. Allow roughly 90 seconds at a natural reading pace; decisions wait for the visitor.

```sh
cd handsfree/web-demo
npm ci
npm run typecheck
npm test
npm run build
PORT=8805 npm run dev
```

Open **http://127.0.0.1:8805/**. Tap the suggested action or press **Space**. Hold **← / →** or the matching on-screen button for a simulated tilt; release early to cancel. **About → Reduce movement** disables camera travel. Restart and the final replay button create a clean session. The final **View saved records** panel shows the actual local patient binding, two saved records, Ben's receipt and emergency response.

The browser reuses `HandsFreeWard`, `Observations`, `PatientNotes` and `Emergencies`. Its instance-only adapter supplies the acted video cast without changing Android fixtures. Both records remain pending until review; the combined save performs **two separately acknowledged writes**, preserves the successful receipt on a partial failure and does not claim an atomic backend transaction. No microphone, camera, account, API key, analytics or remote patient service is used. Assets are served locally; patient data exists only in that tab's memory and replay clears it.

The scene uses a vendored CC0 rigged human (Innerscene / MakeHuman; source and license beside the GLB), modelled hospital equipment and a wristband parented to the actual wrist bone. Display content comes from the v6 fixtures used in the v7 video. Official Meta MRBD styles and notices are vendored; this is a **standalone browser presentation**, not Meta's SDK simulator. The original ECG storyboard is preserved at `/film.html`, and the earlier explorable ward remains at `/playground.html`.

**Netlify is prepared, not published.** Root `netlify.toml` selects `handsfree/web-demo`, Node 22, `npm run typecheck && npm run build`, and `dist`. The nested configuration supports selecting that directory directly. The bundle includes the 3D assets; it does not include the 133 MB reference video. No environment secrets or backend are needed. Run the local checks above before a later authorized deployment.

Verification (2026-11-14): clean `npm ci`, strict TypeScript checking, all **23 tests** and static build pass. Browser checks cover the complete workflow, partial keyboard/pointer holds, review cancellation, a held save spanning the emergency prompt, exact saved-note wording, desktop/phone/compact-phone layouts and reduced movement. The note card keeps a fixed footprint while its 19 words appear. Existing app/server sources were not modified; only a new synthetic scenario fixture was added under `prototype/demo-data/`.

### Patient information and Code Blue (integration build)

The `handsfree/` Android app now opens the patient overview after wristband/selection confirmation: admission reason, allergies, prescriptions and authored care history. Say **Next / Previous** to page, **Capture reading** for the supported HOS-BP-1 monitor, or **Save to patient records that …** for an exact, reviewed note. Reading and note writes still require explicit confirmation.

On the local demo desk, **Code Blue · room 8** sends Ben's synthetic emergency to connected colleagues, including busy staff. **Right tilt** acknowledges and opens the staff-written ISBAR; **left tilt** declines an incoming alert. In the briefing, right advances and left closes. **Hey Lumi → Show ISBAR** reopens an active briefing. **Resolve Code Blue** is the reporter's separate action; closing a briefing never resolves the emergency. Alerts expose no ISBAR before acknowledgement and do not grant full patient-record access.

Rehearsal: start an isolated ward with `ENABLE_SIMULATION=1 HF_PORT=8793 HF_DB_PATH=/tmp/lumi-video-rehearsal.sqlite npm run dev` from `handsfree/`, reverse the same port to the emulator, and launch `HandsFreePreviewActivity` with `wardPort=8793`, `datCamera=true`, `wearableDemo=true`, `actor=aino`. Run `HF_URL=ws://127.0.0.1:8793 node --import tsx server/test/rehearse-video.ts`. This exercises the real Kotlin controller and official DAT mock services using synthetic speech, head traces and camera pixels; it does not validate physical glasses or hospital use.

### Tilt-progress feedback

Actual DAT cards now show fixed edge rails: green right for the available action, coral left for declining an incoming request, neutral left for back/later. Progress follows measured head roll from the existing 8° neutral boundary to the 18° threshold; the existing stable hold and upright rearm still govern actions. Returning upright retreats without acting. Prompt changes, stale samples and disconnects clear feedback.

In the demo desk's **Simulator → Tilt feedback checks**, use partial-right/left, brief-right/left cancellation and held-right traces. These feed the same detector used by physical glasses. For the automated SDK rehearsal, launch the Android mock display against an isolated ward, then run from `handsfree/`:

```sh
HF_URL=ws://127.0.0.1:8796 node --import tsx server/test/rehearse-tilt-feedback.ts
```

Optionally set `ADB` to the local adb executable to capture green/coral/neutral screenshots from emulator-5554 in `/tmp/lumi-*-progress.png`. The rehearsal creates synthetic requests; use an isolated test database. Current verification: 191 Android tests, 69 server tests, TypeScript checking, APK assembly, release Kotlin compilation and the complete DAT tilt rehearsal pass. This is simulator evidence; physical glasses remain a separate check.
## Lumi video and guided-demo handoff

The current filmed reference is [Lumi v7](demo-video/lumi-demo-v7.mp4), a silent 89-second hackathon role-play with composited Display cards. Run `git lfs pull` after checkout to download the video assets. The final export, silent source footage, transparent overlay and three-second intro live in `demo-video/`; earlier local exports remain outside Git.

- [Visual preview](demo-video/preview.jpg)
- [Final 89-second scene timings](prototype/demo-data/lumi-video-final-v6.json)
- [Exact cards, original-footage timings and word reveals](prototype/demo-data/lumi-video-v6.json)
- [Narration and nurse-dialogue cues](demo-video/voiceover-v6.json)
- [Deterministic overlay renderer](demo-video/render_overlay.py)

The intro reads **Your hands save lives. Keep them free.** The outro reads **Lumi — Focusing on the patient.** One patient overview stays visible after QR confirmation; it has no page counter. Medication wording is **40mg Orion Furosemide 1x tab**. The log reveals words inside a fixed-size card. Right tilt confirms, accepts or saves; left declines a request and otherwise goes back. Filmed bars are editorially aligned to the head movements, not recorded sensor telemetry.

For editing, place `demo-video/source-silent.mp4` and `demo-video/lumi-overlay-v6.mov` at 00:03 after `demo-video/intro.mp4`. The alpha track provides the closing fade/title. To regenerate the 86-second body on macOS, install Python Pillow, NumPy and OpenCV plus ffmpeg, then run `python3 demo-video/render_overlay.py --output /tmp/lumi-body.mp4`; use `--alpha` for an editable MOV. The renderer currently uses macOS Arial fonts. Prepend the three-second intro for the final 89-second version. No location audio is included.

The default `handsfree/web-demo/` entry now implements the guided interactive blood-pressure flow described above. The previous ECG film and playground are archived at separate routes. The v7 video remains the visual reference; Netlify publication is still deferred.

Version 7 preserves the full v6 scenario and every cue time, adding a gentle transition: the intro fades out at 00:02.45–00:03.00, then the footage fades in at 00:03.00–00:03.80. Regenerate this pass with `python3 demo-video/add_intro_fade.py`; it uses the retained v6 export and ffmpeg. The v6 card fixtures and voice-over timing remain current.
