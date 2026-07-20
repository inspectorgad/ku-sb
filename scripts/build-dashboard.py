#!/usr/bin/env python3
"""Generates docs/index.html — the KU Softball season dashboard — from
app/src/main/assets/seed.json.

The dashboard is a single self-contained page (inline CSS/JS, data inlined
as JSON) in the same visual system as the KU WBB dashboard: header
scoreboard, stat tiles, run-margin chart, leader bars, full games table,
and a clickable roster where each player opens a modal with per-game
batting (and pitching) stats. Published via GitHub Pages by
deploy-pages.yml; the nightly scrape regenerates it whenever the seed
changes.
"""
import json
import os
from datetime import datetime, timezone

SEED_PATH = "app/src/main/assets/seed.json"
OUT_PATH = "docs/index.html"

with open(SEED_PATH) as f:
    seed = json.load(f)

data_json = json.dumps(seed, separators=(",", ":"))
updated = seed.get("generatedAt") or datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

TEMPLATE = r"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>KU Softball 2026</title>
<link rel="icon" href="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'%3E%3Ctext y='.9em' font-size='90'%3E%F0%9F%A5%8E%3C/text%3E%3C/svg%3E">
<style>
  :root {
    --ground: #F5F6F9;
    --surface: #FFFFFF;
    --surface2: #EDEFF4;
    --ink: #131A26;
    --muted: #5A6373;
    --faint: #C9CEDA;
    --blue: #0051BA;
    --blue-ink: #FFFFFF;
    --crimson: #D0000C;
    --gold: #9A7000;
    --banner-gold: #FFC82D;
    --win: #0051BA;
    --loss: #D0000C;
    --win-bg: #E3ECFA;
    --loss-bg: #FBE5E6;
    --chart-grid: #E2E5EC;
    --shadow: 0 1px 3px rgba(19, 26, 38, .08), 0 4px 16px rgba(19, 26, 38, .06);
  }
  @media (prefers-color-scheme: dark) {
    :root {
      --ground: #10141C; --surface: #171D28; --surface2: #1F2734;
      --ink: #ECEFF5; --muted: #98A1B3; --faint: #3A4354;
      --blue: #5D95E8; --blue-ink: #0C1526; --crimson: #E24B55; --gold: #B08420;
      --banner-gold: #D9A62E; --win: #5D95E8; --loss: #E24B55;
      --win-bg: #1B2A44; --loss-bg: #381E22; --chart-grid: #262E3D;
      --shadow: 0 1px 3px rgba(0, 0, 0, .4), 0 4px 16px rgba(0, 0, 0, .3);
    }
  }

  * { box-sizing: border-box; }
  html, body { margin: 0; }
  body {
    background: var(--ground);
    color: var(--ink);
    font: 15px/1.5 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
  }
  .wrap { max-width: 1080px; margin: 0 auto; padding: 0 20px 64px; }

  header {
    background: linear-gradient(120deg, #0051BA 0%, #003A85 100%);
    color: #fff;
    border-bottom: 6px solid var(--banner-gold);
  }
  .head-in {
    max-width: 1080px; margin: 0 auto; padding: 30px 20px 26px;
    display: flex; flex-wrap: wrap; align-items: flex-end; gap: 16px 32px;
  }
  .eyebrow { font-size: 11px; font-weight: 700; letter-spacing: .14em; text-transform: uppercase; opacity: .85; }
  h1 {
    margin: 2px 0 0; font-size: clamp(26px, 4.5vw, 40px); line-height: 1.05;
    font-weight: 900; letter-spacing: -.02em; text-wrap: balance;
  }
  h1 .thin { font-weight: 400; opacity: .9; }
  .head-spacer { flex: 1; }
  .record { text-align: right; font-variant-numeric: tabular-nums; }
  .record .num { font-size: clamp(34px, 5vw, 52px); font-weight: 900; line-height: 1; }
  .record .sub { font-size: 12px; letter-spacing: .1em; text-transform: uppercase; opacity: .85; margin-top: 4px; }

  section { margin-top: 40px; }
  .sec-head { display: flex; align-items: baseline; gap: 12px; margin-bottom: 14px; }
  .sec-head h2 { margin: 0; font-size: 19px; font-weight: 800; letter-spacing: -.01em; }
  .sec-head .note { color: var(--muted); font-size: 13px; }
  .rule { flex: 1; height: 3px; border-radius: 2px;
    background: linear-gradient(90deg, var(--blue) 0 40%, var(--crimson) 40% 70%, var(--banner-gold) 70% 100%);
    opacity: .55; }

  .tiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 12px; margin-top: 24px; }
  .tile {
    background: var(--surface); border-radius: 10px; box-shadow: var(--shadow);
    padding: 14px 16px 12px; border-top: 3px solid var(--blue);
  }
  .tile.alt { border-top-color: var(--crimson); }
  .tile .v { font-size: 26px; font-weight: 800; font-variant-numeric: tabular-nums; letter-spacing: -.02em; }
  .tile .l { font-size: 11px; font-weight: 700; color: var(--muted); letter-spacing: .1em; text-transform: uppercase; margin-top: 2px; }
  .tile .d { font-size: 12px; color: var(--muted); margin-top: 2px; }

  .card { background: var(--surface); border-radius: 12px; box-shadow: var(--shadow); padding: 18px 20px; }

  #marginChart { display: block; width: 100%; height: auto; }
  .chart-legend { display: flex; gap: 18px; font-size: 12px; color: var(--muted); margin-top: 8px; flex-wrap: wrap; }
  .chart-legend .k { display: inline-flex; align-items: center; gap: 6px; }
  .swatch { width: 10px; height: 10px; border-radius: 3px; display: inline-block; }

  #tip {
    position: fixed; z-index: 50; pointer-events: none; display: none;
    background: var(--ink); color: var(--ground);
    font-size: 12.5px; line-height: 1.45; padding: 8px 11px; border-radius: 8px;
    box-shadow: 0 4px 14px rgba(0,0,0,.25); max-width: 280px;
  }
  #tip b { font-weight: 700; }

  .leader-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 12px; }
  .leader-row { display: grid; grid-template-columns: 148px 1fr 56px; align-items: center; gap: 10px; padding: 5px 0; font-size: 13.5px; }
  .leader-row .nm { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .leader-row .bar-track { background: var(--surface2); border-radius: 4px; height: 14px; position: relative; }
  .leader-row .bar { position: absolute; inset: 0 auto 0 0; border-radius: 4px; background: var(--blue); min-width: 3px; }
  .leader-row .val { text-align: right; font-weight: 700; font-variant-numeric: tabular-nums; }
  .leader-card h3 { margin: 0 0 8px; font-size: 13px; letter-spacing: .08em; text-transform: uppercase; color: var(--muted); }

  .tbl-wrap { overflow-x: auto; }
  table { border-collapse: collapse; width: 100%; font-size: 13.5px; font-variant-numeric: tabular-nums; }
  th, td { padding: 7px 10px; text-align: right; white-space: nowrap; }
  th:first-child, td:first-child, th.lft, td.lft { text-align: left; }
  thead th {
    font-size: 11px; letter-spacing: .08em; text-transform: uppercase;
    color: var(--muted); border-bottom: 2px solid var(--faint); position: sticky; top: 0;
    background: var(--surface);
  }
  tbody tr { border-bottom: 1px solid var(--chart-grid); }
  tbody tr:hover { background: var(--surface2); }
  .chip {
    display: inline-block; min-width: 20px; text-align: center;
    font-weight: 800; font-size: 12px; border-radius: 5px; padding: 2px 7px;
  }
  .chip.W { color: var(--win); background: var(--win-bg); }
  .chip.L { color: var(--loss); background: var(--loss-bg); }
  .dim { color: var(--muted); }
  .phase-lbl { font-size: 10.5px; letter-spacing: .1em; color: var(--muted); text-transform: uppercase; }

  .roster-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); gap: 12px; }
  .pcard {
    background: var(--surface); border-radius: 12px; box-shadow: var(--shadow);
    padding: 14px 16px; cursor: pointer; border: 1px solid transparent;
    transition: transform .12s ease, border-color .12s ease;
    text-align: left; font: inherit; color: inherit; width: 100%;
  }
  .pcard:hover, .pcard:focus-visible { transform: translateY(-2px); border-color: var(--blue); outline: none; }
  .pcard .top { display: flex; align-items: center; gap: 10px; }
  .jersey {
    width: 38px; height: 38px; flex: 0 0 38px; border-radius: 50%;
    background: var(--blue); color: var(--blue-ink);
    display: flex; align-items: center; justify-content: center;
    font-weight: 800; font-size: 15px;
  }
  .pcard.former .jersey { background: var(--surface2); color: var(--muted); }
  .pcard.pitcher .jersey { background: var(--crimson); color: #fff; }
  .pcard .nm { font-weight: 700; line-height: 1.2; }
  .pcard .pos { font-size: 12px; color: var(--muted); }
  .pcard .mini { display: flex; gap: 14px; margin-top: 10px; font-variant-numeric: tabular-nums; }
  .pcard .mini div { font-size: 15px; font-weight: 800; }
  .pcard .mini span { display: block; font-size: 10px; font-weight: 700; color: var(--muted); letter-spacing: .08em; }
  .spark { margin-top: 10px; display: block; width: 100%; height: 26px; }
  .former-head { grid-column: 1 / -1; font-size: 12px; font-weight: 800; letter-spacing: .1em; text-transform: uppercase; color: var(--muted); margin-top: 10px; }

  dialog#pmodal {
    border: none; border-radius: 14px; padding: 0;
    width: min(920px, calc(100vw - 32px));
    max-height: calc(100vh - 48px);
    background: var(--surface); color: var(--ink); box-shadow: var(--shadow);
  }
  dialog#pmodal::backdrop { background: rgba(8, 12, 22, .55); }
  .m-head {
    background: linear-gradient(120deg, #0051BA, #003A85);
    color: #fff; padding: 20px 24px; border-bottom: 4px solid var(--banner-gold);
    display: flex; align-items: center; gap: 14px;
  }
  .m-head .jersey { background: rgba(255,255,255,.16); color: #fff; width: 46px; height: 46px; flex-basis: 46px; font-size: 18px; }
  .m-head h3 { margin: 0; font-size: 22px; font-weight: 900; letter-spacing: -.01em; }
  .m-head .sub { font-size: 12.5px; opacity: .85; }
  .m-close {
    margin-left: auto; background: rgba(255,255,255,.14); color: #fff; border: none;
    width: 34px; height: 34px; border-radius: 50%; font-size: 17px; cursor: pointer;
  }
  .m-close:hover { background: rgba(255,255,255,.28); }
  .m-body { padding: 18px 24px 24px; overflow-y: auto; max-height: calc(100vh - 48px - 95px); }
  .m-tiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(96px, 1fr)); gap: 10px; }
  .m-tiles .tile { padding: 10px 12px 8px; }
  .m-tiles .tile .v { font-size: 21px; }
  .m-sec { margin-top: 20px; }
  .m-sec h4 { margin: 0 0 8px; font-size: 12px; letter-spacing: .09em; text-transform: uppercase; color: var(--muted); }
  #pgChart { display: block; width: 100%; height: auto; }

  footer { margin-top: 48px; color: var(--muted); font-size: 12.5px; }
  footer a { color: var(--blue); }

  @media (prefers-reduced-motion: reduce) { .pcard { transition: none; } }
