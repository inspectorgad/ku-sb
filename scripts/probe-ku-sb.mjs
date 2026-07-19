// Data-source validation probe for the KU Softball app.
//
// Runs in GitHub Actions (open outbound network — the same pattern the
// ku-wbb and ku-volleyball apps use, since interactive sessions have
// restricted egress). Validates, before any app code is written, that the
// Spring 2026 season data is fully retrievable:
//
//  1. NCAA API (ncaa-api.henrygd.me, JSON wrapper around ncaa.com):
//     scans the daily softball/d1 scoreboard across the whole season to
//     discover every Kansas game, then pulls each finished game's info and
//     official box score.
//  2. kuathletics.com (Sidearm) via headless Chromium: roster, schedule,
//     and stats pages, to confirm the roster source works (best-effort).
//
// Everything is dumped under probe/ as raw evidence plus a validation
// summary. Softball box-score JSON shape is unknown up front, so the probe
// is defensive: it records the structure it finds, collects every player
// stat field name it can see, and — where a runs-like field exists — checks
// that per-player runs sum to the team's final score.
import { chromium } from 'playwright';
import fs from 'fs';

const API = 'https://ncaa-api.henrygd.me';
const TEAM_SEO = 'kansas';
// 2026 D1 softball: opening weekend in early February through the WCWS
// finals in early June. Pad both ends to be safe.
const SEASON_START = '2026-02-01';
const SEASON_END = '2026-06-15';

fs.rmSync('probe', { recursive: true, force: true });
fs.mkdirSync('probe/games', { recursive: true });

