# HospitalOS — Team rules & decision log

Junction 2026 entry for the Meta AI glasses challenge (brief: [research/challenge-brief.md](research/challenge-brief.md)).
HospitalOS connects ward staff's Meta glasses: nurse-to-nurse communication, live alerts with briefings, and consented patient-conversation logging.

**Read [SPEC.md](SPEC.md) before doing any work.** It is the single source of truth for what we build. This file holds the rules and everything we've agreed.

## Rules (for humans and AI agents)

1. **SPEC.md first.** If a change alters behavior, scope, message types or architecture, update SPEC.md in the same commit (or before). Code that disagrees with SPEC.md is a bug in one of them; fix it.
2. **Log every agreement** in the decision log below: date, decision, one-line why. If it's not written here, it isn't agreed.
3. **Keep docs in these three files** (README.md, CLAUDE.md, SPEC.md). Don't create new docs files; add a section instead.
4. **Fake data only.** Never commit real patient, staff or personal data. All demo data lives in `demo-data/`.
5. **No secrets in git.** API keys go in `.env` (git-ignored); keep `.env.example` up to date with every variable name.
6. **`main` must always run the demo.** Work on short-lived branches (`feat/…`, `fix/…`), merge to `main` the same day. Don't push broken builds to `main`.
7. **Small commits, clear messages** (`feat(app): wristband QR decode`, `fix(server): alert routing`).
8. **Record glasses gotchas** in SPEC.md §6 as soon as you hit one. Hardware time is scarce; don't let the other person hit the same wall.
9. **Every feature works in scripted mode** too (server fakes the step), so the demo survives hardware or Wi-Fi failure on stage.
10. **Every glasses card has a spoken version** (accessibility + non-display glasses).
11. **AI agents:** don't add features beyond SPEC.md, don't add dependencies without noting why in the decision log, and ask before deleting files.

## Team & ownership

| Who | Owns | Availability |
|---|---|---|
| itsmixu (repo owner) | Specs, research, deck, demo script | Fri 13 Nov + Sat 14 Nov morning |
| Main developer (Mac + Android phone) | `app/`, `server/`, demo build | Whole event |

## Decision log

Newest at the bottom. Format: `YYYY-MM-DD — decision — why`.

- 2026-11-13 — Project is **HospitalOS**: glasses for hospital ward staff — the brief asks for everyday, hands-free, first-person value; ward staff have busy, gloved hands all shift.
- 2026-11-13 — **Core = nurse-to-nurse communication + alerts (F1, F2) and patient-conversation logging (F3).** Wristband QR scan (F4) is an add-on — the team agreed these are the most important.
- 2026-11-13 — **Demo is a scripted fake scenario**, not connected to real hospital systems — keeps scope buildable; judges see every feature on the glasses.
- 2026-11-13 — **Two real glasses talk to each other live**; the rest of the ward is simulated by the server — far more convincing than a fully faked conversation.
- 2026-11-13 — **Companion app on Android (Kotlin)**, developed on macOS with Android Studio — both team members have Android phones; main dev has a Mac.
- 2026-11-13 — **Primary device: Ray-Ban Display**; Gen 2 gets audio-only fallbacks — map, cards and alerts need a screen.
- 2026-11-13 — **Small Node/TypeScript WebSocket server** with the Claude API — relays messages between glasses and keeps the API key off phones.
- 2026-11-13 — **Docs live in 3 files only** (README, CLAUDE, SPEC) — AI agents and people don't read scattered docs.
- 2026-11-13 — Repo **HospitalOS**, private, on itsmixu's GitHub.
