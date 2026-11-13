const $ = (id) => document.getElementById(id);
let ws,
  state,
  selected = null;
const delivered = new Set();
function cmd(type, payload) {
  if (ws?.readyState !== 1) {
    $("status").textContent = "Disconnected — action not sent";
    return;
  }
  ws.send(JSON.stringify({ id: crypto.randomUUID(), type, payload }));
}
$("connect").onclick = () => {
  ws?.close();
  selected = null;
  delivered.clear();
  ws = new WebSocket(
    `${location.protocol === "https:" ? "wss" : "ws"}://${location.host}`,
  );
  ws.onopen = () =>
    ws.send(JSON.stringify({ type: "auth", token: $("token").value }));
  ws.onclose = () => {
    $("status").textContent = "Disconnected — requests are not replayed";
  };
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data);
    if (m.type === "error") {
      $("status").textContent = m.message;
      return;
    }
    if (m.type !== "snapshot") return;
    state = m.payload;
    $("status").textContent = "Connected · synthetic data";
    $("payload").textContent = JSON.stringify(m, null, 2);
    render();
  };
};
$("disconnect").onclick = () => ws?.close();
function options(id, items, label) {
  const old = $(id).value;
  $(id).replaceChildren(...items.map((x) => new Option(label(x), x.id)));
  if (items.some((x) => x.id === old)) $(id).value = old;
}
function render() {
  options("patient", state.patients, (p) => `${p.name} · room ${p.room}`);
  options("recipient", state.staff, (s) => s.name);
  $("tasks").replaceChildren();
  for (const t of state.tasks) {
    const d = document.createElement("div");
    d.className = "task";
    const p = document.createElement("p");
    p.textContent = `${t.summary ?? "Assistance"} · ${t.status} · ${t.from} → ${t.to}`;
    d.append(p);
    for (const [label, type, extra] of [
      ["Acknowledge delivery", "message.delivered", {}],
      ["Accept", "help.response", { accept: true }],
      ["Decline", "help.response", { accept: false }],
      ["Confirm completed", "help.complete", {}],
      ["Cancel", "help.cancel", {}],
    ]) {
      const b = document.createElement("button");
      b.textContent = label;
      b.onclick = () => cmd(type, { taskId: t.id, ...extra });
      d.append(b);
    }
    $("tasks").append(d);
  }
  $("notes").textContent = JSON.stringify(state.notes, null, 2);
  $("encounter").textContent = JSON.stringify(state.drafts, null, 2);
}
$("select").onclick = () => {
  const p = state?.patients.find((p) => p.id === $("patient").value);
  if (!p) return;
  selected = p.id;
  cmd("patient.select", { patientId: selected });
  $("selected").textContent = `Confirmed: ${p.name} (${p.id})`;
};
$("request").onclick = () => {
  if (!selected) {
    $("status").textContent = "Confirm a patient first";
    return;
  }
  cmd("help.request", {
    patientId: selected,
    to: $("recipient").value,
    summary: $("summary").value,
  });
};
$("start").onclick = () => {
  if (!selected || !$("consent").checked) {
    $("status").textContent = "Confirm patient and consent first";
    return;
  }
  cmd("log.start", { patientId: selected, consent: true });
};
for (const [id, type] of [
  ["draft", "log.stop"],
  ["confirm", "log.confirm"],
  ["discard", "log.discard"],
])
  $(id).onclick = () => {
    const d = state?.drafts[0];
    if (!d) {
      $("status").textContent = "No encounter";
      return;
    }
    cmd(type, {
      encounterId: d.id,
      reported: $("reported").value,
      planned: $("planned").value,
    });
    if (id === "discard" || id === "confirm") {
      $("reported").value = "";
      $("planned").value = "";
    }
  };
