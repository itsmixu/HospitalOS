import { readFileSync } from "node:fs";
import { createHash, randomUUID } from "node:crypto";
import { z } from "zod";
const seed = JSON.parse(
  readFileSync(new URL("../../demo-data/ward.json", import.meta.url), "utf8"),
) as { patients: Patient[]; staff: Staff[] };
type Patient = {
  id: string;
  name: string;
  room: string;
  reported: string;
  age?: number;
  warning?: string;
  summary?: string;
};
type Staff = { id: string; name: string; patients: string[] };
type Presence = "free" | "busy";
type Message = {
  id: string;
  from: string;
  to: string;
  text: string;
  at: string;
  status: "sent" | "read";
};
type Task = {
  id: string;
  patientId: string;
  from: string;
  to: string;
  status: "sent" | "delivered" | "accepted" | "completed" | "cancelled";
  summary: string;
  encounterId?: string;
};
type Draft = {
  id: string;
  patientId: string;
  owner: string;
  status: "open" | "review";
  reported: string;
  planned: string;
  created: number;
};
const text = z.string().trim().min(1).max(2000),
  key = z.string().min(1).max(100);
export const command = z
  .object({
    id: key,
    type: key,
    payload: z.record(z.string(), z.unknown()).default({}),
  })
  .strict();
