# HospitalOS — Team rules & decision log

Junction 2026 entry for the Meta AI glasses challenge (brief: [research/challenge-brief.md](research/challenge-brief.md)).
HospitalOS focuses on nurse-to-nurse communication plus verified bedside observation capture, with local-first processing and explicit privacy routing. Display is primary; QR remains optional.

**Read [SPEC.md](SPEC.md) before doing any work.** It is the single source of truth for what we build. This file holds the rules and everything we've agreed.

## Rules (for humans and AI agents)

1. **SPEC.md first.** If a change alters behavior, scope, message types or architecture, update SPEC.md in the same commit (or before). Code that disagrees with SPEC.md is a bug in one of them; fix it.
2. **Log every agreement** in the decision log below: date, decision, one-line why. If it's not written here, it isn't agreed.
3. **Keep docs in these three files** (README.md, CLAUDE.md, SPEC.md). Don't create new docs files; add a section instead.
4. **Fake data only.** Never commit real patient, staff or personal data. All prototype demo data lives in `prototype/demo-data/`.
5. **No secrets in git.** API keys go in `.env` (git-ignored); keep `.env.example` up to date with every variable name.
6. **`main` must always run the demo.** Work on short-lived branches (`feat/…`, `fix/…`), merge to `main` the same day. Don't push broken builds to `main`.
7. **Small commits, clear messages** (`feat(app): wristband QR decode`, `fix(server): alert routing`).
8. **Record glasses gotchas** at the SPEC.md §10 G0 hardware gate as soon as you hit one. Hardware time is scarce; don't let the other person hit the same wall.
9. **Every feature works in scripted mode** too (server fakes the step), so the demo survives hardware or Wi-Fi failure on stage.
10. **Display is the main demo target.** Polish the Display workflow first. Basic non-display audio support is optional only if it does not delay Display development or rehearsal; no requirement for every card to have a spoken version. Use the phone for detailed non-display review.
11. **AI agents:** don't add features beyond SPEC.md, don't add dependencies without noting why in the decision log, and ask before deleting files.

## Team & ownership

| Who | Owns | Availability |
|---|---|---|
| itsmixu (repo owner) | Specs, research, deck, demo script | Fri 13 Nov + Sat 14 Nov morning |
| Main developer (Mac + Android phone) | `prototype/app/`, `prototype/server/`, demo build | Whole event |

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

### Approved scope revision — 2026-11-13

These decisions supersede conflicting earlier scope/hardware decisions above; historical entries remain for context.

- 2026-11-13 — **Core is one connected bedside workflow:** encounter → assistance request → acceptance and relevant briefing → explicit care update → reviewed note — gives the demo one complete outcome.
- 2026-11-13 — **QR wristband scanning remains P1/optional.** Patient context comes from a confirmed task or explicit selection; room alone is insufficient — scanning is a convenience, not the core feature or an access grant.
- 2026-11-13 — **Privacy routing is P0:** explicit server rules and distinct caregiver/ward/record outputs, demonstrated with actual outgoing payloads — privacy must be observable, with external AI processing disclosed and no device-wide guarantees.
- 2026-11-13 — **Delivery, acceptance and completion are separate.** Completed care requires explicit staff confirmation; draft notes distinguish reported, planned and completed — avoid overstating either response or care.
- 2026-11-13 — **Staff map and separate alert scenario are P1.** Core routing uses declared availability and assignment, not simulated proximity — reduce scope and avoid implying indoor positioning exists.
- 2026-11-13 — **One real glasses pair required, two preferred; phone/browser responder fallback.** Display is preferred, core audio remains supported — shared hardware must not block the workflow.
- 2026-11-13 — **Camera value remains an open validation question.** Test optional wristband selection versus selected instrument-reading capture; neither is mandatory or a proven USP — preserve the challenge's first-person focus without inventing value.

- 2026-11-13 — **Display is the main demo and development target**, superseding earlier full-audio-support requirements. Basic Gen 2 requests, acknowledgements and encounter capture are P1 only if they do not slow Display work; detailed review uses the phone, and deferred support is labelled planned — focus hardware time and polish on one complete demo.
- 2026-11-13 — **Download the official Meta Android toolkit/samples as a sibling checkout**, outside HospitalOS; use published SDK dependencies when integrating — keep upstream code out of our repository and provide a clean development starting point.

- 2026-11-13 — **Implement the development foundation:** TypeScript WebSocket server (`ws`), validation (`zod`), browser responder and Kotlin Android Display bridge (`OkHttp`, Android lifecycle, DAT 1.0.1), plus Prettier for consistent team formatting. These dependencies provide transport, validation and lifecycle support. Start with synthetic typed encounter input and an explicitly labelled deterministic draft, not claimed live speech or AI. Add shared state/permission tests and build scripts; leave QR and Gen 2 optional.

- 2026-11-13 — **Move the working core into `prototype/`**, including Android, server, synthetic data and Node setup; planning/research remain at root. Run commands and CI from the new location — keep the prototype separate from other team work.

### Combined product plan — 2026-11-14

These decisions supersede conflicting earlier priorities, including mandatory ambient conversation capture and external AI processing.

- 2026-11-14 — **Build both workflows:** nurse-to-nurse communication is core; deliberate capture/review of one supported instrument display is the demoable first-person feature. Attach verified observations to requests only by choice; communication also works without a patient encounter.
- 2026-11-14 — **Local-first processing:** plan bundled Android OCR and explicitly local speech recognition on the phone or controlled local computer. No cloud model is required for the core workflow. Candidate dependencies need benchmarking/telemetry review before adoption; this planning change does not install them.
- 2026-11-14 — **Defer long ambient recording and generated narrative notes.** Use short reviewed messages and structured observation/task records — protect build time and reduce capture/privacy complexity.
- 2026-11-14 — **SPEC.md is rewritten as the full implementation plan**, including the current code's gaps, state contracts, G0–G7 simulation/hardware gates, release targets and staged hospital deployment. Targets are not evidence of achieved performance; no clinical-readiness claim.
- 2026-11-14 — **Display first; QR and Gen 2 optional.** One supported optical profile; reject uncertainty; nurse confirms every reading; never infer measurement freshness or clinical significance from a captured image.
- 2026-11-14 — **Deployment is gated:** verify Meta platform permissions/terms, hospital access and privacy requirements, intended-purpose classification, EHR integration, hardware suitability and supervised validation before real care. Existing emergency systems remain authoritative.

### Phone + glasses app — 2026-11-14 (branch `feat/full-app`)

- 2026-11-14 — **One phone app drives the glasses from live server state.** Mockup screens (from branch `feat/glasses-app`) were ported into `prototype/app` and wired to the ward server: patient card, consent → corner recording pill → draft note review, help requests with real sent/delivered/accepted/completed states, incoming request → briefing → done. Phone app rebuilt in Compose with two tabs (Connect, Ward) — the earlier button list was too confusing to demo. **Built before the combined plan above:** its encounter/recording flow uses no audio but should be reshaped into short reviewed messages and instrument-reading capture per that plan.
- 2026-11-14 — **Server additions:** snapshot includes `me`; synthetic patients gain optional `age`, `warning`, `summary`; `DEMO_TOKENS=fixed` gives stable rehearsal tokens on loopback. Default behaviour unchanged; all tests pass.
- 2026-11-14 — **Hardware verified on Windows** (Ray-Ban Display + Android phone over USB). Build with JDK 17/21, not Android Studio's bundled Java 25.