</style>
</head>
<body>

<header>
  <div class="head-in">
    <div>
      <div class="eyebrow">2026 Season · NCAA Division I · Big 12</div>
      <h1>KANSAS JAYHAWKS <span class="thin">Softball</span></h1>
    </div>
    <div class="head-spacer"></div>
    <div class="record">
      <div class="num" id="recNum"></div>
      <div class="sub" id="recSub"></div>
    </div>
  </div>
</header>

<div class="wrap">

  <div class="tiles" id="teamTiles"></div>

  <section>
    <div class="sec-head">
      <h2>Season, game by game</h2>
      <span class="note">run margin · hover any bar</span>
      <div class="rule"></div>
    </div>
    <div class="card">
      <svg id="marginChart" viewBox="0 0 1000 300" role="img" aria-label="Bar chart of run margin for each game"></svg>
      <div class="chart-legend">
        <span class="k"><span class="swatch" style="background:var(--win)"></span>Win</span>
        <span class="k"><span class="swatch" style="background:var(--loss)"></span>Loss</span>
        <span class="k dim">Full results in the table below</span>
      </div>
    </div>
  </section>

  <section>
    <div class="sec-head">
      <h2>Team leaders</h2>
      <span class="note">season totals &amp; rates</span>
      <div class="rule"></div>
    </div>
    <div class="leader-grid" id="leaders"></div>
  </section>

  <section>
    <div class="sec-head">
      <h2 id="gamesHead">All games</h2>
      <div class="rule"></div>
    </div>
    <div class="card tbl-wrap" style="max-height:460px; overflow-y:auto;">
      <table id="gamesTbl">
        <thead>
          <tr><th class="lft">#</th><th class="lft">Date</th><th class="lft">Opponent</th><th class="lft"></th>
              <th>Score</th><th class="lft">Innings (KU-opp)</th><th class="lft">Top Jayhawk</th><th class="lft"></th></tr>
        </thead>
        <tbody></tbody>
      </table>
    </div>
  </section>

  <section>
    <div class="sec-head">
      <h2>Roster</h2>
      <span class="note">click a player for their game-by-game stats</span>
      <div class="rule"></div>
    </div>
    <div class="roster-grid" id="roster"></div>
  </section>

  <footer>
    Data: kuathletics.com box scores (batting + pitching) with NCAA-API results
    cross-check, via the nightly
    <a href="https://github.com/inspectorgad/ku-sb">ku-sb</a> scrape ·
    Player runs verified against final scores ·
    The Mar 1 Arkansas game has a result but no published box score ·
    Data updated __UPDATED__ UTC.
  </footer>
