/** Official DAT Speech adapter rehearsal: SDK mock transcriptions, not physical audio. */
import { WebSocket } from "ws";
import { once } from "node:events";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
const url = process.env.HF_URL || "ws://127.0.0.1:8791";
let state: any, own: any, peer: any;
async function connect(actor: string, role: string, receive: (m: any) => void) {
  const ws = new WebSocket(url);
  ws.on("message", (data) => receive(JSON.parse(data.toString())));
  await once(ws, "open");
  ws.send(JSON.stringify({ type: "auth", token: actor + "-demo", role }));
  return ws;
}
const control = await connect("aino", "control", (m) => {
  if (m.type === "sim.state") state = m;
  if (m.type === "snapshot") own = m;
});
const ben = await connect("ben", "responder", (m) => {
  if (m.type === "snapshot") peer = m;
});
const pause = (ms: number) => new Promise((r) => setTimeout(r, ms));
const input = (input: any) =>
  control.send(JSON.stringify({ type: "sim.input", input }));
async function until(check: () => boolean, label: string) {
  const end = Date.now() + 8000;
  while (!check()) {
    if (Date.now() > end) throw Error(label + ": " + JSON.stringify(state));
    await pause(30);
  }
}
async function heard(text: string, title?: string) {
  await until(() => state?.sdkListening && state.sdkReady, "SDK listening");
  input({ kind: "sdk-transcript", text, final: true });
  if (title)
    await until(
      () =>
        state?.card.title === title &&
        state.renderedPromptId === state.card.promptId,
      title,
    );
  await pause(180);
}
try {
  await until(() => state?.motion?.eligible && state.motion.rendered && own && peer, "initial state");
  input({ kind: "sdk-voice", active: true });
  await until(
    () => state.glassesVoice && state.sdkListening,
    "speech attached",
  );
  const count = own.tasks.length;
  input({ kind: "sdk-transcript", text: "Hospital", final: false });
  await pause(200);
  assert.equal(state.card.microphone, false, "Partial result cannot activate");
  await heard("Hospital", "Listening");
  await heard("Ask Ben to bring the hoist to room twelve", "Ask Ben?");
  assert.equal(own.tasks.length, count, "Review does not send");
  await heard("Send request");
  await until(() => peer.tasks.length > count, "reviewed request received");
  const task = peer.tasks.at(-1);
  ben.send(
    JSON.stringify({
      id: randomUUID(),
      type: "request.delivered",
      taskId: task.id,
      revision: task.revision,
    }),
  );
  await until(() => peer.tasks.at(-1).status === "delivered", "delivered");
  ben.send(
    JSON.stringify({
      id: randomUUID(),
      type: "request.accept",
      taskId: task.id,
      revision: peer.tasks.at(-1).revision,
    }),
  );
  await until(() => state.card.title === "Ben is coming", "acceptance card");
  console.log(
    "PASS SDK partial rejection, standalone activation, exact reviewed request and colleague acceptance",
  );
  await heard("Hospital", "Listening");
  await heard("Select patient demo A", "Confirm patient");
  await heard("Confirm patient", "Patient confirmed");
  await heard("Capture reading");
  await until(
    () =>
      state.card.title === "Review reading" &&
      state.renderedPromptId === state.card.promptId,
    "DAT camera review",
  );
  await heard("Save reading", "Saved · synthetic record");
  assert.ok(own.observations.some((o: any) => o.source === "dat-camera"));
  console.log(
    "PASS SDK speech → same-session DAT mock camera → reviewed saved observation",
  );
  await heard("Stop listening");
  await until(
    () => !state.glassesVoice && !state.sdkListening,
    "microphone stopped",
  );
  const stopped = state.card;
  input({
    kind: "sdk-transcript",
    text: "Share reading with Ben",
    final: true,
  });
  await pause(300);
  assert.deepEqual(state.card, stopped, "No late transcript after stop");
  console.log("PASS spoken stop and late SDK result rejection");
  input({ kind: "sdk-voice", active: true });
  await until(() => state.sdkListening, "restarted SDK speech");
  await heard("Hospital");
  input({ kind: "device-lost" });
  await until(
    () => !state.glassesVoice && !state.sdkListening,
    "device loss stops speech",
  );
  const afterLoss = own.tasks.length;
  input({ kind: "sdk-transcript", text: "Send request", final: true });
  await pause(250);
  assert.equal(own.tasks.length, afterLoss);
  input({ kind: "device-ready" });
  await pause(200);
  assert.equal(
    state.glassesVoice,
    false,
    "Recovery must not reopen microphone",
  );
  console.log(
    "PASS device loss stops microphone and recovery requires explicit opt-in",
  );
} finally {
  input({ kind: "sdk-voice", active: false });
  control.close();
  ben.close();
}
