import { randomUUID } from "node:crypto";
import { z } from "zod";
const text = z.string().trim().min(1).max(240);
export const isbarSchema = z
  .object({
    identify: text,
    situation: text,
    background: text,
    assessment: text,
    recommendation: text,
  })
  .strict();
export const emergencyCommandSchema = z.discriminatedUnion("type", [
  z
    .object({
      id: z.string().min(1).max(100),
      type: z.literal("emergency.create"),
      patientId: z.string().min(1).max(100),
      encounterId: z.string().min(1).max(100),
      isbar: isbarSchema,
    })
    .strict(),
  z
    .object({
      id: z.string().min(1).max(100),
      type: z.enum([
        "emergency.acknowledge",
        "emergency.decline",
        "emergency.resolve",
      ]),
      emergencyId: z.string().min(1).max(100),
      revision: z.number().int().positive(),
    })
    .strict(),
]);
export const emergencyRecordSchema = z
  .object({
    id: z.string(),
    from: z.string(),
    patientId: z.string(),
    encounterId: z.string(),
    room: z.string(),
    kind: z.literal("code-blue"),
    status: z.enum(["active", "resolved"]),
    revision: z.number().int().positive(),
    createdAt: z.iso.datetime(),
    updatedAt: z.iso.datetime(),
    recipients: z.array(z.string()),
    responses: z.record(z.string(), z.enum(["acknowledged", "declined"])),
    isbar: isbarSchema,
  })
  .strict();
export type Emergency = z.infer<typeof emergencyRecordSchema>;
export class Emergencies {
  records: Emergency[] = [];
  create(
    actor: string,
    patient: { id: string; encounterId: string; room: string },
    recipients: string[],
    isbar: Emergency["isbar"],
    now: number,
  ): Emergency | string {
    if (!recipients.length) return "No connected colleagues";
    if (this.records.length >= 100) return "Emergency demo capacity reached";
    if (
      this.records.some(
        (e) =>
          e.status === "active" &&
          e.patientId === patient.id &&
          e.encounterId === patient.encounterId,
      )
    )
      return "An active emergency already exists for this encounter";
    const at = new Date(now).toISOString();
    const e: Emergency = {
      id: randomUUID(),
      from: actor,
      patientId: patient.id,
      encounterId: patient.encounterId,
      room: patient.room,
      kind: "code-blue",
      status: "active",
      revision: 1,
      createdAt: at,
      updatedAt: at,
      recipients,
      responses: {},
      isbar,
    };
    this.records.push(e);
    return e;
  }
  change(
    actor: string,
    c: Extract<z.infer<typeof emergencyCommandSchema>, { emergencyId: string }>,
    now: number,
  ): Emergency | string {
    const e = this.records.find((e) => e.id === c.emergencyId);
    if (!e || e.status !== "active" || e.revision !== c.revision)
      return "Emergency changed or unavailable";
    if (c.type === "emergency.resolve") {
      if (e.from !== actor)
        return "Only the reporting staff member can resolve";
      e.status = "resolved";
    } else {
      if (!e.recipients.includes(actor)) return "Not an emergency recipient";
      e.responses[actor] =
        c.type === "emergency.acknowledge" ? "acknowledged" : "declined";
    }
    e.revision++;
    e.updatedAt = new Date(now).toISOString();
    return e;
  }
  snapshot(actor: string, valid: (e: Emergency) => boolean) {
    return this.records
      .filter((e) => e.from === actor || e.recipients.includes(actor))
      .map((e) => {
        const {
          isbar,
          patientId,
          encounterId,
          recipients,
          responses,
          ...alert
        } = e;
        const allowed =
          valid(e) &&
          e.status === "active" &&
          (e.from === actor || e.responses[actor] === "acknowledged");
        return {
          ...alert,
          response: e.responses[actor] ?? "pending",
          ...(e.from === actor ? { recipients, responses } : {}),
          ...(allowed ? { patientId, encounterId, isbar } : {}),
          briefingUnavailable: !valid(e),
        };
      });
  }
}
