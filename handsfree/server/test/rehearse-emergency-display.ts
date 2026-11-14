/** Focused display-lease check: real DAT renderer, deterministic injected tilt, voice disabled. */
import { WebSocket } from "ws";
import { once } from "node:events";
import assert from "node:assert/strict";
import scenario from "../../../prototype/demo-data/handsfree-code-blue.json" with { type: "json" };
let state: any, peer: any;
const acks = new Map<string, any>();
const pause = (ms: number) => new Promise((r) => setTimeout(r, ms));
async function connect(actor: string, role: string, on: (m: any) => void) {
  const w = new WebSocket(process.env.HF_URL || "ws://127.0.0.1:8793");
  w.on("message", (d) => {
    const m = JSON.parse(d.toString());
    if (m.type === "ack") acks.set(m.id, m);
    on(m);
  });
  await once(w, "open");
  w.send(JSON.stringify({ type: "auth", token: actor + "-demo", role }));
  return w;
}
const control = await connect("aino", "control", (m) => {
  if (m.type === "sim.state") state = m;
});
const ben = await connect("ben", "responder", (m) => {
  if (m.type === "snapshot") peer = m;
});
async function until(f: () => boolean, label: string) {
  const end = Date.now() + 15000;
  while (!f()) {
    if (Date.now() > end) throw Error(label + ": " + JSON.stringify(state));
    await pause(50);
  }
}
async function command(c: any) {
  const id = crypto.randomUUID();
  ben.send(JSON.stringify({ id, ...c }));
  await until(() => acks.has(id), "ack");
  assert.equal(acks.get(id).ok, true, JSON.stringify(acks.get(id)));
}
const input = (input: any) =>
  control.send(JSON.stringify({ type: "sim.input", input }));
try {
  await until(() => state?.motion?.eligible && peer, "startup");
  if (state.glassesVoice) {
    input({ kind: "sdk-voice", active: false });
    await until(() => !state.glassesVoice, "voice stopped");
  }
  for (const e of peer.emergencies || [])
    if (e.from === "ben" && e.status === "active")
      await command({
        type: "emergency.resolve",
        emergencyId: e.id,
        revision: e.revision,
      });
  await command({ type: "emergency.create", ...scenario });
  await until(
    () => state.card.phase === "emergency-alert" && state.motion.rendered,
    "rendered alert",
  );
  input({ kind: "gesture", value: "right-tilt" });
  await until(
    () => state.card.phase === "emergency-briefing" && state.motion.rendered,
    "rendered ISBAR",
  );
  console.log(
    "ISBAR rendered with microphone off. Holding beyond the ordinary display timeout.",
  );
  await pause(35000);
  assert.equal(state.card.phase, "emergency-briefing");
  assert.equal(state.motion.rendered, true);
  assert.equal(state.glassesVoice, false);
  assert.equal(state.connectionError, "");
  console.log(
    "PASS 35-second ISBAR hold with microphone off; ready for visual verification",
  );
} finally {
  control.close();
  ben.close();
}