</div>

<div id="tip"></div>

<dialog id="pmodal" aria-label="Player detail">
  <div class="m-head">
    <div class="jersey" id="mJersey"></div>
    <div>
      <h3 id="mName"></h3>
      <div class="sub" id="mSub"></div>
    </div>
    <button class="m-close" id="mClose" aria-label="Close">✕</button>
  </div>
  <div class="m-body">
    <div class="m-tiles" id="mTiles"></div>
    <div class="m-sec">
      <h4 id="pgTitle"></h4>
      <svg id="pgChart" viewBox="0 0 1000 190" role="img" aria-label="Bar chart of per-game production"></svg>
    </div>
    <div class="m-sec" id="batSec">
      <h4>Batting log</h4>
      <div class="tbl-wrap">
        <table id="batTbl">
          <thead><tr>
            <th class="lft">Date</th><th class="lft">Opponent</th><th class="lft"></th>
            <th>AB</th><th>R</th><th>H</th><th>2B</th><th>3B</th><th>HR</th>
            <th>RBI</th><th>BB</th><th>SO</th><th>HBP</th><th>SB</th>
          </tr></thead>
          <tbody></tbody>
        </table>
      </div>
    </div>
    <div class="m-sec" id="pitSec" style="display:none">
      <h4>Pitching log</h4>
      <div class="tbl-wrap">
        <table id="pitTbl">
          <thead><tr>
            <th class="lft">Date</th><th class="lft">Opponent</th><th class="lft"></th><th class="lft">Dec</th>
            <th>IP</th><th>H</th><th>R</th><th>ER</th><th>BB</th><th>SO</th><th>HR</th>
          </tr></thead>
          <tbody></tbody>
        </table>
      </div>
    </div>
  </div>
