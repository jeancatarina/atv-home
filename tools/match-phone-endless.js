// The Match phone page in a real Chrome against a fake TV that deals more cards when asked:
// the line must never end ("Buscando mais opções…" then new cards appear on their own).
//   PLAYWRIGHT=/path/to/node_modules/playwright node tools/match-phone-endless.js
const { chromium, devices } = require(process.env.PLAYWRIGHT || 'playwright');
const http = require('http'), fs = require('fs');
const page = fs.readFileSync(__dirname + '/../app/src/main/assets/match.html', 'utf8');
const card = (n) => ({ key: 'movie:' + n, title: 'Filme ' + n, year: 2000 + n, imdb: '7,5', where: 'Netflix', poster: null, overview: '' });
let cards = [1, 2, 3].map(card), votes = [], moreAt = 0;
const srv = http.createServer((req, res) => {
  const u = new URL(req.url, 'http://x'); const send = (o, t = 'application/json') => { res.setHeader('Content-Type', t); res.end(typeof o === 'string' ? o : JSON.stringify(o)); };
  if (u.pathname === '/') return send(page, 'text/html');
  if (u.pathname === '/join') { req.resume(); return send({ id: 'p1' }); }
  if (u.pathname === '/deck') return send({ cards: cards.slice(+(u.searchParams.get('from') || 0)) });
  if (u.pathname === '/vote') { let b = ''; req.on('data', (d) => b += d); req.on('end', () => { votes.push(JSON.parse(b)); send({ people: 2, match: null }); }); return; }
  if (u.pathname === '/state') return send({ people: 2, match: null, unanimous: false, likes: 0 });
  res.statusCode = 404; res.end();
}).listen(0, '127.0.0.1', async () => {
  const base = 'http://127.0.0.1:' + srv.address().port;
  let fails = 0; const check = (n, ok, x = '') => { if (!ok) fails++; console.log((ok ? 'PASS ' : 'FAIL ') + n + (x ? '  — ' + x : '')); };
  const b = await chromium.launch({ channel: 'chrome' }); const p = await (await b.newContext({ ...devices['iPhone 13'] })).newPage();
  const errors = []; p.on('pageerror', (e) => errors.push(String(e)));
  await p.goto(base); await p.fill('#name', 'Ana'); await p.click('#go'); await p.waitForSelector('.card');
  for (let k = 0; k < 3; k++) { await p.click('#no'); await p.waitForTimeout(350); }
  check('after the last card the phone says it is fetching more', /Buscando mais/.test(await p.textContent('#deck')));
  cards.push(...[4, 5, 6, 7].map(card)); // the TV dealt another batch
  await p.waitForSelector('.card .t', { timeout: 6000 });
  check('new cards show up by themselves', (await p.textContent('#deck .card:last-child .t')) === 'Filme 4');
  await p.click('#yes'); await p.waitForTimeout(400);
  check('votes keep flowing with the right keys', votes.length === 4 && votes[3].key === 'movie:4' && votes[3].like === true, votes.map((v) => v.key + ':' + v.like).join(' '));
  // Keeping ahead: with fewer than 8 cards left the page asks again before running out
  cards.push(...[8, 9, 10].map(card)); await p.waitForTimeout(2200);
  check('it prefetches while there are still cards', await p.evaluate(() => cards.length) === 10, String(await p.evaluate(() => cards.length)));
  check('no page errors', errors.length === 0, errors.join(' | '));
  await b.close(); srv.close(); console.log(fails ? 'FAILED ' + fails : 'ALL PASSED'); process.exit(fails ? 1 : 0);
});
