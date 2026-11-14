import { test } from "node:test";
import assert from "node:assert/strict";
import { HandsFreeWard } from "../src/handsfree-core.ts";
function ward() {
  const w = new HandsFreeWard();
  w.staff.forEach((s) => (s.reachable = true));
  return w;
}
const create = {
  id: "c1",
  type: "request.create",
  to: "ben",
  room: "12",
  text: "bring the hoist",
};
test("full explicit lifecycle; sender cannot accept; completion requires acceptance", () => {
  const w = ward();
  const a = w.apply("aino", create);
  assert.equal(a.ok, true);
  const taskId = a.taskId!;
  assert.equal(
    w.apply("aino", { id: "bad", type: "request.accept", taskId, revision: 1 })
      .ok,
    false,
  );
  assert.equal(
    w.apply("ben", {
      id: "bad2",
      type: "request.complete",
      taskId,
      revision: 1,
    }).ok,
    false,
  );
  for (const [i, type] of [
    "request.delivered",
    "request.accept",
    "request.complete",
  ].entries())
    assert.equal(
      w.apply("ben", { id: `t${i}`, type, taskId, revision: i + 1 }).ok,
      true,
    );
  assert.equal(w.tasks[0].status, "completed");
});
test("actor-scoped idempotence, conflicting duplicate rejected", () => {
  const w = ward();
  const a = w.apply("aino", create);
  assert.deepEqual(w.apply("aino", create), a);
  assert.equal(w.tasks.length, 1);
  assert.equal(w.apply("aino", { ...create, room: "13" }).ok, false);
});
test("stale revision and outsider cannot act", () => {
  const w = ward();
  const a = w.apply("aino", create);
  const c = { id: "a", type: "request.accept", taskId: a.taskId, revision: 1 };
  assert.equal(w.apply("sara", c).ok, false);
  assert.equal(
    w.apply("ben", {
      id: "d",
      type: "request.delivered",
      taskId: a.taskId,
      revision: 1,
    }).ok,
    true,
  );
  assert.equal(w.apply("ben", c).ok, false);
});
test("recipient disconnect rejects; busy never inferred from text", () => {
  const w = ward();
  w.staff[1].reachable = false;
  assert.equal(w.apply("aino", create).ok, false);
  w.staff[1].reachable = true;
  assert.equal(
    w.apply("aino", {
      id: "m",
      type: "message.send",
      to: "ben",
      text: "I'm busy",
    }).ok,
    true,
  );
  assert.equal(w.messages[0].text, "I'm busy");
});
test("participant-only snapshots and no client actor spoofing", () => {
  const w = ward();
  w.apply("aino", create);
  assert.equal(w.snapshot("sara").tasks.length, 0);
  assert.equal(w.apply("sara", { ...create, from: "aino" }).ok, false);
});
test("declined distinct from cancelled; no completion afterwards", () => {
  const w = ward();
  const a = w.apply("aino", create);
  assert.equal(
    w.apply("ben", {
      id: "d",
      type: "request.decline",
      taskId: a.taskId,
      revision: 1,
    }).ok,
    true,
  );
  assert.equal(w.tasks[0].status, "declined");
  assert.equal(
    w.apply("ben", {
      id: "e",
      type: "request.complete",
      taskId: a.taskId,
      revision: 2,
    }).ok,
    false,
  );
});
test("room and text validation and unknown commands fail closed", () => {
  const w = ward();
  for (const c of [
    { ...create, room: "patient1" },
    { ...create, text: "" },
    { ...create, type: "assistant.run" },
    { ...create, to: "nobody" },
  ])
    assert.equal(w.apply("aino", c).ok, false);
  assert.equal(w.tasks.length, 0);
});
test("pending requests expire, accepted work stays accepted, stale accept fails", () => {
  let now = 1000;
  const w = new HandsFreeWard(() => now, 1000);
  w.staff.forEach((s) => (s.reachable = true));
  const a = w.apply("aino", create),
    b = w.apply("aino", { ...create, id: "second" });
  w.apply("ben", {
    id: "accept",
    type: "request.accept",
    taskId: b.taskId,
    revision: 1,
  });
  now = 2001;
  assert.equal(w.expire(), true);
  assert.equal(w.tasks[0].status, "expired");
  assert.equal(w.tasks[1].status, "accepted");
  assert.equal(
    w.apply("ben", {
      id: "late",
      type: "request.accept",
      taskId: a.taskId,
      revision: 1,
    }).ok,
    false,
  );
});
test("availability is explicit and independent of reachability", () => {
  const w = ward();
  assert.equal(
    w.apply("aino", { id: "busy", type: "presence.set", status: "busy" }).ok,
    true,
  );
  assert.equal(w.staff[0].status, "busy");
  assert.equal(w.staff[0].reachable, true);
  assert.equal(
    w.apply("aino", {
      id: "bad-status",
      type: "presence.set",
      status: "offline",
    }).ok,
    false,
  );
});
test("only recipient can mark message opened", () => {
  const w = ward();
  w.apply("aino", {
    id: "msg",
    type: "message.send",
    to: "ben",
    text: "Ready?",
  });
  const messageId = w.messages[0].id;
  assert.equal(
    w.apply("aino", { id: "bad-read", type: "message.read", messageId }).ok,
    false,
  );
  assert.equal(
    w.apply("ben", { id: "read", type: "message.read", messageId }).ok,
    true,
  );
  assert.ok(w.messages[0].readAt);
  assert.equal(w.snapshot("sara").messages.length, 0);
});