</dialog>

<script>
const DATA = __DATA__;

// ---------- derived data ----------
const games = DATA.games.filter(g => g.teamScore != null);
games.sort((a, b) => a.date < b.date ? -1 : 1);

const B12 = new Set(["arizona state","byu","baylor","houston","iowa state","oklahoma state","texas tech","ucf","utah"]);
function phaseOf(g) {
  if (g.date >= "2026-05-10" && g.date <= "2026-05-31") return "NCAA Regional";
  if (g.date >= "2026-05-06" && g.date <= "2026-05-09") return "Big 12 Tourney";
  const opp = g.opponent.toLowerCase().replace(/\s*\(g\d\)$/, "");
  return B12.has(opp) ? "Big 12" : "Non-conference";
}
const fmtDate = iso => {
  const [y, m, d] = iso.split("-").map(Number);
  return ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"][m-1] + " " + d;
};
const fAvg = v => (v ? v.toFixed(3).replace(/^0/, "") : ".000");
const f1 = v => v.toFixed(1);
const f2 = v => v.toFixed(2);
const ip = outs => Math.floor(outs / 3) + "." + (outs % 3);
const era = t => t.outs ? t.er * 21 / t.outs : 0;
const whip = t => t.outs ? (t.ha + t.bba) * 3 / t.outs : 0;
const avg = t => t.ab ? t.h / t.ab : 0;
const obp = t => { const d = t.ab + t.bb + t.hbp + t.sf; return d ? (t.h + t.bb + t.hbp) / d : 0; };
const tb = t => t.h + t.d2 + 2 * t.d3 + 3 * t.hr;
const slg = t => t.ab ? tb(t) / t.ab : 0;
const ops = t => obp(t) + slg(t);

// per-player aggregation
const players = new Map();
for (const p of DATA.players) {
  players.set(p.name, { ...p, log: [],
    t: { g:0, gs:0, ab:0, r:0, h:0, d2:0, d3:0, hr:0, rbi:0, bb:0, so:0, hbp:0, sb:0, cs:0, sf:0, sh:0 },
    pt: { app:0, outs:0, ha:0, ra:0, er:0, bba:0, ks:0, hra:0, w:0, l:0, sv:0 } });
}
games.forEach((g, gi) => {
  g.n = gi + 1;
  g.margin = g.teamScore - g.opponentScore;
  g.won = g.margin > 0;
  let top = null;
  for (const l of g.lines || []) {
    let p = players.get(l.player);
    if (!p) {
      p = { name: l.player, jerseyNumber: "", position: "", active: false, log: [],
        t: { g:0, gs:0, ab:0, r:0, h:0, d2:0, d3:0, hr:0, rbi:0, bb:0, so:0, hbp:0, sb:0, cs:0, sf:0, sh:0 },
        pt: { app:0, outs:0, ha:0, ra:0, er:0, bba:0, ks:0, hra:0, w:0, l:0, sv:0 } };
      players.set(l.player, p);
    }
    p.log.push({ g, l });
    const t = p.t;
    t.g++; t.gs += l.gs || 0; t.ab += l.ab || 0; t.r += l.r || 0; t.h += l.h || 0;
    t.d2 += l["2b"] || 0; t.d3 += l["3b"] || 0; t.hr += l.hr || 0; t.rbi += l.rbi || 0;
    t.bb += l.bb || 0; t.so += l.so || 0; t.hbp += l.hbp || 0; t.sb += l.sb || 0;
    t.cs += l.cs || 0; t.sf += l.sf || 0; t.sh += l.sh || 0;
    if (l.p) {
      const pt = p.pt;
      pt.app++; pt.outs += l.outs || 0; pt.ha += l.ha || 0; pt.ra += l.ra || 0;
      pt.er += l.er || 0; pt.bba += l.bba || 0; pt.ks += l.ks || 0; pt.hra += l.hra || 0;
      pt.w += l.w || 0; pt.l += l.l || 0; pt.sv += l.sv || 0;
    }
    const ltb = (l.h || 0) + (l["2b"] || 0) + 2 * (l["3b"] || 0) + 3 * (l.hr || 0);
    const score = ltb * 10 + (l.rbi || 0);
    if ((l.h || 0) > 0 && (!top || score > top.score)) {
      top = { name: l.player, h: l.h, ab: l.ab, hr: l.hr || 0, rbi: l.rbi || 0, score };
    }
  }
  g.top = top;
});
const played = [...players.values()].filter(p => p.t.g > 0);
const isPitcherPrimary = p => p.pt.outs > 0 && p.pt.outs >= p.t.ab;

