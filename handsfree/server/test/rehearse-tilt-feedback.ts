/** Real DAT render feedback + calibrated injected head samples. Never physical-gesture evidence. */
import { WebSocket } from "ws";
import { once } from "node:events";
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { writeFileSync } from "node:fs";
let state: any, own: any, peer: any;
let seen: any[] = [];
const acks = new Map<string, any>();
const pause = (ms: number) => new Promise((r) => setTimeout(r, ms));
async function connect(actor: string, role: string, on: (m: any) => void) {
  const w = new WebSocket(process.env.HF_URL || "ws://127.0.0.1:8794");
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
  if (m.type === "sim.state") {
    state = m;
    seen.push(m);
  }
  if (m.type === "snapshot") own = m;
});
const ben = await connect("ben", "responder", (m) => {
  if (m.type === "snapshot") peer = m;
});
async function until(f: () => boolean, label: string, ms = 15000) {
  const end = Date.now() + ms;
  while (!f()) {
    if (Date.now() > end) throw Error(label + ": " + JSON.stringify(state));
    await pause(30);
  }
}
const rendered = () =>
  state?.motion?.eligible &&
  state?.motion?.rendered &&
  state?.renderedPromptId === state?.card?.promptId;
const input = (input: any) =>
  control.send(JSON.stringify({ type: "sim.input", input }));
async function request() {
  const id = crypto.randomUUID();
  ben.send(
    JSON.stringify({
      id,
      type: "request.create",
      to: "aino",
      room: "12",
      text: "Help with transfer " + id.slice(0, 4),
    }),
  );
  await until(() => acks.has(id), "request ack");
  assert.equal(acks.get(id).ok, true);
  const task = acks.get(id).taskId;
  await until(
    () =>
      rendered() &&
      state.card.phase === "review" &&
      state.card.body.includes(id.slice(0, 4)),
    "review",
  );
  return task;
}
async function trace(
  value: string,
  side: "Left" | "Right",
  screenshot?: string,
) {
  const prompt = state.card.promptId;
  seen = [];
  input({ kind: "gesture", value });
  await until(
    () => seen.some((m) => m.tilt?.["rendered" + side] > 0.3),
    "visible " + value,
  );
  if (screenshot && process.env.ADB) {
    await pause(350);
    writeFileSync(
      screenshot,
      execFileSync(process.env.ADB, [
        "-s",
        "emulator-5554",
        "exec-out",
        "screencap",
        "-p",
      ]),
    );
  }
  await until(
    () =>
      rendered() &&
      state.tilt?.["rendered" + side] === 0 &&
      state.tilt?.[side.toLowerCase()] === 0 &&
      seen.some((m) => m.tilt?.["rendered" + side] > 0.3),
    "retreat " + value,
  );
  assert.equal(state.card.promptId, prompt);
  assert.equal(state.card.phase, "review");
  return seen.slice();
}
try {
  await until(() => rendered() && own && peer, "startup", 30000);
  if (state.glassesVoice) {
    input({ kind: "sdk-voice", active: false });
    await until(() => !state.glassesVoice, "mic off");
  }
  for (const value of [
    "right-partial",
    "left-partial",
    "right-cancel",
    "left-cancel",
  ]) {
    const task = await request();
    assert.equal(state.tilt.leftRole, "decline");
    const frames = await trace(
      value,
      value.startsWith("left") ? "Left" : "Right",
      value.endsWith("partial")
        ? "/tmp/lumi-" + value + "-progress.png"
        : undefined,
    );
    if (value.endsWith("partial"))
      assert.ok(
        frames.every(
          (m) => Math.max(m.tilt?.right || 0, m.tilt?.left || 0) <= 0.4,
        ),
      );
    assert.equal(
      peer.tasks.find((t: any) => t.id === task).status,
      "delivered",
    );
    input({ kind: "gesture", value: "right-held" });
    await until(
      () => peer.tasks.find((t: any) => t.id === task)?.status === "accepted",
      "accept",
    );
    await pause(2200);
    assert.equal(peer.tasks.find((t: any) => t.id === task).revision, 3);
    assert.equal(state.tilt.right, 0);
    await until(
      () => rendered() && state.card.phase === "status",
      "accepted status rendered",
    );
    input({ kind: "gesture", value: "left-tilt" });
    await until(() => state.card.phase === "quiet", "hide accepted status");
    console.log(
      "PASS " +
        value +
        " retreats without action; full hold then accepts exactly once",
    );
  }
  const declined = await request();
  input({ kind: "gesture", value: "left-tilt" });
  await until(
    () => peer.tasks.find((t: any) => t.id === declined)?.status === "declined",
    "decline",
  );
  await until(
    () => rendered() && state.card.phase === "status",
    "decline status rendered",
  );
  console.log("PASS coral left decline updates the request");
  input({ kind: "gesture", value: "left-tilt" });
  await until(() => state.card.phase === "quiet", "hide decline");
  input({ kind: "wake" });
  await until(
    () => rendered() && state.card.phase === "listening",
    "listening",
  );
  assert.equal(state.tilt.leftRole, "back / later");
  seen = [];
  input({ kind: "gesture", value: "left-partial" });
  await until(() => state.tilt.renderedLeft > 0.3, "neutral back bar");
  if (process.env.ADB) {
    await pause(350);
    writeFileSync(
      "/tmp/lumi-neutral-back-progress.png",
      execFileSync(process.env.ADB, [
        "-s",
        "emulator-5554",
        "exec-out",
        "screencap",
        "-p",
      ]),
    );
  }
  input({
    kind: "transcript",
    text: "Message Ben please bring water",
    final: true,
  });
  await until(() => rendered() && state.card.phase === "review", "new prompt");
  assert.equal(state.tilt.left, 0);
  assert.equal(state.tilt.right, 0);
  console.log("PASS neutral back styling and prompt-change reset");
  input({ kind: "gesture", value: "right-partial" });
  await until(
    () => state.tilt.renderedRight > 0.3,
    "partial before disconnect",
  );
  input({ kind: "device-lost" });
  await until(() => !state.motion.eligible, "disconnected");
  assert.equal(state.tilt.right, 0);
  assert.equal(state.tilt.left, 0);
  input({ kind: "device-ready" });
  await until(rendered, "reconnected");
  assert.equal(state.tilt.right, 0);
  assert.equal(own.messages.length, 0);
  console.log(
    "PASS disconnect resets feedback, discards unsent draft and sends nothing",
  );
  console.log("ALL TILT FEEDBACK CHECKS PASSED");
} finally {
  writeFileSync("/tmp/lumi-tilt-states.json", JSON.stringify(seen, null, 2));
  control.close();
  ben.close();
}
