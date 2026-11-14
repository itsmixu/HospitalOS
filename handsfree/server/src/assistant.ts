import Anthropic from "@anthropic-ai/sdk";
import { randomUUID } from "node:crypto";
import { z } from "zod";
import type { Ward } from "./core.ts";

/**
 * Voice assistant for the glasses: "two nurses to my room now", "tell Ben I'm on break",
 * "set me busy", "what did this patient report?".
 *
 * Speech is transcribed on the glasses (Meta DAT Speech); the text arrives here. Claude turns it
 * into tool calls, and every action runs through `ward.handle()` as the speaking user — so the
 * server's assignment and permission rules apply, never the model's judgement (SPEC F5).
 * Without ANTHROPIC_API_KEY a small rule-based parser covers the demo phrases.
 */

export type AssistantContext = { patientId?: string };
export type AssistantAction = { type: "open_patient"; patientId: string };
export type AssistantReply = {
  heard: string;
  say: string;
  actions: AssistantAction[];
  source: "claude" | "rules";
};

const MODEL = "claude-opus-5-5";
let client: Anthropic | null | undefined;
function claude() {
  if (client === undefined) client = process.env.ANTHROPIC_API_KEY ? new Anthropic() : null;
  return client;
}

const tools: Anthropic.Beta.BetaTool[] = [
  {
    name: "request_help",
    description:
      "Ask free colleagues to come and help with a patient. Use for 'send two nurses', 'I need help', 'someone to room 12'. The server picks free colleagues assigned to that patient.",
    strict: true,
    input_schema: {
      type: "object",
      properties: {
        count: { type: "integer", description: "How many colleagues, 1-4" },
        patient_id: {
          type: "string",
          description: "Patient id from the context; use the current patient for 'my room' / 'here'. Empty string if unknown.",
        },
        summary: { type: "string", description: "Short task text, e.g. 'Help repositioning the patient'" },
      },
      required: ["count", "patient_id", "summary"],
      additionalProperties: false,
    },
  },
  {
    name: "send_message",
    description: "Send a short direct message to one colleague.",
    strict: true,
    input_schema: {
      type: "object",
      properties: {
        to: { type: "string", description: "Colleague id from the context" },
        text: { type: "string", description: "The message, in the speaker's words, tidied up" },
      },
      required: ["to", "text"],
      additionalProperties: false,
    },
  },
  {
    name: "set_status",
    description: "Set the speaker's own availability.",
    strict: true,
    input_schema: {
      type: "object",
      properties: { status: { type: "string", enum: ["free", "busy"] } },
      required: ["status"],
      additionalProperties: false,
    },
  },
  {
    name: "open_patient",
    description: "Show a patient's card on the glasses.",
    strict: true,
    input_schema: {
      type: "object",
      properties: { patient_id: { type: "string" } },
      required: ["patient_id"],
      additionalProperties: false,
    },
  },
  {
    name: "answer",
    description:
      "Answer a question using ONLY the records in the context, naming the source (e.g. 'Patient reported', 'Note 14:05, Aino'). If the records don't say, answer that you don't know. Keep it to one or two short sentences: it is read on a small display.",
    strict: true,
    input_schema: {
      type: "object",
      properties: { text: { type: "string" } },
      required: ["text"],
      additionalProperties: false,
    },
  },
];

const SYSTEM = `You are the hands-free assistant in a ward nurse's smart glasses. The nurse speaks a short request; you act on it with the tools.
- Prefer acting over talking. Use one or more tools; call request_help once with the right count.
- "My room", "here", "this patient" means the current patient in the context.
- Never invent patients, colleagues or clinical facts. Only use ids from the context.
- If the request is unclear or not something you can do, use answer to say so briefly.
All data is synthetic demo data.`;

export async function runAssistant(
  ward: Ward,
  actor: string,
  heard: string,
  ctx: AssistantContext,
): Promise<AssistantReply> {
  const c = claude();
  if (!c) return runRules(ward, actor, heard, ctx);

  const snap = ward.snapshot(actor);
  const context = {
    you: snap.me,
    currentPatientId: ctx.patientId ?? null,
    colleagues: snap.staff.filter((s) => s.id !== actor && s.id !== "observer"),
    patients: snap.patients,
    reviewedNotes: snap.notes,
  };

  let response: Anthropic.Beta.BetaMessage;
  try {
    response = await c.beta.messages.create({
      model: MODEL,
      max_tokens: 2000,
      betas: ["server-side-fallback-2026-07-01"],
      fallbacks: "default",
      // Low effort: these are short, latency-sensitive commands.
      output_config: { effort: "low" },
      system: SYSTEM,
      tools,
      messages: [
        {
          role: "user",
          content: `Context (JSON):\n${JSON.stringify(context)}\n\nThe nurse said: "${heard}"`,
        },
      ],
    } as Anthropic.Beta.MessageCreateParamsNonStreaming);
  } catch (error) {
    // Network/API problems shouldn't break the demo: fall back to the rule parser.
    console.error("assistant: Claude call failed, using rules", error instanceof Anthropic.APIError ? error.status : error);
    return runRules(ward, actor, heard, ctx);
  }

  if (response.stop_reason === "refusal") {
    return { heard, say: "I can't help with that.", actions: [], source: "claude" };
  }

  const said: string[] = [];
  const actions: AssistantAction[] = [];
  for (const block of response.content) {
    if (block.type === "text" && block.text.trim()) said.push(block.text.trim());
    if (block.type !== "tool_use") continue;
    try {
      said.push(execute(ward, actor, block.name, block.input, ctx, actions));
    } catch (e) {
      said.push(e instanceof Error ? e.message : "Couldn't do that");
    }
  }
  return { heard, say: said.join(" ") || "Done.", actions, source: "claude" };
}

