import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, rmSync, statSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { DatabaseSync } from "node:sqlite";
import { DurableWard, StorageUnavailable } from "../src/durable-ward.ts";
const create = {
  id: "request-1",
  type: "request.create",
  to: "ben",
  room: "12",
  text: "bring the hoist",
};
function connected(w: DurableWard) {
  w.staff.forEach((s) => (s.reachable = true));
  return w;
}
function fixture(run: (path: string) => void) {
  const dir = mkdtempSync(join(tmpdir(), "hospitalos-durable-"));
  try {
    run(join(dir, "ward.sqlite"));
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}
test("accepted task survives restart; reachability and patient contexts do not", () =>
  fixture((path) => {
    let w = connected(new DurableWard(path));
    const a = w.apply("aino", create, "s1");
    assert.equal(
      w.apply(
        "ben",
        {
          id: "accept-1",
          type: "request.accept",
          taskId: a.taskId,
          revision: 1,
        },
        "s2",
      ).ok,
      true,
    );
    w.apply(
      "aino",
      {
        id: "confirm-1",
        type: "patient.confirm",
        patientId: "demo-a",
        encounterId: "enc-a",
      },
      "s1",
    );
    w.close();
    w = new DurableWard(path);
    assert.equal(w.tasks[0].status, "accepted");
    assert.equal(
      w.staff.some((s) => s.reachable),
      false,
    );
    assert.equal(w.snapshot("aino", "s1").context, null);
    assert.equal(statSync(path).mode & 0o777, 0o600);
    w.close();
  }));
test("lost acknowledgement reconciles without executing; duplicate after restart stays one task", () =>
  fixture((path) => {
    let w = connected(new DurableWard(path));
    const ack = w.apply("aino", create, "old");
    w.close();
    w = connected(new DurableWard(path));
    assert.deepEqual(w.commandStatus("aino", create.id).ack, ack);
    assert.equal(w.commandStatus("sara", create.id).found, false);
    assert.deepEqual(w.apply("aino", create, "new"), ack);
    assert.equal(w.tasks.length, 1);
    assert.equal(w.apply("aino", { ...create, room: "13" }, "new").ok, false);
    assert.equal(w.tasks.length, 1);
    w.close();
  }));
test("rejected command receipt is stable across sessions and object property order", () =>
  fixture((path) => {
    const w = new DurableWard(path);
    const ack = w.apply("aino", create, "s1");
    assert.equal(ack.ok, false);
    connected(w);
    const reordered = {
      text: create.text,
      room: create.room,
      to: create.to,
      type: create.type,
      id: create.id,
    };
    assert.deepEqual(w.apply("aino", reordered, "s2"), ack);
    assert.equal(w.tasks.length, 0);
    w.close();
  }));
test("commit failure rolls back task and receipt and fails closed", () =>
  fixture((path) => {
    const w = connected(new DurableWard(path));
    const injector = new DatabaseSync(path);
    injector.exec(
      "CREATE TRIGGER fail_receipt BEFORE INSERT ON command_receipts BEGIN SELECT RAISE(ABORT,'test failure'); END;",
    );
    injector.close();
    assert.throws(() => w.apply("aino", create), StorageUnavailable);
    assert.equal(w.healthy, false);
    assert.throws(() => w.apply("aino", create), StorageUnavailable);
    w.close();
    const recovered = new DurableWard(path);
    assert.equal(recovered.tasks.length, 0);
    assert.equal(recovered.commandStatus("aino", create.id).found, false);
    recovered.close();
  }));
test("invalid persisted state fails startup rather than losing records silently", () =>
  fixture((path) => {
    const db = new DatabaseSync(path);
    db.exec(
      "CREATE TABLE ward_state(id INTEGER PRIMARY KEY,body TEXT NOT NULL) STRICT",
    );
    db.prepare("INSERT INTO ward_state VALUES(1,?)").run('{"version":999}');
    db.close();
    assert.throws(() => new DurableWard(path));
  }));
test("two ward instances serialize writes without losing the other task", () =>
  fixture((path) => {
    const a = connected(new DurableWard(path)),
      b = connected(new DurableWard(path));
    a.apply("aino", create);
    b.apply("aino", { ...create, id: "second", room: "7" });
    a.close();
    b.close();
    const w = new DurableWard(path);
    assert.equal(w.tasks.length, 2);
    w.close();
  }));
test("saved observation and message survive restart, active context never does", () =>
  fixture((path) => {
    let w = connected(new DurableWard(path));
    const context = w.apply(
      "aino",
      {
        id: "p",
        type: "patient.confirm",
        patientId: "demo-a",
        encounterId: "enc-a",
      },
      "s",
    ).context!;
    const now = new Date().toISOString();
    const save = {
      id: "save",
      type: "observation.save",
      ...context,
      captureId: "capture",
      capturedAt: now,
      reviewedAt: now,
      profile: "HOS-BP-1/v1",
      values: { systolic: 137, diastolic: 82, pulse: 71 },
      original: { systolic: 137, diastolic: 82, pulse: 71 },
      units: { pressure: "mmHg", pulse: "bpm" },
      corrected: [],
      source: "injected-camera",
    };
    const ack = w.apply("aino", save, "s");
    assert.equal(ack.ok, true);
    w.apply("aino", {
      id: "msg",
      type: "message.send",
      to: "ben",
      text: "Hello",
    });
    w.apply("aino", { id: "busy", type: "presence.set", status: "busy" });
    w.close();
    w = connected(new DurableWard(path));
    assert.equal(w.observations.records.length, 1);
    assert.equal(w.messages.length, 1);
    assert.equal(w.staff[0].status, "busy");
    assert.deepEqual(w.apply("aino", save, "new-session"), ack);
    assert.equal(w.observations.records.length, 1);
    assert.equal(
      w.apply(
        "aino",
        {
          id: "share",
          type: "observation.share",
          ...context,
          observationId: ack.observationId,
          to: "ben",
          text: "Review",
        },
        "new-session",
      ).ok,
      false,
    );
    assert.equal(w.snapshot("ben").observations.length, 0);
    w.close();
  }));
test("expiry is durable and cannot revive a request after restart", () =>
  fixture((path) => {
    let w = connected(new DurableWard(path));
    const ack = w.apply("aino", create);
    const expiry = w.tasks[0].expiresAt!;
    assert.equal(w.expire(expiry + 1), true);
    w.close();
    w = connected(new DurableWard(path));
    assert.equal(w.tasks[0].status, "expired");
    assert.equal(
      w.apply("ben", {
        id: "accept",
        type: "request.accept",
        taskId: ack.taskId,
        revision: 1,
      }).ok,
      false,
    );
    w.close();
  }));
test("100 request lifecycles retain exactly one task per command across reopen", () =>
  fixture((path) => {
    let w = connected(new DurableWard(path));
    for (let i = 0; i < 100; i++) {
      if (i === 50) {
        w.close();
        w = connected(new DurableWard(path));
      }
      const command = { ...create, id: `load-${i}` };
      const ack = w.apply("aino", command, `a-${i}`);
      assert.equal(ack.ok, true);
      assert.deepEqual(w.apply("aino", command, `reconnected-${i}`), ack);
      for (const [revision, type] of [
        "request.delivered",
        "request.accept",
        "request.complete",
      ].entries())
        assert.equal(
          w.apply("ben", {
            id: `${type}-${i}`,
            type,
            taskId: ack.taskId,
            revision: revision + 1,
          }).ok,
          true,
        );
    }
    w.close();
    w = new DurableWard(path);
    assert.equal(w.tasks.length, 100);
    assert.ok(
      w.tasks.every((t) => t.status === "completed" && t.revision === 4),
    );
    w.close();
  }));
