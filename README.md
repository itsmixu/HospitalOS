# HospitalOS

Display-first bedside coordination and reviewed care notes on Meta AI glasses. Junction 2026, Meta challenge.

**One connected workflow:** patient encounter → assistance request → colleague accepts and receives relevant context → explicit care update → reviewed draft note.

- **Reach colleagues and get acknowledged help**, using declared availability and assignment.
- **Receive a relevant briefing** when accepting a patient-linked request.
- **Prepare care notes with consent**, separating patient reports, plans and confirmed completed actions; the nurse reviews before saving.
- **Control information sharing:** caregivers, ward overview and patient record receive different permitted information, demonstrated through actual outgoing payloads.
- **QR wristband scanning stays optional.** Confirmed task context or explicit patient selection supports the full core workflow without scanning.

**Meta Ray-Ban Display is the main demo target.** One real Display pair is required; two are preferred, with a phone/browser responder fallback. Basic Gen 2 audio support (assistance requests, acknowledgements and encounter capture) is optional only if it does not slow Display development or rehearsal; detailed review stays on the phone. No full audio parity is required. Staff maps and separate simulated alerts are optional. All demo data is synthetic. External AI processing is disclosed; this prototype does not establish hospital readiness or device-wide privacy.

**Status:** planning and research scaffold; app/server implementation and run instructions are still to come.

**Start here:** [SPEC.md](SPEC.md) (what we build) · [CLAUDE.md](CLAUDE.md) (rules + decisions)

## Repo

| Folder | What |
|---|---|
| `app/` | Android companion app (Kotlin, Meta Wearables Device Access Toolkit) |
| `server/` | Node/TypeScript WebSocket server + Claude API |
| `demo-data/` | Fake staff, patients, floor plan, wristband QR codes, scenario |
| `research/` | Challenge brief, judging criteria, smart-glasses research |

## Setup

1. **Meta Android toolkit and samples:** official upstream <https://github.com/meta/meta-wearables-dat-android>, downloaded beside this repository at `../meta-wearables-dat-android` (checkout `f4371bc9f90249d51eb07d7f8a61e8d829294f16`). Do not commit upstream code here. Open `../meta-wearables-dat-android/samples/DisplayAccess` in Android Studio as the Display-first starting point; `samples/CameraAccess` is the optional camera reference. The Display sample pins DAT **1.0.1** and resolves its SDK libraries from Maven Central during Gradle sync; cloning alone does not download all build dependencies.
   - Upstream prerequisites: Android Studio Narwhal (2025.1.1)+, JDK 17+, Android SDK 36+. Use Android Studio's bundled JDK for Gradle.
   - Add your Wearables Developer Center application ID and client token to the sample's local `local.properties` (`mwdat_application_id`, `mwdat_client_token`); do not commit credentials.
   - Sync/build the sample, then test its display, input and audio paths on loan hardware before adapting it into `app/`. Download is verified; build, registration and hardware operation are not yet verified.
2. **Glasses:** enable Developer Mode in the Meta AI app (tap the app version 5 times). Without glasses, use the SDK's mock device kit.
3. **Secrets:** copy `.env.example` to `.env` and fill in `ANTHROPIC_API_KEY`.
4. App and server run instructions: added here when the code lands.
