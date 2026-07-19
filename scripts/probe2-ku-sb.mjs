// Second-round data probe for the KU Softball app, following up on findings
// from the first probe (see probe/):
//
//  A. The NCAA API feed misses the May 7 Big 12 tournament game vs UCF
//     (game 6602297 stuck in "pre") and per-player pitching lines exist in
//     only 3 of 56 games, with pitcher batting rows polluted by pitching
//     numbers. So kuathletics.com (Sidearm) box scores are evaluated here
//     as the per-game stats source: full batting + pitching tables with
//     clean player names.
//  B. The kuathletics schedule URL guess 404'd, and the season-stats page
//     tables didn't survive innerText extraction; both are re-captured
//     properly here (correct URLs + full HTML).
//
// Evidence lands in probe2/.
import { chromium } from 'playwright';
import fs from 'fs';

const API = 'https://ncaa-api.henrygd.me';

fs.rmSync('probe2', { recursive: true, force: true });
fs.mkdirSync('probe2', { recursive: true });

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

// --- 1. NCAA: the missing May 7 UCF game + PBP availability -----------------
for (const [name, url] of [
  ['game-6602297-info', `${API}/game/6602297`],
  ['game-6602297-boxscore', `${API}/game/6602297/boxscore`],
  ['game-6602297-pbp', `${API}/game/6602297/play-by-play`],
  // PBP + scoring summary for a normal game, as a possible pitching fallback
  ['game-6542954-pbp', `${API}/game/6542954/play-by-play`],
  ['game-6542954-scoring', `${API}/game/6542954/scoring-summary`],
]) {
  try {
    const data = await getJson(url);
    fs.writeFileSync(`probe2/${name}.json`, JSON.stringify(data, null, 1));
    note(`${name}: captured (${JSON.stringify(data).length} chars)`);
  } catch (e) {
    note(`${name}: ${e.message}`);
  }
}

// --- 2. kuathletics.com (Sidearm) ------------------------------------------
const browser = await chromium.launch();
const context = await browser.newContext({
  userAgent:
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
  viewport: { width: 1400, height: 2400 },
});

async function capture(url, name, { html = false } = {}) {
  const page = await context.newPage();
  try {
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60_000 });
    await page.waitForTimeout(8_000);
    for (let i = 0; i < 12; i++) {
      await page.evaluate(() => window.scrollBy(0, 1500));
      await page.waitForTimeout(400);
    }
    const text = await page.evaluate(() => (document.body ? document.body.innerText : ''));
    fs.writeFileSync(`probe2/${name}.txt`, text);
    if (html) {
      fs.writeFileSync(`probe2/${name}.html`, await page.content());
    }
    // All links, to discover box score / schedule URL patterns.
    const links = await page.evaluate(() =>
      Array.from(document.querySelectorAll('a[href]')).map((a) => ({
        text: (a.innerText || '').trim().slice(0, 80),
        href: a.href,
      }))
    );
    fs.writeFileSync(`probe2/${name}-links.json`, JSON.stringify(links, null, 1));
    note(`${name}: ${text.length} chars text, ${links.length} links${html ? ', html saved' : ''}`);
    return { text, links };
  } catch (e) {
    note(`${name}: FAILED ${e.message}`);
    return { text: '', links: [] };
  } finally {
    await page.close();
  }
}

// Season stats page: has per-player season batting/pitching tables plus
// links to every game's box score.
const stats = await capture('https://kuathletics.com/sports/softball/stats/2026', 'stats-2026', { html: true });

// Schedule: plain /schedule (the /season/2026 form 404'd in probe 1).
await capture('https://kuathletics.com/sports/softball/schedule', 'schedule');

// Box score pages: take up to 3 boxscore links found on the stats page —
// ideally including the May 7 UCF game the NCAA feed is missing.
const boxLinks = stats.links.filter((l) => /boxscore/i.test(l.href) || /boxscore/i.test(l.text));
note(`boxscore-ish links found on stats page: ${boxLinks.length}`);
const unique = [...new Map(boxLinks.map((l) => [l.href, l])).values()];
const may7 = unique.filter((l) => /ucf|central-florida/i.test(l.href));
const picks = [...may7, ...unique].slice(0, 3);
let bi = 0;
for (const l of picks) {
  bi++;
  note(`capturing box score ${bi}: ${l.href}`);
  await capture(l.href, `boxscore-${bi}`, { html: true });
}

await browser.close();

fs.writeFileSync('probe2/summary.txt', summary.join('\n') + '\n');
note('probe2 complete');
