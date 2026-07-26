// Scrapes KU Softball data (see DATA-VALIDATION.md):
//  1. kuathletics.com (Sidearm) via headless Chromium — the PRIMARY per-game
//     source. The season stats page links every game's box score page, and
//     each box score page embeds complete structured data (full batting,
//     pitching, and fielding lines with clean names, line scores, W/L/S)
//     in its __NUXT_DATA__ payload. Also scrapes the current roster and
//     upcoming schedule.
//  2. NCAA API (ncaa-api.henrygd.me) — game discovery/results cross-check,
//     plus every Big 12 team's results (each scoreboard side carries a
//     conference tag), which is what the Big 12 standings are computed
//     from. The softball feed's stat lines are NOT trustworthy (see
//     validation), so only schedule facts are kept from it.
//  3. Rankings — the NCAA RPI through the API, and the ESPN.com/USA
//     Softball poll parsed from ncaa.com's server-rendered rankings page
//     (probe5: the poll's JSON endpoint 404s but the HTML table is stable).
// Runs in GitHub Actions where outbound network is open. Incremental: an
// index in scraped/ku-index.json records scanned dates and captured box
// scores so nightly runs only touch new games.
import { chromium } from 'playwright';
import fs from 'fs';

const API = 'https://ncaa-api.henrygd.me';
// Season years: "2026" is the Spring 2026 season.
const SEASONS = (process.env.SEASONS || '2026 2027').trim().split(/\s+/);
const TEAM_SEO = 'kansas';
const CONFERENCE_SEO = 'big-12';
// Bump to force a one-time full re-sweep of scanned dates (e.g. when the
// sweep starts collecting something new, like v2's big12Games).
const INDEX_VERSION = 2;

fs.mkdirSync('scraped', { recursive: true });

const INDEX_PATH = 'scraped/ku-index.json';
const index = fs.existsSync(INDEX_PATH)
  ? JSON.parse(fs.readFileSync(INDEX_PATH, 'utf8'))
  : { indexVersion: INDEX_VERSION, scannedDates: {}, ncaaGames: {}, sidearmGames: {}, big12Games: {} };
