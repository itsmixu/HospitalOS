import { windowKey, routeSpeech, normalizeSpeech } from "./voice-gate.js";
export function attachLiveVoice({ state, connected, token, sendInput }) {
  const button = document.getElementById("voice-start"),
    stop = document.getElementById("voice-stop"),
    status = document.getElementById("voice-status"),
    heard = document.getElementById("voice-heard");
  let stream,
    context,
    node,
    source,
    expiry,
    active = false,
    starting = false,
    busy = false,
    generation = 0,
    abort;
  let frames = [],
    pre = [],
    speaking = false,
    silent = 0,
    voiced = 0,
    started;
  const say = (text) => {
    status.textContent = text;
  };
  function clear() {
    frames = [];
    pre = [];
    speaking = false;
    silent = 0;
    voiced = 0;
    started = undefined;
  }
  async function end(message = "Microphone off") {
    if (active && state()?.card?.microphone)
      sendInput({ kind: "transcript", text: "cancel" });
    generation++;
    active = false;
    starting = false;
    clearTimeout(expiry);
    abort?.abort();
    node?.disconnect();
    source?.disconnect();
    stream?.getTracks().forEach((t) => t.stop());
    stream = undefined;
    node = undefined;
    source = undefined;
    await context?.close().catch(() => {});
    context = undefined;
    clear();
    busy = false;
    button.disabled = false;
    stop.disabled = true;
    sendInput({ kind: "voice-session", active: false });
    say(message);
  }
  async function recognize(samples, stamp) {
    const mine = generation;
    busy = true;
    say("Recognizing on this computer…");
    const pcm = new ArrayBuffer(samples.length * 3200);
    const view = new DataView(pcm);
    let offset = 0;
    for (const frame of samples)
      for (const value of frame) {
        view.setInt16(
          offset,
          Math.round(Math.max(-1, Math.min(1, value)) * 32767),
          true,
        );
        offset += 2;
      }
    samples.forEach((f) => f.fill(0));
    abort = new AbortController();
    const timer = setTimeout(() => abort.abort(), 6000);
    try {
      const response = await fetch("/speech", {
        method: "POST",
        headers: {
          "Content-Type": "application/octet-stream",
          Authorization: `Bearer ${token()}`,
        },
        body: pcm,
        signal: abort.signal,
      });
      if (!response.ok)
        throw new Error(
          response.status === 409
            ? "Recognizer busy — try again"
            : "Local recognizer unavailable",
        );
      const result = await response.json();
      if (!active || mine !== generation) return;
      const text = normalizeSpeech(result.text || "");
      heard.textContent = text
        ? `Heard: “${text}”`
        : "No clear speech detected";
      if (
        !text ||
        text.length > 200 ||
        (typeof result.confidence === "number" && result.confidence < 0.6)
      ) {
        say("Not clear enough. Please repeat.");
        return;
      }
      if (/^stop listening$/i.test(text)) {
        await end();
        return;
      }
      const route = routeSpeech(text, stamp, state());
      if (route === "wake") {
        sendInput({ kind: "wake" });
        say("Command window opening — speak after Listening appears");
      } else if (route === "transcript") {
        sendInput({ kind: "transcript", text, final: true });
        say("Command heard — check the glasses");
      } else if (route === "stale")
        say("Screen changed while you spoke. Repeat for the current screen.");
      else say("Microphone on · say “Hey Lumi”, pause, then your command");
    } catch (e) {
      if (active && mine === generation)
        await end(
          e.name === "AbortError"
            ? "Speech timed out · microphone stopped"
            : e.message,
        );
    } finally {
      new Uint8Array(pcm).fill(0);
      clearTimeout(timer);
      if (mine === generation) {
        busy = false;
        clear();
      }
    }
  }
  function frame(data) {
    if (!active || busy) return;
    if (!connected()) {
      void end("Connection lost · microphone stopped");
      return;
    }
    const rms = Math.sqrt(
      data.reduce((sum, n) => sum + n * n, 0) / data.length,
    );
    const sound = rms > 0.012;
    if (!speaking) {
      pre.push(data);
      if (pre.length > 3) pre.shift();
      if (!sound) return;
      speaking = true;
      started = state() ? structuredClone(state()) : undefined;
      frames = pre;
      pre = [];
      voiced = 1;
      silent = 0;
      say("Hearing speech…");
      return;
    }
    frames.push(data);
    if (sound) {
      voiced++;
      silent = 0;
    } else silent++;
    if (frames.length >= 80) {
      clear();
      say("Phrase too long. Use one short command.");
      return;
    }
    if (silent >= 7) {
      const samples = frames,
        stamp = started,
        voiceFrames = voiced;
      clear();
      if (voiceFrames >= 2) void recognize(samples, stamp);
    }
  }
  button.onclick = async () => {
    if (active || starting) return;
    if (
      !connected() ||
      !state() ||
      ["offline", "reconciling"].includes(state().card.phase)
    ) {
      say("Connect the tester and ready Android display first");
      return;
    }
    starting = true;
    button.disabled = true;
    stop.disabled = false;
    const mine = ++generation;
    say("Checking offline speech model…");
    try {
      const response = await fetch("/speech/status");
      const service = await response.json();
      if (!service.ready)
        throw new Error(
          "Local speech model unavailable — run the speech setup",
        );
      if (mine !== generation) return;
      stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          channelCount: 1,
          echoCancellation: true,
          noiseSuppression: true,
        },
        video: false,
      });
      if (mine !== generation) {
        stream.getTracks().forEach((t) => t.stop());
        return;
      }
      context = new AudioContext();
      await context.resume();
      await context.audioWorklet.addModule("/pcm-worklet.js");
      if (mine !== generation) {
        await end();
        return;
      }
      source = context.createMediaStreamSource(stream);
      node = new AudioWorkletNode(context, "pcm-capture");
      node.port.onmessage = (e) => frame(e.data);
      source.connect(node);
      node.connect(context.destination);
      for (const track of stream.getTracks())
        track.onended = () => {
          if (active) void end("Microphone disconnected");
        };
      active = true;
      starting = false;
      clear();
      sendInput({ kind: "voice-session", active: true });
      say("Microphone on · say “Hey Lumi”, pause, then your command");
      expiry = setTimeout(
        () => void end("Five-minute demo session ended · microphone off"),
        300000,
      );
    } catch (e) {
      await end(
        e.name === "NotAllowedError"
          ? "Microphone permission denied — enable it in the browser"
          : e.message,
      );
    }
  };
  stop.onclick = () => void end();
  document.addEventListener("visibilitychange", () => {
    if (document.hidden && (active || starting))
      void end("Page hidden · microphone off");
  });
  window.addEventListener("pagehide", () => {
    stream?.getTracks().forEach((t) => t.stop());
    abort?.abort();
    sendInput({ kind: "voice-session", active: false });
  });
  return {
    stop: () => void end("Connection changed · microphone off"),
    isActive: () => active,
  };
}
