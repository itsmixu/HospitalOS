# HospitalOS

Hands-free bedside coordination and reviewed care notes on Meta AI glasses. Junction 2026, Meta challenge.

**One connected workflow:** patient encounter → assistance request → colleague accepts and receives relevant context → explicit care update → reviewed draft note.

- **Reach colleagues and get acknowledged help**, using declared availability and assignment.
- **Receive a relevant briefing** when accepting a patient-linked request.
- **Prepare care notes with consent**, separating patient reports, plans and confirmed completed actions; the nurse reviews before saving.
- **Control information sharing:** caregivers, ward overview and patient record receive different permitted information, demonstrated through actual outgoing payloads.
- **QR wristband scanning stays optional.** Confirmed task context or explicit patient selection supports the full core workflow without scanning.

One real pair of glasses is required; two are preferred, with a phone/browser responder fallback. Staff maps and separate simulated alerts are optional. All demo data is synthetic. External AI processing is disclosed; this prototype does not establish hospital readiness or device-wide privacy.

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

1. **Meta SDK:** the Android SDK and samples are at <https://github.com/meta/meta-wearables-dat-android> (use 1.0.1 or later). Don't commit a copy of it here.
2. **Glasses:** enable Developer Mode in the Meta AI app (tap the app version 5 times). Without glasses, use the SDK's mock device kit.
3. **Secrets:** copy `.env.example` to `.env` and fill in `ANTHROPIC_API_KEY`.
4. App and server run instructions: added here when the code lands.
