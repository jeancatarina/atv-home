// Premium layer of the phone page in a real Chrome: level card, profile sheet, a moment with confetti,
// the sealed envelope, and the night's poster. Needs the dev room (BOT_MS=1200 tools/social-devroom.sh).
//   PLAYWRIGHT=/path/to/node_modules/playwright node tools/social-phone-premium.js
const { chromium, devices } = require(process.env.PLAYWRIGHT || 'playwright');
const fs = require('fs');
const key = fs.readFileSync('/tmp/e2e/out.log', 'utf8').match(/Local : (\S+)/)[1];
const tv = (cmd) => fs.appendFileSync('/tmp/e2e/in.fifo', cmd + '\n');
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
let fails = 0;
const check = (name, ok, extra = '') => { if (!ok) fails++; console.log((ok ? 'PASS ' : 'FAIL ') + name + (extra ? '  — ' + extra : '')); };

(async () => {
  const browser = await chromium.launch({ channel: 'chrome' });
  const ctx = await browser.newContext({ ...devices['iPhone 13'], deviceScaleFactor: 2, acceptDownloads: true });
  // The selfie is required to join
  const first = await ctx.newPage();
  await first.goto(key); await first.fill('#name', 'Sem foto'); await first.click('#go');
  check('joining without a selfie is refused with a hint', /selfie/i.test(await first.textContent('#joinErr')));
  await first.close();
  await ctx.addInitScript(() => { try { localStorage.setItem('social.hasFace', '1'); } catch (e) { /* about:blank has no storage */ } });
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(String(e)));
  page.on('console', m => { if (m.type() === 'error' && !/ERR_INTERNET_DISCONNECTED|status of 401/.test(m.text())) errors.push(m.text()); });
  await page.goto(key);
  await page.fill('#name', 'Ana'); await page.click('#go');
  await page.waitForSelector('#game:not([hidden])');
  await sleep(800);

  // level card and header emblem (the ledger knows this phone once it has played; a new one has no profile yet)
  await page.waitForSelector('.mecard', { timeout: 5000 }).catch(() => {});
  const hasCard = !!(await page.$('.mecard'));
  check('lobby shows the level card', hasCard);
  check('the lobby offers to change the photo', !!(await page.$('button.link:has-text("Trocar minha foto")')));
  if (hasCard) {
    check('header shows the level emblem', !(await page.$eval('#meBtn', e => e.hidden)) && /\d+/.test(await page.textContent('#meBtn .hex')));
    await page.screenshot({ path: '/tmp/e2e/p1-lobby.png' });
    await page.click('#meBtn'); await page.waitForSelector('#sheet:not([hidden])');
    check('profile sheet lists challenges and badges', (await page.$$('.chal')).length === 3 && (await page.$$('.bdg')).length > 10);
    await page.screenshot({ path: '/tmp/e2e/p2-profile.png' });
    await page.mouse.click(180, 30); await page.waitForSelector('#sheet', { state: 'hidden' });
    check('tapping outside closes the sheet', true);
  }

  // play one game so the phone earns XP and (as a first game) the first-night moments
  tv('start mostlikely');
  await page.waitForSelector('.grid button', { timeout: 10000 });
  await page.click('.grid button:nth-child(2)');
  // A first game may not be enough for a level or a badge: play again until something is earned
  for (let i = 0; i < 90; i++) { await sleep(1200); if (await page.$('.moment')) break; tv(/Acabou a partida/.test(await page.textContent('#body')) ? 'again' : 'continue'); }
  const moment = await page.$('.moment');
  if (!moment) console.log('   no moment; phone shows:', JSON.stringify((await page.textContent('#body')).slice(0, 160)), '| title:', await page.textContent('#title'));
  check('a moment sheet appears after the game', !!moment);
  if (moment) {
    check('the moment shows a title and a close button', (await page.textContent('.moment h2')).length > 0 && !!(await page.$('.moment button.primary')));
    check('confetti canvas is running', !(await page.$eval('#confetti', e => e.hidden)));
    await page.screenshot({ path: '/tmp/e2e/p3-moment.png' });
    // several moments can be queued (level, badges): each shows once, then none is left
    let shownMoments = 0;
    for (let i = 0; i < 8 && await page.$('.moment button.primary'); i++) { shownMoments++; await page.click('.moment button.primary'); await sleep(900); }
    await page.waitForSelector('#sheet', { state: 'hidden' });
    check('queued moments show one after another and then stop', shownMoments >= 1 && !(await page.$('.moment')), shownMoments + ' moments');
    await sleep(2200);
    check('confetti stops with the last sheet', await page.$eval('#confetti', e => e.hidden));
  }

  // the envelope has a seal that opens on hold
  tv('lobby'); tv('bots 3'); await sleep(500); tv('start undercover');
  const env = await page.waitForSelector('.envelope', { timeout: 15000 }).catch(() => null);
  if (env) {
    check('envelope starts sealed and empty', (await page.textContent('.envelope .v')) === '' && !!(await page.$('.envelope .seal')));
    await env.dispatchEvent('pointerdown');
    check('holding opens the seal and shows the secret', (await page.$eval('.envelope', e => e.classList.contains('open'))) && (await page.textContent('.envelope .v')).length > 0);
    await page.screenshot({ path: '/tmp/e2e/p4-envelope.png' });
    await env.dispatchEvent('pointerup');
    check('letting go seals it again', (await page.textContent('.envelope .v')) === '');
  } else check('envelope shows up in a secret-role game', false);

  // poster after the night is closed
  tv('lobby'); tv('night');
  await page.waitForSelector('button.primary:has-text("pôster")', { timeout: 15000 });
  for (let i = 0; i < 8 && await page.$('.moment button.primary'); i++) { await page.click('.moment button.primary'); await sleep(900); }
  const [dl] = await Promise.all([page.waitForEvent('download', { timeout: 15000 }), page.click('button.primary:has-text("pôster")')]);
  const path = '/tmp/e2e/poster.png'; await dl.saveAs(path);
  check('the poster downloads as a PNG', fs.statSync(path).size > 20000, fs.statSync(path).size + ' bytes');

  check('no page errors', errors.length === 0, errors.join(' | '));
  await browser.close();
  console.log(fails ? 'FAILED ' + fails : 'ALL PASSED'); process.exit(fails ? 1 : 0);
})().catch(e => { console.log('CRASH', e); process.exit(2); });
