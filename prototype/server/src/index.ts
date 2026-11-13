import { createServer } from "node:http";
import { readFileSync } from "node:fs";
import { randomBytes } from "node:crypto";
import { WebSocketServer, WebSocket } from "ws";
import { Ward } from "./core.ts";
const ward = new Ward();
const tokens = new Map(
  ward.staff.map((s) => [randomBytes(24).toString("hex"), s.id]),
);
const server = createServer((req, res) => {
  if (req.url === "/health") {
    res.setHeader("Content-Type", "application/json");
    res.end(JSON.stringify({ ok: true, mode: "synthetic-development" }));
    return;
  }
  if (req.url === "/" || req.url === "/client.js") {
    res.setHeader(
      "Content-Type",
      req.url === "/" ? "text/html" : "text/javascript",
    );
    res.setHeader("Cache-Control", "no-store");
    res.setHeader(
      "Content-Security-Policy",
      "default-src 'self'; connect-src 'self' ws:; style-src 'self' 'unsafe-inline'; script-src 'self'",
    );
    res.end(
      readFileSync(
        new URL(
          req.url === "/" ? "../public/index.html" : "../public/client.js",
          import.meta.url,
        ),
      ),
    );
    return;
  }
  res.writeHead(404).end();
});
const wss = new WebSocketServer({ server, maxPayload: 16384 });
const clients = new Map<WebSocket, string>();
function send(ws: WebSocket, data: unknown) {
  if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(data));
}
function broadcast() {
  for (const [ws, id] of clients)
    send(ws, { type: "snapshot", payload: ward.snapshot(id) });
}
wss.on("connection", (ws, req) => {
  const origin = req.headers.origin;
  if (origin && new URL(origin).host !== req.headers.host) {
    ws.close(1008, "Origin denied");
    return;
  }
  const timeout = setTimeout(() => {
    if (!clients.has(ws)) ws.close(1008, "Authentication required");
  }, 5000);
  ws.on("message", (buf) => {
    try {
      const msg = JSON.parse(buf.toString());
      if (!clients.has(ws)) {
        if (msg.type !== "auth" || !tokens.has(msg.token))
          throw Error("Invalid demo token");
        clients.set(ws, tokens.get(msg.token)!);
        clearTimeout(timeout);
        send(ws, {
          type: "snapshot",
          payload: ward.snapshot(clients.get(ws)!),
        });
        return;
      }
      ward.handle(clients.get(ws)!, msg);
      send(ws, { type: "ok", id: msg.id });
      broadcast();
    } catch (e) {
      send(ws, {
        type: "error",
        message: e instanceof Error ? e.message : "Invalid command",
      });
    }
  });
  ws.on("close", () => {
    clearTimeout(timeout);
    clients.delete(ws);
  });
  ws.on("error", () => {});
});
const cleanup = setInterval(() => {
  ward.expire();
  broadcast();
}, 60000);
cleanup.unref();
const host = process.env.HOST ?? "127.0.0.1",
  port = Number(process.env.PORT ?? 8787);
server.listen(port, host, () => {
  console.log(
    `Synthetic ward: http://${host}:${(server.address() as { port: number }).port}`,
  );
  for (const [token, id] of tokens) console.log(`${id} demo token: ${token}`);
});
for (const sig of ["SIGINT", "SIGTERM"] as const)
  process.on(sig, () => {
    clearInterval(cleanup);
    for (const ws of wss.clients) ws.close();
    wss.close();
    server.close(() => process.exit(0));
  });