// team totals
const team = { rf: 0, ra: 0, ab:0, h:0, d2:0, d3:0, hr:0, bb:0, hbp:0, sf:0, sb:0, outs:0, er:0, ks:0, ha:0, bba:0 };
for (const g of games) {
  team.rf += g.teamScore; team.ra += g.opponentScore;
  for (const l of g.lines || []) {
    team.ab += l.ab || 0; team.h += l.h || 0; team.d2 += l["2b"] || 0; team.d3 += l["3b"] || 0;
    team.hr += l.hr || 0; team.bb += l.bb || 0; team.hbp += l.hbp || 0; team.sf += l.sf || 0;
    team.sb += l.sb || 0;
    if (l.p) { team.outs += l.outs || 0; team.er += l.er || 0; team.ks += l.ks || 0;
               team.ha += l.ha || 0; team.bba += l.bba || 0; }
  }
}
const wins = games.filter(g => g.won).length, losses = games.length - wins;

// ---------- header + tiles ----------
document.getElementById("recNum").textContent = wins + "–" + losses;
document.getElementById("recSub").textContent = games.length + " games · NCAA Regional";
document.getElementById("gamesHead").textContent = "All " + games.length + " games";

const teamBat = { ab: team.ab, h: team.h, d2: team.d2, d3: team.d3, hr: team.hr,
                  bb: team.bb, hbp: team.hbp, sf: team.sf };
const teamPit = { outs: team.outs, er: team.er, ha: team.ha, bba: team.bba };
const tiles = [
  ["Runs / game", f1(team.rf / games.length), "opponents " + f1(team.ra / games.length), 0],
  ["Team batting avg", fAvg(avg(teamBat)), team.h + " hits", 0],
  ["Home runs", team.hr, f1(slg(teamBat)) + " team SLG", 1],
  ["Stolen bases", team.sb, team.bb + " walks drawn", 0],
  ["Team ERA", f2(era(teamPit)), ip(team.outs) + " innings", 1],
  ["Strikeouts thrown", team.ks, f2(whip(teamPit)) + " WHIP", 1],
];
document.getElementById("teamTiles").innerHTML = tiles.map(([l, v, d, alt]) =>
  `<div class="tile${alt ? " alt" : ""}"><div class="v">${v}</div><div class="l">${l}</div><div class="d">${d}</div></div>`).join("");

// ---------- tooltip ----------
const tip = document.getElementById("tip");
function showTip(html, ev) {
  tip.innerHTML = html; tip.style.display = "block";
  const w = tip.offsetWidth, h = tip.offsetHeight;
  let x = ev.clientX + 14, y = ev.clientY - h - 10;
  if (x + w > innerWidth - 8) x = ev.clientX - w - 14;
  if (y < 8) y = ev.clientY + 16;
  tip.style.left = x + "px"; tip.style.top = y + "px";
}
function hideTip() { tip.style.display = "none"; }

// ---------- margin chart ----------
(function marginChart() {
  const svg = document.getElementById("marginChart");
  const W = 1000, H = 300, padL = 34, padR = 8, padT = 26, padB = 24;
  const maxM = Math.max(...games.map(g => Math.abs(g.margin)));
  const scale = (H - padT - padB) / (2 * maxM);
  const zero = padT + maxM * scale;
  const bw = (W - padL - padR) / games.length;
  let el = "";

  for (let v = -Math.floor(maxM / 5) * 5; v <= maxM; v += 5) {
    const y = zero - v * scale;
    el += `<line x1="${padL}" x2="${W - padR}" y1="${y}" y2="${y}" stroke="var(--chart-grid)" stroke-width="1"/>`;
    el += `<text x="${padL - 6}" y="${y + 4}" text-anchor="end" font-size="11" fill="var(--muted)">${v > 0 ? "+" + v : v}</text>`;
  }
  const SHORT = { "Non-conference": "NON-CON", "Big 12": "BIG 12", "Big 12 Tourney": "B12T", "NCAA Regional": "NCAA" };
  let lastPhase = "";
  games.forEach((g, i) => {
    const ph = phaseOf(g);
    if (ph !== lastPhase) {
      const x = padL + i * bw;
      if (i > 0) el += `<line x1="${x}" x2="${x}" y1="${padT - 14}" y2="${H - padB}" stroke="var(--faint)" stroke-width="1" stroke-dasharray="3 4"/>`;
      el += `<text x="${x + 3}" y="${padT - 16}" font-size="10.5" letter-spacing="1" fill="var(--muted)">${SHORT[ph]}</text>`;
      lastPhase = ph;
    }
  });
  el += `<line x1="${padL}" x2="${W - padR}" y1="${zero}" y2="${zero}" stroke="var(--muted)" stroke-width="1.5"/>`;

  games.forEach((g, i) => {
    const x = padL + i * bw + 1.5;
    const h = Math.max(Math.abs(g.margin) * scale, 2);
    const y = g.won ? zero - h : zero;
    el += `<rect data-g="${i}" x="${x}" width="${bw - 3}" y="${y}" height="${h}" rx="3" fill="var(--${g.won ? "win" : "loss"})"/>`;
  });
  const best = games.reduce((a, b) => b.margin > a.margin ? b : a);
  const worst = games.reduce((a, b) => b.margin < a.margin ? b : a);
  for (const g of [best, worst]) {
    const i = games.indexOf(g);
    const x = padL + i * bw + bw / 2;
    const y = g.won ? zero - g.margin * scale - 6 : zero - g.margin * scale + 14;
    el += `<text x="${x}" y="${y}" text-anchor="middle" font-size="11" font-weight="700" fill="var(--ink)">${g.margin > 0 ? "+" + g.margin : g.margin}</text>`;
  }
  svg.innerHTML = el;

  svg.addEventListener("mousemove", ev => {
    const r = ev.target.closest("rect[data-g]");
    if (!r) { hideTip(); return; }
    const g = games[+r.dataset.g];
    showTip(`<b>${g.won ? "W" : "L"} ${g.teamScore}–${g.opponentScore}</b> ${g.won ? "over" : "to"} ${g.opponent}<br>
      ${fmtDate(g.date)} · ${phaseOf(g)}<br>
      <span style="opacity:.8">R/H/E: ${g.teamScore}-${g.teamHits ?? "–"}-${g.teamErrors ?? "–"} vs ${g.opponentScore}-${g.opponentHits ?? "–"}-${g.opponentErrors ?? "–"}</span><br>
      <span style="opacity:.8">Top: ${g.top ? g.top.name + " " + g.top.h + "-" + g.top.ab : "—"}</span>`, ev);
  });
  svg.addEventListener("mouseleave", hideTip);
})();

