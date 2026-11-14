import { test } from "node:test";
import assert from "node:assert/strict";
import { spawn, type ChildProcess } from "node:child_process";
import { once } from "node:events";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { WebSocket } from "ws";

test("real process crash/restart preserves accepted work and reconciles missing acknowledgement", async () => {
  const dir = mkdtempSync(join(tmpdir(), "hospitalos-wire-"));
  let child: ChildProcess | undefined;
  let port = "";
  const sockets: WebSocket[] = [];
  async function start() {
    child = spawn(
      process.execPath,
      ["--import", "tsx", "server/src/handsfree-server.ts"],
      {
        env: {
          ...process.env,
          HF_PORT: "0",
          HF_DB_PATH: join(dir, "ward.sqlite"),
          ENABLE_SIMULATION: "0",
        },
        stdio: ["ignore", "pipe", "pipe"],
      },
    );
    port = await new Promise<string>((resolve, reject) => {
      let out = "";
      const timer = setTimeout(() => reject(Error("Startup timeout")), 5000);
      child!.stdout!.on("data", (b) => {
        out += b;
        const match = out.match(
          /127.0.0.1:(\d+) \(simulation=false; storage=sqlite\)/,
        );
        if (match) {
          clearTimeout(timer);
          resolve(match[1]);
        }
      });
      child!.on("error", reject);
    });
  }
  function wait(ws: WebSocket, predicate: (m: any) => boolean) {
    return new Promise<any>((resolve, reject) => {
      const timer = setTimeout(() => {
        ws.off("message", onMessage);
        reject(Error("Response timeout"));
      }, 3000);
      function onMessage(raw: any) {
        const m = JSON.parse(raw.toString());
        if (predicate(m)) {
          clearTimeout(timer);
          ws.off("message", onMessage);
          resolve(m);
        }
      }
      ws.on("message", onMessage);
    });
  }
  async function connect(actor: string) {
    const ws = new WebSocket(`ws://127.0.0.1:${port}`);
    sockets.push(ws);
    await once(ws, "open");
    const snapshot = wait(ws, (m) => m.type === "snapshot");
    ws.send(
      JSON.stringify({
        type: "auth",
        token: `${actor}-demo`,
        role: "responder",
      }),
    );
    return { ws, snapshot: await snapshot };
  }
  async function command(ws: WebSocket, c: any) {
    const ack = wait(ws, (m) => m.type === "ack" && m.id === c.id);
    ws.send(JSON.stringify(c));
    return ack;
  }
  try {
    await start();
    let a = await connect("aino"),
      b = await connect("ben");
    const create = {
      id: "stable-command",
      type: "request.create",
      to: "ben",
      room: "12",
      text: "bring the hoist",
    };
    const ack = await command(a.ws, create);
    assert.equal(ack.ok, true);
    assert.equal(
      (
        await command(b.ws, {
          id: "accept",
          type: "request.accept",
          taskId: ack.taskId,
          revision: 1,
        })
      ).ok,
      true,
    );
    const exit = once(child!, "exit");
    child!.kill("SIGKILL");
    await exit;
    await start();
    a = await connect("aino");
    b = await connect("ben");
    assert.equal(a.snapshot.tasks.length, 1);
    assert.equal(a.snapshot.tasks[0].status, "accepted");
    const lookup = wait(a.ws, (m) => m.type === "command.status");
    a.ws.send(JSON.stringify({ type: "command.status", commandId: create.id }));
    assert.deepEqual((await lookup).ack, ack);
    assert.deepEqual(await command(a.ws, create), ack);
    assert.equal((await command(a.ws, { ...create, room: "14" })).ok, false);
    assert.equal(
      (
        await command(b.ws, {
          id: "complete",
          type: "request.complete",
          taskId: ack.taskId,
          revision: 2,
        })
      ).ok,
      true,
    );
    const sara = await connect("sara");
    assert.equal(sara.snapshot.tasks.length, 0);
    const privateLookup = wait(sara.ws, (m) => m.type === "command.status");
    sara.ws.send(
      JSON.stringify({ type: "command.status", commandId: create.id }),
    );
    assert.equal((await privateLookup).found, false);
    const bad = new WebSocket(`ws://127.0.0.1:${port}`);
    sockets.push(bad);
    await once(bad, "open");
    const closed = once(bad, "close");
    bad.send(JSON.stringify({ type: "auth", token: { toString: null } }));
    assert.equal((await closed)[0], 1008);
    assert.equal((await fetch(`http://127.0.0.1:${port}/health`)).status, 200);
  } finally {
    for (const ws of sockets) ws.terminate();
    if (child && child.exitCode === null && child.signalCode === null) {
      const exit = once(child, "exit");
      child.kill();
      await exit;
    }
    rmSync(dir, { recursive: true, force: true });
  }
});
