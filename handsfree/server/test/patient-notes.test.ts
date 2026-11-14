import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { DurableWard } from "../src/durable-ward.ts";
import { patients } from "../src/observations.ts";
const content = "I gave 400mg of ibuprofen at 12:00.";
function confirmed(
  w: DurableWard,
  actor = "aino",
  session = "s",
  patientId = "demo-a",
) {
  const p = patients.find((p) => p.id === patientId)!;
  const ack = w.apply(
    actor,
    {
      id: Math.random().toString(),
      type: "patient.confirm",
      patientId,
      encounterId: p.encounterId,
    },
    session,
  );
  assert.equal(ack.ok, true);
  return ack.context!;
}
function note(context: object, overrides: object = {}) {
  return {
    id: "note-command",
    type: "note.save",
    ...context,
    draftId: "draft-1",
    text: content,
    reviewedAt: new Date().toISOString(),
    source: "manual-dictation",
    ...overrides,
  };
}
test("manual note preserves author statement without deriving dose, event time or administration", () => {
  const w = new DurableWard(":memory:");
  try {
    const c = confirmed(w),
      command = note(c),
      ack = w.apply("aino", command, "s");
    assert.equal(ack.ok, true);
    assert.ok(ack.noteId);
    const saved = w.snapshot("aino", "s").notes[0];
    assert.equal(saved.text, content);
    assert.equal(saved.eventTime, null);
    assert.equal(saved.source, "manual-dictation");
    assert.equal(saved.author, "aino");
    assert.equal(saved.synthetic, true);
    assert.equal(saved.reviewedAt, command.reviewedAt);
    assert.equal(w.tasks.length, 0);
    assert.equal(w.snapshot("ben").notes.length, 0);
    assert.equal(w.snapshot("sara").notes.length, 0);
  } finally {
    w.close();
  }
});
test("note needs current confirmed assignment and cannot move between patients", () => {
  const w = new DurableWard(":memory:");
  try {
    assert.equal(
      w.apply(
        "aino",
        note({ patientId: "demo-a", encounterId: "enc-a", contextRevision: 1 }),
        "s",
      ).ok,
      false,
    );
    const c = confirmed(w);
    assert.equal(
      w.apply("aino", note(c, { id: "other-session" }), "different").ok,
      false,
    );
    confirmed(w, "aino", "s", "demo-b");
    assert.equal(w.apply("aino", note(c, { id: "switched" }), "s").ok, false);
    assert.equal(w.apply("sara", note(c, { id: "spoofed" }), "s").ok, false);
    assert.equal(w.patientNotes.records.length, 0);
  } finally {
    w.close();
  }
});
test("duplicate save is idempotent and changed draft content is rejected", () => {
  const w = new DurableWard(":memory:");
  try {
    const c = confirmed(w),
      command = note(c),
      ack = w.apply("aino", command, "s");
    assert.deepEqual(w.apply("aino", command, "s"), ack);
    assert.equal(
      w.apply("aino", { ...command, id: "duplicate-draft" }, "s").noteId,
      ack.noteId,
    );
    assert.equal(
      w.apply(
        "aino",
        {
          ...command,
          id: "changed-draft",
          text: "I did not give the medication.",
        },
        "s",
      ).ok,
      false,
    );
    assert.equal(
      w.apply("aino", { ...command, text: "changed" }, "s").ok,
      false,
    );
    assert.equal(w.patientNotes.records.length, 1);
  } finally {
    w.close();
  }
});
test("note review rejects malformed content, provenance and clocks without truncating", () => {
  const w = new DurableWard(":memory:");
  try {
    const c = confirmed(w);
    let index = 0;
    for (const extra of [
      { text: "x".repeat(161) },
      { text: "   " },
      { text: "first\nsecond" },
      { source: "dat-camera" },
      { reviewedAt: new Date(Date.now() + 60000).toISOString() },
      { reviewedAt: new Date(Date.now() - 700000).toISOString() },
      { dose: 400 },
    ])
      assert.equal(
        w.apply("aino", note(c, { ...extra, id: "invalid-" + index++ }), "s")
          .ok,
        false,
      );
    assert.equal(w.patientNotes.records.length, 0);
  } finally {
    w.close();
  }
});
test("note and message survive restart; lost acknowledgement is recovered without replay", () => {
  const directory = mkdtempSync(join(tmpdir(), "lumi-notes-")),
    path = join(directory, "ward.sqlite");
  let w = new DurableWard(path);
  try {
    const command = note(confirmed(w));
    const ack = w.apply("aino", command, "s");
    w.staff.find((s) => s.id === "ben")!.reachable = true;
    const text = "Do not send the trolley. I will come at 12:00 — thanks!";
    const sent = w.apply("aino", {
      id: "message-command",
      type: "message.send",
      to: "ben",
      text,
    });
    assert.ok(sent.messageId);
    w.close();
    w = new DurableWard(path);
    assert.equal(w.snapshot("aino").notes[0].text, content);
    assert.equal(w.snapshot("aino").context, null);
    assert.deepEqual(w.commandStatus("aino", command.id).ack, ack);
    assert.equal(w.commandStatus("ben", command.id).found, false);
    assert.deepEqual(w.apply("aino", command, "new-session"), ack);
    assert.equal(w.patientNotes.records.length, 1);
    assert.equal(w.snapshot("ben").messages[0].text, text);
    assert.ok(w.snapshot("ben").messages[0].sentAt);
    assert.equal(w.snapshot("sara").messages.length, 0);
    assert.equal(
      w.apply("aino", { ...command, id: "new-attempt" }, "new-session").ok,
      false,
    );
  } finally {
    w.close();
    rmSync(directory, { recursive: true, force: true });
  }
});
test("removing patient assignment removes note visibility and blocks further saves", () => {
  const w = new DurableWard(":memory:"),
    p = patients[0],
    before = [...p.staff];
  try {
    const c = confirmed(w);
    assert.equal(w.apply("aino", note(c), "s").ok, true);
    p.staff = p.staff.filter((s) => s !== "aino");
    assert.equal(w.snapshot("aino").notes.length, 0);
    assert.equal(
      w.apply("aino", note(c, { id: "removed", draftId: "new" }), "s").ok,
      false,
    );
  } finally {
    p.staff = before;
    w.close();
  }
});
