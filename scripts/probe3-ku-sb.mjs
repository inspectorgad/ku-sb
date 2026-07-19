// Third-round probe: are KU Softball FALL (pre-season/exhibition) games
// published anywhere we can scrape?
//
//  1. NCAA API: scan Sep-Nov 2025 (the fall before the 2026 season) for any
//     Kansas softball games — exhibitions usually never reach the NCAA
//     scoreboard, but verify rather than assume.
//  2. kuathletics.com: capture the current schedule page (does it show a
//     fall section or 2027 games yet?), the 2027 season schedule URL, a
//     possible fall stats page, and the softball news feed (fall-ball
//     recaps from Oct/Nov 2025 would prove results get published even if
//     structured box scores don't).
//
// Evidence lands in probe3/.
import { chromium } from 'playwright';
import fs from 'fs';

const API = 'https://ncaa-api.henrygd.me';

fs.rmSync('probe3', { recursive: true, force: true });
fs.mkdirSync('probe3', { recursive: true });

const summary = [];
function note(line) {
  summary.push(line);
  console.log(line);
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function getJson(url) {
  const resp = await fetch(url, { headers: { accept: 'application/json' } });
  await sleep(350);
  if (!resp.ok) throw new Error(`${resp.status} for ${url}`);
  return resp.json();
}

// --- 1. NCAA scoreboard: fall 2025 window -----------------------------------
note('Scanning NCAA softball/d1 scoreboard 2025-09-01..2025-11-30 for kansas');
let found = 0;
let daysWithGames = 0;
for (
  let d = new Date('2025-09-01T00:00:00Z');
  d <= new Date('2025-11-30T00:00:00Z');
  d = new Date(d.getTime() + 86_400_000)
) {
  const date = d.toISOString().slice(0, 10);
  const [y, m, day] = date.split('-');
  let data;
  try {
    data = await getJson(`${API}/scoreboard/softball/d1/${y}/${m}/${day}`);
  } catch (e) {
    continue; // 404 = no games that day
  }
  const games = (data.games || []).map((w) => w.game || w);
  if (games.length) daysWithGames++;
  for (const g of games) {
    if ([g.home, g.away].some((s) => s?.names?.seo === 'kansas')) {
      found++;
      note(`  KU fall game on NCAA scoreboard ${date}: ${g.away?.names?.short} at ${g.home?.names?.short} (${g.gameState}, id ${g.gameID})`);
    }
  }
}
note(`NCAA fall window: ${found} Kansas games found; ${daysWithGames} days had any D1 softball games at all`);

// --- 2. kuathletics.com ------------------------------------------------------
const browser = await chromium.launch();
const context = await browser.newContext({
  userAgent:
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
  viewport: { width: 1400, height: 2400 },
});

async function capture(url, name) {
  const page = await context.newPage();
  try {
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60_000 });
    await page.waitForTimeout(8_000);
    for (let i = 0; i < 12; i++) {
      await page.evaluate(() => window.scrollBy(0, 1500));
      await page.waitForTimeout(400);
    }
    const text = await page.evaluate(() => (document.body ? document.body.innerText : ''));
    fs.writeFileSync(`probe3/${name}.txt`, text);
    const links = await page.evaluate(() =>
      Array.from(document.querySelectorAll('a[href]')).map((a) => ({
        text: (a.innerText || '').trim().slice(0, 80),
        href: a.href,
      }))
    );
    fs.writeFileSync(`probe3/${name}-links.json`, JSON.stringify(links, null, 1));
    note(`${name}: ${text.length} chars, ${links.length} links`);
  } catch (e) {
    note(`${name}: FAILED ${e.message}`);
  } finally {
    await page.close();
  }
}

// Current schedule page — a posted fall/2027 slate would appear here first.
await capture('https://kuathletics.com/sports/softball/schedule', 'schedule-current');
// Explicit next-season schedule URLs (Sidearm uses /season/<year>).
await capture('https://kuathletics.com/sports/softball/schedule/season/2027', 'schedule-2027');
// Did LAST fall's games ever get a schedule entry? The 2026 season page
// starts in February, so also try the raw fall label some Sidearm sites use.
await capture('https://kuathletics.com/sports/softball/schedule/season/2026-fall', 'schedule-2026-fall');
// News feed: fall-ball recaps (Oct/Nov 2025) would prove fall results are
// published as stories even without structured stats.
await capture('https://kuathletics.com/sports/softball/news', 'news');
// Stats pages that could hold fall data.
await capture('https://kuathletics.com/sports/softball/stats/2027', 'stats-2027');

await browser.close();
fs.writeFileSync('probe3/summary.txt', summary.join('\n') + '\n');
note('probe3 complete');
