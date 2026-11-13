import { test } from "node:test";
import assert from "node:assert/strict";
import { Ward } from "../src/core.ts";
let seq = 0;
const call = (
  w: Ward,
  who: string,
  type: string,
  payload: Record<string, unknown>,
) => w.handle(who, { id: String(++seq), type, payload });
test("patient permissions and filtered ward payload", () => {
  const w = new Ward();
  assert.throws(() =>
    call(w, "observer", "patient.select", { patientId: "p1" }),
  );
  call(w, "aino", "help.request", {
    patientId: "p1",
    to: "ben",
    summary: "Sensitive task",
  });
  const s = JSON.stringify(w.snapshot("observer"));
  assert(!s.includes("Sensitive"));
  assert(!s.includes("dizziness"));
  assert(!s.includes("patientId"));
});
test("delivery is not acceptance; actor and transition checks", () => {
  const w = new Ward();
  call(w, "aino", "help.request", {
    patientId: "p1",
    to: "ben",
    summary: "Assist",
  });
  const taskId = w.tasks[0].id;
  assert.throws(() => call(w, "ben", "help.complete", { taskId }));
  assert.throws(() =>
    call(w, "aino", "help.response", { taskId, accept: true }),
  );
  call(w, "ben", "message.delivered", { taskId });
  assert.equal(w.tasks[0].status, "delivered");
  call(w, "ben", "help.response", { taskId, accept: true });
  call(w, "ben", "help.complete", { taskId });
  assert.equal(w.tasks[0].status, "completed");
});
test("consent, reviewed notes and deletion", () => {
  const w = new Ward();
  assert.throws(() =>
    call(w, "aino", "log.start", { patientId: "p1", consent: false }),
  );
  call(w, "aino", "log.start", { patientId: "p1", consent: true });
  const encounterId = w.drafts[0].id;
  call(w, "aino", "log.stop", {
    encounterId,
    reported: "Feels dizzy",
    planned: "Will walk",
  });
  assert.equal(w.notes.length, 0);
  assert.throws(() =>
    call(w, "ben", "log.confirm", { encounterId, reported: "x", planned: "y" }),
  );
  call(w, "aino", "log.confirm", {
    encounterId,
    reported: "Feels dizzy",
    planned: "Will walk",
  });
  assert.deepEqual(w.notes[0].completed, []);
  assert.equal(w.drafts.length, 0);
});
test("idempotent retry and cancelled task cannot revive", () => {
  const w = new Ward();
  const msg = {
    id: "retry",
    type: "help.request",
    payload: { patientId: "p1", to: "ben", summary: "Assist" },
  };
  w.handle("aino", msg);
  w.handle("aino", msg);
  assert.equal(w.tasks.length, 1);
  const taskId = w.tasks[0].id;
  call(w, "aino", "help.cancel", { taskId });
  assert.throws(() =>
    call(w, "ben", "help.response", { taskId, accept: true }),
  );
});

test("completed tasks stay in their own encounter", () => {
  const w = new Ward();
  call(w, "aino", "log.start", { patientId: "p1", consent: true });
  const first = w.drafts[0].id;
  call(w, "aino", "help.request", {
    patientId: "p1",
    to: "ben",
    summary: "Assist",
  });
  const taskId = w.tasks[0].id;
  call(w, "ben", "help.response", { taskId, accept: true });
  call(w, "ben", "help.complete", { taskId });
  call(w, "aino", "log.stop", {
    encounterId: first,
    reported: "",
    planned: "",
  });
  call(w, "aino", "log.confirm", {
    encounterId: first,
    reported: "",
    planned: "",
  });
  assert.deepEqual(w.notes[0].completed, ["Assist"]);
  call(w, "aino", "log.start", { patientId: "p1", consent: true });
  const second = w.drafts[0].id;
  call(w, "aino", "log.stop", {
    encounterId: second,
    reported: "",
    planned: "",
  });
  call(w, "aino", "log.confirm", {
    encounterId: second,
    reported: "",
    planned: "",
  });
  assert.deepEqual(w.notes[1].completed, []);
});
