/** Synthetic spoken audio -> local recognizer -> existing Android controller. Not a human mic accuracy test. */
import { WebSocket } from "ws";
import { once } from "node:events";
import { execFileSync } from "node:child_process";
import { mkdir, readFile } from "node:fs/promises";
import { randomUUID } from "node:crypto";
import assert from "node:assert/strict";
import { normalizeSpeech, routeSpeech } from "../public/voice-gate.js";
let state: any, own: any, peer: any;
const base = process.env.HF_HTTP || "http://127.0.0.1:8790";
async function connect(actor: string, role: string, receive: (m: any) => void) {
  const ws = new WebSocket(base.replace("http", "ws"));
  ws.on("message", (raw) => receive(JSON.parse(raw.toString())));
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
const input = (input: any) =>
  control.send(JSON.stringify({ type: "sim.input", input }));
const delay = (ms: number) => new Promise((r) => setTimeout(r, ms));
async function until(p: () => boolean) {
  const end = Date.now() + 8000;
  while (!p()) {
    if (Date.now() > end) throw Error("State timeout: " + state?.card?.title);
    await delay(25);
  }
}
await mkdir(".local/speech-check", { recursive: true });
const phrases = [
  "Hospital",
  "Ask Ben to bring the hoist to room twelve",
  "Send request",
  "Select patient demo A",
  "Confirm patient",
  "Capture reading",
  "Save reading",
  "Share reading with Ben",
  "Send attachment",
  "Stop listening",
];
const audio = new Map<string, Buffer>();
for (const [i, phrase] of phrases.entries()) {
  const path = `.local/speech-check/phrase-${i}`;
  execFileSync("say", ["-v", "Samantha", "-o", path + ".aiff", phrase]);
  execFileSync("ffmpeg", [
    "-y",
    "-loglevel",
    "error",
    "-i",
    path + ".aiff",
    "-ar",
    "16000",
    "-ac",
    "1",
    "-f",
    "s16le",
    path + ".pcm",
  ]);
  audio.set(phrase, await readFile(path + ".pcm"));
}
async function say(phrase: string) {
  const stamp = structuredClone(state);
  const start = Date.now();
  const r = await fetch(base + "/speech", {
    method: "POST",
    headers: {
      Origin: base,
      Authorization: "Bearer aino-demo",
      "Content-Type": "application/octet-stream",
    },
    body: new Uint8Array(audio.get(phrase)!).buffer,
  });
  assert.equal(r.status, 200);
  const result = await r.json();
  const text = normalizeSpeech(result.text);
  console.log(
    JSON.stringify({ expected: phrase, heard: text, ms: Date.now() - start }),
  );
  const route = routeSpeech(text, stamp, state);
  if (route === "wake") input({ kind: "wake" });
  else if (route === "transcript") input({ kind: "transcript", text });
  else
    throw Error(
      "Speech rejected: " +
        route +
        " " +
        JSON.stringify({ started: stamp, current: state }),
    );
  await delay(150);
}
async function review(title: string) {
  await until(
    () =>
      state?.card?.title === title &&
      state.renderedPromptId === state.card.promptId,
  );
}
try {
  await until(() => state && own && peer);
  await say("Hospital");
  await until(() => state.card.microphone);
  await say("Ask Ben to bring the hoist to room twelve");
  await review("Ask Ben?");
  await say("Send request");
  await until(() =>
    peer.tasks.some(
      (t: any) =>
        t.from === "aino" &&
        t.text === "bring the hoist" &&
        ["sent", "delivered"].includes(t.status),
    ),
  );
  const task = peer.tasks.findLast(
    (t: any) =>
      t.from === "aino" &&
      t.text === "bring the hoist" &&
      ["sent", "delivered"].includes(t.status),
  );
  ben.send(
    JSON.stringify({
      id: randomUUID(),
      type: "request.accept",
      taskId: task.id,
      revision: task.revision,
    }),
  );
  await until(() => state.card.title === "Ben is coming");
  await say("Hospital");
  await until(() => state.card.microphone);
  await say("Select patient demo A");
  await review("Confirm patient");
  await say("Confirm patient");
  await until(() => state.card.title === "Patient confirmed");
  input({ kind: "camera", scenario: "clear", seed: 42 });
  await say("Capture reading");
  await review("Review reading");
  await say("Save reading");
  await until(() => state.card.title === "Saved · synthetic record");
  await say("Share reading with Ben");
  await review("Send to Ben?");
  await say("Send attachment");
  await until(() =>
    peer.tasks.some(
      (t: any) => t.attachment?.id === own.observations.at(-1)?.id,
    ),
  );
  console.log(
    "PASS spoken audio request + acceptance + patient selection/capture/save/share; no typed command substitutions",
  );
} finally {
  control.terminate();
  ben.terminate();
}
