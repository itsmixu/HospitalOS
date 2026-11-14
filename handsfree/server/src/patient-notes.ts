import { randomUUID } from "node:crypto";
import { z } from "zod";
import type { Observations } from "./observations.ts";

const id = z.string().min(1).max(100);
export const noteCommandSchema = z
  .object({
    id,
    type: z.literal("note.save"),
    patientId: id,
    encounterId: id,
    contextRevision: z.number().int().positive(),
    draftId: id,
    text: z
      .string()
      .min(1)
      .max(160)
      .refine((t) => t.trim() === t && !/[\u0000-\u001f\u007f]/.test(t)),
    reviewedAt: z.iso.datetime(),
    source: z.literal("manual-dictation"),
  })
  .strict();
export type NoteCommand = z.infer<typeof noteCommandSchema>;
export const patientNoteSchema = noteCommandSchema
  .omit({ id: true, type: true })
  .extend({
    id,
    author: id,
    savedAt: z.iso.datetime(),
    eventTime: z.null(),
    synthetic: z.literal(true),
  })
  .strict();
export type PatientNote = z.infer<typeof patientNoteSchema>;
export class PatientNotes {
  records: PatientNote[] = [];
  save(
    actor: string,
    session: string,
    c: NoteCommand,
    contexts: Observations,
    now = Date.now(),
  ): PatientNote | string {
    if (!contexts.valid(actor, session, c))
      return "Patient context changed; confirm patient again";
    const reviewed = Date.parse(c.reviewedAt);
    if (reviewed > now + 5000 || reviewed < now - 600000)
      return "Note review expired or invalid clock";
    const old = this.records.find(
      (n) => n.author === actor && n.draftId === c.draftId,
    );
    if (old)
      return old.patientId === c.patientId &&
        old.encounterId === c.encounterId &&
        old.contextRevision === c.contextRevision &&
        old.text === c.text &&
        old.reviewedAt === c.reviewedAt
        ? old
        : "Note already saved with different content";
    if (this.records.length >= 500) return "Synthetic note capacity reached";
    const { id: commandId, type, ...payload } = c;
    const record: PatientNote = {
      ...payload,
      id: randomUUID(),
      author: actor,
      savedAt: new Date(now).toISOString(),
      eventTime: null,
      synthetic: true,
    };
    this.records.push(record);
    return record;
  }
}