const helpInput = z.object({ count: z.number().int(), patient_id: z.string(), summary: z.string() });
const msgInput = z.object({ to: z.string(), text: z.string().min(1) });
const statusInput = z.object({ status: z.enum(["free", "busy"]) });
const patientInput = z.object({ patient_id: z.string() });
const answerInput = z.object({ text: z.string() });

/** Run one assistant action through the ward's normal command path. Returns what to say. */
function execute(
  ward: Ward,
  actor: string,
  tool: string,
  raw: unknown,
  ctx: AssistantContext,
  actions: AssistantAction[],
): string {
  const run = (type: string, payload: Record<string, unknown>) =>
    ward.handle(actor, { id: randomUUID(), type, payload });
  switch (tool) {
    case "request_help": {
      const v = helpInput.parse(raw);
      const pid = v.patient_id || ctx.patientId;
      if (!pid) return "Which patient? Open their card first.";
      const room = ward.access(actor, pid).room;
      const want = Math.min(Math.max(v.count, 1), 4);
      const helpers = ward.freeHelpers(actor, pid).slice(0, want);
      if (helpers.length === 0) return `Nobody free for Room ${room} right now.`;
      for (const h of helpers) run("help.request", { patientId: pid, to: h.id, summary: v.summary || "Assistance requested" });
      const names = helpers.map((h) => h.name).join(" and ");
      return helpers.length < want
        ? `Only ${names} ${helpers.length === 1 ? "is" : "are"} free. Asked to come to Room ${room}.`
        : `Asked ${names} to come to Room ${room}.`;
    }
    case "send_message": {
      const v = msgInput.parse(raw);
      const to = ward.staff.find((s) => s.id === v.to || s.name.toLowerCase() === v.to.toLowerCase());
      if (!to) return "I don't know who that is.";
      run("msg.send", { to: to.id, text: v.text });
      return `Sent to ${to.name}.`;
    }
    case "set_status": {
      const v = statusInput.parse(raw);
      run("presence.update", { status: v.status });
      return `You're now ${v.status}.`;
    }
    case "open_patient": {
      const v = patientInput.parse(raw);
      const p = ward.access(actor, v.patient_id);
      actions.push({ type: "open_patient", patientId: p.id });
      return `Opening ${p.name}.`;
    }
    case "answer":
      return answerInput.parse(raw).text;
    default:
      return "";
  }
}

// ------------------------------------------------------------------ offline fallback

const NUMBERS: Record<string, number> = { one: 1, a: 1, an: 1, two: 2, couple: 2, three: 3, four: 4 };

/** Small rule parser for the demo phrases, used when there's no API key or the API fails. */
export function runRules(ward: Ward, actor: string, heard: string, ctx: AssistantContext): AssistantReply {
  const t = heard.toLowerCase();
  const actions: AssistantAction[] = [];
  const reply = (say: string): AssistantReply => ({ heard, say, actions, source: "rules" });
  const exec = (tool: string, input: unknown) => {
    try {
      return reply(execute(ward, actor, tool, input, ctx, actions));
    } catch (e) {
      return reply(e instanceof Error ? e.message : "Couldn't do that");
    }
  };

  if (/\b(busy|break)\b/.test(t) && /\b(me|i'm|i am|set)\b/.test(t)) return exec("set_status", { status: "busy" });
  if (/\b(free|available|back)\b/.test(t) && /\b(me|i'm|i am|set)\b/.test(t)) return exec("set_status", { status: "free" });

  const msg = t.match(/^(?:tell|message|text)\s+(\w+)\s+(?:that\s+)?(.+)$/);
  if (msg) return exec("send_message", { to: msg[1], text: heard.slice(heard.length - msg[2].length) });

  if (/\b(nurse|nurses|help|someone|people|hands)\b/.test(t)) {
    const n = t.match(/\b(\d)\b/)?.[1] ?? Object.keys(NUMBERS).find((w) => new RegExp(`\\b${w}\\b`).test(t));
    const count = n ? (Number(n) || NUMBERS[n] || 1) : 1;
    return exec("request_help", { count, patient_id: ctx.patientId ?? "", summary: "Help needed in the room" });
  }

  if (ctx.patientId && /\b(what|when|latest|report|reported|note)\b/.test(t)) {
    const snap = ward.snapshot(actor);
    const p = snap.patients.find((x) => x.id === ctx.patientId);
    const note = [...snap.notes].reverse().find((n) => n.patientId === ctx.patientId);
    if (note) return reply(`${note.planned || note.reported} (Note, ${note.at.slice(11, 16)})`);
    if (p) return reply(`Patient reported: ${p.reported}`);
  }
  return reply("Sorry, I didn't catch that. Try: two nurses to my room.");
}
