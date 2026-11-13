# HospitalOS

Display-first bedside coordination and reviewed care notes on Meta AI glasses. Junction 2026, Meta challenge.

**One connected workflow:** patient encounter → assistance request → colleague accepts and receives relevant context → explicit care update → reviewed draft note.

- **Reach colleagues and get acknowledged help**, using declared availability and assignment.
- **Receive a relevant briefing** when accepting a patient-linked request.
- **Prepare care notes with consent**, separating patient reports, plans and confirmed completed actions; the nurse reviews before saving.
- **Control information sharing:** caregivers, ward overview and patient record receive different permitted information, demonstrated through actual outgoing payloads.
- **QR wristband scanning stays optional.** Confirmed task context or explicit patient selection supports the full core workflow without scanning.

**Meta Ray-Ban Display is the main demo target.** One real Display pair is required; two are preferred, with a phone/browser responder fallback. Basic Gen 2 audio support (assistance requests, acknowledgements and encounter capture) is optional only if it does not slow Display development or rehearsal; detailed review stays on the phone. No full audio parity is required. Staff maps and separate simulated alerts are optional. All demo data is synthetic. External AI processing is disclosed; this prototype does not establish hospital readiness or device-wide privacy.

**Status:** runnable synthetic development foundation: local WebSocket ward, browser responder, permission-filtered tasks and reviewed notes, plus an Android DAT Display shell. Voice capture, live AI summaries and hardware verification are still pending.

**Start here:** [SPEC.md](SPEC.md) (what we build) · [CLAUDE.md](CLAUDE.md) (rules + decisions)

## Repo

| Folder | What |
|---|---|
| `prototype/app/` | Android companion app (Kotlin, Meta Wearables Device Access Toolkit) |
| `prototype/server/` | Node/TypeScript WebSocket server + Claude API |
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

- Verify registration, Display session and interactions on loan hardware; refine task/briefing/review cards.
- Add deliberate voice input and consented capture, then real transcription and a validated external-model adapter.
- Expand task-relevant briefings and patient selection; QR stays optional.
- Keep basic Gen 2 audio deferred until the Display workflow is stable.

## Verified setup (2026-11-13)

- `npm run check`: passed.
- `npm test`: six checks passed, including real WebSocket auth/routing/reconnect, recipient filtering, explicit completion, consent/review, idempotent retry and encounter isolation.
- `npm audit --audit-level=high`: no reported vulnerabilities at setup time.
- `:mobile:assembleDebug`: passed using Android Studio's bundled Java 21 and SDK 36; debug APK generated. No physical phone or glasses test has been performed.
