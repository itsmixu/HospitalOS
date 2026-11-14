/** Runs against the installed debug DAT preview; speech/motion are simulated. */
import { WebSocket } from "ws";
import { once } from "node:events";
import { randomUUID } from "node:crypto";
import assert from "node:assert/strict";
const endpoint = process.env.HF_URL || "ws://127.0.0.1:8790";
let state: any, own: any, peer: any;
let stage = "connecting";
async function connect(actor: string, role: string, receive: (m: any) => void) {
  const ws = new WebSocket(endpoint);
  ws.on("message", (raw) => receive(JSON.parse(raw.toString())));
  await once(ws, "open");
  ws.send(JSON.stringify({ type: "auth", token: `${actor}-demo`, role }));
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
async function until(predicate: () => boolean, ms = 6000) {
  const end = Date.now() + ms;
  while (!predicate()) {
    if (Date.now() > end) throw Error(`${stage}: ${state?.card?.title}`);
    await pause(25);
  }
}
async function speak(text: string) {
  input({ kind: "transcript", text });
  await pause(150);
}
async function wake() {
  input({ kind: "wake" });
  await until(() => state?.card?.microphone);
}
async function review(title: string) {
  stage = title;
  await until(
    () =>
      state?.card?.title === title &&
      state.renderedPromptId === state.card.promptId,
  );
}
function peerCommand(fields: any) {
  ben.send(JSON.stringify({ id: randomUUID(), ...fields }));
}
try {
  await until(() => state && own && peer);
  input({ kind: "device-ready" });
  await pause(150);
  await wake();
  await speak("done");
  assert.equal(
    state.card.microphone,
    false,
    "Bedside exit must not reveal stale listening",
  );
  const marker = `bring rehearsal belt ${randomUUID().slice(0, 8)}`;
  const before = own.tasks.length;
  await wake();
  await speak(`Ask Ben to ${marker} to room eighty eight`);
  await review("Ask Ben?");
  input({ kind: "gesture", value: "single-nod" });
  await pause(1200);
  assert.equal(own.tasks.length, before, "A single nod cannot send");
  await speak("cancel");
  await wake();
  await speak(`Ask Ben to ${marker} to room eighty eight`);
  await review("Ask Ben?");
  input({ kind: "gesture", value: "nod" });
  stage = "deliberate nod sends";
  await until(() => peer.tasks.some((t: any) => t.text === marker));
  let task = peer.tasks.find((t: any) => t.text === marker);
  peerCommand({
    type: "request.delivered",
    taskId: task.id,
    revision: task.revision,
  });
  await until(() => state.card.title === "Delivered to Ben");
  task = peer.tasks.find((t: any) => t.id === task.id);
  peerCommand({
    type: "request.accept",
    taskId: task.id,
    revision: task.revision,
  });
  await until(() => state.card.title === "Ben is coming");
  assert.equal(state.card.microphone, false);
  task = peer.tasks.find((t: any) => t.id === task.id);
  peerCommand({
    type: "request.complete",
    taskId: task.id,
    revision: task.revision,
  });
  await until(() => state.card.title === "Marked complete");
  console.log(
    "PASS named request, single-nod rejection, deliberate nod, delivered/accepted/completed",
  );

  peerCommand({ type: "request.create", to: "aino", room: "89", text: marker });
  await until(() =>
    own.tasks.some((t: any) => t.to === "aino" && t.text === marker),
  );
  await wake();
  await speak("review request in room eighty nine from Ben");
  await review("Ben needs help");
  await speak("accept request");
  await until(() => state.card.title === "Accepted · Ben notified");
  await wake();
  await speak("complete request in room eighty nine from Ben");
  await review("Mark complete?");
  await speak("confirm completion");
  await until(() => state.card.title === "Marked complete");
  console.log(
    "PASS incoming request selection, voice acceptance and completion",
  );

  peerCommand({ type: "message.send", to: "aino", text: marker });
  await until(() => own.messages.some((m: any) => m.text === marker));
  await wake();
  await speak("read message from Ben");
  stage = "read message";
  await until(() => state.card.title === "Message from Ben");
  await speak(`reply ${marker}`);
  await review("Reply to Ben?");
  await speak("send message");
  await until(() =>
    peer.messages.some((m: any) => m.from === "aino" && m.text === marker),
  );
  await wake();
  await speak("read message from Ben");
  await until(() => state.card.title === "Message from Ben");
  await speak("close message");
  await until(() => state.card.title === "Ward confirmed");
  console.log("PASS message read, exact reply review, send and close");

  for (const status of ["busy", "available"]) {
    await wake();
    await speak(`set me ${status}`);
    await review(`Set ${status}?`);
    await speak("confirm status");
    await until(
      () => own.staff.find((p: any) => p.id === "aino")?.status === status,
    );
  }
  console.log("PASS declared availability separate from reachability");

  const lossMarker = `lost draft ${randomUUID().slice(0, 8)}`;
  await wake();
  await speak(`Ask Ben to bring ${lossMarker} to room ninety`);
  await review("Ask Ben?");
  input({ kind: "device-lost" });
  stage = "device loss";
  await until(
    () =>
      !own.staff.find((p: any) => p.id === "aino")?.reachable &&
      state.card.microphone === false,
  );
  assert.equal(state.card.microphone, false);
  input({ kind: "device-ready" });
  await until(() => own.staff.find((p: any) => p.id === "aino")?.reachable);
  input({ kind: "gesture", value: "nod" });
  await pause(1500);
  assert.equal(
    own.tasks.some((t: any) => t.text.includes(lossMarker)),
    false,
    "Stale confirmation cannot send after reconnection",
  );
  await wake();
  await speak("check requests");
  await until(() => state.card.title === "Your requests");
  assert.equal(state.card.microphone, false);
  console.log(
    "PASS device loss invalidates draft, reachability recovers, stale nod cannot send",
  );
  console.log(
    "CORE REHEARSAL PASSED: actual Android controller and DAT display; injected speech/motion only",
  );
} finally {
  control.terminate();
  ben.terminate();
}
