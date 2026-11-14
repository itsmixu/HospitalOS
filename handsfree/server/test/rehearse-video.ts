/** Real Kotlin controller + official DAT mock services; synthetic speech/tilt/pixels, no physical validation. */
import { WebSocket } from "ws";
import { once } from "node:events";
import assert from "node:assert/strict";
import scenario from "../../../prototype/demo-data/handsfree-code-blue.json" with { type: "json" };
let state: any, own: any, peer: any;
let speechSequence = 0;
const pause = (ms: number) => new Promise((r) => setTimeout(r, ms));
const receipts = new Map<string, any>();
async function connect(actor: string, role: string, on: (m: any) => void) {
  const w = new WebSocket(process.env.HF_URL || "ws://127.0.0.1:8793");
  w.on("message", (d) => {
    const m = JSON.parse(d.toString());
    if (m.type === "ack") receipts.set(m.id, m);
    on(m);
  });
  await once(w, "open");
  w.send(JSON.stringify({ type: "auth", token: actor + "-demo", role }));
  return w;
}
const control = await connect("aino", "control", (m) => {
  if (m.type === "sim.state") state = m;
  if (m.type === "snapshot") own = m;
  if (m.type === "error") console.error(m);
});
const ben = await connect("ben", "responder", (m) => {
  if (m.type === "snapshot") peer = m;
});
const input = (input: any) =>
  control.send(JSON.stringify({ type: "sim.input", input }));
