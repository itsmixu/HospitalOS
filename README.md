# HospitalOS

Hands-free ward communication on Meta AI glasses. Junction 2026, Meta challenge.

- **Talk to any colleague instantly** and see who's free on a live staff map.
- **Get alerts with a briefing** about the patient while you walk to the room.
- **Log patient conversations** (with consent): what you advised, what they said, turned into a confirmed care note.
- **Look at a wristband** to pull up the patient's card.

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
