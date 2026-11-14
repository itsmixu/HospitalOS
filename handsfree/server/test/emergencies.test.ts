import { test } from "node:test";
import assert from "node:assert/strict";
import { HandsFreeWard } from "../src/handsfree-core.ts";
import { DurableWard } from "../src/durable-ward.ts";
import { patients } from "../src/observations.ts";
import scenario from "../../../prototype/demo-data/handsfree-code-blue.json" with { type: "json" };
import { mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
function ward() {
  const w = new HandsFreeWard();
  w.staff.forEach((s) => (s.reachable = true));
  return w;
}
const create = { id: "emergency-1", type: "emergency.create", ...scenario };
function respond(
  w: HandsFreeWard,
  actor = "aino",
  type = "emergency.acknowledge",
  revision = 1,
) {
  return w.apply(actor, {
    id: `${actor}-${type}-${revision}`,
    type,
    emergencyId: w.emergencies.records[0].id,
    revision,
  });
}
test("assigned patient records carry authored care and prescriptions without widening assignment", () => {
  const w = ward();
  const p = w.snapshot("aino").patients.find((p) => p.id === "demo-a")!;
  assert.match(p.admissionReason, /Blood-pressure/);
  assert.equal(p.medications[0].prescribedBy, "Dr Demo");
  assert.equal(p.careHistory[1].author, "Ben L.");
  assert.ok(!w.snapshot("aino").patients.some((p) => p.id === "demo-c"));
});
test("busy colleagues receive only metadata until acknowledged; ISBAR does not grant chart access", () => {
  const w = ward();
  w.staff.find((s) => s.id === "aino")!.status = "busy";
  assert.equal(w.apply("ben", create).ok, true);
  const before = w.snapshot("aino").emergencies[0];
  assert.equal(before.room, "8");
  assert.equal("isbar" in before, false);
  assert.equal("patientId" in before, false);
  assert.equal(respond(w).ok, true);
  const after = w.snapshot("aino").emergencies[0];
  assert.deepEqual(after.isbar, scenario.isbar);
  assert.equal(after.response, "acknowledged");
  assert.ok(!w.snapshot("aino").patients.some((p) => p.id === "demo-c"));
  assert.equal("isbar" in w.snapshot("sara").emergencies[0], false);
});
test("creation requires assignment, exact encounter, complete report, and connected recipient", () => {
  const w = ward();
  assert.equal(w.apply("aino", create).ok, false);
  assert.equal(
    w.apply("ben", { ...create, id: "bad-enc", encounterId: "other" }).ok,
    false,
  );
  assert.equal(
    w.apply("ben", { ...create, id: "bad-report", isbar: { identify: "x" } })
      .ok,
    false,
  );
  w.staff.forEach((s) => (s.reachable = false));
  assert.equal(w.apply("ben", { ...create, id: "none" }).ok, false);
  assert.equal(w.emergencies.records.length, 0);
});
test("stable command IDs deduplicate; stale revisions and nonrecipients cannot respond", () => {
  const w = ward();
  const ack = w.apply("ben", create);
  assert.deepEqual(w.apply("ben", create), ack);
  assert.equal(w.emergencies.records.length, 1);
  assert.equal(w.apply("ben", { ...create, id: "new" }).ok, false);
  assert.equal(respond(w, "ben").ok, false);
  assert.equal(respond(w).ok, true);
  assert.equal(respond(w, "sara", "emergency.acknowledge", 1).ok, false);
  assert.equal(respond(w, "sara", "emergency.acknowledge", 2).ok, true);
});
test("decline affects only its recipient; reporter-only resolution removes every briefing", () => {
  const w = ward();
  w.apply("ben", create);
  assert.equal(respond(w, "aino", "emergency.decline").ok, true);
  assert.equal(w.emergencies.records[0].status, "active");
  assert.equal(respond(w, "sara", "emergency.acknowledge", 2).ok, true);
  assert.equal(respond(w, "sara", "emergency.resolve", 3).ok, false);
  assert.equal(respond(w, "ben", "emergency.resolve", 3).ok, true);
  assert.equal(w.snapshot("sara").emergencies[0].status, "resolved");
  assert.equal("isbar" in w.snapshot("sara").emergencies[0], false);
  assert.equal(respond(w, "aino", "emergency.acknowledge", 4).ok, false);
});
test("revoked reporter assignment removes briefing and prevents new response", () => {
  const w = ward();
  w.apply("ben", create);
  respond(w);
  const p = patients.find((p) => p.id === "demo-c")!;
  const old = p.staff;
  try {
    p.staff = ["sara"];
    assert.equal(w.snapshot("aino").emergencies[0].briefingUnavailable, true);
    assert.equal("isbar" in w.snapshot("aino").emergencies[0], false);
    assert.equal(respond(w, "sara", "emergency.acknowledge", 2).ok, false);
  } finally {
    p.staff = old;
  }
});
test("durable response and receipt survive restart without duplicate emergency", () => {
  const path = join(
    mkdtempSync(join(tmpdir(), "lumi-emergency-")),
    "ward.sqlite",
  );
  let w = new DurableWard(path);
  w.staff.forEach((s) => (s.reachable = true));
  const ack = w.apply("ben", create);
  assert.equal(ack.ok, true);
  assert.equal(respond(w).ok, true);
  w.close();
  w = new DurableWard(path);
  try {
    assert.deepEqual(w.apply("ben", create), ack);
    assert.equal(w.emergencies.records.length, 1);
    assert.deepEqual(w.snapshot("aino").emergencies[0].isbar, scenario.isbar);
    assert.equal(w.snapshot("aino").emergencies[0].response, "acknowledged");
  } finally {
    w.close();
  }
});
