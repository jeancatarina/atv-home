// Plays every game through the phone page in a real Chrome, tapping whatever the screen offers, next
// to dev-room bots. Catches widgets that fail to draw, console errors and games that stop moving.
//
//   BOT_MS=700 tools/social-devroom.sh
//   PLAYWRIGHT=/path/to/node_modules/playwright node tools/social-phone-fuzz.js [gameId ...]
const { chromium, devices } = require(process.env.PLAYWRIGHT || 'playwright');
const fs = require('fs');
const key = fs.readFileSync('/tmp/e2e/out.log', 'utf8').match(/Local : (\S+)/)[1];
const tv = (c) => fs.appendFileSync('/tmp/e2e/in.fifo', c + '\n');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const GAMES = { // id → bots besides the phone (so the room fits the game)
  mostlikely: 3, undercover: 5, frequency: 3, couple: 1, twotruths: 3, spyfall: 4, insider: 4,
  psychologist: 4, blackstory: 1, missions: 3, mafia: 5, tribunal: 4, film5: 2, bomb: 2,
};
const only = process.argv.slice(2);

/** A valid PNG of a [w]x[h] gradient, built here so the test needs no image file. */
function makePng(w, h) {
  const zlib = require('zlib');
  const crcTable = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
  const crc = (buf) => { let c = 0xffffffff; for (const b of buf) c = crcTable[(c ^ b) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
  const chunk = (type, data) => { const len = Buffer.alloc(4); len.writeUInt32BE(data.length); const td = Buffer.concat([Buffer.from(type), data]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([len, td, c]); };
  const raw = Buffer.alloc((w * 3 + 1) * h);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const o = y * (w * 3 + 1) + 1 + x * 3; raw[o] = x * 16; raw[o + 1] = y * 16; raw[o + 2] = 160; }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 2;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}

(async () => {
  const browser = await chromium.launch({ channel: 'chrome' });
  const ctx = await browser.newContext({ ...devices['iPhone 13'] });
  await ctx.addInitScript(() => { try { localStorage.setItem('social.hasFace', '1'); } catch (e) { /* about:blank has no storage */ } }); // the selfie is required to join; a phone that sent one may skip it
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  page.on('console', (m) => { if (m.type() === 'error' && !/status of 40[13]|ERR_/.test(m.text())) errors.push(m.text()); });
  await page.goto(key);
  // A selfie from the file picker (a tiny PNG here), shrunk and sent after joining
  const PNG = makePng(16, 16);
  await page.setInputFiles('#face', { name: 'eu.png', mimeType: 'image/png', buffer: PNG });
  await page.waitForSelector('#facePreview:not([hidden])');
  await page.fill('#name', 'Ana');
  const faceSent = page.waitForResponse((r) => r.url().includes('/api/face'), { timeout: 8000 });
  await page.click('#go');
  const faceResp = await faceSent;
  const faceOk = faceResp.status() === 200 && (await faceResp.json()).ok === true;
  console.log((faceOk ? 'PASS ' : 'FAIL ') + 'face'.padEnd(13) + ' selfie chosen, shrunk and accepted by the room');
  await page.waitForSelector('#game:not([hidden])');
  let failed = faceOk ? 0 : 1;

  for (const [id, bots] of Object.entries(GAMES)) {
    if (only.length && !only.includes(id)) continue;
    errors.length = 0;
    tv('lobby'); await sleep(400); tv('bots ' + bots); await sleep(600); tv('start ' + id);
    const seen = new Set(); const kinds = new Set();
    let t0 = Date.now(); let retries = 0;
    while (Date.now() - t0 < 22000) {
      // Some roles are passive on the phone (the Guardian, the psychic are random): deal again until ours is active
      if (Date.now() - t0 > 6000 && kinds.size === 0 && retries < 6) { tv('again'); retries++; t0 = Date.now() - 2000; }
      await sleep(450);
      // Frequência waits for the TV remote (turn the dial, OK): stand in for it
      if (id === 'frequency' && Math.random() < 0.12) tv('continue');
      const phase = await page.evaluate(() => ({ t: document.getElementById('title').textContent, b: document.getElementById('body').innerText.slice(0, 60) }));
      seen.add(phase.t + '|' + phase.b);
      const tapped = await page.evaluate(() => {
        const pools = ['.tabs button', '.btnrow button', '.grid button', '.stack button', '.scale button', 'input', 'button.primary', '.envelope'];
        const found = [];
        for (const sel of pools) document.querySelectorAll('#body ' + sel).forEach((e) => found.push([sel, e]));
        if (!found.length) return null;
        const [sel, el] = found[Math.floor(Math.random() * found.length)];
        if (el.tagName === 'INPUT') { el.value = 'resposta'; el.dispatchEvent(new Event('input')); const b = el.parentElement.querySelector('button'); if (b) b.click(); }
        else if (el.classList.contains('envelope')) { el.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true })); el.dispatchEvent(new PointerEvent('pointerup', { bubbles: true })); }
        else el.click();
        return sel;
      });
      if (tapped) kinds.add(tapped);
    }
    const ok = errors.length === 0 && seen.size >= 2 && (kinds.size > 0 || seen.size >= 3);
    if (!ok) failed++;
    console.log((ok ? 'PASS ' : 'FAIL ') + id.padEnd(13) + ` screens=${seen.size} taps=[${[...kinds].join(' ')}]` + (errors.length ? ' ERR: ' + errors.slice(0, 2).join(' | ') : ''));
    if (id === 'frequency' || id === 'missions' || id === 'bomb') await page.screenshot({ path: `/tmp/e2e/fuzz-${id}.png` });
  }
  if (!only.length || only.includes('night')) {
    // A whole game, then the night's ceremony and the poster download
    errors.length = 0;
    tv('newnight'); tv('lobby'); await sleep(300); tv('bots 3'); await sleep(500); tv('start mostlikely');
    for (let i = 0; i < 30; i++) { await sleep(300); tv('continue'); }
    tv('night');
    let ok = false, detail = '';
    try {
      await page.waitForSelector('#body .big:has-text("Seu troféu"), #body .big:has-text("Valeu")', { timeout: 8000 });
      // Moments earned in that game (level, badges) cover the page until they are dismissed
      for (let i = 0; i < 12; i++) { await sleep(700); if (await page.$eval('#sheet', x => x.hidden)) break; await page.click('#sheet button.primary'); }
      const [download] = await Promise.all([page.waitForEvent('download', { timeout: 8000 }), page.click('#body button.primary:has-text("pôster")')]);
      const path = await download.path(); const size = fs.statSync(path).size;
      ok = size > 15000 && errors.length === 0; detail = `poster ${size} bytes, file ${download.suggestedFilename()}`;
      fs.copyFileSync(path, '/tmp/e2e/poster.png');
    } catch (e) { detail = String(e).split('\n')[0]; await page.screenshot({ path: '/tmp/e2e/fuzz-night-fail.png' }).catch(() => {}); detail += ' | sheet hidden=' + await page.$eval('#sheet', x => x.hidden).catch(() => '?') + ' errors=' + errors.slice(0, 2).join(' / '); }
    if (!ok) failed++;
    console.log((ok ? 'PASS ' : 'FAIL ') + 'night'.padEnd(13) + ' ' + detail + (errors.length ? ' ERR: ' + errors[0] : ''));
    // Another day: a brand-new room, the same phone (its page storage is kept). It must be recognised and the score must go on
    if (ok) {
      errors.length = 0;
      tv('newroom'); await sleep(800);
      const link = fs.readFileSync('/tmp/e2e/out.log', 'utf8').split('\n').filter((l) => l.startsWith('Phones:')).pop().replace(/http:\/\/[^/]+/, 'http://127.0.0.1:18766').replace('Phones: ', '');
      let back = false, standing = '', gone = false;
      try {
        await page.goto('about:blank'); await page.goto(link);
        await page.waitForSelector('#join:not([hidden])', { timeout: 8000 });
        const hello = await page.textContent('#joinSub');
        await page.click('#go');
        await page.waitForSelector('#body .sub2:has-text("No ranking geral")', { timeout: 8000 });
        standing = await page.textContent('#body .sub2:has-text("No ranking geral")');
        back = /Que bom te ver de novo/.test(hello) && /fichas/.test(standing) && (await page.$$('#body .board div')).length >= 1;
        // And the right to be forgotten
        page.once('dialog', (d) => d.accept());
        await page.click('#body button.link:has-text("Esquecer")');
        await page.waitForSelector('#join:not([hidden])', { timeout: 8000 });
        gone = !/Que bom ver/.test(await page.textContent('#joinSub'));
      } catch (e) { standing = String(e).split('\n')[0]; }
      const good = back && gone && errors.length === 0;
      if (!good) failed++;
      console.log((good ? 'PASS ' : 'FAIL ') + 'returning'.padEnd(13) + ` ${standing}${gone ? ' · forgotten on request' : ''}` + (errors.length ? ' ERR: ' + errors[0] : ''));
    }
  }
  await browser.close();
  console.log(failed ? `\n${failed} FAILED` : '\nALL PASSED');
  tv('quit'); process.exit(failed ? 1 : 0);
})().catch((e) => { console.error('CRASH', e); tv('quit'); process.exit(2); });
