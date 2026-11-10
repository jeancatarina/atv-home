// End-to-end test of the phone page in a real Chrome (iPhone and Pixel emulation) against a real room.
//
//   BOT_MS=5000 tools/social-devroom.sh            # starts the dev room with 2 bots on port 18766
//   PLAYWRIGHT=/path/to/node_modules/playwright node tools/social-phone-e2e.js
//
// It plays the TV's part through the dev room's stdin (/tmp/e2e/in.fifo) and reads the link from its log.
const { chromium, devices } = require(process.env.PLAYWRIGHT || 'playwright');
const fs = require('fs');
const key = fs.readFileSync('/tmp/e2e/out.log', 'utf8').match(/Local : (\S+)/)[1];
const tv = (cmd) => fs.appendFileSync('/tmp/e2e/in.fifo', cmd + '\n');
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const results = [];
const check = (name, ok, extra='') => { results.push([ok, name, extra]); console.log((ok ? 'PASS ' : 'FAIL ') + name + (extra ? '  — ' + extra : '')); };

(async () => {
  const browser = await chromium.launch({ channel: 'chrome' });
  const ctx = await browser.newContext({ ...devices['iPhone 13'], deviceScaleFactor: 1 });
  await ctx.addInitScript(() => { try { localStorage.setItem('social.hasFace', '1'); } catch (e) { /* about:blank has no storage */ } }); // the selfie is required to join; a phone that sent one may skip it
  const page = await ctx.newPage();
  const errors = [];
  page.on('response', r => { if (r.status() !== 200) console.log('   HTTP', r.status(), r.url().replace(/.*:18766/, '')); });
  page.on('requestfailed', r => console.log('   REQFAIL', r.url().replace(/.*:18766/, ''), r.failure().errorText));
  page.on('pageerror', e => errors.push(String(e)));
  page.on('console', m => { if (m.type() === 'error' && !/ERR_INTERNET_DISCONNECTED|status of 401/.test(m.text())) { errors.push(m.text()); console.log('   CONSOLE', m.text()); } });

  await page.goto(key);
  await page.waitForSelector('#join:not([hidden])');
  check('join form shows with the key in the link', true);
  check('the key is removed from the address bar', !page.url().includes('#k='), page.url());
  await page.fill('#name', 'Ana');
  await page.click('#go');
  await page.waitForSelector('#game:not([hidden])');
  await page.waitForSelector('.people .chip');
  const chips = await page.$$eval('.people .chip .name', els => els.map(e => e.textContent));
  check('lobby lists me and the two bots', chips.length === 3 && chips.includes('Ana'), chips.join(','));
  check('title is "Na sala"', (await page.textContent('#title')) === 'Na sala');
  await page.screenshot({ path: '/tmp/e2e/1-lobby.png' });

  // reactions in the lobby
  await page.click('.stamps button:first-child');
  check('a reaction tap does not break the page', errors.length === 0, errors.join(' | '));

  // reload keeps the seat (token in localStorage)
  await page.reload();
  await page.waitForSelector('#game:not([hidden])');
  await page.waitForSelector('.people .chip');
  check('reload returns to the same seat without joining again', (await page.$$('.people .chip')).length === 3);

  tv('start mostlikely');
  await page.waitForSelector('.grid button');
  const options = await page.$$eval('.grid button .name', els => els.map(e => e.textContent));
  check('vote screen shows the 3 people', options.length === 3, options.join(','));
  check('title shows the question counter', /Pergunta 1 de 10/.test(await page.textContent('#title')), await page.textContent('#title'));
  check('countdown is running', /\d+ s/.test(await page.textContent('#clock')), await page.textContent('#clock'));
  await page.screenshot({ path: '/tmp/e2e/2-vote.png' });

  await page.click('.grid button:nth-child(2)');
  await page.waitForSelector('.sub2:has-text("Voto dado")');
  check('vote is locked with feedback', true);
  await page.click('.grid button:nth-child(3)');
  await sleep(300);
  const on = await page.$$eval('.grid button.on .name', els => els.map(e => e.textContent));
  check('changing the vote moves the highlight', on.length === 1 && on[0] === options[2], on.join(','));

  // the bots vote every 1.5 s: the game moves to the reveal and the phone says look at the TV
  await page.waitForSelector('.big:has-text("Olha pra TV")', { timeout: 15000 });
  check('after everyone voted the phone tells me to look at the TV', true);
  await page.screenshot({ path: '/tmp/e2e/3-reveal.png' });
  check('reactions appear during the reveal', (await page.$$('.stamps button')).length === 4);

  // pause overlay
  tv('pause');
  await page.waitForSelector('#paused:not([hidden])', { timeout: 5000 });
  check('pause shows the overlay on the phone', true);
  await page.screenshot({ path: '/tmp/e2e/4-paused.png' });
  tv('resume');
  await page.waitForSelector('#paused', { state: 'hidden', timeout: 5000 });
  check('resume hides it', true);

  // losing the network shows the banner and recovers
  await ctx.setOffline(true);
  tv('continue');
  await page.waitForSelector('#banner:not([hidden])', { timeout: 40000 });
  check('offline shows the reconnecting banner', true);
  await page.screenshot({ path: '/tmp/e2e/5-offline.png' });
  await ctx.setOffline(false);
  await page.waitForSelector('#banner', { state: 'hidden', timeout: 15000 });
  check('back online hides the banner and the view catches up', true);
  await page.waitForSelector('.grid button', { timeout: 15000 });
  check('the phone shows the next question after reconnecting', /Pergunta 2/.test(await page.textContent('#title')), await page.textContent('#title'));

  // a second phone as a spectator while the game runs
  const ctx2 = await browser.newContext({ ...devices['Pixel 7'] });
  await ctx2.addInitScript(() => { try { localStorage.setItem('social.hasFace', '1'); } catch (e) { /* about:blank has no storage */ } }); // the selfie is required to join; a phone that sent one may skip it
  const p2 = await ctx2.newPage();
  await p2.goto(key);
  await p2.fill('#name', 'Caio');
  await p2.click('#go');
  await p2.waitForSelector('.big:has-text("Você tá só assistindo")', { timeout: 8000 });
  check('someone arriving during a game watches', true);
  await p2.screenshot({ path: '/tmp/e2e/6-spectator.png' });

  // new room kicks everyone
  tv('newroom');
  await page.waitForSelector('#join:not([hidden]), #closed:not([hidden])', { timeout: 40000 });
  check('a new room sends the old phones back out', true);

  check('no script errors on the page', errors.length === 0, errors.join(' | '));
  await browser.close();
  const failed = results.filter(r => !r[0]).length;
  console.log(failed ? `\n${failed} FAILED` : '\nALL PASSED');
  tv('quit');
  process.exit(failed ? 1 : 0);
})().catch(e => { console.error('CRASH', e); tv('quit'); process.exit(2); });
