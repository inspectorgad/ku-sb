// Fifth-round probe: discover the real ncaa.com rankings slugs for softball
// (probe4's guesses all 404'd), then capture each ranking through the NCAA
// API. The rankings index page carries a poll selector whose options hold
// the slugs.
import fs from 'fs';

const API = 'https://ncaa-api.henrygd.me';

fs.rmSync('probe5', { recursive: true, force: true });
fs.mkdirSync('probe5', { recursive: true });

const summary = [];
function note(line) {
  summary.push(line);
  console.log(line);
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function getJson(url) {
  const resp = await fetch(url, { headers: { accept: 'application/json' } });
  await sleep(400);
  if (!resp.ok) throw new Error(`${resp.status} for ${url}`);
  return resp.json();
}

// --- 1. Slug discovery from ncaa.com ----------------------------------------
let slugs = new Set();
try {
  const resp = await fetch('https://www.ncaa.com/rankings/softball/d1', {
    headers: {
      'user-agent':
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36',
      accept: 'text/html',
    },
  });
  note(`ncaa.com rankings page: HTTP ${resp.status}`);
  const html = await resp.text();
  fs.writeFileSync('probe5/rankings-page.html', html);
  for (const m of html.matchAll(/\/rankings\/softball\/d1\/([a-z0-9-]+)/g)) {
    slugs.add(m[1]);
  }
  note(`slugs found: ${[...slugs].join(', ') || '(none)'}`);
} catch (e) {
  note(`ncaa.com fetch failed: ${e.message}`);
}

// --- 2. Capture each discovered ranking through the API ---------------------
for (const slug of slugs) {
  try {
    const data = await getJson(`${API}/rankings/softball/d1/${slug}`);
    fs.writeFileSync(`probe5/rankings-${slug}.json`, JSON.stringify(data, null, 1));
    const first = (data.data || [])[0] || {};
    note(`API ${slug}: OK — ${data.data?.length ?? 0} rows, updated "${data.updated ?? ''}", title "${data.title ?? ''}", columns: ${Object.keys(first).join(', ')}`);
  } catch (e) {
    note(`API ${slug}: ${e.message}`);
  }
}

fs.writeFileSync('probe5/summary.txt', summary.join('\n') + '\n');
note('probe5 complete');