// ---------- leaders ----------
(function leaders() {
  const minAB = games.length * 2;
  const minOuts = games.length * 3;
  const cats = [
    ["Batting average (min " + minAB + " AB)", p => avg(p.t), fAvg, p => p.t.ab >= minAB],
    ["OPS (min " + minAB + " AB)", p => ops(p.t), fAvg, p => p.t.ab >= minAB],
    ["Home runs", p => p.t.hr, v => v, p => p.t.hr > 0],
    ["Runs batted in", p => p.t.rbi, v => v, p => p.t.rbi > 0],
    ["ERA (min " + Math.floor(minOuts / 3) + " IP, lower is better)", p => era(p.pt), f2, p => p.pt.outs >= minOuts, true],
    ["Strikeouts (pitching)", p => p.pt.ks, v => v, p => p.pt.ks > 0],
  ];
  document.getElementById("leaders").innerHTML = cats.map(([title, val, fmt, qual, asc]) => {
    let rows = played.filter(qual).map(p => [p, val(p)]).sort((a, b) => asc ? a[1] - b[1] : b[1] - a[1]).slice(0, 5);
    const max = Math.max(...rows.map(r => r[1]), 0.001);
    return `<div class="card leader-card"><h3>${title}</h3>` + (rows.length ? rows.map(([p, v]) =>
      `<div class="leader-row">
        <span class="nm">${p.name}</span>
        <span class="bar-track"><span class="bar" style="width:${Math.max((asc ? (max ? (max - v) / max + v / max * .25 : 1) : v / max) * 100, 5)}%"></span></span>
        <span class="val">${fmt(v)}</span>
      </div>`).join("") : '<div class="dim" style="font-size:13px">No qualifiers</div>') + `</div>`;
  }).join("");
})();

// ---------- games table ----------
(function gamesTable() {
  document.querySelector("#gamesTbl tbody").innerHTML = games.map(g => `
    <tr>
      <td class="dim">${g.n}</td>
      <td class="lft">${fmtDate(g.date)}</td>
      <td class="lft"><b>${g.opponent}</b></td>
      <td class="lft"><span class="chip ${g.won ? "W" : "L"}">${g.won ? "W" : "L"}</span></td>
      <td><b>${g.teamScore}–${g.opponentScore}</b></td>
      <td class="lft dim">${g.inningScores || ""}</td>
      <td class="lft">${g.top ? g.top.name + ' <span class="dim">' + g.top.h + "-" + g.top.ab +
        (g.top.hr ? ", " + (g.top.hr > 1 ? g.top.hr + " HR" : "HR") : "") +
        (g.top.rbi ? ", " + g.top.rbi + " RBI" : "") + "</span>" : '<span class="dim">no box score</span>'}</td>
      <td class="lft"><span class="phase-lbl">${phaseOf(g)}</span></td>
    </tr>`).join("");
})();

// ---------- roster ----------
function sparkSVG(p, w = 200, h = 26) {
  const pitcher = isPitcherPrimary(p);
  const entries = pitcher ? p.log.filter(e => e.l.p) : p.log;
  const vals = entries.map(e => pitcher ? (e.l.ks || 0) : (e.l.h || 0));
  if (!vals.length) return "";
  const max = Math.max(...vals, 1);
  const bw = w / vals.length;
  let el = "";
  vals.forEach((v, i) => {
    const bh = Math.max(v / max * (h - 2), 1.5);
    el += `<rect x="${i * bw + .5}" width="${Math.max(bw - 1.5, 1)}" y="${h - bh}" height="${bh}" rx="1" fill="var(--${pitcher ? "crimson" : "blue"})" opacity="${entries[i].g.won ? 1 : .45}"/>`;
  });
  return `<svg class="spark" viewBox="0 0 ${w} ${h}" preserveAspectRatio="none" aria-hidden="true">${el}</svg>`;
}

