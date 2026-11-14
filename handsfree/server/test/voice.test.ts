import { test } from "node:test";
import assert from "node:assert/strict";
import {
  routeSpeech,
  normalizeSpeech,
  type VoiceState,
} from "../public/voice-gate.js";
import { speechHttp } from "../src/speech-http.ts";
import { createServer } from "node:http";
import { once } from "node:events";
import type { LocalSpeech } from "../src/local-speech.ts";
const ready: VoiceState = {
  card: { phase: "idle", promptId: 0, title: "Ready", microphone: false },
  renderedPromptId: 0,
};
const review: VoiceState = {
  card: { phase: "review", promptId: 7, title: "Ask Ben?", microphone: true },
  renderedPromptId: 7,
};
test("only standalone wake phrase opens an idle command window", () => {
  assert.equal(routeSpeech("Hospital", ready, ready), "wake");
  assert.equal(routeSpeech("hey hospital", ready, ready), "wake");
  for (const text of [
    "send request",
    "hospital send request",
    "we work at the hospital",
  ])
    assert.equal(routeSpeech(text, ready, ready), "ignore");
});
test("confirmation belongs to exact visible review and is not rewoken", () => {
  assert.equal(routeSpeech("send request", review, review), "transcript");
  assert.equal(routeSpeech("hospital", review, review), "ignore");
  assert.equal(
    routeSpeech("send request", review, {
      ...review,
      card: { ...review.card, promptId: 8 },
    }),
    "stale",
  );
  const hidden = { ...review, renderedPromptId: 0 };
  assert.equal(routeSpeech("send request", hidden, hidden), "stale");
});
test("timed out, missing or replaced speech window cannot execute late recognition", () => {
  assert.equal(routeSpeech("send request", review, ready), "stale");
  assert.equal(routeSpeech("send request", undefined, review), "stale");
  assert.equal(
    routeSpeech("save reading", review, {
      ...review,
      card: { ...review.card, title: "Review reading" },
    }),
    "stale",
  );
});
test("normalize only exact patient alias and correction number syntax", () => {
  assert.equal(
    normalizeSpeech("select patient demo a"),
    "select patient demo-a",
  );
  assert.equal(
    normalizeSpeech("correct systolic one hundred and twenty five"),
    "correct systolic 125",
  );
  assert.equal(
    normalizeSpeech("correct pulse seventy seven"),
    "correct pulse 77",
  );
  for (const text of [
    "tell Ben patient demo a is ready",
    "correct pulse seven or seventy",
    "do not send request",
    "ask them to bring the hoist to room twelve",
  ])
    assert.equal(normalizeSpeech(text), text);
});
test("speech HTTP is same-origin, authorized, bounded, non-persistent and handles failure", async () => {
  let called = 0,
    retained: Buffer | undefined,
    fail = false;
  const speech = {
    ready: true,
    async transcribe(audio: Buffer) {
      called++;
      retained = audio;
      if (fail) throw Error("busy");
      return { text: "hospital", confidence: 1 };
    },
  } as unknown as LocalSpeech;
  let port = 0;
  const server = createServer(
    (req, res) =>
      void speechHttp(req, res, speech, port, { "aino-demo": "aino" }),
  );
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  port = (server.address() as any).port;
  const url = `http://127.0.0.1:${port}`;
  const headers = {
    Origin: url,
    Authorization: "Bearer aino-demo",
    "Content-Type": "application/octet-stream",
  };
  try {
    assert.equal((await fetch(url + "/speech/status")).status, 200);
    for (const changed of [
      { Origin: "https://example.com" },
      { Authorization: "Bearer invalid" },
    ])
      assert.equal(
        (
          await fetch(url + "/speech", {
            method: "POST",
            headers: { ...headers, ...changed },
            body: Buffer.alloc(4000),
          })
        ).status,
        403,
      );
    assert.equal(called, 0);
    assert.equal(
      (
        await fetch(url + "/speech", {
          method: "POST",
          headers,
          body: Buffer.alloc(256002),
        })
      ).status,
      413,
    );
    assert.equal(called, 0);
    const result = await fetch(url + "/speech", {
      method: "POST",
      headers,
      body: Buffer.alloc(4000, 1),
    });
    assert.equal(result.status, 200);
    assert.deepEqual(await result.json(), { text: "hospital", confidence: 1 });
    assert.equal(
      retained?.every((v) => v === 0),
      true,
    );
    fail = true;
    assert.equal(
      (
        await fetch(url + "/speech", {
          method: "POST",
          headers,
          body: Buffer.alloc(4000),
        })
      ).status,
      409,
    );
  } finally {
    server.closeAllConnections();
    await new Promise<void>((r) => server.close(() => r()));
  }
});

test("PCM wrapper preserves samples and declares exact offline audio format", async () => {
  const { pcmWav } = await import("../src/local-speech.ts");
  const raw = Buffer.from([1, 0, 255, 127]);
  const wav = pcmWav(raw);
  assert.equal(wav.toString("ascii", 0, 4), "RIFF");
  assert.equal(wav.readUInt32LE(24), 16000);
  assert.equal(wav.readUInt16LE(22), 1);
  assert.equal(wav.readUInt16LE(34), 16);
  assert.equal(wav.readUInt32LE(40), raw.length);
  assert.deepEqual(wav.subarray(44), raw);
});