index.scannedDates ??= {};
index.ncaaGames ??= {};
index.sidearmGames ??= {};
index.big12Games ??= {};
if ((index.indexVersion ?? 1) < INDEX_VERSION) {
  console.log(
    `index v${index.indexVersion ?? 1} < v${INDEX_VERSION}: clearing ${Object.keys(index.scannedDates).length} scanned dates for a one-time full re-sweep`
  );
  index.scannedDates = {};
  index.indexVersion = INDEX_VERSION;
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function getJson(url) {
  const resp = await fetch(url, { headers: { accept: 'application/json' } });
  await sleep(400); // public instance is limited to 5 req/s
  if (!resp.ok) throw new Error(`${resp.status} for ${url}`);
  return resp.json();
}

function* seasonDates(year) {
  // D1 softball: opening weekend in early February through the WCWS in June.
  const start = new Date(Date.UTC(year, 1, 1));
  const end = new Date(Date.UTC(year, 5, 15));
  for (let d = start; d <= end; d = new Date(d.getTime() + 86_400_000)) {
    yield d.toISOString().slice(0, 10);
  }
}

const today = new Date().toISOString().slice(0, 10);
const recentCutoff = new Date(Date.now() - 4 * 86_400_000).toISOString().slice(0, 10);

// --- 1. NCAA scoreboard scan: game discovery + results ----------------------
for (const season of SEASONS) {
  for (const date of seasonDates(Number(season))) {
    if (date > today) break;
    // Rescan recent dates (results may have just gone final); skip older
    // dates we've already scanned.
    if (index.scannedDates[date] && date < recentCutoff) continue;

    const [y, m, d] = date.split('-');
    let data;
    try {
      data = await getJson(`${API}/scoreboard/softball/d1/${y}/${m}/${d}`);
    } catch (e) {
      // Days with no D1 games return 404; that still counts as scanned.
      if (String(e.message).startsWith('404')) {
        index.scannedDates[date] = true;
      } else {
        console.log(`scoreboard ${date}: ${e.message}`);
      }
      continue; // non-404 failures stay unscanned so a transient error retries tomorrow
    }
    for (const wrap of data.games || []) {
      const g = wrap.game || wrap;
      const sides = [g.home, g.away];

      // Big 12 capture: any final game with at least one conference team.
      // Conference games (both sides tagged) drive conference records; the
      // rest still count toward each team's overall record. Membership comes
      // from the tags themselves, never a hardcoded list, so realignment
      // can't stale it.
      const confTags = (s) => (s?.conferences ?? []).map((c) => c.conferenceSeo);
      const homeInConf = confTags(g.home).includes(CONFERENCE_SEO);
      const awayInConf = confTags(g.away).includes(CONFERENCE_SEO);
      if ((homeInConf || awayInConf) && g.gameState === 'final') {
        const side = (s, inConf) => ({
          name: s?.names?.short ?? '',
          seo: s?.names?.seo ?? '',
          score: Number(s?.score ?? 0),
          winner: Boolean(s?.winner),
          rank: s?.rank ? Number(s.rank) : null,
          inConference: inConf,
        });
        index.big12Games[g.gameID] = {
          date,
          // Softball seasons sit inside one calendar year.
          season: date.slice(0, 4),
          home: side(g.home, homeInConf),
          away: side(g.away, awayInConf),
          conferenceGame: homeInConf && awayInConf,
          // NCAA-tournament games carry bracket fields on the scoreboard.
          // The Big 12 tournament does NOT (probe4), so update-seed.py
          // fences it off with a date window instead. Bracket games count
          // toward overall records but never conference records.
          bracket: Boolean(g.bracketRound || g.bracketId),
        };
      }

      if (!sides.some((s) => s?.names?.seo === TEAM_SEO)) continue;
      const kuIsHome = g.home?.names?.seo === TEAM_SEO;
      const ku = kuIsHome ? g.home : g.away;
      const opp = kuIsHome ? g.away : g.home;
      index.ncaaGames[g.gameID] = {
        date,
        final: g.gameState === 'final',
        opponent: opp?.names?.short,
        kuIsHome,
        kuScore: ku?.score,
        oppScore: opp?.score,
      };
      console.log(`NCAA: KU game ${g.gameID} on ${date} vs ${opp?.names?.short} (${g.gameState})`);
    }
    index.scannedDates[date] = true;
  }
}

console.log(`big12 games captured: ${Object.keys(index.big12Games).length}`);

// --- 1b. Rankings snapshots (best effort; never fail the run) ---------------
// Both sources serve only the CURRENT snapshot — no per-season history — so
// each run overwrites its file and update-seed.py keys it by the season in
// the "Through Games ..." label.

// NCAA RPI (softball's selection metric): plain JSON via the API.
try {
  const data = await getJson(`${API}/rankings/softball/d1/ncaa-womens-softball-rpi`);
  fs.writeFileSync('scraped/rankings-rpi.json', JSON.stringify(data, null, 1));
  console.log(`rankings rpi: ${data.data?.length ?? 0} rows (${data.updated ?? 'no date'})`);
} catch (e) {
  console.log(`rankings rpi failed (non-fatal): ${e.message}`);
}

// ESPN.com/USA Softball poll: the API 404s on its two-segment slug, but
// ncaa.com serves the table server-rendered (probe5) — parse the HTML.
try {
  const resp = await fetch('https://www.ncaa.com/rankings/softball/d1/espncom/usa-softball', {
    headers: {
      'user-agent':
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36',
      accept: 'text/html',
    },
  });
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
  const html = await resp.text();
  const updated = (html.match(/[Tt]hrough [Gg]ames[^<]*/) || [''])[0].trim();
  const table = html.slice(html.indexOf('<table'));
  const strip = (s) => s.replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim();
  const rows = [];
  for (const tr of table.matchAll(/<tr[^>]*>([\s\S]*?)<\/tr>/g)) {
    const cells = [...tr[1].matchAll(/<td[^>]*>([\s\S]*?)<\/td>/g)].map((m) => strip(m[1]));
    // RANK, COLLEGE, RECORD, POINTS, PREVIOUS RANK
    if (cells.length >= 5 && /^t?-?\d/i.test(cells[0])) {
      rows.push({
        Rank: cells[0],
        School: cells[1],
        Record: cells[2],
        Points: cells[3],
        Previous: cells[4],
      });
    }
  }
  const poll = { title: 'ESPN.com/USA Softball Top 25', updated, data: rows };
  fs.writeFileSync('scraped/rankings-poll.json', JSON.stringify(poll, null, 1));
  console.log(`rankings poll: ${rows.length} rows (${updated || 'no date'})`);
} catch (e) {
  console.log(`rankings poll failed (non-fatal): ${e.message}`);
}

// --- 2. kuathletics.com (Sidearm) -------------------------------------------
const browser = await chromium.launch();
const context = await browser.newContext({
  userAgent:
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
  viewport: { width: 1400, height: 2400 },
});

async function openPage(url, settleMs = 6000) {
  const page = await context.newPage();
  await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60_000 });
  await page.waitForTimeout(settleMs);
  return page;
}