(function roster() {
  const host = document.getElementById("roster");
  const sorted = [...played].sort((a, b) =>
    Math.max(b.t.ab, b.pt.outs) - Math.max(a.t.ab, a.pt.outs));
  const current = sorted.filter(p => p.active);
  const former = sorted.filter(p => !p.active);
  const bench = [...players.values()].filter(p => p.t.g === 0 && p.active)
    .sort((a, b) => a.name.localeCompare(b.name));

  const card = (p, isFormer) => {
    const pitcher = isPitcherPrimary(p);
    const mini = pitcher ? `
        <div>${f2(era(p.pt))}<span>ERA</span></div>
        <div>${p.pt.w}–${p.pt.l}<span>W–L</span></div>
        <div>${p.pt.ks}<span>SO</span></div>
        <div>${ip(p.pt.outs)}<span>IP</span></div>` : `
        <div>${fAvg(avg(p.t))}<span>AVG</span></div>
        <div>${p.t.hr}<span>HR</span></div>
        <div>${p.t.rbi}<span>RBI</span></div>
        <div>${fAvg(ops(p.t))}<span>OPS</span></div>`;
    return `
    <button class="pcard${isFormer ? " former" : ""}${pitcher ? " pitcher" : ""}" data-p="${p.name.replace(/"/g, "&quot;")}">
      <div class="top">
        <span class="jersey">${p.jerseyNumber || "–"}</span>
        <span><span class="nm">${p.name}</span><br><span class="pos">${p.position || ""} · ${p.t.g} games${p.pt.app ? " · " + p.pt.app + " app" : ""}</span></span>
      </div>
      <div class="mini">${mini}</div>
      ${sparkSVG(p)}
    </button>`;
  };

  let html = current.map(p => card(p, false)).join("");
  if (bench.length) {
    html += `<div class="former-head">On the roster — no 2026 game stats</div>`;
    html += bench.map(p => `
      <div class="pcard" style="cursor:default">
        <div class="top">
          <span class="jersey">${p.jerseyNumber || "–"}</span>
          <span><span class="nm">${p.name}</span><br><span class="pos">${p.position || ""}</span></span>
        </div>
      </div>`).join("");
  }
  if (former.length) {
    html += `<div class="former-head">Former players — 2026 stats retained</div>`;
    html += former.map(p => card(p, true)).join("");
  }
  host.innerHTML = html;
  host.addEventListener("click", ev => {
    const btn = ev.target.closest(".pcard[data-p]");
    if (btn) openPlayer(btn.dataset.p);
  });
})();

// ---------- player modal ----------
const modal = document.getElementById("pmodal");
document.getElementById("mClose").addEventListener("click", () => modal.close());
modal.addEventListener("click", ev => { if (ev.target === modal) modal.close(); });

