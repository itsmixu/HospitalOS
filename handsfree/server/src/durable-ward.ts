import { DatabaseSync } from "node:sqlite";
import { mkdirSync, chmodSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { createHash } from "node:crypto";
import { z } from "zod";
import { HandsFreeWard, type Ack, type Task } from "./handsfree-core.ts";
import { patientNoteSchema } from "./patient-notes.ts";
import type { Observation } from "./observations.ts";

const record = z
  .object({
    id: z.string(),
    from: z.string(),
    to: z.string(),
    room: z.string(),
    text: z.string(),
    status: z.string(),
    revision: z.number().int().positive(),
  })
  .passthrough();
const stateSchema = z
  .object({
    version: z.literal(1),
    tasks: z.array(record).max(500),
    messages: z
      .array(
        z
          .object({
            id: z.string(),
            from: z.string(),
            to: z.string(),
            text: z.string(),
          })
          .passthrough(),
      )
      .max(500),
    notes: z.array(patientNoteSchema).max(500).optional(),
    availability: z
      .record(z.string(), z.enum(["available", "busy"]))
      .optional(),
    observations: z
      .array(
        z
          .object({
            id: z.string(),
            patientId: z.string(),
            reviewer: z.string(),
            captureId: z.string(),
          })
          .passthrough(),
      )
      .max(500),
  })
  .strict();
function canonical(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(canonical).join(",")}]`;
  if (value !== null && typeof value === "object")
    return `{${Object.keys(value)
      .sort()
      .map(
        (k) =>
          `${JSON.stringify(k)}:${canonical((value as Record<string, unknown>)[k])}`,
      )
      .join(",")}}`;
  return JSON.stringify(value) ?? "null";
}
export class StorageUnavailable extends Error {
  constructor() {
    super(
      "Local ward storage unavailable; reconnect and reconcile before proceeding",
    );
  }
}

/** Snapshot and receipt commit together. Active patient contexts remain memory/session scoped. */
export class DurableWard extends HandsFreeWard {
  private readonly db: DatabaseSync;
  healthy = true;
  private inTransaction = false;
  constructor(path: string) {
    super();
    if (path !== ":memory:")
      mkdirSync(dirname(resolve(path)), { recursive: true, mode: 0o700 });
    this.db = new DatabaseSync(path);
    try {
      if (path !== ":memory:") chmodSync(path, 0o600);
      this.db
        .exec(`PRAGMA busy_timeout=3000; PRAGMA journal_mode=DELETE; PRAGMA synchronous=FULL;
        CREATE TABLE IF NOT EXISTS ward_state(id INTEGER PRIMARY KEY CHECK(id=1), body TEXT NOT NULL) STRICT;
        CREATE TABLE IF NOT EXISTS command_receipts(actor TEXT NOT NULL, id TEXT NOT NULL, digest TEXT NOT NULL, ack TEXT NOT NULL, PRIMARY KEY(actor,id)) STRICT;`);
      this.restore();
    } catch (e) {
      this.healthy = false;
      this.db.close();
      throw e;
    }
  }
  private restore() {
    const row = this.db.prepare("SELECT body FROM ward_state WHERE id=1").get();
    if (!row) return;
    const state = stateSchema.parse(JSON.parse(String(row.body)));
    for (const person of this.staff)
      person.status = state.availability?.[person.id] ?? "available";
    this.tasks = state.tasks as Task[];
    this.messages = state.messages;
    this.observations.records = state.observations as Observation[];
    this.patientNotes.records = state.notes ?? [];
  }
  private body() {
    return JSON.stringify({
      version: 1,
      availability: Object.fromEntries(
        this.staff.map((s) => [s.id, s.status ?? "available"]),
      ),
      tasks: this.tasks,
      messages: this.messages,
      observations: this.observations.records,
      notes: this.patientNotes.records,
    });
  }
  /** Only the authenticated actor's result is returned. This is a read, never a retry. */
  commandStatus(
    actor: string,
    id: string,
  ): { type: "command.status"; commandId: string; found: boolean; ack?: Ack } {
    if (!this.healthy) throw new StorageUnavailable();
    const row = this.db
      .prepare("SELECT ack FROM command_receipts WHERE actor=? AND id=?")
      .get(actor, id);
    return {
      type: "command.status",
      commandId: id,
      found: !!row,
      ...(row ? { ack: JSON.parse(String(row.ack)) as Ack } : {}),
    };
  }
  override apply(actor: string, input: unknown, session = actor): Ack {
    if (!this.healthy) throw new StorageUnavailable();
    if (
      !input ||
      typeof input !== "object" ||
      !("id" in input) ||
      typeof input.id !== "string" ||
      input.id.length > 100 ||
      !("type" in input)
    )
      return super.apply(actor, input, session);
    // Context commands must never restore a former connection's patient confirmation.
    if (input.type === "patient.confirm" || input.type === "context.clear")
      return super.apply(actor, input, session);
    const id = input.id,
      digest = createHash("sha256").update(canonical(input)).digest("hex");
    try {
      this.db.exec("BEGIN IMMEDIATE");
      const old = this.db
        .prepare(
          "SELECT digest,ack FROM command_receipts WHERE actor=? AND id=?",
        )
        .get(actor, id);
      if (old) {
        this.db.exec("COMMIT");
        return old.digest === digest
          ? (JSON.parse(String(old.ack)) as Ack)
          : {
              type: "ack",
              id,
              ok: false,
              reason: "Command identifier already used",
            };
      }
      const count = this.db
        .prepare("SELECT count(*) AS count FROM command_receipts")
        .get()!;
      if (Number(count.count) >= 10000) {
        this.db.exec("ROLLBACK");
        return {
          type: "ack",
          id,
          ok: false,
          reason: "Synthetic command capacity reached",
        };
      }
      // Refresh persistent entities inside the write lock; do not restore connection contexts.
      this.restore();
      this.inTransaction = true;
      let ack: Ack;
      try {
        ack = super.apply(actor, input, session);
      } finally {
        this.inTransaction = false;
      }
      this.db
        .prepare(
          "INSERT INTO ward_state(id,body) VALUES(1,?) ON CONFLICT(id) DO UPDATE SET body=excluded.body",
        )
        .run(this.body());
      this.db
        .prepare(
          "INSERT INTO command_receipts(actor,id,digest,ack) VALUES(?,?,?,?)",
        )
        .run(actor, id, digest, JSON.stringify(ack));
      this.db.exec("COMMIT");
      return ack;
    } catch {
      try {
        this.db.exec("ROLLBACK");
      } catch {}
      // Never emit a success from the base in-memory dedup cache after a failed commit.
      this.healthy = false;
      throw new StorageUnavailable();
    }
  }
  override expire(now = Date.now()): boolean {
    if (this.inTransaction) return super.expire(now);
    if (!this.healthy) throw new StorageUnavailable();
    try {
      this.db.exec("BEGIN IMMEDIATE");
      this.restore();
      const changed = super.expire(now);
      if (changed)
        this.db
          .prepare(
            "INSERT INTO ward_state(id,body) VALUES(1,?) ON CONFLICT(id) DO UPDATE SET body=excluded.body",
          )
          .run(this.body());
      this.db.exec("COMMIT");
      return changed;
    } catch {
      try {
        this.db.exec("ROLLBACK");
      } catch {}
      this.healthy = false;
      throw new StorageUnavailable();
    }
  }
  close() {
    this.db.close();
  }
}
