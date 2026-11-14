export type VoiceState = {
  card: { phase: string; promptId: number; title: string; microphone: boolean };
  renderedPromptId: number;
};
export function windowKey(state?: VoiceState): string;
export function routeSpeech(
  text: string,
  started?: VoiceState,
  current?: VoiceState,
): "stale" | "ignore" | "wake" | "transcript";
export function normalizeSpeech(text: string): string;
