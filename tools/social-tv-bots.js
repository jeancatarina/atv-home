// Test phones against the real TV (no Chrome): join, stay online by long-polling, answer whatever the phone asks.
//   KEY=$(tools/social-tv-open.sh) TV_URL=http://192.168.1.3:8766 node tools/social-tv-bots.js
// NAMES=a,b,c picks the names; PASSIVE=1 only sits. Afterwards, forget each bot (POST /api/forget) so the house ranking stays clean.
const B = process.env.TV_URL, K = process.env.KEY;
const names = (process.env.NAMES || 'Ana,Bia,Caio,Dani,Edu').split(',');
const pick = (a) => a[Math.floor(Math.random() * a.length)];
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
async function post(path, body, token) {
  const r = await fetch(B + path, { method: 'POST', headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, body: JSON.stringify(body) });
  return r.json().catch(() => ({}));
}
async function bot(name, i) {
  const j = await post('/api/join', { key: K, name, device: 'bot-device-' + name.toLowerCase().padEnd(20, '0') });
  const token = j.token; let v = 0, lastAct = '';
  console.log(name, 'joined', j.me);
  for (;;) {
    try {
      const r = await fetch(B + '/api/view?since=' + v, { headers: { Authorization: 'Bearer ' + token } });
      const d = await r.json(); v = d.v;
      const w = d.view && d.view.body; const phase = d.view && d.view.phase;
      if (!w || process.env.PASSIVE) continue;
      const sig = phase + JSON.stringify(w).length + (w.chosen ?? '') + (w.done ?? '');
      if (sig === lastAct) continue;
      await sleep(800 + Math.random() * 2500 + i * 300);
      let a = null;
      if (w.w === 'pick' && w.options?.length && w.chosen == null) a = ['pick', { id: pick(w.options) }];
      else if (w.w === 'choose' && w.options?.length && w.chosen == null) a = ['choose', { key: pick(w.options).key }];
      else if (w.w === 'text' && !w.done) a = ['text', { value: pick(['praia', 'pizza', 'cachorro', 'chuva', 'cinema']) }];
      else if (w.w === 'scale' && w.chosen == null) a = ['scale', { value: Math.floor(Math.random() * 11) }];
      else if (w.w === 'ready' && !w.done) a = ['ready', {}];
      else if (d.view.buttons?.length && Math.random() < 0.3) a = ['button', { key: pick(d.view.buttons).key }];
      if (a) { lastAct = sig; await post('/api/act', { aid: Math.random().toString(36).slice(2), phase, type: a[0], data: a[1] }, token); }
      if (Math.random() < 0.04) post('/api/react', { kind: pick(['laugh', 'wow', 'clap', 'fire']) }, token);
    } catch (e) { await sleep(1000); }
  }
}
names.forEach((n, i) => bot(n, i));
