# HospitalOS

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

Use `ws://127.0.0.1:8787` in the phone app after USB reverse; the Android emulator uses `ws://10.0.2.2:8787`. Paste Aino's fresh demo token, connect and use the labelled synthetic request button. Browser tabs provide detailed encounter entry/review. The phone preview receives the same filtered snapshot.

For glasses, initialise Bluetooth/SDK, register with Meta AI (Developer Mode enabled), then start Display. The initial Display bridge shows ward task/note status; full patient/task card interactions are the next development step. Stop Display explicitly when finished. Hardware registration and rendering must be verified on the provided glasses. Debug builds alone permit cleartext local traffic; this is not a production network configuration. SDK analytics/crash-reporting opt-outs are configured, without claiming device-wide privacy.

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
- `:mobile:assembleDebug`: passed using Android Studio's bundled Java 21 and SDK 36; debug APK generated. No physical phone or glasses test has been performed.
