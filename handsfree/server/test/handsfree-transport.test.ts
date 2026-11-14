import { test } from "node:test";
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { WebSocket } from "ws";
for (const enabled of [true, false])
  test(`hands-free transport auth, scoped simulation and routing (simulation=${enabled})`, async () => {
    const child = spawn(
      process.execPath,
      ["--import", "tsx", "server/src/handsfree-server.ts"],
      {
        env: {
          ...process.env,
          HF_PORT: "0",
          HF_DB_PATH: ":memory:",
          ENABLE_SIMULATION: enabled ? "1" : "0",
        },
        stdio: ["ignore", "pipe", "pipe"],
      },
    );
    const sockets: WebSocket[] = [];
    try {
      const ready = await new Promise<string>((resolve, reject) => {
        let output = "";
        const timer = setTimeout(() => reject(Error("Startup timeout")), 5000);
        child.stdout.on("data", (b) => {
          output += b;
          if (output.includes("storage=volatile)")) {
            clearTimeout(timer);
            resolve(output);
          }
        });
        child.on("error", reject);
      });
      const port = ready.match(/127.0.0.1:(\d+)/)![1],
        url = `ws://127.0.0.1:${port}`;
      assert.equal(
        (await fetch(`http://127.0.0.1:${port}/health`)).status,
        200,
      );
      async function connect(token: string, role: string) {
        const ws = new WebSocket(url);
        sockets.push(ws);
        await once(ws, "open");
        const next = once(ws, "message");
        ws.send(JSON.stringify({ type: "auth", token, role }));
        await next;
        return ws;
      }
      const invalid = new WebSocket(url);
      sockets.push(invalid);
      await once(invalid, "open");
      const closed = once(invalid, "close");
      invalid.send(JSON.stringify({ type: "auth", token: "bad" }));
      assert.equal((await closed)[0], 1008);
      const a = await connect("aino-demo", "headset"),
        b = await connect("ben-demo", "responder"),
        s = await connect("sara-demo", "responder");
      function waitFor(ws: WebSocket, type: string) {
        return new Promise<any>((resolve, reject) => {
          const timer = setTimeout(() => {
            ws.off("message", listener);
            reject(Error(`No ${type}`));
          }, 3000);
          function listener(raw: any) {
            const m = JSON.parse(raw.toString());
            if (m.type === type) {
              clearTimeout(timer);
              ws.off("message", listener);
              resolve(m);
            }
          }
          ws.on("message", listener);
        });
      }
      const ack = waitFor(a, "ack"),
        incoming = waitFor(b, "snapshot"),
        outsider = waitFor(s, "snapshot");
      a.send(
        JSON.stringify({
          id: "new",
          type: "request.create",
          to: "ben",
          room: "12",
          text: "bring the hoist",
        }),
      );
      assert.equal((await ack).ok, true);
      assert.equal((await incoming).tasks[0].status, "sent");
      assert.equal((await outsider).tasks.length, 0);
      const control = new WebSocket(url);
      sockets.push(control);
      await once(control, "open");
      if (!enabled) {
        const close = once(control, "close");
        control.send(
          JSON.stringify({ type: "auth", token: "aino-demo", role: "control" }),
        );
        assert.equal((await close)[0], 1008);
      } else {
        const query = waitFor(a, "sim.query");
        control.send(
          JSON.stringify({ type: "auth", token: "aino-demo", role: "control" }),
        );
        await query;
        const input = waitFor(a, "sim.input");
        control.send(
          JSON.stringify({ type: "sim.input", input: { kind: "wake" } }),
        );
        assert.equal((await input).input.kind, "wake");
        const denied = waitFor(control, "error");
        control.send(
          JSON.stringify({
            id: "unauthorised",
            type: "request.create",
            to: "ben",
            room: "12",
            text: "bad",
          }),
        );
        assert.match((await denied).reason, /cannot mutate/);
        const response = waitFor(control, "sim.state");
        a.send(JSON.stringify({ type: "sim.state", card: { phase: "idle" } }));
        assert.equal((await response).card.phase, "idle");
      }
    } finally {
      for (const ws of sockets) ws.terminate();
      const exit = once(child, "exit");
      child.kill();
      await exit;
    }
  });

test("headset readiness gates reachability and mutation while preserving read-only recovery", async () => {
  const child = spawn(
    process.execPath,
    ["--import", "tsx", "server/src/handsfree-server.ts"],
    {
      env: {
        ...process.env,
        HF_PORT: "0",
        HF_DB_PATH: ":memory:",
        ENABLE_SIMULATION: "0",
      },
      stdio: ["ignore", "pipe", "pipe"],
    },
  );
  const sockets: WebSocket[] = [];
  function wait(ws: WebSocket, type: string) {
    return new Promise<any>((resolve, reject) => {
      const timer = setTimeout(() => {
        ws.off("message", listener);
        reject(Error("No " + type));
      }, 3000);
      function listener(raw: any) {
        const m = JSON.parse(raw.toString());
        if (m.type === type) {
          clearTimeout(timer);
          ws.off("message", listener);
          resolve(m);
        }
      }
      ws.on("message", listener);
    });
  }
  try {
    const port = await new Promise<string>((resolve, reject) => {
      let out = "";
      const timer = setTimeout(() => reject(Error("startup")), 5000);
      child.stdout.on("data", (b) => {
        out += b;
        const m = out.match(/127.0.0.1:(\d+)/);
        if (m) {
          clearTimeout(timer);
          resolve(m[1]);
        }
      });
    });
    const ws = new WebSocket(`ws://127.0.0.1:${port}`);
    sockets.push(ws);
    await once(ws, "open");
    let snapshot = wait(ws, "snapshot");
    ws.send(
      JSON.stringify({
        type: "auth",
        token: "aino-demo",
        role: "headset",
        ready: false,
      }),
    );
    assert.equal(
      (await snapshot).staff.find((s: any) => s.id === "aino").reachable,
      false,
    );
    const denied = wait(ws, "ack");
    ws.send(
      JSON.stringify({ id: "status", type: "presence.set", status: "busy" }),
    );
    assert.equal((await denied).ok, false);
    snapshot = wait(ws, "snapshot");
    ws.send(JSON.stringify({ type: "device.state", ready: true }));
    assert.equal(
      (await snapshot).staff.find((s: any) => s.id === "aino").reachable,
      true,
    );
    const accepted = wait(ws, "ack");
    ws.send(
      JSON.stringify({ id: "status", type: "presence.set", status: "busy" }),
    );
    assert.equal((await accepted).ok, true);
    snapshot = wait(ws, "snapshot");
    ws.send(JSON.stringify({ type: "device.state", ready: false }));
    await snapshot;
    const lookup = wait(ws, "command.status");
    ws.send(JSON.stringify({ type: "command.status", commandId: "status" }));
    assert.equal((await lookup).ack.ok, true);
  } finally {
    for (const ws of sockets) ws.terminate();
    const exit = once(child, "exit");
    child.kill();
    await exit;
  }
});
