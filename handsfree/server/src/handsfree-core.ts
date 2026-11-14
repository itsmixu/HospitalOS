import { randomUUID } from "node:crypto";
import { z } from "zod";
import { PatientNotes, noteCommandSchema } from "./patient-notes.ts";
import {
  Observations,
  observationSchema,
  patients,
  type Observation,
  type Context,
} from "./observations.ts";

export type Person = {
  id: string;
  name: string;
  reachable: boolean;
  status?: "available" | "busy";
};
export type Task = {
  id: string;
  from: string;
  to: string;
  room: string;
  text: string;
  status: string;
  revision: number;
  createdAt?: number;
  expiresAt?: number;
  required?: number;
  recipients?: string[];
  acceptedBy?: string[];
  deliveredTo?: string[];
  declinedBy?: string[];
  completedBy?: string[];
  attachment?: Pick<
    Observation,
    | "id"
    | "patientId"
    | "encounterId"
    | "values"
    | "units"
    | "capturedAt"
    | "corrected"
    | "source"
  > & { patientName: string; measurementTime: null };
};
const base = { id: z.string().min(1).max(100) };
const taskFields = {
  taskId: z.string().min(1).max(100),
  revision: z.number().int().positive(),
};
const request = z
  .object({
    ...base,
    type: z.literal("request.create"),
    to: z.string(),
    room: z.string().regex(/^\d{1,3}$/),
    text: z.string().trim().min(1).max(120),
  })
  .strict();
const broadcast = z.object({...base,type:z.literal("request.broadcast"),room:z.string().regex(/^\d{1,3}$/),required:z.number().int().min(1).max(10)}).strict();
const message = z
  .object({
    ...base,
    type: z.literal("message.send"),
    to: z.string(),
    text: z.string().trim().min(1).max(160),
  })
  .strict();
const change = z
  .object({
    ...base,
    ...taskFields,
    type: z.enum([
      "request.delivered",
      "request.accept",
      "request.decline",
      "request.complete",
      "request.cancel",
    ]),
  })
  .strict();
const presence = z
  .object({
    ...base,
    type: z.literal("presence.set"),
    status: z.enum(["available", "busy"]),
  })
  .strict();
const read = z
  .object({
    ...base,
    type: z.literal("message.read"),
    messageId: z.string().min(1).max(100),
  })
  .strict();
const schema = z.union([
  request,
  broadcast,
  message,
  change,
  presence,
  read,
  observationSchema,
  noteCommandSchema,
]);
export type Ack = {
  type: "ack";
  id: string;
  ok: boolean;
  taskId?: string;
  reason?: string;
  context?: Context;
  observationId?: string;
  noteId?: string;
  messageId?: string;
};

