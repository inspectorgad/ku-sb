// Eighth-round probe: are there results anywhere for KU's Sept 26 2026 fall
// doubleheader at Nebraska (Sidearm schedule ids 20786 / 20787)?
//
// probe3 concluded fall exhibition results aren't published, but that was
// before any fall game had been played. Now that two have, check every
// plausible source rather than assume:
//   1. kuathletics schedule page — do the Sept 26 rows carry a result now?
//   2. The box-score pages for ids 20786/20787 directly (the schedule
//      assigned them ids, so a page may exist even if nothing links to it).
//   3. kuathletics 2027 stats page — does it link fall box scores?
//   4. KU softball news feed — a recap story would at least give a score.
//   5. Nebraska (huskers.com) — the HOME team may post what KU doesn't.
//   6. NCAA API scoreboard for 2026-09-26.
//
// Evidence lands in probe8/.
import { chromium } from 'playwright';
import fs from 'fs';

fs.rmSync('probe8', { recursive: true, force: true });
fs.mkdirSync('probe8', { recursive: true });

const summary = [];
const note = (l) => { summary.push(l); console.log(l); };

const DEVALUE_TAGS = new Set([
  'ShallowReactive', 'Reactive', 'Ref', 'ShallowRef', 'EmptyRef', 'EmptyShallowRef',
]);
function resolveDevalue(arr, idx, depth = 0) {
  if (depth > 14) return null;
  const v = arr[idx];
  if (Array.isArray(v)) {
    if (v.length === 2 && typeof v[0] === 'string' && DEVALUE_TAGS.has(v[0])) {
      return resolveDevalue(arr, v[1], depth + 1);
    }
    if (v[0] === 'Set') return v.slice(1).map((i) => resolveDevalue(arr, i, depth + 1));
    return v.map((i) => resolveDevalue(arr, i, depth + 1));
  }
  if (v !== null && typeof v === 'object') {
    const out = {};
    for (const [k, i] of Object.entries(v)) out[k] = resolveDevalue(arr, i, depth + 1);
    return out;
  }
  return v;
}

const browser = await chromium.launch();
const context = await browser.newContext({
  userAgent:
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
  viewport: { width: 1400, height: 3000 },
});

async function open(url, name, scrolls = 12) {
  const page = await context.newPage();
  try {
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60_000 });
    await page.waitForTimeout(7_000);
    for (let i = 0; i < scrolls; i++) {
      await page.evaluate(() => window.scrollBy(0, 1500));
      await page.waitForTimeout(200);
    }
    const title = await page.title();
    const text = await page.evaluate(() => (document.body ? document.body.innerText : ''));
    fs.writeFileSync(`probe8/${name}.txt`, text);
    const payload = await page.evaluate(() => {
      const el = document.getElementById('__NUXT_DATA__');
      return el ? el.textContent : null;
    });
    return { page, title, text, payload };
  } catch (e) {
    note(`${name}: FAILED ${e.message}`);
    await page.close();
    return null;
  }
}

// --- 1. KU schedule page: do the Sept 26 rows have results? -----------------
{
  const r = await open('https://kuathletics.com/sports/softball/schedule', 'ku-schedule');
  if (r) {
    note(`KU schedule: "${r.title}"`);
    if (r.payload) {
      const arr = JSON.parse(r.payload);
      let bestIdx = -1, bestLen = 0;
      for (let i = 0; i < arr.length; i++) {
        const v = arr[i];
        if (!Array.isArray(v) || v.length < 1) continue;
        if (typeof v[0] === 'string' && DEVALUE_TAGS.has(v[0])) continue;
        const first = arr[v[0]];
        if (!first || typeof first !== 'object' || Array.isArray(first)) continue;
        const keys = Object.keys(first);
        if (['date', 'at_vs', 'location_indicator', 'opponent'].every((k) => keys.includes(k)) && v.length > bestLen) {
          bestIdx = i; bestLen = v.length;
        }
      }
      if (bestIdx >= 0) {
        const events = (resolveDevalue(arr, bestIdx) || []).filter((e) => e && e.date);
        fs.writeFileSync('probe8/ku-schedule-events.json', JSON.stringify(events, null, 1));
        const fall = events.filter((e) => String(e.date).slice(0, 7) <= '2026-12' && String(e.date).slice(0, 7) >= '2026-08');
        note(`  fall events on page: ${fall.length}`);
        for (const f of fall) {
          const res = f.result || null;
          note(`    ${String(f.date).slice(0,10)} ${f.at_vs||''} ${f.opponent?.title||''} id=${f.id} ` +
               `RESULT=${res ? JSON.stringify(res) : 'none'} status=${f.status||''}`);
        }
      }
    }
    await r.page.close();
  }
}