function openPlayer(name) {
  const p = players.get(name);
  if (!p || !p.log.length) return;
  const t = p.t, pt = p.pt;
  const pitcher = isPitcherPrimary(p);
  document.getElementById("mJersey").textContent = p.jerseyNumber || "–";
  document.getElementById("mName").textContent = p.name;
  document.getElementById("mSub").textContent =
    (p.position ? p.position + " · " : "") + t.g + " games (" + t.gs + " starts)" +
    (pt.app ? " · " + pt.app + " pitching app" : "") + " · " +
    (p.active ? "current roster" : "former player") + " · 2026";

  let mt = [];
  if (t.ab > 0 || !pt.app) {
    mt.push(["AVG", fAvg(avg(t))], ["OBP", fAvg(obp(t))], ["SLG", fAvg(slg(t))],
      ["HR", t.hr], ["RBI", t.rbi], ["R", t.r], ["SB", t.sb], ["BB", t.bb]);
  }
  if (pt.app) {
    mt.push(["ERA", f2(era(pt))], ["W–L", pt.w + "–" + pt.l], ["SV", pt.sv],
      ["IP", ip(pt.outs)], ["K", pt.ks], ["WHIP", f2(whip(pt))]);
  }
  document.getElementById("mTiles").innerHTML = mt.map(([l, v], i) =>
    `<div class="tile${i % 2 ? " alt" : ""}"><div class="v">${v}</div><div class="l">${l}</div></div>`).join("");

  // per-game chart: hits for batters, strikeouts for pitchers
  const entries = pitcher ? p.log.filter(e => e.l.p) : p.log;
  const val = e => pitcher ? (e.l.ks || 0) : (e.l.h || 0);
  document.getElementById("pgTitle").innerHTML = (pitcher ? "Strikeouts by appearance" : "Hits by game") +
    ' <span style="text-transform:none; letter-spacing:0; font-weight:400">· bar color = team result (<span style="color:var(--win); font-weight:700">win</span> / <span style="color:var(--loss); font-weight:700">loss</span>)</span>';
  const svg = document.getElementById("pgChart");
  const W = 1000, H = 190, padL = 30, padR = 8, padT = 18, padB = 22;
  const maxV = Math.max(...entries.map(val), 3);
  const bw = (W - padL - padR) / entries.length;
  const sy = v => padT + (1 - v / maxV) * (H - padT - padB);
  let el = "";
  const step = maxV > 8 ? 2 : 1;
  for (let v = 0; v <= maxV; v += step) {
    el += `<line x1="${padL}" x2="${W - padR}" y1="${sy(v)}" y2="${sy(v)}" stroke="var(--chart-grid)"/>`;
    el += `<text x="${padL - 5}" y="${sy(v) + 4}" text-anchor="end" font-size="11" fill="var(--muted)">${v}</text>`;
  }
  const avgV = entries.reduce((s, e) => s + val(e), 0) / entries.length;
  el += `<line x1="${padL}" x2="${W - padR}" y1="${sy(avgV)}" y2="${sy(avgV)}" stroke="var(--gold)" stroke-width="1.5" stroke-dasharray="5 4"/>`;
  el += `<text x="${W - padR}" y="${sy(avgV) - 5}" text-anchor="end" font-size="11" font-weight="700" fill="var(--gold)">avg ${f1(avgV)}</text>`;
  entries.forEach((e, i) => {
    const x = padL + i * bw + 1.5;
    const y = sy(val(e));
    el += `<rect data-i="${i}" x="${x}" width="${Math.max(bw - 3, 2)}" y="${y}" height="${H - padB - y || 1}" rx="2.5" fill="var(--${e.g.won ? "win" : "loss"})"/>`;
  });
  svg.innerHTML = el;
  svg.onmousemove = ev => {
    const r = ev.target.closest("rect[data-i]");
    if (!r) { hideTip(); return; }
    const e = entries[+r.dataset.i];
    const l = e.l;
    const batBits = `${l.h || 0}-${l.ab || 0}` +
      ((l.hr || 0) ? `, ${l.hr} HR` : (l["3b"] || 0) ? `, ${l["3b"]} 3B` : (l["2b"] || 0) ? `, ${l["2b"]} 2B` : "") +
      ((l.rbi || 0) ? `, ${l.rbi} RBI` : "") + ((l.r || 0) ? `, ${l.r} R` : "") + ((l.sb || 0) ? `, ${l.sb} SB` : "");
    const pitBits = l.p ? `${ip(l.outs || 0)} IP, ${l.er || 0} ER, ${l.ks || 0} K` +
      (l.w ? " (W)" : l.l ? " (L)" : l.sv ? " (S)" : "") : "";
    showTip(`<b>${pitcher ? (l.ks || 0) + " K" : (l.h || 0) + " H"}</b> vs ${e.g.opponent} (${e.g.won ? "W" : "L"})<br>
      ${fmtDate(e.g.date)}<br>
      <span style="opacity:.8">${pitcher && pitBits ? pitBits : batBits}</span>` +
      (pitcher || !pitBits ? "" : `<br><span style="opacity:.8">Pitched: ${pitBits}</span>`), ev);
  };
  svg.onmouseleave = hideTip;

  // batting log (hide for pure pitchers who never batted)
  const batSec = document.getElementById("batSec");
  const batted = p.log.filter(e => (e.l.ab || 0) + (e.l.h || 0) + (e.l.bb || 0) + (e.l.r || 0) + (e.l.sb || 0) > 0);
  if (batted.length) {
    batSec.style.display = "";
    document.querySelector("#batTbl tbody").innerHTML = [...batted].reverse().map(e => `
      <tr>
        <td class="lft">${fmtDate(e.g.date)}</td>
        <td class="lft">${e.g.opponent}</td>
        <td class="lft"><span class="chip ${e.g.won ? "W" : "L"}">${e.g.won ? "W" : "L"}</span></td>
        <td>${e.l.ab || 0}</td><td>${e.l.r || 0}</td><td><b>${e.l.h || 0}</b></td>
        <td>${e.l["2b"] || 0}</td><td>${e.l["3b"] || 0}</td><td>${e.l.hr || 0}</td>
        <td>${e.l.rbi || 0}</td><td>${e.l.bb || 0}</td><td>${e.l.so || 0}</td>
        <td>${e.l.hbp || 0}</td><td>${e.l.sb || 0}</td>
      </tr>`).join("");
  } else {
    batSec.style.display = "none";
  }

  // pitching log
  const pitSec = document.getElementById("pitSec");
  const pitched = p.log.filter(e => e.l.p);
  if (pitched.length) {
    pitSec.style.display = "";
    document.querySelector("#pitTbl tbody").innerHTML = [...pitched].reverse().map(e => `
      <tr>
        <td class="lft">${fmtDate(e.g.date)}</td>
        <td class="lft">${e.g.opponent}</td>
        <td class="lft"><span class="chip ${e.g.won ? "W" : "L"}">${e.g.won ? "W" : "L"}</span></td>
        <td class="lft"><b>${e.l.w ? "W" : e.l.l ? "L" : e.l.sv ? "SV" : ""}</b></td>
        <td><b>${ip(e.l.outs || 0)}</b></td><td>${e.l.ha || 0}</td><td>${e.l.ra || 0}</td>
        <td>${e.l.er || 0}</td><td>${e.l.bba || 0}</td><td>${e.l.ks || 0}</td><td>${e.l.hra || 0}</td>
      </tr>`).join("");
  } else {
    pitSec.style.display = "none";
  }

  modal.showModal();
  modal.querySelector(".m-body").scrollTop = 0;
}
</script>

</body></html>
"""

html = TEMPLATE.replace("__DATA__", data_json).replace(
    "__UPDATED__", updated.replace("T", " ").replace("Z", "")
)

os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
with open(OUT_PATH, "w") as f:
    f.write(html)
print(f"dashboard written: {OUT_PATH} ({len(html)} bytes, data updated {updated})")