// Resolves the devalue-encoded __NUXT_DATA__ payload embedded in every
// Sidearm nextgen page. Values are stored in one flat array; objects map
// keys to element indices, and reactive wrappers tag-index pairs.
const DEVALUE_TAGS = new Set([
  'ShallowReactive', 'Reactive', 'Ref', 'ShallowRef', 'EmptyRef', 'EmptyShallowRef',
]);
function resolveDevalue(arr, idx, depth = 0) {
  if (depth > 16) return null;
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

function trimTeam(team) {
  if (!team) return null;
  return {
    name: team.name,
    score: team.score,
    record: team.record,
    isTenantTeam: team.isTenantTeam,
    scoringSummary: team.scoringSummary && {
      runs: team.scoringSummary.runs,
      hits: team.scoringSummary.hits,
      errors: team.scoringSummary.errors,
      scoreByInnings: team.scoringSummary.scoreByInnings,
    },
    totals: team.totals && { hitting: team.totals.hitting, pitching: team.totals.pitching },
    players: (team.players || []).map((p) => ({
      name: p.name,
      uniform: p.uniform,
      position: p.position,
      spot: p.spot,
      gamePlayed: p.gamePlayed,
      gameStarted: p.gameStarted,
      substitute: p.substitute,
      hitting: p.hitting,
      pitching: p.pitching,
    })),
  };
}

async function scrapeBoxScore(url, sidearmId) {
  const page = await openPage(url, 8000);
  try {
    const payload = await page.evaluate(() => {
      const el = document.getElementById('__NUXT_DATA__');
      return el ? el.textContent : null;
    });
    if (!payload) throw new Error('no __NUXT_DATA__ on page');
    const arr = JSON.parse(payload);
    let box = null;
    for (let i = 0; i < arr.length; i++) {
      const v = arr[i];
      if (
        v && typeof v === 'object' && !Array.isArray(v) &&
        'homeTeam' in v && 'visitingTeam' in v && 'gameDate' in v
      ) {
        box = resolveDevalue(arr, i);
        break;
      }
    }
    if (!box) throw new Error('no boxscore node in payload');
    const out = {
      sidearmId,
      url,
      gameDate: box.gameDate,
      venue: box.venue && {
        date: box.venue.date,
        location: box.venue.location,
        attendance: box.venue.attendance,
        doubleHeaderGame: box.venue.doubleHeaderGame,
      },
      homeTeam: trimTeam(box.homeTeam),
      visitingTeam: trimTeam(box.visitingTeam),
    };
    fs.writeFileSync(`scraped/sidearm-game-${sidearmId}.json`, JSON.stringify(out, null, 1));
    return true;
  } finally {
    await page.close();
  }
}

try {
  // 2a. Season stats pages -> box score links, then any uncaptured box scores.
  for (const season of SEASONS) {
    let links = [];
    try {
      const page = await openPage(`https://kuathletics.com/sports/softball/stats/${season}`, 8000);
      links = await page.evaluate(() =>
        Array.from(document.querySelectorAll('a[href]')).map((a) => a.href)
      );
      await page.close();
    } catch (e) {
      console.log(`stats page ${season}: ${e.message}`);
      continue;
    }
    const ids = new Map();
    for (const href of links) {
      const m = href.match(/\/sports\/softball\/stats\/\d+\/[^/]+\/boxscore\/(\d+)/);
      if (m) ids.set(m[1], href);
    }
    console.log(`stats ${season}: ${ids.size} box score links`);
    for (const [id, href] of ids) {
      if (index.sidearmGames[id]?.captured) continue;
      try {
        await scrapeBoxScore(href, id);
        index.sidearmGames[id] = { captured: true, season, url: href };
        console.log(`captured sidearm box ${id}`);
      } catch (e) {
        console.log(`sidearm box ${id}: ${e.message}`);
      }
    }
  }

  // 2b. Roster: lines run "Jersey Number\n<num>\n<name>\nPosition\n<pos>\n..."
  const rosterPage = await openPage('https://kuathletics.com/sports/softball/roster', 8000);
  for (let i = 0; i < 10; i++) {
    await rosterPage.evaluate(() => window.scrollBy(0, 1200));
    await rosterPage.waitForTimeout(300);
  }
  const rosterText = await rosterPage.evaluate(() =>
    document.body ? document.body.innerText : ''
  );
  await rosterPage.close();
  fs.writeFileSync('scraped/roster-page.txt', rosterText);
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
  fs.writeFileSync('scraped/roster.json', JSON.stringify(roster, null, 1));
  console.log(`roster: ${roster.length} players`);

  // 2c. Upcoming games from the site-wide scoreboard rotator:
  // "Upcoming Event: Softball versus X on February 5, 2027 at 6 p.m. CT"
  const schedPage = await openPage('https://kuathletics.com/sports/softball/schedule', 8000);
  const schedText = await schedPage.evaluate(() =>
    document.body ? document.body.innerText : ''
  );
  await schedPage.close();
  fs.writeFileSync('scraped/schedule-page.txt', schedText);
  const months = ['January', 'February', 'March', 'April', 'May', 'June', 'July',
    'August', 'September', 'October', 'November', 'December'];
  const upcoming = [];
  const re = /Upcoming Event: Softball (versus|at) (.+?) on ([A-Z][a-z]+) (\d{1,2}), (\d{4})/g;
  for (const m of schedText.matchAll(re)) {
    const month = months.indexOf(m[3]) + 1;
    if (month === 0) continue;
    const date = `${m[5]}-${String(month).padStart(2, '0')}-${String(m[4]).padStart(2, '0')}`;
    upcoming.push({ date, opponent: m[2].trim(), home: m[1] === 'versus' });
  }
  // De-dup (the rotator repeats on every page view)
  const seen = new Set();
  const uniqueUpcoming = upcoming.filter((u) => {
    const k = `${u.date}|${u.opponent}`;
    if (seen.has(k)) return false;
    seen.add(k);
    return true;
  });
  fs.writeFileSync('scraped/upcoming.json', JSON.stringify(uniqueUpcoming, null, 1));
  console.log(`upcoming: ${uniqueUpcoming.length} games`);
} catch (e) {
  console.log(`kuathletics scrape failed (non-fatal): ${e.message}`);
}

await browser.close();
fs.writeFileSync(INDEX_PATH, JSON.stringify(index, null, 1));
console.log('scrape complete');
