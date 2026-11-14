import { test } from "node:test";
import assert from "node:assert/strict";
import { Ward } from "../src/core.ts";
import { runRules } from "../src/assistant.ts";
let seq = 0;
const call = (w: Ward, who: string, type: string, payload: Record<string, unknown>) =>
  w.handle(who, { id: "t" + ++seq, type, payload });

test("presence is declared, shown to everyone, and drives who is free", () => {
  const w = new Ward();
  assert.deepEqual(
    w.freeHelpers("aino", "p1").map((s) => s.id),
    ["ben", "sara"],
  );
  call(w, "ben", "presence.update", { status: "busy" });
  assert.equal(w.snapshot("aino").staff.find((s) => s.id === "ben")?.status, "busy");
  assert.deepEqual(w.freeHelpers("aino", "p1").map((s) => s.id), ["sara"]);
  assert.throws(() => call(w, "observer", "presence.update", { status: "free" }));
  assert.throws(() => call(w, "ben", "presence.update", { status: "asleep" }));
});

test("direct messages reach only sender and recipient; only the recipient marks read", () => {
  const w = new Ward();
  call(w, "aino", "msg.send", { to: "ben", text: "Bring the hoist" });
  const id = w.messages[0].id;
  assert.equal(w.snapshot("ben").messages.length, 1);
  assert.equal(w.snapshot("sara").messages.length, 0);
  assert.throws(() => call(w, "aino", "msg.read", { messageId: id }));
  call(w, "ben", "msg.read", { messageId: id });
  assert.equal(w.messages[0].status, "read");
  assert.throws(() => call(w, "aino", "msg.send", { to: "aino", text: "Hi" }));
  assert.throws(() => call(w, "aino", "msg.send", { to: "observer", text: "Hi" }));
});

test("offline assistant: 'two nurses to my room' asks two free assigned colleagues", () => {
  const w = new Ward();
  const r = runRules(w, "aino", "Two nurses to my room now", { patientId: "p1" });
  assert.equal(r.source, "rules");
  assert.deepEqual(w.tasks.map((t) => t.to).sort(), ["ben", "sara"]);
  assert(w.tasks.every((t) => t.patientId === "p1" && t.from === "aino" && t.status === "sent"));
  assert.match(r.say, /Room 12/);
});

test("offline assistant respects assignment and availability", () => {
  const w = new Ward();
  call(w, "sara", "presence.update", { status: "busy" });
  // Patient B (p2): Ben isn't assigned, Sara is busy, so only Leo can be asked.
  const r = runRules(w, "aino", "I need three nurses here", { patientId: "p2" });
  assert.deepEqual(w.tasks.map((t) => t.to), ["leo"]);
  assert.match(r.say, /Only Leo is free/);
  // No patient context: nothing is sent.
  const none = runRules(w, "aino", "send two nurses", {});
  assert.match(none.say, /Which patient/);
  assert.equal(w.tasks.length, 1);
});

test("offline assistant: status, message and record answer", () => {
  const w = new Ward();
  runRules(w, "aino", "set me busy", {});
  assert.equal(w.presence.get("aino"), "busy");
  const m = runRules(w, "aino", "tell Ben the hoist is in room 7", {});
  assert.equal(w.messages[0].to, "ben");
  assert.match(m.say, /Sent to Ben/);
  const a = runRules(w, "aino", "what did the patient report", { patientId: "p1" });
  assert.match(a.say, /dizziness/);
});

test("help.auto routes to free assigned colleagues and refuses when none", () => {
  const w = new Ward();
  call(w, "aino", "help.auto", { patientId: "p1", count: 1, summary: "Lift" });
  assert.deepEqual(w.tasks.map((t) => t.to), ["ben"]);
  call(w, "ben", "presence.update", { status: "busy" });
  call(w, "sara", "presence.update", { status: "busy" });
  assert.throws(() => call(w, "aino", "help.auto", { patientId: "p1", count: 2, summary: "Lift" }), /Nobody free/);
  assert.throws(() => call(w, "observer", "help.auto", { patientId: "p1", count: 1, summary: "x" }));
});
