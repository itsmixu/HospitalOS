import type { IncomingMessage, ServerResponse } from "node:http";
import type { LocalSpeech } from "./local-speech.ts";
export async function speechHttp(
  req: IncomingMessage,
  res: ServerResponse,
  speech: LocalSpeech,
  port: number,
  tokens: Record<string, string>,
) {
  res.setHeader("Cache-Control", "no-store");
  res.setHeader("Content-Type", "application/json");
  const reply = (code: number, value: unknown) => {
    res.writeHead(code).end(JSON.stringify(value));
  };
  if (req.url === "/speech/status" && req.method === "GET") {
    reply(200, { ready: speech.ready, local: true });
    return;
  }
  if (req.method !== "POST" || req.url !== "/speech") {
    reply(405, { error: "Method not allowed" });
    return;
  }
  if (
    ![`http://127.0.0.1:${port}`, `http://localhost:${port}`].includes(
      req.headers.origin || "",
    ) ||
    !Object.hasOwn(
      tokens,
      (req.headers.authorization || "").replace(/^Bearer /, ""),
    )
  ) {
    reply(403, { error: "Speech access denied" });
    return;
  }
  if (req.headers["content-type"] !== "application/octet-stream") {
    reply(415, { error: "Use mono 16 kHz PCM" });
    return;
  }
  const chunks: Buffer[] = [];
  let size = 0;
  try {
    for await (const data of req) {
      const chunk = Buffer.from(data);
      size += chunk.length;
      if (size > 256000) {
        reply(413, { error: "Utterance too long" });
        return;
      }
      chunks.push(chunk);
    }
    const audio = Buffer.concat(chunks);
    try {
      const result = await speech.transcribe(audio);
      reply(200, result);
    } finally {
      audio.fill(0);
    }
  } catch (e) {
    if (!res.headersSent)
      reply(e instanceof Error && e.message.includes("busy") ? 409 : 503, {
        error: "Local speech unavailable",
      });
  } finally {
    chunks.forEach((c) => c.fill(0));
  }
}