// --- 2. Box score pages for the two Nebraska ids ----------------------------
for (const id of ['20786', '20787']) {
  for (const url of [
    `https://kuathletics.com/sports/softball/stats/2027/nebraska/boxscore/${id}`,
    `https://kuathletics.com/sports/softball/boxscore/${id}`,
  ]) {
    const r = await open(url, `box-${id}-${url.includes('2027') ? 'a' : 'b'}`, 4);
    if (!r) continue;
    let has = false;
    if (r.payload) {
      const arr = JSON.parse(r.payload);
      for (let i = 0; i < arr.length; i++) {
        const v = arr[i];
        if (v && typeof v === 'object' && !Array.isArray(v) &&
            'homeTeam' in v && 'visitingTeam' in v && 'gameDate' in v) {
          const box = resolveDevalue(arr, i);
          fs.writeFileSync(`probe8/box-${id}.json`, JSON.stringify(box, null, 1));
          const h = box.homeTeam || {}, a = box.visitingTeam || {};
          note(`  BOX ${id}: ${a.name} ${a.score} at ${h.name} ${h.score} (${box.gameDate}) — players: ` +
               `${(h.players||[]).length + (a.players||[]).length}`);
          has = true;
          break;
        }
      }
    }
    note(`box ${id} @ ${url.replace('https://kuathletics.com','')}: "${r.title}" — boxscore node: ${has ? 'YES' : 'no'}`);
    await r.page.close();
    if (has) break;
  }
}

// --- 3. 2027 stats page: any box score links yet? ---------------------------
{
  const r = await open('https://kuathletics.com/sports/softball/stats/2027', 'ku-stats-2027', 8);
  if (r) {
    const links = await r.page.evaluate(() =>
      Array.from(document.querySelectorAll('a[href]')).map((a) => a.getAttribute('href') || ''));
    const boxes = [...new Set(links.filter((h) => /boxscore\/\d+/.test(h)))];
    note(`KU 2027 stats page: "${r.title}" — ${boxes.length} box score links: ${boxes.slice(0,6).join(', ')}`);
    await r.page.close();
  }
}

// --- 4. KU news feed --------------------------------------------------------
{
  const r = await open('https://kuathletics.com/sports/softball/news', 'ku-news', 6);
  if (r) {
    const hits = [...r.text.matchAll(/.{0,100}(Nebraska|fall|exhibition|scrimmage).{0,100}/gi)]
      .map((m) => m[0].replace(/\s+/g, ' '));
    note(`KU news: ${hits.length} Nebraska/fall mentions`);
    for (const h of hits.slice(0, 8)) note(`    ...${h}...`);
    await r.page.close();
  }
}

// --- 5. Nebraska's own site -------------------------------------------------
for (const url of [
  'https://huskers.com/sports/softball/schedule',
  'https://huskers.com/sports/softball/stats',
]) {
  const r = await open(url, `huskers-${url.endsWith('stats') ? 'stats' : 'schedule'}`, 10);
  if (!r) continue;
  const hits = [...r.text.matchAll(/.{0,120}(Kansas|Sep(t|tember)?\.? ?26).{0,120}/gi)]
    .map((m) => m[0].replace(/\s+/g, ' '));
  note(`huskers ${url.split('/').pop()}: "${r.title}" — ${hits.length} Kansas/Sep-26 mentions`);
  for (const h of hits.slice(0, 6)) note(`    ...${h}...`);
  await r.page.close();
}

await browser.close();

// --- 6. NCAA API ------------------------------------------------------------
for (const day of ['2026/09/26']) {
  try {
    const resp = await fetch(`https://ncaa-api.henrygd.me/scoreboard/softball/d1/${day}`, {
      headers: { accept: 'application/json' },
    });
    if (!resp.ok) { note(`NCAA ${day}: HTTP ${resp.status}`); continue; }
    const data = await resp.json();
    const games = (data.games || []).map((w) => w.game || w);
    const ku = games.filter((g) => [g.home, g.away].some((s) => /kansas|nebraska/i.test(s?.names?.short || '')));
    note(`NCAA ${day}: ${games.length} D1 games total; KU/Nebraska: ${ku.length}`);
    for (const g of ku) note(`    ${g.away?.names?.short} ${g.away?.score} at ${g.home?.names?.short} ${g.home?.score} (${g.gameState})`);
  } catch (e) {
    note(`NCAA ${day}: ${e.message}`);
  }
}

fs.writeFileSync('probe8/summary.txt', summary.join('\n') + '\n');
note('probe8 complete');