const summary = [];
function note(line) {
  summary.push(line);
  console.log(line);
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function getJson(url) {
  const resp = await fetch(url, { headers: { accept: 'application/json' } });
  await sleep(350); // public instance is limited to 5 req/s
  if (!resp.ok) throw new Error(`${resp.status} for ${url}`);
  return resp.json();
}

// --- 1. Scoreboard scan: discover every KU game of 2026 --------------------
note(`Scanning NCAA softball/d1 scoreboard ${SEASON_START}..${SEASON_END} for '${TEAM_SEO}' games`);
const games = [];
let scanErrors = 0;
for (
  let d = new Date(`${SEASON_START}T00:00:00Z`);
  d <= new Date(`${SEASON_END}T00:00:00Z`);
  d = new Date(d.getTime() + 86_400_000)
) {
  const date = d.toISOString().slice(0, 10);
  const [y, m, day] = date.split('-');
  let data;
  try {
    data = await getJson(`${API}/scoreboard/softball/d1/${y}/${m}/${day}`);
  } catch (e) {
    // Days with no D1 games return 404; anything else is worth counting.
    if (!String(e.message).startsWith('404')) {
      scanErrors++;
      note(`scoreboard ${date}: ${e.message}`);
    }
    continue;
  }
  for (const wrap of data.games || []) {
    const g = wrap.game || wrap;
    const sides = [g.home, g.away];
    if (!sides.some((s) => s?.names?.seo === TEAM_SEO)) continue;
    games.push({
      gameId: g.gameID,
      date,
      state: g.gameState,
      home: g.home?.names?.short,
      homeScore: g.home?.score,
      away: g.away?.names?.short,
      awayScore: g.away?.score,
      kuIsHome: g.home?.names?.seo === TEAM_SEO,
    });
    note(`  ${date}: ${g.away?.names?.short} ${g.away?.score || ''} at ${g.home?.names?.short} ${g.home?.score || ''} (${g.gameState}, id ${g.gameID})`);
  }
}
fs.writeFileSync('probe/schedule.json', JSON.stringify(games, null, 1));
note(`Found ${games.length} Kansas games (${scanErrors} scan errors)`);

// --- 2. Info + box scores for every final game ------------------------------
// Softball box-score JSON shape is unverified, so capture raw and inspect.
let boxOk = 0;
let boxFail = 0;
const statFields = new Set();
const boxShapes = new Set();
const checks = [];

// Find a runs-like value on a player stat line regardless of exact field name.
function runsOf(p) {
  for (const k of ['runs', 'r', 'R']) {
    if (p[k] !== undefined && p[k] !== null && p[k] !== '') {
      const n = Number(p[k]);
      if (Number.isFinite(n)) return n;
    }
  }
  return null;
}

for (const g of games) {
  if (g.state !== 'final') continue;
  try {
    const info = await getJson(`${API}/game/${g.gameId}`);
    const box = await getJson(`${API}/game/${g.gameId}/boxscore`);
    fs.writeFileSync(
      `probe/games/ncaa-game-${g.gameId}.json`,
      JSON.stringify({ gameId: g.gameId, date: g.date, info, box }, null, 1)
    );
    boxOk++;
    boxShapes.add(Object.keys(box).sort().join(','));

    // Consistency check: per-team sum of player runs vs final score
    // (every softball run is credited to a player, so these must match).
    const teams = box.teams || [];
    for (const tb of box.teamBoxscore || []) {
      // In the WBB feed teams[].teamId is a string but teamBoxscore[].teamId
      // is a number — compare as strings here too.
      const team = teams.find((t) => String(t.teamId) === String(tb.teamId));
      const isKuSide = team?.seoname === TEAM_SEO || /kansas jayhawks/i.test(team?.nameFull || '');
      const finalScore = Number(team?.isHome ? g.homeScore : g.awayScore);
      const players = tb.playerStats || [];
      players.forEach((p) => Object.keys(p).forEach((k) => statFields.add(k)));
      const runsVals = players.map(runsOf).filter((r) => r !== null);
      const runsSum = runsVals.length ? runsVals.reduce((s, r) => s + r, 0) : null;
      checks.push({
        gameId: g.gameId,
        date: g.date,
        team: team?.nameShort,
        ku: isKuSide,
        players: players.length,
        playerRunsSum: runsSum,
        finalScore,
        match:
          runsSum !== null && Number.isFinite(finalScore) ? runsSum === finalScore : null,
      });
    }
  } catch (e) {
    boxFail++;
    note(`boxscore ${g.gameId} (${g.date}): ${e.message}`);
  }
}
fs.writeFileSync('probe/boxscore-checks.json', JSON.stringify(checks, null, 1));
fs.writeFileSync('probe/stat-fields.json', JSON.stringify([...statFields].sort(), null, 1));
note(`Box scores: ${boxOk} captured, ${boxFail} failed`);
note(`Box top-level shapes seen: ${[...boxShapes].join(' | ') || '(none)'}`);
const checked = checks.filter((c) => c.match !== null);
const mismatches = checked.filter((c) => c.match === false);
note(`Runs consistency: ${checked.length - mismatches.length}/${checked.length} team-games where player runs sum to the final score (${checks.length - checked.length} team-games uncheckable)`);
for (const mm of mismatches) {
  note(`  MISMATCH ${mm.date} ${mm.team}: players sum ${mm.playerRunsSum}, final ${mm.finalScore} (game ${mm.gameId})`);
}
note(`Player stat fields seen: ${[...statFields].sort().join(', ') || '(none — inspect raw box JSON shape)'}`);

// --- 3. kuathletics.com roster + schedule + stats (best-effort) -------------
try {
  const browser = await chromium.launch();
  const context = await browser.newContext({
    userAgent:
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
    viewport: { width: 1400, height: 2400 },
  });

  async function pageText(url) {
    const page = await context.newPage();
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60_000 });
    await page.waitForTimeout(8_000);
    for (let i = 0; i < 10; i++) {
      await page.evaluate(() => window.scrollBy(0, 1200));
      await page.waitForTimeout(400);
    }
    const text = await page.evaluate(() => (document.body ? document.body.innerText : ''));
    await page.close();
    return text;
  }

  const rosterText = await pageText('https://kuathletics.com/sports/softball/roster');
  fs.writeFileSync('probe/roster-page.txt', rosterText);
  const rosterLines = rosterText.split('\n').map((l) => l.trim());
  const roster = [];
  for (let i = 0; i < rosterLines.length; i++) {
    if (rosterLines[i] !== 'Jersey Number') continue;
    const number = rosterLines[i + 1] || '';
    const name = rosterLines[i + 2] || '';
    let position = '';
    if (rosterLines[i + 3] === 'Position') position = (rosterLines[i + 4] || '').trim();
    if (/^\d{1,2}$/.test(number) && /^[A-Za-z'.-]+( [A-Za-z'.-]+)+$/.test(name)) {
      roster.push({ name, jerseyNumber: number, position });
    }
  }
  fs.writeFileSync('probe/roster.json', JSON.stringify(roster, null, 1));
  note(`kuathletics roster: parsed ${roster.length} players`);

  const schedText = await pageText('https://kuathletics.com/sports/softball/schedule/season/2026');
  fs.writeFileSync('probe/schedule-page.txt', schedText);
  note(`kuathletics schedule page: ${schedText.length} chars captured`);

  const statsText = await pageText('https://kuathletics.com/sports/softball/stats/2026');
  fs.writeFileSync('probe/stats-page.txt', statsText);
  note(`kuathletics stats page: ${statsText.length} chars captured`);

  await browser.close();
} catch (e) {
  note(`kuathletics scrape failed (non-fatal): ${e.message}`);
}

// --- Verdict ----------------------------------------------------------------
const finals = games.filter((g) => g.state === 'final').length;
note('');
note('=== VALIDATION VERDICT ===');
note(`Games discovered: ${games.length} (${finals} final)`);
note(`Box scores captured: ${boxOk}/${finals}`);
note(`Runs consistency mismatches: ${mismatches.length} (of ${checked.length} checkable team-games)`);
// D1 softball teams play ~50 games; require most of the season plus every
// final game's box score to call it retrievable.
const pass = games.length >= 40 && boxOk === finals && boxOk > 0;
note(pass ? 'PASS: season data is retrievable — inspect probe/games/ for softball box shape'
          : 'ATTENTION: thresholds not met — inspect notes above');

fs.writeFileSync('probe/summary.txt', summary.join('\n') + '\n');
