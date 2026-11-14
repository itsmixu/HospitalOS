import { randomUUID } from "node:crypto";
import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { WebSocket, WebSocketServer } from "ws";
import { z } from "zod";
import { DurableWard } from "./durable-ward.ts";
import { resolve } from "node:path";
import { LocalSpeech } from "./local-speech.ts";
import { speechHttp } from "./speech-http.ts";

const ward = new DurableWard(
  process.env.HF_DB_PATH || resolve(".local/handsfree.sqlite"),
);
const simulation = process.env.ENABLE_SIMULATION === "1";
const speech =
  simulation && process.env.ENABLE_LOCAL_SPEECH === "1"
    ? new LocalSpeech()
    : undefined;
process.on("exit", () => speech?.stop());
const port = Number(process.env.HF_PORT || 8790);
let boundPort = port;
const tokens: Record<string, string> = {
  "aino-demo": "aino",
  "ben-demo": "ben",
  "sara-demo": "sara",
};
const clients = new Map<
  WebSocket,
  {
    actor: string;
    role: string;
    alive: boolean;
    session: string;
    ready: boolean;
    voice?: boolean;
  }
>();
const page = new URL("../public/handsfree.html", import.meta.url);
const server = createServer(async (req, res) => {
  if (
    req.headers.host !== `127.0.0.1:${boundPort}` &&
    req.headers.host !== `localhost:${boundPort}`
  ) {
    res.writeHead(403).end();
    return;
  }
  if (simulation && (req.url === "/speech" || req.url === "/speech/status")) {
    if (speech) await speechHttp(req, res, speech, boundPort, tokens);
    else
      res
        .writeHead(503, { "Content-Type": "application/json" })
        .end(JSON.stringify({ ready: false, error: "Local speech disabled" }));
    return;
  }
  if (
    simulation &&
    ["/live-voice.js", "/voice-gate.js", "/pcm-worklet.js"].includes(
      req.url || "",
    )
  ) {
    res.setHeader("Content-Type", "text/javascript; charset=utf-8");
    res.setHeader("Cache-Control", "no-store");
    res.end(await readFile(new URL("../public" + req.url, import.meta.url)));
    return;
  }
  if (simulation && req.url === "/emergency-scenario") {
    res.setHeader("Content-Type","application/json");res.end(await readFile(new URL("../../../prototype/demo-data/handsfree-code-blue.json",import.meta.url)));return;
  }
  if ((req.url === "/" || req.url === "/monitor" || req.url === "/patients") && simulation) {
    res.setHeader("Content-Type", "text/html; charset=utf-8");
    res.setHeader(
      "Content-Security-Policy",
      "default-src 'self'; script-src 'self' 'unsafe-inline'; worker-src 'self'; style-src 'unsafe-inline'; connect-src 'self'",
    );
    res.end(await readFile(req.url === "/patients" ? new URL("../public/patients.html", import.meta.url) : req.url === "/monitor" ? new URL("../public/monitor.html", import.meta.url) : page));
  } else if (req.url === "/health") {
    res.statusCode = ward.healthy ? 200 : 503;
    res.setHeader("Content-Type", "application/json");
    res.end(
      JSON.stringify({
        synthetic: true,
        simulation,
        persistence:
          process.env.HF_DB_PATH === ":memory:" ? "volatile" : "sqlite",
        healthy: ward.healthy,
      }),
    );
  } else res.writeHead(404).end();
});
const wss = new WebSocketServer({ server, maxPayload: 16_384 });
function send(ws: WebSocket, data: unknown) {
  if (ws.bufferedAmount > 1_000_000) {
    ws.close(1013, "Reconnect to recover ward state");
    return;
  }
  if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(data));
}
function broadcast() {
  if (!ward.healthy) return;
  for (const p of ward.staff)
    p.reachable = [...clients.values()].some(
      (c) => c.actor === p.id && c.role !== "control" && c.ready,
    );
  for (const [ws, c] of clients) send(ws, ward.snapshot(c.actor, c.session));
}
const simInput = z.discriminatedUnion("kind", [
  z.object({ kind: z.literal("wake") }),
  z.object({ kind: z.literal("voice-session"), active: z.boolean() }),
  z.object({ kind: z.literal("sdk-voice"), active: z.boolean() }),
  z.object({
    kind: z.literal("sdk-transcript"),
    confidence: z.number().min(0).max(1).optional(),
    text: z.string().max(200),
    final: z.boolean().default(true),
  }),
  z.object({
    kind: z.literal("transcript"),
    text: z.string().max(200),
    final: z.boolean().optional(),
  }),
  z.object({
    kind: z.literal("gesture"),
    value: z.enum(["left-tilt", "right-tilt", "left-partial", "right-partial", "left-cancel", "right-cancel", "right-held", "nod", "shake", "single-nod", "walking", "mixed"]),
  }),
  z.object({ kind: z.literal("device-lost") }),
  z.object({ kind: z.literal("device-ready") }),
  z.object({
    kind: z.literal("camera"),
    scenario: z.enum([
      "clear",
      "blur",
      "glare",
      "crop",
      "missing-label",
      "ambiguous",
      "unavailable",
      "denied",
    ]),
    seed: z.number().int().min(0).max(2147483647),
  }),
]);
wss.on("connection", (ws, req) => {
  if (
    ![`127.0.0.1:${boundPort}`, `localhost:${boundPort}`].includes(
      req.headers.host || "",
    )
  ) {
    ws.close(1008);
    return;
  }
  const origin = req.headers.origin;
  if (
    origin &&
    ![
      `http://127.0.0.1:${boundPort}`,
      `http://localhost:${boundPort}`,
    ].includes(origin)
  ) {
    ws.close(1008);
    return;
  }
  const authTimeout = setTimeout(() => {
    if (!clients.has(ws)) ws.close(1008);
  }, 3000);
  ws.on("pong", () => {
    const c = clients.get(ws);
    if (c) c.alive = true;
  });
  ws.on("message", (raw) => {
    let m: any;
    try {
      m = JSON.parse(raw.toString());
    } catch {
      ws.close(1008);
      return;
    }
    if (!m || typeof m !== "object") {
      ws.close(1008);
      return;
    }
    let c = clients.get(ws);
    if (!c) {
      if (
        m.type !== "auth" ||
        typeof m.token !== "string" ||
        !Object.hasOwn(tokens, m.token)
      ) {
        ws.close(1008);
        return;
      }
      const role = ["headset", "control", "responder"].includes(m.role)
        ? m.role
        : "responder";
      if (role === "control" && !simulation) {
        ws.close(1008);
        return;
      }
      clients.set(ws, {
        actor: tokens[m.token],
        role,
        alive: true,
        session: randomUUID(),
        ready: m.ready !== false,
      });
      clearTimeout(authTimeout);
      broadcast();
      if (role === "control")
        for (const [target, other] of clients)
          if (other.actor === tokens[m.token] && other.role === "headset")
            send(target, { type: "sim.query" });
      return;
    }
    if (m.type === "device.state" && c.role === "headset") {
      if (typeof m.ready !== "boolean") {
        send(ws, { type: "error", reason: "Invalid device state" });
        return;
      }
      c.ready = m.ready;
      if (!m.ready) ward.observations.clear(c.session);
      broadcast();
      return;
    }
    if (m.type === "sim.input") {
      const parsed = simInput.safeParse(m.input);
      if (!simulation || c.role !== "control" || !parsed.success) {
        send(ws, { type: "error", reason: "Simulator input unavailable" });
        return;
      }
      const targets = [...clients.entries()].filter(
        ([, v]) => v.actor === c!.actor && v.role === "headset",
      );
      if (targets.length !== 1) {
        send(ws, {
          type: "error",
          reason: "Connect exactly one simulated headset for this account",
        });
        return;
      }
      if (parsed.data.kind === "voice-session") c.voice = parsed.data.active;
      send(targets[0][0], { type: "sim.input", input: parsed.data });
      return;
    }
    if (m.type === "sim.state" && simulation && c.role === "headset") {
      // Debug renderer/status only, relayed solely to the same authenticated demo actor.
      for (const [target, other] of clients)
        if (other.actor === c.actor && other.role === "control")
          send(target, m);
      return;
    }
    if (c.role === "headset" && !c.ready && m.type !== "command.status") {
      send(ws, {
        type: "ack",
        id: typeof m.id === "string" ? m.id : "",
        ok: false,
        reason: "Headset is not ready",
      });
      return;
    }
    if (c.role === "control") {
      send(ws, {
        type: "error",
        reason: "Control clients cannot mutate ward records",
      });
      return;
    }
    try {
      if (m.type === "command.status") {
        const query = z
          .object({
            id: z.string().max(100).optional(),
            type: z.literal("command.status"),
            commandId: z.string().min(1).max(100),
          })
          .strict()
          .safeParse(m);
        if (!query.success) {
          send(ws, { type: "error", reason: "Invalid command lookup" });
          return;
        }
        send(ws, ward.commandStatus(c.actor, query.data.commandId));
        return;
      }
      send(ws, ward.apply(c.actor, m, c.session));
      broadcast();
    } catch {
      for (const client of clients.keys())
        client.close(1011, "Ward storage unavailable");
    }
  });
  ws.on("close", () => {
    clearTimeout(authTimeout);
    const c = clients.get(ws);
    if (c?.voice)
      for (const [target, other] of clients)
        if (other.actor === c.actor && other.role === "headset")
          send(target, {
            type: "sim.input",
            input: { kind: "voice-session", active: false },
          });
    if (c) ward.observations.clear(c.session);
    clients.delete(ws);
    broadcast();
  });
  ws.on("error", () => {});
});
const expiry = setInterval(() => {
  try {
    if (ward.expire()) broadcast();
  } catch {
    for (const ws of clients.keys()) ws.close(1011, "Ward storage unavailable");
  }
}, 1000);
const heartbeat = setInterval(() => {
  for (const [ws, c] of clients) {
    if (!c.alive) {
      ws.terminate();
      continue;
    }
    c.alive = false;
    ws.ping();
  }
}, 5000);
server.listen(port, "127.0.0.1", () => {
  const address = server.address();
  if (address && typeof address === "object") boundPort = address.port;
  console.log(
    `Hands-free synthetic ward: http://127.0.0.1:${boundPort} (simulation=${simulation}; storage=${process.env.HF_DB_PATH === ":memory:" ? "volatile" : "sqlite"})`,
  );
});
function shutdown() {
  clearInterval(expiry);
  clearInterval(heartbeat);
  for (const ws of clients.keys()) ws.terminate();
  speech?.stop();
  wss.close();
  server.close();
  ward.close();
}
process.once("SIGTERM", shutdown);
process.once("SIGINT", shutdown);