/** Isolated synthetic ward. Volatile by design in HF1; no patient records or cloud route. */
export class HandsFreeWard {
  constructor(
    protected readonly clock: () => number = Date.now,
    protected readonly requestTtlMs = 120000,
  ) {}
  expire(now = this.clock()): boolean {
    let changed = false;
    for (const t of this.tasks)
      if (
        ["sent", "delivered"].includes(t.status) &&
        t.expiresAt !== undefined &&
        t.expiresAt <= now
      ) {
        t.status = "expired";
        t.revision++;
        changed = true;
      }
    return changed;
  }
  staff: Person[] = [
    { id: "aino", name: "Aino", reachable: false },
    { id: "ben", name: "Ben", reachable: false },
    { id: "sara", name: "Sara", reachable: false },
  ];
  observations = new Observations();
  patientNotes = new PatientNotes();
  tasks: Task[] = [];
  messages: {
    id: string;
    from: string;
    to: string;
    text: string;
    readAt?: number;
    sentAt?: number;
  }[] = [];
  private dedup = new Map<string, { payload: string; ack: Ack }>();
  snapshot(actor: string, session = actor) {
    return {
      patients: patients
        .filter((p) => p.staff.includes(actor))
        .map(({ staff, ...p }) => p),
      context: this.observations.context(session) ?? null,
      observations: this.observations.records.filter(
        (o) => o.reviewer === actor && this.observations.assigned(actor, o.patientId)?.encounterId === o.encounterId,
      ),
      notes: this.patientNotes.records.filter(
        (n) =>
          n.author === actor &&
          this.observations.assigned(actor, n.patientId)?.encounterId === n.encounterId,
      ),
      type: "snapshot",
      me: actor,
      staff: this.staff,
      tasks: this.tasks.filter((t) => t.from === actor || t.to === actor || t.recipients?.includes(actor)).map((t): Task & {attachmentUnavailable?: boolean}=>{
        if(!t.required){
          if (!t.attachment) return t;
          const patient = this.observations.assigned(actor, t.attachment.patientId);
          if (patient?.encounterId === t.attachment.encounterId) return t;
          const {attachment, ...generic} = t;
          return {...generic, text: "Patient-bound request", attachmentUnavailable: true};
        }
        const accepted=t.acceptedBy??[];
        const status=t.from===actor?t.status:(t.completedBy?.includes(actor)?"completed":accepted.includes(actor)?"accepted":t.declinedBy?.includes(actor)?"declined":["cancelled","expired"].includes(t.status)?t.status:accepted.length>=t.required?"filled":t.deliveredTo?.includes(actor)?"delivered":"sent");
        return {...t,to:t.from===actor?"available nurses":actor,status};
      }),
      messages: this.messages.filter((m) => m.from === actor || m.to === actor),
    };
  }
  apply(actor: string, input: unknown, session = actor): Ack {
    this.expire();
    const parsed = schema.safeParse(input);
    const id =
      typeof input === "object" &&
      input !== null &&
      "id" in input &&
      typeof input.id === "string"
        ? input.id.slice(0, 100)
        : "";
    const fail = (reason: string): Ack => ({
      type: "ack",
      id,
      ok: false,
      reason,
    });
    if (!parsed.success || !this.staff.some((s) => s.id === actor))
      return fail("Invalid command");
    const c = parsed.data,
      key = `${actor}:${session}:${id}`,
      payload = JSON.stringify(c),
      previous = this.dedup.get(key);
    if (previous)
      return previous.payload === payload
        ? previous.ack
        : fail("Command identifier already used");
    const execute = (): Ack => {
      if(c.type==="request.broadcast"){
        const recipients=this.staff.filter(p=>p.id!==actor&&p.reachable&&(p.status??"available")==="available").map(p=>p.id);
        if(!recipients.length)return fail("No available nurses connected");
        if(this.tasks.length>=500)return fail("Demo capacity reached");
        const t:Task={id:randomUUID(),from:actor,to:"available nurses",room:c.room,text:`${c.required} nurses requested`,required:c.required,recipients,acceptedBy:[],deliveredTo:[],declinedBy:[],completedBy:[],status:"sent",revision:1};
        this.tasks.push(t);return {type:"ack",id,ok:true,taskId:t.id};
      }
      if (c.type === "presence.set") {
        this.staff.find((s) => s.id === actor)!.status = c.status;
        return { type: "ack", id, ok: true };
      }
      if (c.type === "message.read") {
        const m = this.messages.find(
          (m) => m.id === c.messageId && m.to === actor,
        );
        if (!m) return fail("Message unavailable");
        m.readAt ??= this.clock();
        return { type: "ack", id, ok: true };
      }
      if (c.type === "context.clear") {
        this.observations.clear(session);
        return { type: "ack", id, ok: true };
      }
      if (c.type === "patient.confirm") {
        const context = this.observations.confirm(
          actor,
          session,
          c.patientId,
          c.encounterId,
        );
        return context
          ? { type: "ack", id, ok: true, context }
          : fail("Patient not assigned");
      }
      if (c.type === "note.save") {
        const record = this.patientNotes.save(
          actor,
          session,
          c,
          this.observations,
          this.clock(),
        );
        return typeof record === "string"
          ? fail(record)
          : { type: "ack", id, ok: true, noteId: record.id };
      }
      if (c.type === "observation.save") {
        const record = this.observations.save(actor, session, c);
        return typeof record === "string"
          ? fail(record)
          : { type: "ack", id, ok: true, observationId: record.id };
      }
      if (c.type === "observation.share") {
        if (!this.observations.valid(actor, session, c))
          return fail("Patient context changed");
        const o = this.observations.records.find(
          (o) =>
            o.id === c.observationId &&
            o.reviewer === actor &&
            o.patientId === c.patientId &&
            o.encounterId === c.encounterId &&
            o.contextRevision === c.contextRevision,
        );
        const p = this.observations.assigned(c.to, c.patientId);
        if (
          !o ||
          !p ||
          c.to === actor ||
          !this.staff.some((s) => s.id === c.to && s.reachable)
        )
          return fail("Observation or recipient unavailable");
        if (this.tasks.length >= 500) return fail("Demo capacity reached");
        const existing = this.tasks.find(
          (t) => t.attachment?.id === o.id && t.to === c.to && t.from === actor,
        );
        if (existing)
          return existing.text === c.text
            ? { type: "ack", id, ok: true, taskId: existing.id }
            : fail("Already shared with this recipient");
        const t: Task = {
          id: randomUUID(),
          from: actor,
          to: c.to,
          room: p.room,
          text: c.text,
          status: "sent",
          revision: 1,
          attachment: {
            id: o.id,
            patientId: o.patientId,
            patientName: p.name,
            encounterId: o.encounterId,
            values: o.values,
            units: o.units,
            capturedAt: o.capturedAt,
            corrected: o.corrected,
            source: o.source,
            measurementTime: null,
          },
        };
        this.tasks.push(t);
        return { type: "ack", id, ok: true, taskId: t.id };
      }
      if (c.type === "request.create" || c.type === "message.send") {
        if (
          actor === c.to ||
          !this.staff.some((s) => s.id === c.to && s.reachable)
        )
          return fail("Colleague is not connected");
        if (this.tasks.length + this.messages.length >= 500)
          return fail("Demo capacity reached; restart the synthetic ward");
        if (c.type === "message.send") {
          const messageId = randomUUID();
          this.messages.push({
            id: messageId,
            sentAt: this.clock(),
            from: actor,
            to: c.to,
            text: c.text,
          });
          return { type: "ack", id, ok: true, messageId };
        }
        const t: Task = {
          id: randomUUID(),
          from: actor,
          to: c.to,
          room: c.room,
          text: c.text,
          status: "sent",
          revision: 1,
        };
        this.tasks.push(t);
        return { type: "ack", id, ok: true, taskId: t.id };
      }
      const t = this.tasks.find((t) => t.id === c.taskId);
      if(t?.required){
        const accepted=t.acceptedBy??=[];const completed=t.completedBy??=[];const declined=t.declinedBy??=[];
        if(t.from!==actor&&!t.recipients?.includes(actor))return fail("Request unavailable");
        if(c.type==="request.delivered"&&t.recipients?.includes(actor)){if(!t.deliveredTo?.includes(actor))(t.deliveredTo??=[]).push(actor);return {type:"ack",id,ok:true,taskId:t.id}}
        if(t.revision!==c.revision)return fail("Request changed. Review it again");
        if(c.type==="request.accept"){
          if(!t.recipients?.includes(actor)||accepted.includes(actor)||declined.includes(actor)||accepted.length>=t.required||["cancelled","expired"].includes(t.status))return fail("Request is filled or unavailable");
          if(!this.staff.some(p=>p.id===actor&&p.reachable&&(p.status??"available")==="available"))return fail("Set yourself available before accepting");
          accepted.push(actor);t.status=accepted.length>=t.required?"accepted":"delivered";
        }else if(c.type==="request.decline"&&t.recipients?.includes(actor)&&!accepted.includes(actor)&&!declined.includes(actor)&&["sent","delivered"].includes(t.status)){declined.push(actor)}
        else if(c.type==="request.complete"&&accepted.includes(actor)&&!completed.includes(actor)){completed.push(actor);if(accepted.length>=t.required&&completed.length===accepted.length)t.status="completed"}
        else if(c.type==="request.cancel"&&t.from===actor&&accepted.length===0&&["sent","delivered"].includes(t.status)){t.status="cancelled"}
        else return fail("Action not allowed in the current state");
        t.revision++;return {type:"ack",id,ok:true,taskId:t.id};
      }
      if (!t || (t.from !== actor && t.to !== actor))
        return fail("Request unavailable");
      if (t.revision !== c.revision)
        return fail("Request changed. Review it again");
      const incoming = t.to === actor;
      let status: string | undefined;
      if (c.type === "request.delivered" && incoming && t.status === "sent")
        status = "delivered";
      if (
        c.type === "request.accept" &&
        incoming &&
        ["sent", "delivered"].includes(t.status)
      )
        status = "accepted";
      if (
        c.type === "request.decline" &&
        incoming &&
        ["sent", "delivered"].includes(t.status)
      )
        status = "declined";
      if (c.type === "request.complete" && incoming && t.status === "accepted")
        status = "completed";
      if (
        c.type === "request.cancel" &&
        t.from === actor &&
        ["sent", "delivered"].includes(t.status)
      )
        status = "cancelled";
      if (!status) return fail("Action not allowed in the current state");
      t.status = status;
      t.revision++;
      return { type: "ack", id, ok: true, taskId: t.id };
    };
    // Never evict a successful id during this volatile session: refuse safely when full.
    if (this.dedup.size >= 10000) return fail("Demo command limit reached");
    const ack = execute();
    if (ack.ok && ack.taskId) {
      const t = this.tasks.find((t) => t.id === ack.taskId)!;
      if (t.createdAt === undefined) {
        t.createdAt = this.clock();
        t.expiresAt = this.clock() + this.requestTtlMs;
      }
    }
    this.dedup.set(key, { payload, ack });
    return ack;
  }
}
