// Seventh-round probe: has KU posted a FALL (or 2027) softball schedule yet,
// and if so how do those rows differ from regular-season ones?
//
// probe3 (July) found no fall games anywhere. Fall slates are typically
// announced in late summer, so this re-checks and — critically — captures
// how the payload marks such games (exhibition flag? tournament label?
// separate season?) so they can be labeled correctly rather than silently
// folded into the spring season's record.
//
// Evidence lands in probe7/.
import { chromium } from 'playwright';
import fs from 'fs';

fs.rmSync('probe7', { recursive: true, force: true });
fs.mkdirSync('probe7', { recursive: true });

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

async function schedule(url, name) {
  const page = await context.newPage();
  try {
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60_000 });
    await page.waitForTimeout(7_000);
    for (let i = 0; i < 16; i++) {
      await page.evaluate(() => window.scrollBy(0, 1500));
      await page.waitForTimeout(200);
    }
    const title = await page.title();
    const text = await page.evaluate(() => (document.body ? document.body.innerText : ''));
    fs.writeFileSync(`probe7/${name}.txt`, text);

    // Season selector options — reveals which seasons the site now offers.
    const options = await page.evaluate(() =>
      Array.from(document.querySelectorAll('select option, [role="option"], a[href*="season"]'))
        .map((el) => ({ text: (el.textContent || '').trim().slice(0, 60), value: el.getAttribute('value') || el.getAttribute('href') || '' }))
        .filter((o) => o.text)
        .slice(0, 40)
    );
    fs.writeFileSync(`probe7/${name}-options.json`, JSON.stringify(options, null, 1));

    const payload = await page.evaluate(() => {
      const el = document.getElementById('__NUXT_DATA__');
      return el ? el.textContent : null;
    });

    let events = [];
    if (payload) {
      const arr = JSON.parse(payload);
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
        events = (resolveDevalue(arr, bestIdx) || []).filter((e) => e && e.date);
        fs.writeFileSync(`probe7/${name}-events.json`, JSON.stringify(events, null, 1));
        // Dump the FULL key set of a row so any fall/exhibition marker shows up.
        if (events[0]) {
          fs.writeFileSync(`probe7/${name}-row-keys.json`, JSON.stringify(Object.keys(events[0]).sort(), null, 1));
        }
      }
    }

    const months = {};
    for (const e of events) {
      const m = String(e.date).slice(0, 7);
      months[m] = (months[m] || 0) + 1;
    }
    note(`${name}: "${title}" — ${events.length} events; months: ${JSON.stringify(months)}`);
    note(`  season options: ${options.map((o) => o.text).join(' | ').slice(0, 300) || '(none)'}`);

    // Anything outside Feb-Jun is a candidate fall/exhibition game.
    const fall = events.filter((e) => {
      const m = Number(String(e.date).slice(5, 7));
      return m >= 8 || m === 1;
    });
    note(`  FALL-WINDOW GAMES (Aug-Jan): ${fall.length}`);
    for (const f of fall.slice(0, 20)) {
      note(`    ${String(f.date).slice(0, 10)} ${f.at_vs || ''} ${f.opponent?.title || ''} | exhibition=${f.exhibition ?? f.is_exhibition ?? 'n/a'} tournament="${f.tournament || ''}" status="${f.status || ''}" note="${(f.note || '').slice(0, 60)}"`);
    }
    return { events, fall };
  } catch (e) {
    note(`${name}: FAILED ${e.message}`);
    return { events: [], fall: [] };
  } finally {
    await page.close();
  }
}

await schedule('https://kuathletics.com/sports/softball/schedule', 'schedule-current');
await schedule('https://kuathletics.com/sports/softball/schedule/season/2027', 'schedule-2027');

// News feed: a fall-ball announcement would show up here first.
const news = await context.newPage();
try {
  await news.goto('https://kuathletics.com/sports/softball/news', { waitUntil: 'domcontentloaded', timeout: 60_000 });
  await news.waitForTimeout(6_000);
  const text = await news.evaluate(() => (document.body ? document.body.innerText : ''));
  fs.writeFileSync('probe7/news.txt', text);
  const hits = [...text.matchAll(/.{0,90}(fall|exhibition|scrimmage|2027 schedule).{0,90}/gi)].map((m) => m[0].replace(/\s+/g, ' '));
  note(`news page: ${text.length} chars; fall/exhibition/2027 mentions: ${hits.length}`);
  for (const h of hits.slice(0, 10)) note(`  ...${h}...`);
} catch (e) {
  note(`news: FAILED ${e.message}`);
} finally {
  await news.close();
}

await browser.close();
fs.writeFileSync('probe7/summary.txt', summary.join('\n') + '\n');
note('probe7 complete');