export class Ward {
  staff = seed.staff;
  patients = seed.patients;
  tasks: Task[] = [];
  drafts: Draft[] = [];
  notes: {
    patientId: string;
    author: string;
    reported: string;
    planned: string;
    completed: string[];
    at: string;
  }[] = [];
  /** Declared availability (SPEC F1). Everyone starts free; the overview account has none. */
  presence = new Map<string, Presence>(
    seed.staff.filter((s) => s.id !== "observer").map((s) => [s.id, "free"]),
  );
  /** Direct messages between staff. Not patient-linked, so no clinical fields. */
  messages: Message[] = [];
  private seen = new Map<string, string>();
  /**
   * Colleagues who may help with this patient and are free right now — the routing rule for
   * "send 2 nurses": declared availability + assignment, never simulated proximity.
   */
  freeHelpers(actor: string, patientId: string) {
    return this.staff.filter(
      (s) =>
        s.id !== actor &&
        s.patients.includes(patientId) &&
        this.presence.get(s.id) === "free",
    );
  }
  person(id: string) {
    const s = this.staff.find((s) => s.id === id);
    if (!s) throw Error("Unknown staff");
    return s;
  }
  access(id: string, pid: string) {
    if (!this.person(id).patients.includes(pid)) throw Error("Access denied");
    const p = this.patients.find((p) => p.id === pid);
    if (!p) throw Error("Unknown patient");
    return p;
  }
  snapshot(id: string) {
    const s = this.person(id);
    return {
      me: { id: s.id, name: s.name, status: this.presence.get(s.id) ?? null },
      staff: this.staff.map((x) => ({
        id: x.id,
        name: x.name,
        status: this.presence.get(x.id) ?? null,
      })),
      messages: this.messages.filter((m) => m.from === id || m.to === id),
      patients: this.patients.filter((p) => s.patients.includes(p.id)),
      tasks: this.tasks.map((t) =>
        s.patients.includes(t.patientId)
          ? t
          : {
              id: t.id,
              room: this.patients.find((p) => p.id === t.patientId)!.room,
              from: t.from,
              to: t.to,
              status: t.status,
            },
      ),
      drafts: this.drafts.filter((d) => d.owner === id),
      notes: this.notes.filter((n) => s.patients.includes(n.patientId)),
    };
  }
  expire() {
    this.drafts = this.drafts.filter(
      (d) => Date.now() - d.created < 30 * 60 * 1000,
    );
  }
  handle(actor: string, input: unknown) {
    this.person(actor);
    this.expire();
    const c = command.parse(input);
    const dedup = actor + ":" + c.id;
    const fingerprint = createHash("sha256")
      .update(JSON.stringify(c))
      .digest("hex");
    if (this.seen.has(dedup)) {
      if (this.seen.get(dedup) !== fingerprint)
        throw Error("Command ID reused");
      return;
    }
    const p = c.payload;
    if (c.type === "patient.select") {
      this.access(actor, key.parse(p.patientId));
    } else if (c.type === "presence.update") {
      if (!this.presence.has(actor)) throw Error("No presence for this account");
      this.presence.set(actor, z.enum(["free", "busy"]).parse(p.status));
    } else if (c.type === "msg.send") {
      const v = z.object({ to: key, text }).strict().parse(p);
      this.person(v.to);
      if (v.to === actor) throw Error("Select another colleague");
      if (!this.presence.has(v.to)) throw Error("Cannot message this account");
      this.messages.push({
        id: randomUUID(),
        from: actor,
        to: v.to,
        text: v.text,
        at: new Date().toISOString(),
        status: "sent",
      });
      if (this.messages.length > 200) this.messages.shift();
    } else if (c.type === "msg.read") {
      const m = this.messages.find((m) => m.id === key.parse(p.messageId));
      if (!m || m.to !== actor) throw Error("Unknown message");
      m.status = "read";
    } else if (c.type === "help.auto") {
      // "Get help": the server picks free colleagues assigned to this patient (SPEC F1 routing).
      const v = z
        .object({ patientId: key, count: z.number().int().min(1).max(4), summary: text })
        .strict()
        .parse(p);
      this.access(actor, v.patientId);
      const helpers = this.freeHelpers(actor, v.patientId).slice(0, v.count);
      if (helpers.length === 0) throw Error("Nobody free for this patient right now");
      const encounterId = this.drafts.find(
        (d) => d.owner === actor && d.patientId === v.patientId && d.status === "open",
      )?.id;
      for (const h of helpers)
        this.tasks.push({
          id: randomUUID(),
          from: actor,
          to: h.id,
          patientId: v.patientId,
          summary: v.summary,
          status: "sent",
          encounterId,
        });
    } else if (c.type === "help.request") {
      const v = z
        .object({ patientId: key, to: key, summary: text })
        .strict()
        .parse(p);
      this.access(actor, v.patientId);
      this.access(v.to, v.patientId);
      if (actor === v.to) throw Error("Select another colleague");
      this.tasks.push({
        id: randomUUID(),
        from: actor,
        ...v,
        status: "sent",
        encounterId: this.drafts.find(
          (d) =>
            d.owner === actor &&
            d.patientId === v.patientId &&
            d.status === "open",
        )?.id,
      });
    } else if (
      [
        "message.delivered",
        "help.response",
        "help.complete",
        "help.cancel",
      ].includes(c.type)
    ) {
      const t = this.tasks.find((t) => t.id === key.parse(p.taskId));
      if (!t) throw Error("Unknown task");
      this.access(actor, t.patientId);
      if (c.type === "help.cancel") {
        if (actor !== t.from || !["sent", "delivered"].includes(t.status))
          throw Error("Cannot cancel");
        t.status = "cancelled";
      } else {
        if (actor !== t.to) throw Error("Only assigned recipient may update");
        if (c.type === "message.delivered") {
          if (t.status === "sent") t.status = "delivered";
        }
        if (c.type === "help.response") {
          if (!["sent", "delivered"].includes(t.status))
            throw Error("Request no longer pending");
          t.status = z.boolean().parse(p.accept) ? "accepted" : "cancelled";
        }
        if (c.type === "help.complete") {
          if (t.status !== "accepted") throw Error("Accept before completion");
          t.status = "completed";
        }
      }
    } else if (c.type === "log.start") {
      const v = z
        .object({ patientId: key, consent: z.literal(true) })
        .strict()
        .parse(p);
      this.access(actor, v.patientId);
      if (this.drafts.some((d) => d.owner === actor))
        throw Error("Finish or discard current encounter");
      this.drafts.push({
        id: randomUUID(),
        patientId: v.patientId,
        owner: actor,
        status: "open",
        reported: "",
        planned: "",
        created: Date.now(),
      });
    } else if (["log.stop", "log.confirm", "log.discard"].includes(c.type)) {
      const d = this.drafts.find((d) => d.id === key.parse(p.encounterId));
      if (!d || d.owner !== actor) throw Error("Access denied");
      this.access(actor, d.patientId);
      if (c.type === "log.stop") {
        if (d.status !== "open") throw Error("Already drafted");
        d.reported = z.string().max(2000).parse(p.reported);
        d.planned = z.string().max(2000).parse(p.planned);
        d.status = "review";
      }
      if (c.type === "log.confirm") {
        if (d.status !== "review") throw Error("Prepare draft first");
        const reported = z.string().max(2000).parse(p.reported);
        const planned = z.string().max(2000).parse(p.planned);
        this.notes.push({
          patientId: d.patientId,
          author: actor,
          reported,
          planned,
          completed: this.tasks
            .filter(
              (t) =>
                t.patientId === d.patientId &&
                t.status === "completed" &&
                t.from === actor &&
                t.encounterId === d.id,
            )
            .map((t) => t.summary),
          at: new Date().toISOString(),
        });
      }
      if (c.type !== "log.stop")
        this.drafts = this.drafts.filter((x) => x.id !== d.id);
    } else throw Error("Unsupported command");
    this.seen.set(dedup, fingerprint);
    if (this.seen.size > 5000) this.seen.delete(this.seen.keys().next().value!);
  }
}
