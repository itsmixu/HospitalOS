import { test } from "node:test";
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { WebSocket } from "ws";
test("real WebSocket authentication, request routing and reconnect snapshot", async () => {
  const child = spawn(
    process.execPath,
    ["--import", "tsx", "server/src/index.ts"],
    { env: { ...process.env, PORT: "0" }, stdio: ["ignore", "pipe", "pipe"] },
  );
  const sockets: WebSocket[] = [];
  try {
    let output = "";
    await new Promise<void>((resolve, reject) => {
      const timer = setTimeout(() => reject(Error("Server timeout")), 10000);
      child.stdout.on("data", (b) => {
        output += b.toString();
        if (output.includes("observer demo token:")) {
          clearTimeout(timer);
          resolve();
        }
      });
      child.on("error", reject);
    });
    const port = output.match(/127.0.0.1:(\d+)/)![1];
    const token = (id: string) =>
      output.match(new RegExp(id + " demo token: (\\w+)"))![1];
    async function connect(id: string) {
      const ws = new WebSocket("ws://127.0.0.1:" + port);
      sockets.push(ws);
      await once(ws, "open");
      const reply = once(ws, "message");
      ws.send(JSON.stringify({ type: "auth", token: token(id) }));
      await reply;
      return ws;
    }
    const a = await connect("aino"),
      b = await connect("ben");
    const received = once(b, "message");
    a.send(
      JSON.stringify({
        id: "request-1",
        type: "help.request",
        payload: { patientId: "p1", to: "ben", summary: "Assist" },
      }),
    );
    const snapshot = JSON.parse((await received)[0].toString());
    assert.equal(snapshot.payload.tasks[0].status, "sent");
    b.close();
    const again = await connect("ben");
    assert.equal(again.readyState, WebSocket.OPEN);
    const health = await fetch("http://127.0.0.1:" + port + "/health");
    assert.equal(health.status, 200);
  } finally {
    for (const ws of sockets) ws.terminate();
    child.kill();
    await once(child, "exit");
  }
});
