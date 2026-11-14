// A completed utterance belongs only to the command window in which it started.
export function windowKey(state) {
  const c = state?.card;
  return c
    ? [c.phase, c.promptId, c.title, c.microphone, state.renderedPromptId].join(
        "|",
      )
    : "";
}
export function routeSpeech(text, started, current) {
  if (!started || !current || windowKey(started) !== windowKey(current))
    return "stale";
  if (/^(hey[, ]+lumi|(hey )?hospital)[.!?]?$/i.test(text.trim()))
    return current.card.microphone ? "ignore" : "wake";
  if (!current.card.microphone) return "ignore";
  if (
    current.card.phase === "review" &&
    current.renderedPromptId !== current.card.promptId
  )
    return "stale";
  return "transcript";
}
const ones = [
  "zero",
  "one",
  "two",
  "three",
  "four",
  "five",
  "six",
  "seven",
  "eight",
  "nine",
  "ten",
  "eleven",
  "twelve",
  "thirteen",
  "fourteen",
  "fifteen",
  "sixteen",
  "seventeen",
  "eighteen",
  "nineteen",
];
const tens = {
  twenty: 20,
  thirty: 30,
  forty: 40,
  fifty: 50,
  sixty: 60,
  seventy: 70,
  eighty: 80,
  ninety: 90,
};
function number(words) {
  const raw = words.trim();
  if (/^\d{1,3}$/.test(raw)) return Number(raw);
  const tokens = raw.replace(/-/g, " ").split(/\s+/);
  let value = 0;
  const h = tokens.indexOf("hundred");
  if (h !== -1) {
    if (h !== 1 || ones.indexOf(tokens[0]) < 1 || ones.indexOf(tokens[0]) > 9)
      return null;
    value = ones.indexOf(tokens[0]) * 100;
    tokens.splice(0, 2);
    if (tokens[0] === "and") tokens.shift();
  }
  if (!tokens.length) return value;
  if (tokens.length === 1 && ones.includes(tokens[0]))
    return value + ones.indexOf(tokens[0]);
  if (tokens.length === 1 && tens[tokens[0]]) return value + tens[tokens[0]];
  if (
    tokens.length === 2 &&
    tens[tokens[0]] &&
    ones.indexOf(tokens[1]) > 0 &&
    ones.indexOf(tokens[1]) < 10
  )
    return value + tens[tokens[0]] + ones.indexOf(tokens[1]);
  return null;
}
export function normalizeSpeech(text) {
  const clean = text.trim().replace(/[.!?]+$/, "");
  if (/^select patient demo [ab]$/i.test(clean))
    return clean.replace(
      /demo ([ab])$/i,
      (_, id) => "demo-" + id.toLowerCase(),
    );
  const correction = /^correct (systolic|diastolic|pulse) (.+)$/i.exec(clean);
  if (correction) {
    const n = number(correction[2].toLowerCase());
    if (n !== null && n <= 999)
      return `correct ${correction[1].toLowerCase()} ${n}`;
  }
  return text.trim();
}
