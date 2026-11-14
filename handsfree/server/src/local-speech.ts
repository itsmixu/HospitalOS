import {
  spawn,
  spawnSync,
  type ChildProcessWithoutNullStreams,
} from "node:child_process";
import { existsSync } from "node:fs";
import { resolve } from "node:path";

export function pcmWav(audio: Buffer) {
  const wav = Buffer.alloc(44 + audio.length);
  wav.write("RIFF");
  wav.writeUInt32LE(36 + audio.length, 4);
  wav.write("WAVEfmt ", 8);
  wav.writeUInt32LE(16, 16);
  wav.writeUInt16LE(1, 20);
  wav.writeUInt16LE(1, 22);
  wav.writeUInt32LE(16000, 24);
  wav.writeUInt32LE(32000, 28);
  wav.writeUInt16LE(2, 32);
  wav.writeUInt16LE(16, 34);
  wav.write("data", 36);
  wav.writeUInt32LE(audio.length, 40);
  audio.copy(wav, 44);
  return wav;
}
/** One bounded local process per utterance. WAV goes through stdin, never a recording file. */
export class LocalSpeech {
  readonly bin =
    process.env.HF_WHISPER_BIN ||
    spawnSync("which", ["whisper-cli"], { encoding: "utf8" }).stdout?.trim() ||
    "";
  readonly model = resolve(
    process.env.HF_SPEECH_MODEL || ".local/ggml-base.en.bin",
  );
  ready = existsSync(this.bin) && existsSync(this.model);
  private child?: ChildProcessWithoutNullStreams;
  async transcribe(audio: Buffer): Promise<{ text: string; confidence: null }> {
    if (!this.ready) throw Error("Local speech unavailable");
    if (this.child) throw Error("Local speech busy");
    if (audio.length < 3200 || audio.length > 256000 || audio.length % 2)
      throw Error("Invalid audio length");
    let energy = 0;
    for (let i = 0; i < audio.length; i += 2)
      energy += (audio.readInt16LE(i) / 32768) ** 2;
    if (Math.sqrt(energy / (audio.length / 2)) < 0.004)
      return { text: "", confidence: null };
    const wav = pcmWav(audio);
    return new Promise((resolve, reject) => {
      const child = (this.child = spawn(
        this.bin,
        [
          "-m",
          this.model,
          "-f",
          "-",
          "-otxt",
          "-of",
          "-",
          "-l",
          "en",
          "-nt",
          "-np",
          "--prompt",
          "HospitalOS. Ben. Sara. Aino. Select patient. Confirm patient. Capture reading. Save reading. Share reading. Send attachment. Send request.",
          "-sns",
          "-bs",
          "1",
          "-bo",
          "1",
          "-t",
          "4",
          "-ng",
        ],
        { stdio: ["pipe", "pipe", "pipe"] },
      ));
      let text = "",
        settled = false;
      const finish = (error?: Error) => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        wav.fill(0);
        this.child = undefined;
        if (error) {
          child.kill("SIGKILL");
          reject(error);
        } else resolve({ text: text.trim().slice(0, 201), confidence: null });
      };
      const timer = setTimeout(() => {
        child.kill("SIGKILL");
        finish(Error("Local speech timed out"));
      }, 5000);
      child.stdout.on("data", (chunk) => {
        text += chunk.toString();
        if (text.length > 4096) {
          child.kill();
          finish(Error("Invalid speech output"));
        }
      });
      child.stderr.on("data", () => {}); // Deliberately discard diagnostics; never log spoken content.
      child.on("error", () => finish(Error("Local speech unavailable")));
      child.on("close", (code) =>
        finish(code === 0 ? undefined : Error("Local speech failed")),
      );
      child.stdin.on("error", () => finish(Error("Local speech failed")));
      child.stdin.end(wav);
    });
  }
  stop() {
    this.ready = false;
    this.child?.kill();
  }
}
