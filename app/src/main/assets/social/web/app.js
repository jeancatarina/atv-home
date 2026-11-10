'use strict';
(() => {
  // Same colours and shapes as the TV (Palette.kt); a test keeps them equal.
  const COLORS = ['#ff453a', '#ff9f0a', '#ffd60a', '#30d158', '#64d2ff', '#0a84ff', '#bf5af2', '#ff6482', '#ac8e68', '#63e6e2', '#a2845e', '#98989d'];
  const SHAPES = ['●', '▲', '■', '◆', '★', '✚'];
  const STAMPS = [['suspect', 'Suspeito!'], ['lie', 'Mentira!'], ['laugh', 'Kkkk'], ['wow', 'Uau!']];
  const $ = (id) => document.getElementById(id);
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

  let key = null, token = null, v = 0, offset = 0, last = null, polling = null, closedForGood = false;

  // This phone's own random id: how the TV knows it came back another day (never shown to anyone)
  let device = localStorage.getItem('social.device');
  if (!device) {
    device = Array.from(crypto.getRandomValues(new Uint8Array(16)), (b) => b.toString(16).padStart(2, '0')).join('');
    localStorage.setItem('social.device', device);
  }

  const hash = new URLSearchParams(location.hash.slice(1));
  if (hash.get('k')) { localStorage.setItem('social.key', hash.get('k')); history.replaceState(null, '', location.pathname); }
  key = localStorage.getItem('social.key');
  token = key && localStorage.getItem('social.token.' + key);
  $('name').value = localStorage.getItem('social.name') || '';
  if ($('name').value) $('joinSub').textContent = 'Que bom te ver de novo, ' + $('name').value + '.';

  const show = (id) => ['join', 'closed', 'game'].forEach((s) => { $(s).hidden = s !== id; });
  const toast = (t) => { const el = $('toast'); el.textContent = t; el.hidden = false; clearTimeout(toast.t); toast.t = setTimeout(() => { el.hidden = true; }, 2200); };

  async function api(path, body) {
    const r = await fetch(path, {
      method: body === undefined ? 'GET' : 'POST', cache: 'no-store',
      headers: Object.assign({ 'Content-Type': 'application/json' }, token ? { Authorization: 'Bearer ' + token } : {}),
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    return r;
  }

  // --- The face: an optional selfie, shrunk here to a 112 px JPEG (about 6 KB) before it leaves the phone ----
  let faceB64 = null;
  // A person is their face on the TV, so it is required to join (a phone that already sent one may skip it)
  async function shrink(file) {
    const bmp = await createImageBitmap(file);
    const s = Math.min(bmp.width, bmp.height), N = 112;
    const c = document.createElement('canvas'); c.width = c.height = N;
    c.getContext('2d').drawImage(bmp, (bmp.width - s) / 2, (bmp.height - s) / 2, s, s, 0, 0, N, N);
    const url = c.toDataURL('image/jpeg', 0.72);
    return { url, b64: url.slice(url.indexOf(',') + 1) };
  }
  $('face').onchange = async (ev) => {
    const file = ev.target.files && ev.target.files[0];
    if (!file) return;
    try {
      const f = await shrink(file); faceB64 = f.b64;
      $('facePreview').src = f.url; $('facePreview').hidden = false; $('faceHint').textContent = 'Trocar a foto'; $('rememberRow').hidden = false; $('joinErr').textContent = '';
    } catch (e) { toast('Não consegui usar essa foto'); }
  };
  // Later, from the lobby or the profile: add or change the photo at any time
  $('face2').onchange = async (ev) => {
    const file = ev.target.files && ev.target.files[0]; ev.target.value = '';
    if (!file) return;
    try {
      const f = await shrink(file);
      const r = await api('/api/face', { jpeg: f.b64, remember: !!localStorage.getItem('social.remember') });
      if ((await r.json()).ok) { localStorage.setItem('social.hasFace', '1'); toast('Foto atualizada ✓'); } else toast('A TV não aceitou essa foto');
    } catch (e) { toast('Não consegui enviar a foto'); }
  };
  const pickFace = () => $('face2').click();
  async function sendFace() {
    if (!faceB64) return;
    try { await (await api('/api/face', { jpeg: faceB64, remember: $('remember').checked })).text(); localStorage.setItem('social.hasFace', '1'); if ($('remember').checked) localStorage.setItem('social.remember', '1'); } catch (e) { /* the game goes on without a face */ }
    faceB64 = null;
  }

  // --- Joining ---------------------------------------------------------------------------------
  function closed(title) { closedForGood = true; $('closedTitle').textContent = title || 'A sala fechou'; show('closed'); }

  $('go').onclick = async () => {
    const name = $('name').value.trim();
    if (!faceB64 && !localStorage.getItem('social.hasFace')) { $('joinErr').textContent = 'Tira uma selfie pra entrar: é o seu rosto na TV.'; return; }
    localStorage.setItem('social.name', name);
    $('joinErr').textContent = '';
    try {
      const r = await api('/api/join', { key, name, seat: token || undefined, device });
      if (r.status === 403) return closed();
      if (r.status === 409) { $('joinErr').textContent = 'A sala tá cheia.'; return; }
      if (r.status === 429) { $('joinErr').textContent = 'Tentou demais. Espera um minutinho.'; return; }
      const d = await r.json();
      token = d.token; localStorage.setItem('social.token.' + key, token);
      sendFace();
      start();
    } catch (e) { $('joinErr').textContent = 'Não consegui falar com a TV. Tá no mesmo Wi-Fi?'; }
  };

  // --- Long poll -------------------------------------------------------------------------------
  function start() {
    if (polling) polling.abort();
    show('game');
    loop();
  }

  // After any failure the first poll answers at once (wait=0), so the banner clears as soon as the
  // TV is reachable instead of after a held 25 s poll. A poll the TV never answers (a half-dead
  // connection, common on phones that slept) is cut at 35 s.
  let recovering = false;
  async function loop() {
    let wait = 500;
    for (;;) {
      if (closedForGood) return;
      const ctl = (polling = new AbortController());
      let timedOut = false;
      const cut = setTimeout(() => { timedOut = true; ctl.abort(); }, 35000);
      try {
        const r = await fetch('/api/view?since=' + v + (recovering ? '&wait=0' : ''), { headers: { Authorization: 'Bearer ' + token }, cache: 'no-store', signal: ctl.signal });
        if (r.status === 401) { token = null; localStorage.removeItem('social.token.' + key); return fresh(); }
        if (!r.ok) throw new Error(r.status);
        const d = await r.json();
        wait = 500; recovering = false; $('banner').hidden = true;
        offset = d.now - Date.now();
        if (d.v !== v) {
          v = d.v;
          // A drawing bug must not look like a network failure
          try { render(d); } catch (err) { console.error('render', err); }
        }
      } catch (e) {
        if (ctl.signal.aborted && !timedOut) continue; // we aborted on purpose (the phone woke up)
        recovering = true;
        $('banner').hidden = false;
        await sleep(wait); wait = Math.min(wait * 2, 4000);
      } finally { clearTimeout(cut); }
    }
  }

  // The seat is gone but the room may still exist: ask for a name again, or say the room closed
  async function fresh() {
    v = 0; last = null;
    $('joinSub').textContent = 'Você saiu da sala. Entra de novo.';
    show('join');
  }

  document.addEventListener('visibilitychange', () => { if (!document.hidden && polling) { recovering = true; polling.abort(); } });

  // --- Drawing ---------------------------------------------------------------------------------
  const el = (tag, cls, text) => { const e = document.createElement(tag); if (cls) e.className = cls; if (text != null) e.textContent = text; return e; };
  const playerOf = (d, id) => d.players.find((p) => p.id === id) || { id, name: '?', color: 0, shape: 0 };
  function dot(p) { const d = el('span', 'dot', SHAPES[p.shape % SHAPES.length]); d.style.background = COLORS[p.color % COLORS.length]; return d; }
  function who(p) { const b = el('span', 'chip'); b.append(dot(p), el('span', 'name', p.name)); return b; }


  // --- Level, profile and moments ------------------------------------------------------------------
  const tierOf = (lv) => (lv >= 50 ? 5 : lv >= 40 ? 4 : lv >= 30 ? 3 : lv >= 20 ? 2 : lv >= 10 ? 1 : 0);
  const TIER_NAMES = ['Bronze', 'Prata', 'Ouro', 'Platina', 'Diamante', 'Lenda'];
  const METAL = ['#cd7f32', '#d1d1d6', '#ffd60a'];
  const hex = (lv, size) => { const h = el('span', 'hex t-' + tierOf(lv), String(lv)); if (size) h.style.setProperty('--s', size + 'px'); return h; };

  function meCard(p) {
    p.into = p.into || 0; p.span = p.span || 0;
    const c = el('button', 'mecard'); c.style.setProperty('--c', ['#f0b27a', '#e5e5ea', '#ffd60a', '#7fe3da', '#b992ff', '#ff6b8e'][tierOf(p.level)]);
    const ring = el('div', 'ring'); ring.style.setProperty('--p', p.span ? Math.round(100 * p.into / p.span) : 100); ring.append(el('div', null, String(p.level)));
    const t = el('div'); t.append(el('div', 't', p.title), el('div', 's', TIER_NAMES[tierOf(p.level)] + ' · ' + (p.span ? 'faltam ' + (p.span - p.into) + ' XP' : 'nível máximo')));
    const bar = el('div', 'xp'); const fill = el('i'); fill.style.width = (p.span ? Math.round(100 * p.into / p.span) : 100) + '%'; bar.append(fill); t.append(bar);
    c.append(ring, t); c.onclick = () => openProfile(current);
    return c;
  }

  const sheet = $('sheet'), sheetBody = $('sheetBody');
  let sheetOpen = false;
  function openSheet(build, onClose) {
    sheetBody.replaceChildren(); build(sheetBody); sheet.hidden = false; sheetOpen = true; sheet.onclose = onClose || null;
    sheet.firstElementChild.onclick = closeSheet;
  }
  function closeSheet() { sheet.hidden = true; sheetOpen = false; sheetBody.replaceChildren(); stopConfetti(); const f = sheet.onclose; sheet.onclose = null; if (f) f(); else setTimeout(nextMoment, 250); }

  function openProfile(d) {
    const p = d && d.profile; if (!p) return;
    // The server leaves out empty lists and zeros
    p.rivals = p.rivals || []; p.badges = p.badges || []; p.challenges = p.challenges || []; p.wins = p.wins || 0; p.seasonXp = p.seasonXp || 0; p.streakWeeks = p.streakWeeks || 0;
    openSheet((root) => {
      root.append(meCard(p)); root.lastChild.onclick = null;
      const st = el('div', 'stats');
      [[p.wins, 'vitórias'], [p.seasonXp, 'XP no mês' + (p.seasonRank ? ' · ' + p.seasonRank + 'º' : '')], [p.streakWeeks, 'semanas seguidas']].forEach(([n, l]) => { const b = el('div'); b.append(el('b', null, String(n)), el('span', null, l)); st.append(b); });
      root.append(el('div', 'sec', 'Seus números'), st);
      if (p.challenges.length) {
        root.append(el('div', 'sec', 'Desafios da semana'));
        p.challenges.forEach((c) => { const b = el('div', 'chal' + (c.progress >= c.target ? ' done' : ''), c.text + (c.progress >= c.target ? ' ✓' : '  ' + c.progress + '/' + c.target)); const pb = el('div', 'pb'); const i = el('i'); i.style.width = Math.round(100 * Math.min(1, c.progress / c.target)) + '%'; pb.append(i); b.append(pb); root.append(b); });
      }
      root.append(el('div', 'sec', 'Conquistas · ' + p.badges.filter((b) => b.at).length + ' de ' + p.badges.length));
      const g = el('div', 'badges');
      p.badges.slice().sort((a, b) => (!!b.at - !!a.at) || b.tier - a.tier).forEach((b) => { const d1 = el('div', 'bdg' + (b.at ? '' : ' off')); const i = el('i', null, b.at ? '★' : '🔒'); i.style.setProperty('--m', METAL[Math.min(2, Math.max(0, b.tier - 1))]); d1.append(i, el('div', null, b.name)); d1.title = b.hint; g.append(d1); });
      root.append(g);
      if (p.rivals.length) {
        root.append(el('div', 'sec', 'Rivalidades'));
        p.rivals.forEach((r) => { const row = el('div', 'rival'); row.append(el('span', null, r.name), el('span', null, r.wins + ' × ' + r.losses)); root.append(row); });
      }
      const pf = el('button', 'link', '📷 Trocar minha foto'); pf.onclick = pickFace; root.append(pf);
      const f = el('button', 'link', 'Esquecer meus dados nesta TV'); f.onclick = forgetMe; root.append(f);
      const x = el('button', 'primary', 'Fechar'); x.onclick = closeSheet; root.append(x);
    });
  }

  async function forgetMe() {
    if (!confirm('Apagar seus pontos, seu rosto e este aparelho da memória da TV?')) return;
    try { await (await api('/api/forget', {})).text(); } catch (e) { /* offline: nothing to erase yet */ }
    ['social.device', 'social.name'].forEach((k) => localStorage.removeItem(k)); localStorage.removeItem('social.token.' + key);
    location.reload();
  }

  // Moments (level, badge, record, challenge) show once each, one after another, with a little confetti
  const MOMENTS = { LEVEL: ['Subiu de nível', '🚀', '#30d158'], BADGE: ['Conquista desbloqueada', '🏅', '#ffd60a'], RECORD: ['Recorde da casa', '🏆', '#ffd60a'], CHALLENGE: ['Desafio da semana', '✅', '#64d2ff'], STREAK: ['Em chamas', '🔥', '#ff9f0a'] };
  const seenKey = () => 'social.moments.' + key;
  const queue = [];
  function moments(d) {
    const list = d.moments || [];
    const max = list.reduce((m, c) => Math.max(m, c.id), 0);
    let seen = localStorage.getItem(seenKey());
    if (seen === null) { localStorage.setItem(seenKey(), String(max)); return; } // first visit: no old news
    seen = +seen;
    list.filter((c) => c.id > seen && !queue.some((q) => q.id === c.id)).forEach((c) => queue.push(c));
    if (max > seen) localStorage.setItem(seenKey(), String(max));
    nextMoment();
  }
  function nextMoment() {
    if (sheetOpen || !queue.length) return;
    const c = queue.shift(); const m = MOMENTS[c.kind] || ['Momento', '⭐', '#ffd60a'];
    openSheet((root) => {
      const b = el('div', 'moment'); const k = el('div', 'k', m[0]); k.style.color = m[2];
      b.append(k, el('div', 'ico', m[1]), el('h2', null, c.title), el('p', null, c.detail));
      const ok = el('button', 'primary', 'Boa!'); ok.onclick = closeSheet; b.append(ok); root.append(b);
    }, () => setTimeout(nextMoment, 250));
    if (navigator.vibrate) navigator.vibrate([30, 40, 30]);
    confetti(m[2]);
  }

  // A short burst, drawn on one canvas and stopped by itself
  let raf = 0;
  function stopConfetti() { cancelAnimationFrame(raf); const cv = $('confetti'); cv.hidden = true; }
  function confetti(color) {
    if (matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const cv = $('confetti'); cv.width = innerWidth; cv.height = innerHeight; cv.hidden = false;
    const g = cv.getContext('2d'); const palette = [color, '#fff', '#ffd60a', '#ff6482', '#64d2ff'];
    const ps = Array.from({ length: 70 }, () => ({ x: innerWidth / 2, y: innerHeight * 0.55, vx: (Math.random() - .5) * 14, vy: -6 - Math.random() * 11, r: 3 + Math.random() * 4, a: Math.random() * 6, c: palette[Math.floor(Math.random() * palette.length)] }));
    const t0 = performance.now();
    const step = (t) => {
      const age = t - t0; g.clearRect(0, 0, cv.width, cv.height);
      ps.forEach((p) => { p.vy += .35; p.x += p.vx; p.y += p.vy; p.a += .2; g.save(); g.globalAlpha = Math.max(0, 1 - age / 1800); g.translate(p.x, p.y); g.rotate(p.a); g.fillStyle = p.c; g.fillRect(-p.r, -p.r / 2, p.r * 2, p.r); g.restore(); });
      if (age < 1800) raf = requestAnimationFrame(step); else stopConfetti();
    };
    raf = requestAnimationFrame(step);
  }

  $('meBtn').onclick = () => openProfile(current);

  let current = null; // the envelope on screen
  function render(d) {
    current = d;
    $('paused').hidden = !d.paused;
    const view = d.view;
    if (last && last !== view.phase && navigator.vibrate) navigator.vibrate(25);
    last = view.phase;
    $('title').textContent = view.title || (view.phase === 'lobby' ? 'Na sala' : '');
    const body = $('body'); body.replaceChildren();
    draw(body, view.body, d);
    if (view.buttons && view.buttons.length) {
      const row = el('div', 'btnrow');
      view.buttons.forEach((bt) => { const b = el('button', '', bt.label); b.onclick = () => act('button', { key: bt.key }); row.append(b); });
      body.append(row);
    }
    const badge = $('badge');
    if (view.badge) { badge.hidden = false; hold(badge, view.badge.label + ': ', view.badge.value); } else { badge.hidden = true; }
    $('bar').hidden = $('clock').hidden = !view.endsAt;
    const mb = $('meBtn'); mb.hidden = !d.profile; if (d.profile) mb.replaceChildren(hex(d.profile.level, 30));
    moments(d);
    tick();
  }

  function hold(node, label, value) {
    const show = (on) => { node.replaceChildren(label, Object.assign(el('b'), { textContent: on ? value : '••••' })); };
    show(false);
    node.onpointerdown = (e) => { e.preventDefault(); show(true); };
    ['onpointerup', 'onpointercancel', 'onpointerleave'].forEach((h) => { node[h] = () => show(false); });
    node.oncontextmenu = (e) => e.preventDefault();
  }

  function draw(root, w, d) {
    switch (w.w) {
      case 'wait': {
        root.append(el('div', 'big', w.text));
        if (w.sub) root.append(el('div', 'sub2', w.sub));
        if (d.profile && (d.view.phase === 'lobby' || d.view.phase === 'watch' || d.view.phase === 'ceremony')) root.append(meCard(d.profile));
        if (d.standing && (d.view.phase === 'lobby' || d.view.phase === 'watch' || d.view.phase === 'ceremony')) {
          const s = d.standing;
          root.append(el('div', 'sub2', 'No ranking geral' + (s.rank ? ': ' + s.rank + 'º lugar' : '') + ' · ' + s.chips + ' fichas · ' + s.games + ' jogo' + (s.games === 1 ? '' : 's') + ' · ' + s.nights + ' noite' + (s.nights === 1 ? '' : 's')));
        }
        if (d.top && d.top.length && (d.view.phase === 'lobby' || d.view.phase === 'ceremony')) {
          const board = el('div', 'board');
          d.top.forEach((l, i) => { const row = el('div', l.me ? 'me' : ''); row.append(el('span', null, (i + 1) + 'º  ' + l.name), el('span', null, '★ ' + l.chips)); board.append(row); });
          root.append(el('div', 'sub2', 'Ranking geral'), board);
        }
        if (d.view.phase === 'lobby' || d.view.phase === 'watch') { const pf = el('button', 'link', '📷 Trocar minha foto'); pf.onclick = pickFace; root.append(pf); }
        if (d.view.phase === 'lobby' && d.standing) {
          const f = el('button', 'link', 'Esquecer meus dados nesta TV');
          f.onclick = forgetMe;
          root.append(f);
        }
        if (d.view.phase === 'lobby' || d.view.phase === 'watch') {
          const people = el('div', 'people'); d.players.forEach((p) => people.append(who(p))); root.append(people);
          const night = Object.entries(d.night || {}).sort((a, b) => b[1] - a[1]);
          if (night.length) {
            root.append(el('div', 'sub2', 'Placar da noite'));
            const board = el('div', 'people');
            night.forEach(([id, n]) => { const c = who(playerOf(d, id)); c.append(el('span', null, '★ ' + n)); board.append(c); });
            root.append(board);
          }
        }
        if (w.react) {
          const row = el('div', 'stamps');
          STAMPS.forEach(([k, label]) => { const b = el('button', null, label); b.onclick = () => api('/api/react', { kind: k }).then((r) => r.text()).catch(() => {}); row.append(b); });
          root.append(row);
        }
        break;
      }
      case 'secret': {
        const box = el('div', 'envelope');
        const val = el('div', 'v', ''); const hint = el('div', 'h', w.label + ' · segura pra ver');
        box.append(val, hint);
        const seal = el('div', 'seal', '✉'); box.prepend(seal);
        const set = (on) => { val.textContent = on ? w.value : ''; box.classList.toggle('open', on); hint.textContent = on ? w.label : w.label + ' · segura pra ver'; if (on && navigator.vibrate) navigator.vibrate(12); };
        box.onpointerdown = (e) => { e.preventDefault(); set(true); };
        ['onpointerup', 'onpointercancel', 'onpointerleave'].forEach((h) => { box[h] = () => set(false); });
        box.oncontextmenu = (e) => e.preventDefault();
        root.append(box);
        break;
      }
      case 'pick': {
        root.append(el('div', 'prompt', w.prompt));
        const grid = el('div', 'grid');
        w.options.forEach((id) => {
          const p = playerOf(d, id);
          const b = el('button', id === w.chosen ? 'on' : '');
          b.append(dot(p), el('span', 'name', p.name));
          b.onclick = () => { grid.querySelectorAll('button').forEach((x) => x.classList.remove('on')); b.classList.add('on'); act('pick', { id }); };
          grid.append(b);
        });
        root.append(grid);
        if (w.chosen) root.append(el('div', 'sub2', 'Voto dado ✓ Dá pra mudar até o tempo acabar'));
        break;
      }
      case 'choose': {
        root.append(el('div', 'prompt', w.prompt));
        const stack = el('div', 'stack');
        w.options.forEach((o) => {
          const b = el('button', o.key === w.chosen ? 'on' : '', o.label);
          b.onclick = () => { stack.querySelectorAll('button').forEach((x) => x.classList.remove('on')); b.classList.add('on'); act('choose', { key: o.key }); };
          stack.append(b);
        });
        root.append(stack);
        break;
      }
      case 'text': {
        root.append(el('div', 'prompt', w.prompt));
        const input = el('input'); input.maxLength = 40; input.placeholder = w.hint || ''; input.autocomplete = 'off';
        const b = el('button', 'primary', w.done ? 'Enviado ✓ (enviar de novo)' : 'Enviar');
        b.onclick = () => { if (input.value.trim()) act('text', { value: input.value.trim() }); };
        root.append(input, b);
        break;
      }
      case 'scale': {
        root.append(el('div', 'prompt', w.prompt));
        const row = el('div', 'scale');
        for (let i = 0; i <= 10; i++) {
          const b = el('button', i === w.chosen ? 'on' : '', String(i));
          b.onclick = () => { row.querySelectorAll('button').forEach((x) => x.classList.remove('on')); b.classList.add('on'); act('scale', { value: i }); };
          row.append(b);
        }
        const poles = el('div', 'poles'); poles.append(el('span', null, w.low), el('span', null, w.high));
        root.append(row, poles);
        break;
      }
      case 'grid': {
        if (w.caption) root.append(el('div', 'sub2', w.caption));
        const g = el('div', 'cells'); g.style.gridTemplateColumns = 'repeat(' + w.cols + ', 1fr)';
        w.cells.forEach((c) => g.append(el('div', 'cell ' + (c.tone || 'plain'), c.label)));
        root.append(g);
        break;
      }
      case 'manual': {
        const tabs = el('div', 'tabs'); const page = el('div', 'page');
        const show = (i) => { page.replaceChildren(el('div', 'prompt', w.pages[i].title), ...w.pages[i].lines.map((l) => l.startsWith('¦') ? el('div', 'maze', l.slice(1)) : el('p', 'line', l))); [...tabs.children].forEach((t, k) => t.classList.toggle('on', k === i)); };
        w.pages.forEach((p, i) => { const t = el('button', '', p.title); t.onclick = () => show(i); tabs.append(t); });
        root.append(tabs, page); show(0);
        break;
      }
      case 'ready': {
        root.append(el('div', 'prompt', w.text));
        const b = el('button', 'primary', w.done ? 'Tudo certo ✓' : 'Tudo certo');
        b.onclick = () => act('ready', {});
        root.append(b);
        break;
      }
      default: root.append(el('div', 'big', 'Olha pra TV'));
    }
    if (d.view.phase === 'ceremony') {
      const b = el('button', 'primary', 'Baixar o pôster da noite');
      b.onclick = () => poster(d);
      root.append(b);
    }
  }

  // The night's poster, drawn on this phone from the awards, the faces the TV will share with seated phones, and levels
  async function faceOf(id) {
    try {
      const r = await fetch('/api/face/' + encodeURIComponent(id), { headers: { Authorization: 'Bearer ' + token }, cache: 'no-store' });
      if (!r.ok) return null;
      const b = await r.blob();
      return b.size ? await createImageBitmap(b) : null;
    } catch (e) { return null; }
  }
  async function poster(d) {
    const W = 1080, H = 1350, c = document.createElement('canvas'); c.width = W; c.height = H;
    const g = c.getContext('2d');
    const shown = d.awards.slice(0, 8);
    const faces = Object.fromEntries(await Promise.all([...new Set(shown.map((a) => a.who))].map(async (id) => [id, await faceOf(id)])));
    const bg = g.createLinearGradient(0, 0, 0, H); bg.addColorStop(0, '#3a2c10'); bg.addColorStop(.5, '#14110a'); bg.addColorStop(1, '#0a0a0b');
    g.fillStyle = bg; g.fillRect(0, 0, W, H);
    const glow = g.createRadialGradient(W * .85, 0, 0, W * .85, 0, 900); glow.addColorStop(0, 'rgba(217,180,90,.45)'); glow.addColorStop(1, 'rgba(217,180,90,0)'); g.fillStyle = glow; g.fillRect(0, 0, W, H);
    g.fillStyle = '#fff'; g.textAlign = 'center'; g.font = '800 88px -apple-system, Helvetica, Arial'; g.fillText('Noite de jogos', W / 2, 150);
    g.fillStyle = '#d9b45a'; g.font = '700 34px -apple-system, Helvetica, Arial'; g.fillText(new Date().toLocaleDateString('pt-BR', { day: 'numeric', month: 'long', year: 'numeric' }).toUpperCase(), W / 2, 206);
    let y = 300;
    shown.forEach((a) => {
      const p = playerOf(d, a.who), cx = 130, r = 46;
      g.fillStyle = 'rgba(255,255,255,.06)'; g.beginPath(); g.roundRect(60, y - 70, W - 120, 118, 28); g.fill();
      g.save(); g.beginPath(); g.arc(cx, y - 11, r, 0, 7); g.closePath(); g.clip();
      if (faces[a.who]) { const f = faces[a.who], s = Math.min(f.width, f.height); g.drawImage(f, (f.width - s) / 2, (f.height - s) / 2, s, s, cx - r, y - 11 - r, r * 2, r * 2); }
      else { g.fillStyle = COLORS[p.color % COLORS.length]; g.fillRect(cx - r, y - 11 - r, r * 2, r * 2); g.fillStyle = '#000'; g.font = '700 46px -apple-system, Helvetica, Arial'; g.textAlign = 'center'; g.fillText(SHAPES[p.shape % SHAPES.length], cx, y + 4); }
      g.restore();
      g.strokeStyle = '#fff'; g.lineWidth = 4; g.beginPath(); g.arc(cx, y - 11, r, 0, 7); g.stroke();
      g.textAlign = 'left'; g.fillStyle = '#d9b45a'; g.font = '800 44px -apple-system, Helvetica, Arial'; g.fillText(a.title, 205, y - 14);
      g.fillStyle = '#fff'; g.font = '500 31px -apple-system, Helvetica, Arial'; g.fillText((p.name + ' · ' + a.blurb).length > 50 ? (p.name + ' · ' + a.blurb).slice(0, 49) + '…' : p.name + ' · ' + a.blurb, 205, y + 28);
      y += 128;
    });
    g.textAlign = 'center'; g.fillStyle = 'rgba(255,255,255,.45)'; g.font = '500 30px -apple-system, Helvetica, Arial'; g.fillText('Início · Social', W / 2, H - 50);
    c.toBlob((blob) => {
      const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = 'noite-de-jogos.png'; a.click();
    }, 'image/png');
  }

  async function act(type, data) {
    const phase = current && current.view.phase;
    try {
      const r = await api('/api/act', { aid: Math.random().toString(36).slice(2) + Date.now().toString(36), phase, type, data });
      if (r.status === 401) return;
      const d = await r.json();
      if (!d.ok && d.why) toast(d.why);
    } catch (e) { toast('Sem conexão. Tenta de novo.'); }
  }

  // --- Countdown, drawn locally from the TV's clock -----------------------------------------------
  let total = 0, endsFor = null;
  function tick() {
    if (!current || !current.view.endsAt) return;
    const end = current.view.endsAt;
    if (endsFor !== end) { endsFor = end; total = Math.max(1, end - (Date.now() + offset)); }
    const left = Math.max(0, end - (Date.now() + offset));
    const sec = Math.ceil(left / 1000);
    $('clock').textContent = sec >= 100 ? Math.floor(sec / 60) + ':' + String(sec % 60).padStart(2, '0') : sec + ' s';
    $('bar').firstElementChild.style.transform = 'scaleX(' + (left / total) + ')';
  }
  setInterval(tick, 250);

  // --- Boot --------------------------------------------------------------------------------------
  if (!key) closed('Falta o código da sala');
  else if (token) start();
  else show('join');
})();