async function until(f: () => boolean, label: string, ms = 12000) {
  const end = Date.now() + ms;
  while (!f()) {
    if (Date.now() > end) throw Error(label + ": " + JSON.stringify(state));
    await pause(40);
  }
}
const rendered = () => state?.motion?.eligible && state?.motion?.rendered;
async function title(t: string) {
  await until(() => rendered() && state.card.title === t, t);
}
async function heard(text: string, t?: string) {
  const beforePrompt = state?.card?.promptId;
  await until(() => state?.sdkReady && state.sdkListening, "speech ready");
  input({ kind: "sdk-transcript", text: "", final: false, confidence: 0 });
  await pause(60);
  input({
    kind: "sdk-transcript",
    text,
    final: true,
    confidence: ++speechSequence % 2 ? 0.94 : 0.96,
  });
  if (t) await title(t);
  else
    await until(
      () => rendered() && state.card.promptId !== beforePrompt,
      "command completed: " + text,
    );
}
async function tilt(side: string, t?: string) {
  await until(rendered, "rendered tilt");
  input({ kind: "gesture", value: side + "-tilt" });
  if (t) await title(t);
  else await pause(1400);
}
async function command(c: any) {
  const id = crypto.randomUUID();
  ben.send(JSON.stringify({ id, ...c }));
  await until(() => receipts.has(id), "server ack");
  const a = receipts.get(id);
  assert.equal(a.ok, true, JSON.stringify(a));
  return a;
}
async function wake() {
  await heard("Hey Lumi", "Listening");
}
try {
  await until(() => rendered() && own && peer, "startup", 25000);
  input({ kind: "sdk-voice", active: true });
  await until(() => state?.sdkReady && state.sdkListening, "SDK speech start");
  // Clean up only this synthetic reporter's active rehearsal event via the normal resolve action.
  for (const e of peer.emergencies || [])
    if (e.from === "ben" && e.status === "active")
      await command({
        type: "emergency.resolve",
        emergencyId: e.id,
        revision: e.revision,
      });
  await tilt("left");
  await wake();
  await heard("Scan patient", "Confirm patient");
  assert.equal(own.context, null);
  await tilt("right");
  await until(
    () => rendered() && state.card.title.startsWith("Patient overview"),
    "patient overview",
  );
  assert.ok(state.card.body.includes("demo-a"));
  assert.ok(state.card.body.includes("Blood-pressure observation"));
  const overview = [state.card.body];
  for (let i = 0; i < 6; i++) {
    await heard("Next");
    overview.push(state.card.body);
  }
  const details = overview.join("\n");
  assert.ok(details.includes("Penicillin"));
  assert.ok(details.includes("Amlodipine"));
  assert.ok(details.includes("Dr Demo"));
  assert.ok(details.includes("Aino K."));
  console.log(
    "PASS QR pixels -> exact patient confirmation -> admission, allergies, prescriptions and authored care history",
  );
  await heard("Capture reading", "Review reading");
  const review = state.card.body;
  const before = own.observations.length;
  // Routine interruption queues behind a protected bedside review.
  const help = await command({
    type: "request.create",
    to: "aino",
    room: "12",
    text: "Help with transfer",
  });
  await pause(500);
  assert.equal(state.card.title, "Review reading");
  await tilt("right");
  await until(
    () => own.observations.length === before + 1,
    "observation persisted",
  );
  await title("Saved · synthetic record");
  const obs = own.observations.at(-1);
  assert.equal(obs.source, "dat-camera");
  assert.ok(review.includes(String(obs.values.systolic)));
  assert.ok(review.includes(String(obs.values.diastolic)));
  assert.ok(review.includes(String(obs.values.pulse)));
  await tilt("left");
  await until(
    () =>
      rendered() &&
      state.card.phase === "review" &&
      state.card.title.includes("needs help"),
    "routine request",
  );
  await tilt("left");
  await until(
    () =>
      peer.tasks.find((t: any) => t.id === help.taskId)?.status === "declined",
    "left declined",
  );
  console.log(
    "PASS DAT mock camera pixels -> reviewed BP save; routine help left-tilt decline notifies Ben",
  );
  await tilt("left");
  await wake();
  const note = "I gave the prescribed amlodipine 5 mg orally at 12:00.";
  await heard("Save to patient records that " + note);
  await until(
    () => rendered() && state.card.phase === "review",
    "medication review",
  );
  assert.ok(state.card.body.includes(note));
  const noteCount = own.notes.length;
  await tilt("right");
  await until(() => own.notes.length === noteCount + 1, "medication saved");
  const saved = own.notes.at(-1);
  assert.equal(saved.text, note);
  assert.equal(saved.author, "aino");
  assert.equal(saved.eventTime, null);
  assert.ok(saved.savedAt);
  console.log(
    "PASS exact dictated medication note -> review -> durable authored record",
  );
  const blue = await command({ type: "emergency.create", ...scenario });
  await title("CODE BLUE · Room 8");
  assert.equal(
    own.emergencies.find((e: any) => e.id === blue.emergencyId).isbar,
    undefined,
  );
  await tilt("right");
  await until(
    () => rendered() && state.card.phase === "emergency-briefing",
    "ISBAR visible",
  );
  assert.deepEqual(
    own.emergencies.find((e: any) => e.id === blue.emergencyId).isbar,
    scenario.isbar,
  );
  assert.ok(!own.patients.some((p: any) => p.id === "demo-c"));
  const pages = [state.card.body];
  for (let i = 0; i < 5; i++) {
    await tilt("right");
    pages.push(state.card.body);
  }
  const briefing = pages.join("\n");
  for (const value of Object.values(scenario.isbar))
    assert.ok(briefing.includes(value), value);
  await pause(21000);
  assert.equal(state.card.phase, "emergency-briefing");
  await tilt("left", "Display hidden");
  await wake();
  await heard("Show ISBAR");
  await until(
    () => rendered() && state.card.phase === "emergency-briefing",
    "reopened ISBAR",
  );
  const active = peer.emergencies.find((e: any) => e.id === blue.emergencyId);
  await command({
    type: "emergency.resolve",
    emergencyId: active.id,
    revision: active.revision,
  });
  await title("Code Blue resolved");
  assert.equal(
    own.emergencies.find((e: any) => e.id === blue.emergencyId).isbar,
    undefined,
  );
  console.log(
    "PASS priority Code Blue -> right response -> exact paged ISBAR, remains visible, left close, reopen, reporter resolution",
  );
  if (process.env.KEEP_BRIEFING === "1") {
    input({ kind: "sdk-voice", active: false });
    await until(() => !state.glassesVoice, "microphone stopped");
    await command({ type: "emergency.create", ...scenario });
    await title("CODE BLUE · Room 8");
    await tilt("right");
    await until(
      () => rendered() && state.card.phase === "emergency-briefing",
      "briefing without voice",
    );
    await pause(35000);
    assert.equal(state.card.phase, "emergency-briefing");
    assert.equal(state.glassesVoice, false);
    console.log(
      "PASS emergency briefing remains active with voice off; ready for visual lease check",
    );
  }
  console.log("ALL VIDEO WORKFLOW CHECKS PASSED");
} finally {
  if (state?.glassesVoice) input({ kind: "sdk-voice", active: false });
  control.close();
  ben.close();
}
