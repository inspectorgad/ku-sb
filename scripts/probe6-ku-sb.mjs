// Sixth-round probe: can the FULL schedule (home and away, played and
// unplayed) be parsed the moment KU posts it?
//
// Today the scraper only learns about future games from the site-wide
// "Coming Up" rotator, which surfaces softball only when games are
// imminent. The schedule page itself lists every game — this probe works
// out how to read it structurally.
//
// Validated against the CURRENT (2026) schedule page, whose answer is known:
// 57 games, 36-21, with a documented home/away/neutral split (18 home:
// 13-5, 18 away: 9-9, 21 neutral: 14-7). A parser that reproduces that from
// the page can be trusted on the 2027 page, which is the same Sidearm
// template.
//
// Captures: the schedule page's __NUXT_DATA__ payload (preferred — the box
// scores use the same mechanism), the rendered text, and the "Text Only"
// view as a fallback. Evidence lands in probe6/.
import { chromium } from 'playwright';
import fs from 'fs';

fs.rmSync('probe6', { recursive: true, force: true });
fs.mkdirSync('probe6', { recursive: true });

const summary = [];
function note(line) {
  summary.push(line);
  console.log(line);
}

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

async function grab(url, name) {
  const page = await context.newPage();
  try {
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60_000 });
    await page.waitForTimeout(8_000);
    for (let i = 0; i < 20; i++) {
      await page.evaluate(() => window.scrollBy(0, 1500));
      await page.waitForTimeout(250);
    }
    const html = await page.content();
    fs.writeFileSync(`probe6/${name}.html`, html);
    const text = await page.evaluate(() => (document.body ? document.body.innerText : ''));
    fs.writeFileSync(`probe6/${name}.txt`, text);
    const payload = await page.evaluate(() => {
      const el = document.getElementById('__NUXT_DATA__');
      return el ? el.textContent : null;
    });
    note(`${name}: ${html.length} chars html, ${text.length} chars text, payload ${payload ? payload.length + ' chars' : 'ABSENT'}`);
    return payload;
  } catch (e) {
    note(`${name}: FAILED ${e.message}`);
    return null;
  } finally {
    await page.close();
  }
}

const payload = await grab('https://kuathletics.com/sports/softball/schedule', 'schedule');
await grab('https://kuathletics.com/sports/softball/schedule/text', 'schedule-text');

// --- Hunt the payload for schedule-event arrays ------------------------------
if (payload) {
  const arr = JSON.parse(payload);
  fs.writeFileSync('probe6/schedule-payload.json', JSON.stringify(arr).slice(0, 4_000_000));

  // Any object carrying date-ish + opponent-ish keys is a candidate event.
  const DATE_KEYS = ['date', 'gameDate', 'startDate', 'eventDate', 'dateUtc'];
  const OPP_KEYS = ['opponent', 'opponentName', 'opponentTitle', 'team', 'title', 'name'];
  const shapes = new Map();
  for (let i = 0; i < arr.length; i++) {
    const v = arr[i];
    if (!v || typeof v !== 'object' || Array.isArray(v)) continue;
    const keys = Object.keys(v);
    const hasDate = keys.some((k) => DATE_KEYS.includes(k));
    const hasOpp = keys.some((k) => OPP_KEYS.includes(k));
    if (hasDate && hasOpp) {
      const sig = keys.sort().join(',');
      if (!shapes.has(sig)) shapes.set(sig, { sig, count: 0, sampleIdx: i });
      shapes.get(sig).count++;
    }
  }
  note(`candidate event shapes: ${shapes.size}`);
  const found = [];
  for (const s of [...shapes.values()].sort((a, b) => b.count - a.count).slice(0, 6)) {
    const sample = resolveDevalue(arr, s.sampleIdx);
    found.push({ signature: s.sig, occurrences: s.count, sample });
    note(`  shape x${s.count}: ${s.sig.slice(0, 300)}`);
    note(`    sample: ${JSON.stringify(sample).slice(0, 600)}`);
  }
  fs.writeFileSync('probe6/candidate-shapes.json', JSON.stringify(found, null, 1));

  // Also look for arrays of >=20 such objects — the schedule list itself.
  const lists = [];
  for (let i = 0; i < arr.length; i++) {
    const v = arr[i];
    if (!Array.isArray(v) || v.length < 20) continue;
    if (typeof v[0] === 'string' && DEVALUE_TAGS.has(v[0])) continue;
    const first = arr[v[0]];
    if (first && typeof first === 'object' && !Array.isArray(first)) {
      const keys = Object.keys(first);
      if (keys.some((k) => DATE_KEYS.includes(k)) || keys.some((k) => OPP_KEYS.includes(k))) {
        lists.push({ idx: i, length: v.length, firstKeys: keys.sort().join(',').slice(0, 300) });
      }
    }
  }
  note(`schedule-list candidates (arrays of 20+ event-ish objects): ${lists.length}`);
  for (const l of lists.slice(0, 8)) note(`  idx ${l.idx}: ${l.length} items — ${l.firstKeys}`);
  if (lists.length) {
    const best = lists.sort((a, b) => b.length - a.length)[0];
    const resolved = resolveDevalue(arr, best.idx);
    fs.writeFileSync('probe6/schedule-list.json', JSON.stringify(resolved, null, 1));
    note(`largest list (idx ${best.idx}, ${best.length} items) written to probe6/schedule-list.json`);
    note(`  item 0: ${JSON.stringify(resolved[0]).slice(0, 900)}`);
  }
  fs.writeFileSync('probe6/list-candidates.json', JSON.stringify(lists, null, 1));
}

await browser.close();
fs.writeFileSync('probe6/summary.txt', summary.join('\n') + '\n');
note('probe6 complete');
