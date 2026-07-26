// Fourth-round probe: validate the data sources needed to port the ku-wbb
// "Big 12 standings + national rankings" feature to softball.
//
//  1. Rankings endpoints — softball has no AP poll; the equivalents are the
//     USA Today/NFCA coaches poll and the NCAA RPI (softball's NET). The
//     exact ncaa.com slugs are unverified, so try the plausible set and
//     record which respond and with what columns/"updated" labels.
//  2. Scoreboard shape — the standings computation needs (a) conference
//     tags on each side of every scoreboard game and (b) bracket fields on
//     tournament games so they can be fenced out of conference records.
//     Dump raw scoreboard days: a Big 12 series day (Apr 24), the Big 12
//     tournament (May 7-8), and the NCAA regionals (May 16).
//
// Evidence lands in probe4/.
import fs from 'fs';

const API = 'https://ncaa-api.henrygd.me';

fs.rmSync('probe4', { recursive: true, force: true });
fs.mkdirSync('probe4', { recursive: true });

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

// --- 1. Candidate rankings endpoints -----------------------------------------
const CANDIDATES = [
  ['nfca', 'rankings/softball/d1/usa-today-nfca-division-i-top-25-coaches-poll'],
  ['nfca-alt', 'rankings/softball/d1/nfca-division-i-top-25-coaches-poll'],
  ['usa-softball', 'rankings/softball/d1/usa-softball-collegiate-top-25'],
  ['rpi', 'rankings/softball/d1/rpi'],
  ['rpi-alt', 'rankings/softball/d1/ncaa-softball-rpi-rankings'],
  ['d1softball', 'rankings/softball/d1/d1softball-top-25'],
];
for (const [name, path] of CANDIDATES) {
  try {
    const data = await getJson(`${API}/${path}`);
    fs.writeFileSync(`probe4/rankings-${name}.json`, JSON.stringify(data, null, 1));
    const first = (data.data || [])[0] || {};
    note(`rankings ${name}: OK — ${data.data?.length ?? 0} rows, updated "${data.updated ?? ''}", title "${data.title ?? ''}", columns: ${Object.keys(first).join(', ')}`);
  } catch (e) {
    note(`rankings ${name}: ${e.message}`);
  }
}

// --- 2. Raw scoreboard days ---------------------------------------------------
// Keep only games where either side is a Big 12 team (or Kansas), full raw.
for (const day of ['2026/04/24', '2026/05/07', '2026/05/08', '2026/05/16']) {
  try {
    const data = await getJson(`${API}/scoreboard/softball/d1/${day}`);
    const games = (data.games || []).map((w) => w.game || w).filter((g) => {
      const conf = (s) => (s?.conferences ?? []).map((c) => c.conferenceSeo || c.conferenceName);
      return conf(g.home).includes('big-12') || conf(g.away).includes('big-12') ||
        [g.home, g.away].some((s) => s?.names?.seo === 'kansas');
    });
    const name = day.replaceAll('/', '-');
    fs.writeFileSync(`probe4/scoreboard-${name}.json`, JSON.stringify(games, null, 1));
    const summaryLine = games.map((g) =>
      `${g.away?.names?.short} at ${g.home?.names?.short} [bracketRound=${g.bracketRound ?? ''} bracketId=${g.bracketId ?? ''} confs=${(g.home?.conferences ?? []).map((c) => c.conferenceSeo).join('/')}]`
    ).join(' | ');
    note(`scoreboard ${day}: ${games.length} Big-12-involved games — ${summaryLine}`);
  } catch (e) {
    note(`scoreboard ${day}: ${e.message}`);
  }
}

fs.writeFileSync('probe4/summary.txt', summary.join('\n') + '\n');
note('probe4 complete');
