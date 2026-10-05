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
<link rel="icon" href="icon.svg" type="image/svg+xml">
<link rel="apple-touch-icon" href="icon-180.png">
<link rel="manifest" href="manifest.json">
<meta name="theme-color" content="#0051BA">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-title" content="KU Softball">
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

  /* "Ask about the team" — see docs/ask.js */
  /* .ask-row sets display:flex, which outranks the browser's own
     [hidden] { display: none } — without this the "key saved" row and the
     Stop button are visible from the start, both at once. */
  [hidden] { display: none !important; }
  #ask-wrap { margin-top: 24px; }
  #ask-wrap h2 { margin: 0 0 4px; }
  /* .note is scoped to .sec-head in this page, so the subtitle needs its own
     rule here or it renders at full heading weight. */
  #ask-wrap h2 .note { font-weight: 400; font-size: 13px; color: var(--muted); }
  .ask-label { font-size: 13px; color: var(--muted); }
  .ask-row { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin: 6px 0; }
  .ask-check { font-size: 12px; color: var(--muted); display: flex; gap: 6px; align-items: center; }
  #ask-key, #ask-q, #ask-model { font: inherit; color: var(--ink); background: var(--surface);
    border: 1px solid var(--faint); border-radius: 8px; padding: 7px 10px; }
  #ask-key { flex: 1 1 220px; min-width: 0; }
  #ask-model { flex: 1 1 200px; min-width: 0; max-width: 100%; text-overflow: ellipsis; }
  #ask-q { flex: 1 1 260px; min-width: 0; resize: vertical; }
  .ask-btn { font: inherit; font-size: 13px; cursor: pointer; color: var(--ink);
    background: var(--surface2); border: 1px solid var(--faint); border-radius: 8px; padding: 6px 12px; }
  .ask-btn:hover { border-color: var(--blue); }
  .ask-go { background: var(--blue); border-color: var(--blue); color: #fff; font-weight: 700; }
  .ask-go:disabled { opacity: .5; cursor: default; }
  .ask-status { font-size: 12px; color: var(--muted); flex: 1; }
  .ask-examples { display: flex; flex-wrap: wrap; gap: 6px; margin: 4px 0 8px; }
  .ask-example { font-size: 12px; padding: 4px 10px; text-align: left; }
  .ask-turn { border-top: 1px solid var(--chart-grid); padding: 10px 0; }
  .ask-q { font-weight: 700; margin-bottom: 6px; }
  .ask-a { font-size: 14px; overflow-wrap: anywhere; }
  .ask-a p, .ask-a ul, .ask-a ol { margin: 0 0 8px; }
  .ask-a ul, .ask-a ol { padding-left: 20px; }
  .ask-a h3 { font-size: 14px; margin: 8px 0 4px; }
  .ask-a pre, .ask-code pre { background: var(--surface2); border-radius: 6px; padding: 8px;
    overflow-x: auto; font-size: 12px; white-space: pre; }
  .ask-a code { background: var(--surface2); border-radius: 4px; padding: 0 4px; }
  .ask-table { overflow-x: auto; margin: 0 0 8px; }
  .ask-table th, .ask-table td { white-space: nowrap; }
  .ask-note { font-size: 11px; color: var(--muted); margin: 4px 0 0; }
  .ask-error { color: var(--loss); }
  .ask-code summary { font-size: 12px; color: var(--muted); cursor: pointer; margin-top: 6px; }
  .fineprint { font-size: 11.5px; color: var(--muted); margin: 10px 0 0; line-height: 1.5; }

  #marginChart { display: block; width: 100%; height: auto; }
  #rankTrend { display: block; width: 100%; height: auto; }
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
  /* Exhibitions sit in the same table but count toward nothing, so they read
     as a quieter tier rather than as more season. */
  /* A ranked opponent is the one thing a score alone never tells you. */
  .oppRank { color: var(--crimson); font-weight: 700; }
  tr.exhib { opacity: .62; }
  tr.exhib td:first-child { color: var(--muted); }

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
  .pcard .bio { font-size: 11.5px; color: var(--muted); margin-top: 9px; line-height: 1.5; }

  .split-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(340px, 1fr)); gap: 12px; }
  .split-card h3 { margin: 0 0 6px; font-size: 13px; letter-spacing: .08em; text-transform: uppercase; color: var(--muted); }
  .split-card table { font-size: 13px; }
  .split-card th, .split-card td { padding: 5px 7px; }
  .split-card tbody tr:last-child { border-bottom: none; }
  #inningChart { display: block; width: 100%; height: auto; }
  .hl-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(215px, 1fr)); gap: 12px; }
  .hl-card .t { font-size: 10.5px; font-weight: 700; letter-spacing: .1em; text-transform: uppercase; color: var(--muted); }
  .hl-card .v { font-size: 20px; font-weight: 800; margin-top: 4px; letter-spacing: -.01em; }
  .hl-card .d { font-size: 12px; color: var(--muted); margin-top: 4px; }
  /* How a game ended, when that is not simply "as scheduled". */
  .ending { display: block; font-size: 10px; letter-spacing: .06em; text-transform: uppercase; color: var(--muted); }

  .opp-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr)); gap: 12px; }
  .ocard .nm { font-weight: 700; line-height: 1.25; }
  .ocard .rec { font-size: 23px; font-weight: 800; margin-top: 7px; font-variant-numeric: tabular-nums; }
  .ocard .rec small { font-size: 12px; font-weight: 600; color: var(--muted); margin-left: 5px; }
  .ocard .d { font-size: 12px; color: var(--muted); margin-top: 3px; }
  .ocard.beat .rec { color: var(--win); }
  .ocard.lost .rec { color: var(--loss); }

  tr.clickable { cursor: pointer; }
  tr.clickable:focus-visible { outline: 2px solid var(--blue); outline-offset: -2px; }
  .ls-wrap { overflow-x: auto; }
  table.linescore td, table.linescore th { padding: 6px 9px; }
  table.linescore .tm { text-align: left; font-weight: 700; min-width: 150px; }
  table.linescore .tot { font-weight: 800; border-left: 2px solid var(--faint); }
  table.linescore tr.ku { background: var(--surface2); }
  .ginfo { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 10px 18px; font-size: 13px; }
  .ginfo .k { font-size: 10.5px; font-weight: 700; letter-spacing: .09em; text-transform: uppercase; color: var(--muted); }
  .scoring { list-style: none; margin: 0; padding: 0; }
  .scoring li { display: grid; grid-template-columns: 34px 1fr 54px; gap: 10px; align-items: baseline;
                padding: 6px 0; border-bottom: 1px solid var(--chart-grid); font-size: 13.5px; }
  .scoring li:last-child { border-bottom: none; }
  .scoring .inn { font-size: 11px; font-weight: 800; color: var(--muted); letter-spacing: .06em; }
  .scoring .sc { text-align: right; font-weight: 700; font-variant-numeric: tabular-nums; }
  .scoring li.them { color: var(--muted); }
  .scoring li.them .sc { color: var(--muted); }
  .m-sec .sub-h { font-size: 12px; color: var(--muted); margin: 0 0 6px; }
  .m-links { display: flex; flex-wrap: wrap; gap: 14px; font-size: 13px; margin-top: 14px; }
  .m-links a { color: var(--blue); }
  .form-row { display: flex; flex-wrap: wrap; gap: 4px; margin-bottom: 8px; }
  .form-g { font-size: 11px; font-weight: 700; border-radius: 5px; padding: 3px 6px;
            background: var(--surface2); color: var(--muted); }
  .form-g.hit { background: var(--win-bg); color: var(--win); }

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

  <section class="card" id="ask-wrap">
    <h2>Ask about the team <span class="note">— questions answered by Claude from this season's data</span></h2>
    <div id="ask-key-form">
      <label for="ask-key" class="ask-label">Your Anthropic API key</label>
      <div class="ask-row">
        <input id="ask-key" type="password" autocomplete="off" spellcheck="false" placeholder="sk-ant-…">
        <button class="ask-btn" id="ask-key-save">Save key</button>
      </div>
      <label class="ask-check"><input type="checkbox" id="ask-remember" checked> Remember on this device</label>
    </div>
    <div id="ask-key-set" class="ask-row" hidden>
      <span class="ask-label">API key saved in this browser.</span>
      <button class="ask-btn" id="ask-key-forget">Forget key</button>
      <select id="ask-model" aria-label="Model"></select>
    </div>
    <div id="ask-thread" aria-live="polite"></div>
    <div class="ask-row">
      <textarea id="ask-q" rows="2" placeholder="e.g. How did we hit against ranked opponents compared with unranked ones?"></textarea>
      <button class="ask-btn ask-go" id="ask-go">Ask</button>
      <button class="ask-btn" id="ask-stop" hidden>Stop</button>
    </div>
    <div class="ask-row"><span id="ask-status" class="ask-status" role="status"></span>
      <button class="ask-btn" id="ask-new" hidden>New conversation</button></div>
    <div class="ask-examples" id="ask-examples"></div>
    <p class="fineprint">You bring your own Anthropic API key (console.anthropic.com). It is kept
      only in this browser and sent only to Anthropic, never to this site, and the questions are
      billed to your account: usually a few cents to about 15¢ each with Claude Opus 5.5 (the
      default), less with Sonnet 5, and follow-ups within a few minutes cost less because the season
      data is cached. Claude works from the same validated box scores this page is built from, and
      computes every figure by running Python on them rather than from memory — "Show the code"
      under an answer lists exactly what it ran. It knows nothing about injuries, practice,
      line-ups or recruiting.</p>
  </section>

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

  <section id="splitsSec" hidden>
    <div class="sec-head">
      <h2>Splits</h2>
      <span class="note">the same season, cut four ways</span>
      <div class="rule"></div>
    </div>
    <div class="split-grid" id="splits"></div>
  </section>

  <section id="inningSec" hidden>
    <div class="sec-head">
      <h2>Runs by inning</h2>
      <span class="note">scored and allowed, every game added up</span>
      <div class="rule"></div>
    </div>
    <div class="card">
      <svg id="inningChart" viewBox="0 0 1000 260" role="img" aria-label="Runs scored and allowed in each inning"></svg>
      <div class="chart-legend">
        <span class="k"><span class="swatch" style="background:var(--blue)"></span>Kansas scored</span>
        <span class="k"><span class="swatch" style="background:var(--crimson)"></span>Opponents scored</span>
        <span class="k dim">Innings a team did not bat are left out of its total</span>
      </div>
    </div>
  </section>

  <section id="highlightSec" hidden>
    <div class="sec-head">
      <h2>Season highlights</h2>
      <span class="note">derived from the box scores, not written down</span>
      <div class="rule"></div>
    </div>
    <div class="hl-grid" id="highlights"></div>
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

  <section id="oppSec" hidden>
    <div class="sec-head">
      <h2>Opponents</h2>
      <span class="note">head to head · open one for their box score</span>
      <div class="rule"></div>
    </div>
    <div class="opp-grid" id="opponents"></div>
  </section>

  <section id="upcomingSec" style="display:none">
    <div class="sec-head">
      <h2 id="upcomingHead">Schedule</h2>
      <span class="note">posted games not yet played</span>
      <div class="rule"></div>
    </div>
    <div class="card tbl-wrap" style="max-height:420px; overflow-y:auto;">
      <table id="upcomingTbl">
        <thead><tr>
          <th class="lft">Date</th><th class="lft">Opponent</th><th class="lft">Site</th><th class="lft">First pitch</th>
        </tr></thead>
        <tbody></tbody>
      </table>
    </div>
  </section>

  <section id="big12Sec" style="display:none">
    <div class="sec-head">
      <h2>Big 12</h2>
      <span class="note" id="big12Note"></span>
      <div class="rule"></div>
    </div>
    <div style="display:grid; grid-template-columns: repeat(auto-fit, minmax(340px, 1fr)); gap: 12px;">
      <div class="card tbl-wrap">
        <h3 style="margin:0 0 8px; font-size:13px; letter-spacing:.08em; text-transform:uppercase; color:var(--muted)">Standings</h3>
        <table id="standingsTbl">
          <thead><tr>
            <th class="lft">Team</th><th>Conf</th><th>Pct</th><th>Overall</th><th>Poll</th><th>RPI</th>
          </tr></thead>
          <tbody></tbody>
        </table>
        <div class="dim" style="font-size:11.5px; margin-top:8px">Ordered by conference win % — not official Big 12 tiebreakers.</div>
      </div>
      <div class="card" id="rankTrendCard" style="display:none">
        <h3 style="margin:0 0 2px; font-size:13px; letter-spacing:.08em; text-transform:uppercase; color:var(--muted)">Kansas in the RPI</h3>
        <div class="dim" id="rankTrendSub" style="font-size:11.5px; margin-bottom:6px"></div>
        <svg id="rankTrend" viewBox="0 0 1000 300" role="img" aria-label="Kansas RPI rank by week"></svg>
      </div>
      <div class="card tbl-wrap" id="pollCard" style="display:none">
        <h3 id="pollTitle" style="margin:0 0 2px; font-size:13px; letter-spacing:.08em; text-transform:uppercase; color:var(--muted)"></h3>
        <div class="dim" id="pollUpdated" style="font-size:11.5px; margin-bottom:6px"></div>
        <table id="pollTbl">
          <thead><tr>
            <th class="lft">Rank</th><th class="lft">Team</th><th class="lft"></th><th>Record</th><th>Pts</th>
          </tr></thead>
          <tbody></tbody>
        </table>
      </div>
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
    Player runs verified against final scores ·<span id="noBox"></span>
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
    <div class="m-sec" id="formSec" hidden>
      <h4>Recent form</h4>
      <p class="sub-h" id="formSub"></p>
      <div class="form-row" id="formRow"></div>
    </div>
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

<dialog id="gmodal" aria-label="Game detail">
  <div class="m-head">
    <div>
      <h3 id="gTitle"></h3>
      <div class="sub" id="gSub"></div>
    </div>
    <button class="m-close" data-close="gmodal" aria-label="Close">✕</button>
  </div>
  <div class="m-body" id="gBody"></div>
</dialog>

<dialog id="omodal" aria-label="Opponent detail">
  <div class="m-head">
    <div>
      <h3 id="oTitle"></h3>
      <div class="sub" id="oSub"></div>
    </div>
    <button class="m-close" data-close="omodal" aria-label="Close">✕</button>
  </div>
  <div class="m-body" id="oBody"></div>
</dialog>

<script>
const DATA = __DATA__;

// ---------- derived data ----------
// Fall games are exhibitions that count toward no official record, so they
// are kept out of the record, the tiles, the margin chart and the leaders —
// exactly as the app does. Without this split the two fall losses turned a
// 36-21 championship season into "36-23 over 59 games" in the header.
const isExhibition = g => String(g.season || "").startsWith("Fall");
const games = DATA.games.filter(g => g.teamScore != null && !isExhibition(g));
games.sort((a, b) => a.date < b.date ? -1 : 1);
const exhibitions = DATA.games.filter(g => g.teamScore != null && isExhibition(g));
// The aggregation loop below only walks `games`, so exhibitions never get the
// two fields the games table reads off each row.
for (const g of exhibitions) {
  g.margin = g.teamScore - g.opponentScore;
  g.won = g.margin > 0;
}

// Canonical key for matching a team across sources, which spell the same
// school differently: "Iowa State" in a schedule, "Iowa St." in the standings.
// Deliberately conservative — "Utah State" must not collapse into "Utah",
// which is a different school and, in 2026, the difference between a
// conference game and a non-conference one. Mirrors teamKey() in Splits.kt
// and norm_team() in update-seed.py; the three have to agree or the app, the
// seed and this page would disagree about who Kansas played.
const teamKey = s => String(s || "")
  .replace(/\s*\(G\d\)\s*$/i, "")
  .replace(/\s*\(\d+\)\s*$/, "")
  .toLowerCase()
  .replace(/\./g, "")
  .replace(/\bstate\b/g, "st")
  .replace(/\s+/g, " ")
  .trim();
const oppName = s => String(s || "").replace(/\s*\(G\d\)\s*$/i, "").trim();
const esc = s => String(s == null ? "" : s)
  .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");

const SEASON = games.length ? games[games.length - 1].season : "";
// The conference is read off the standings rather than kept as a literal here,
// so realignment arrives with the data instead of needing an edit. The list
// this replaced had nine teams in it and was already missing Arizona.
const conference = new Set(
  (DATA.standings || [])
    .filter(s => !SEASON || s.season === SEASON)
    .map(s => teamKey(s.team)));
// Standings are computed by the scrape and can be empty early in a season;
// an empty set would silently call every game non-conference.
if (!conference.size) {
  ["arizona","arizona st","byu","baylor","houston","iowa st",
   "oklahoma st","texas tech","ucf","utah"].forEach(t => conference.add(t));
}
conference.delete(teamKey("Kansas"));
const inConference = g => conference.has(teamKey(g.opponent));
function phaseOf(g) {
  if (g.date >= "2026-05-10" && g.date <= "2026-05-31") return "NCAA Regional";
  if (g.date >= "2026-05-06" && g.date <= "2026-05-09") return "Big 12 Tourney";
  return inConference(g) ? "Big 12" : "Non-conference";
}

// How a game ended, where that is not simply "after seven innings". Softball
// stops a game when a team leads by eight after five, and 19 of 2026's 57
// ended that way. Both halves are required: without the scheduled length a
// run-rule win and a game called for weather look identical, since both are
// five innings with a winner. Mirrors GameShape.kt.
const inningsPlayed = g => String(g.inningScores || "").split(",").filter(s => s.trim()).length;
function endingPhrase(g) {
  const played = inningsPlayed(g), sched = g.scheduledInnings || 0;
  if (!played || !sched || g.teamScore == null || g.opponentScore == null) return "";
  if (played > sched) return played + " innings";
  if (played === sched) return "";
  return played >= 5 && Math.abs(g.teamScore - g.opponentScore) >= 8
    ? "Run rule · " + played + " inn"
    : "Shortened · " + played + " inn";
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
// What opposing hitters batted off her. ERA says how many of them scored;
// this says how often they hit her at all. The denominator is at-bats
// against, which is not batters faced — a walk, a hit batter and a sacrifice
// are plate appearances that are not at-bats, 240 of 1659 across 2026.
const baa = t => t.oab ? t.ha / t.oab : 0;
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
    pt: { app:0, outs:0, ha:0, ra:0, er:0, bba:0, ks:0, hra:0, w:0, l:0, sv:0, oab:0 } });
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
        pt: { app:0, outs:0, ha:0, ra:0, er:0, bba:0, ks:0, hra:0, w:0, l:0, sv:0, oab:0 } };
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
      pt.oab += l.oab || 0;
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
const team = { rf: 0, ra: 0, ab:0, h:0, d2:0, d3:0, hr:0, bb:0, hbp:0, sf:0, sb:0, outs:0, er:0, ks:0, ha:0, bba:0, oab:0 };
for (const g of games) {
  team.rf += g.teamScore; team.ra += g.opponentScore;
  for (const l of g.lines || []) {
    team.ab += l.ab || 0; team.h += l.h || 0; team.d2 += l["2b"] || 0; team.d3 += l["3b"] || 0;
    team.hr += l.hr || 0; team.bb += l.bb || 0; team.hbp += l.hbp || 0; team.sf += l.sf || 0;
    team.sb += l.sb || 0;
    if (l.p) { team.outs += l.outs || 0; team.er += l.er || 0; team.ks += l.ks || 0;
               team.ha += l.ha || 0; team.bba += l.bba || 0; team.oab += l.oab || 0; }
  }
}
const wins = games.filter(g => g.won).length, losses = games.length - wins;

// ---------- header + tiles ----------
document.getElementById("recNum").textContent = wins + "–" + losses;
document.getElementById("recSub").textContent =
  games.length + " games · NCAA Regional" +
  (exhibitions.length
    ? " · plus " + exhibitions.length + " fall exhibition" +
      (exhibitions.length === 1 ? "" : "s")
    : "");
document.getElementById("gamesHead").textContent = "All " + games.length + " games";

const teamBat = { ab: team.ab, h: team.h, d2: team.d2, d3: team.d3, hr: team.hr,
                  bb: team.bb, hbp: team.hbp, sf: team.sf };
const teamPit = { outs: team.outs, er: team.er, ha: team.ha, bba: team.bba, oab: team.oab };
const tiles = [
  ["Runs / game", f1(team.rf / games.length), "opponents " + f1(team.ra / games.length), 0],
  ["Team batting avg", fAvg(avg(teamBat)), team.h + " hits", 0],
  ["Home runs", team.hr, f1(slg(teamBat)) + " team SLG", 1],
  ["Stolen bases", team.sb, team.bb + " walks drawn", 0],
  ["Team ERA", f2(era(teamPit)), ip(team.outs) + " innings", 1],
  ["Opponent batting avg", fAvg(baa(team)), team.ha + " hits allowed", 1],
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

// ---------- splits ----------
// A season average is one number and hides everything interesting. The same
// 2026 team hit .349 with a 3.04 ERA against unranked opponents and .244 with
// a 7.89 ERA against ranked ones, and only a split says so.
function aggregate(gs) {
  const b = { ab:0, h:0, d2:0, d3:0, hr:0, bb:0, hbp:0, sf:0 };
  const p = { outs:0, er:0, ha:0, bba:0 };
  let w = 0, l = 0, rf = 0, ra = 0;
  for (const g of gs) {
    rf += g.teamScore; ra += g.opponentScore;
    // A tie is neither, so w + l can be short of gs.length. Softball does play
    // them — weather shortens a game and it stands — and calling one a loss
    // would quietly misstate the record.
    if (g.teamScore > g.opponentScore) w++;
    else if (g.teamScore < g.opponentScore) l++;
    for (const ln of g.lines || []) {
      b.ab += ln.ab || 0; b.h += ln.h || 0; b.d2 += ln["2b"] || 0; b.d3 += ln["3b"] || 0;
      b.hr += ln.hr || 0; b.bb += ln.bb || 0; b.hbp += ln.hbp || 0; b.sf += ln.sf || 0;
      if (ln.p) {
        p.outs += ln.outs || 0; p.er += ln.er || 0;
        p.ha += ln.ha || 0; p.bba += ln.bba || 0;
      }
    }
  }
  return { n: gs.length, w, l, rf, ra, b, p };
}

(function splits() {
  const absM = g => Math.abs(g.margin);
  const groups = [
    ["Where", [
      ["Home", g => g.site === "H"],
      ["Away", g => g.site === "A"],
      ["Neutral", g => g.site === "N"],
    ]],
    // Counts every meeting with a conference team, the conference tournament
    // included — which the standings' own conference record excludes. The
    // label says "opponents" rather than "record" for exactly that reason.
    ["Who", [
      ["Big 12 opponents", inConference],
      ["Non-conference", g => !inConference(g)],
    ]],
    ["Quality of opponent", [
      ["vs ranked", g => (g.opponentRank || 0) > 0],
      ["vs unranked", g => !(g.opponentRank || 0)],
    ]],
    ["How close", [
      ["One-run games", g => absM(g) === 1],
      ["Decided by 2–4", g => absM(g) >= 2 && absM(g) <= 4],
      ["Decided by 5+", g => absM(g) >= 5],
    ]],
  ];

  const cards = groups.map(([title, buckets]) => {
    // Buckets no game fell into are dropped — an empty "Neutral" row early in
    // a season is noise, not information.
    const rows = buckets
      .map(([label, match]) => [label, aggregate(games.filter(match))])
      .filter(([, s]) => s.n > 0);
    if (!rows.length) return "";
    return `<div class="card split-card"><h3>${esc(title)}</h3>
      <table>
        <thead><tr><th class="lft">&nbsp;</th><th>W–L</th><th>R/G</th>
          <th>AVG</th><th>OPS</th><th>ERA</th><th>WHIP</th></tr></thead>
        <tbody>${rows.map(([label, s]) => `
          <tr><td class="lft"><b>${esc(label)}</b> <span class="dim">${s.n}</span></td>
            <td><b>${s.w}–${s.l}</b></td>
            <td>${f1(s.rf / s.n)}<span class="dim">–${f1(s.ra / s.n)}</span></td>
            <td>${fAvg(avg(s.b))}</td><td>${fAvg(ops(s.b))}</td>
            <td>${f2(era(s.p))}</td><td>${f2(whip(s.p))}</td></tr>`).join("")}
        </tbody>
      </table></div>`;
  }).filter(Boolean);

  if (!cards.length) return;
  document.getElementById("splits").innerHTML = cards.join("");
  document.getElementById("splitsSec").hidden = false;
})();

// ---------- runs by inning ----------
(function inningChart() {
  const scored = new Map(), allowed = new Map();
  for (const g of games) {
    String(g.inningScores || "").split(",").forEach((raw, i) => {
      const part = raw.trim();
      if (!part) return;
      const halves = part.split("-");
      if (halves.length !== 2) return;
      // "X" marks a half-inning never batted — the home team was already
      // ahead. It is not a zero, so it is left out rather than averaged in.
      const inn = i + 1;
      const us = parseInt(halves[0], 10), them = parseInt(halves[1], 10);
      if (Number.isFinite(us)) scored.set(inn, (scored.get(inn) || 0) + us);
      if (Number.isFinite(them)) allowed.set(inn, (allowed.get(inn) || 0) + them);
    });
  }
  const innings = [...new Set([...scored.keys(), ...allowed.keys()])].sort((a, b) => a - b);
  if (!innings.length) return;

  const W = 1000, H = 260, padL = 36, padR = 8, padT = 16, padB = 34;
  const max = Math.max(...innings.map(i => Math.max(scored.get(i) || 0, allowed.get(i) || 0)));
  const plot = H - padT - padB;
  const slot = (W - padL - padR) / innings.length;
  const bw = Math.min(slot * 0.36, 44);
  let el = "";
  for (let t = 0; t <= 4; t++) {
    const v = Math.round(max * t / 4), y = padT + plot - (max ? v / max * plot : 0);
    el += `<line x1="${padL}" x2="${W - padR}" y1="${y}" y2="${y}" stroke="var(--chart-grid)" stroke-width="1"/>` +
          `<text x="${padL - 6}" y="${y + 4}" text-anchor="end" font-size="11" fill="var(--muted)">${v}</text>`;
  }
  innings.forEach((inn, i) => {
    const cx = padL + slot * i + slot / 2;
    const pair = [[scored.get(inn) || 0, "blue", -1], [allowed.get(inn) || 0, "crimson", 1]];
    for (const [v, color, side] of pair) {
      const h = max ? v / max * plot : 0;
      const x = cx + (side < 0 ? -bw - 1.5 : 1.5);
      el += `<rect x="${x}" y="${padT + plot - h}" width="${bw}" height="${h}" rx="2" fill="var(--${color})"/>`;
      if (v) el += `<text x="${x + bw / 2}" y="${padT + plot - h - 4}" text-anchor="middle" font-size="10.5" fill="var(--muted)">${v}</text>`;
    }
    el += `<text x="${cx}" y="${H - padB + 17}" text-anchor="middle" font-size="12" fill="var(--muted)">${inn}</text>`;
  });
  el += `<text x="${W / 2}" y="${H - 4}" text-anchor="middle" font-size="11" fill="var(--muted)">Inning</text>`;
  document.getElementById("inningChart").innerHTML = el;
  document.getElementById("inningSec").hidden = false;
})();

// ---------- season highlights ----------
// Each entry is derived rather than written down, and skipped when the season
// has nothing to say about it — so an empty list is the right answer in
// February rather than a screen of zeroes. Mirrors Highlights.kt.
(function highlights() {
  const where = g => (g.site === "A" ? "at " : "vs ") + oppName(g.opponent) + " · " + fmtDate(g.date);
  const best = (pred, score) => {
    let top = null;
    for (const g of games) for (const l of g.lines || []) {
      if (!pred(l)) continue;
      const s = score(l);
      if (!top || s > top.s) top = { g, l, s };
    }
    return top;
  };
  const out = [];
  const push = (t, v, d) => out.push({ t, v, d });

  const mostRuns = games.reduce((a, g) => !a || g.teamScore > a.teamScore ? g : a, null);
  if (mostRuns) push("Most runs scored", mostRuns.teamScore + " runs", where(mostRuns));

  const biggest = games.reduce((a, g) => !a || g.margin > a.margin ? g : a, null);
  if (biggest && biggest.margin > 0) {
    push("Biggest win", biggest.teamScore + "–" + biggest.opponentScore, where(biggest));
  }
  // Hits first, RBI only to break a tie — four hits is a bigger day than
  // three hits that happened to drive in more.
  const hits = best(l => (l.h || 0) > 0, l => (l.h || 0) * 100 + (l.rbi || 0));
  if (hits) push("Most hits in a game", hits.l.player + " — " + hits.l.h + "-for-" + hits.l.ab, where(hits.g));

  const rbi = best(l => (l.rbi || 0) > 0, l => l.rbi);
  if (rbi) push("Most RBI in a game", rbi.l.player + " — " + rbi.l.rbi + " RBI", where(rbi.g));

  const ks = best(l => l.p && (l.ks || 0) > 0, l => l.ks);
  if (ks) push("Most strikeouts in a game", ks.l.player + " — " + ks.l.ks + " K", where(ks.g));

  const crowd = games.filter(g => g.attendance > 0)
    .reduce((a, g) => !a || g.attendance > a.attendance ? g : a, null);
  if (crowd) push("Biggest crowd", crowd.attendance.toLocaleString("en-US"), where(crowd));

  let run = 0, bestRun = 0;
  for (const g of games) { run = g.won ? run + 1 : 0; if (run > bestRun) bestRun = run; }
  if (bestRun >= 2) push("Longest winning streak", bestRun + " straight", "");

  if (!out.length) return;
  document.getElementById("highlights").innerHTML = out.map(h =>
    `<div class="card hl-card"><div class="t">${esc(h.t)}</div>` +
    `<div class="v">${esc(h.v)}</div>` +
    (h.d ? `<div class="d">${esc(h.d)}</div>` : "") + "</div>").join("");
  document.getElementById("highlightSec").hidden = false;
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
    ["Hits", p => p.t.h, v => v, p => p.t.h > 0],
    ["Runs scored", p => p.t.r, v => v, p => p.t.r > 0],
    ["Stolen bases", p => p.t.sb, v => v, p => p.t.sb > 0],
    ["ERA (min " + Math.floor(minOuts / 3) + " IP, lower is better)", p => era(p.pt), f2, p => p.pt.outs >= minOuts, true],
    ["Strikeouts (pitching)", p => p.pt.ks, v => v, p => p.pt.ks > 0],
    ["Avg against (min " + Math.floor(minOuts / 3) + " IP, lower is better)",
     p => baa(p.pt), fAvg, p => p.pt.outs >= minOuts && p.pt.oab > 0, true],
    ["Wins", p => p.pt.w, v => v, p => p.pt.w > 0],
    ["Saves", p => p.pt.sv, v => v, p => p.pt.sv > 0],
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

// ---------- one game, in full ----------
// The page could already say what happened across a season but had no way to
// open a single game, so the scoring summary and both box scores — which the
// scrape has collected all along — had nowhere to be shown.
const gameKey = g => g.date + "|" + g.opponent;
const hasDetail = g => !!((g.lines && g.lines.length) || (g.scoring && g.scoring.length));
const allGames = [...games, ...exhibitions];
const gamesByKey = new Map(allGames.map(g => [gameKey(g), g]));

/**
 * The line score, away team on top as a scoreboard prints it.
 *
 * Which team was at home is taken from the line score itself where it can be:
 * a half-inning marked "X" is one nobody batted, and only the home team is
 * ever spared that. At a neutral-site tournament — most of February — that is
 * more reliable than the site letter, which only says it was not Lawrence.
 */
function lineScoreHTML(g) {
  const parts = String(g.inningScores || "").split(",").map(s => s.trim()).filter(Boolean);
  if (!parts.length) return "";
  const us = [], them = [];
  for (const p of parts) {
    const h = p.split("-");
    us.push((h[0] || "").trim());
    them.push((h.length > 1 ? h[1] : "").trim());
  }
  const isX = v => /^x$/i.test(v);
  const kuHome = us.some(isX) ? true : them.some(isX) ? false : g.site === "H";
  const ku = { name: "Kansas", cells: us, r: g.teamScore, h: g.teamHits, e: g.teamErrors, ku: 1 };
  const op = { name: oppName(g.opponent), cells: them, r: g.opponentScore, h: g.opponentHits, e: g.opponentErrors, ku: 0 };
  const order = kuHome ? [op, ku] : [ku, op];
  const num = v => v == null ? "–" : v;
  return `<div class="ls-wrap"><table class="linescore">
    <thead><tr><th class="tm">&nbsp;</th>${
      parts.map((_, i) => `<th>${i + 1}</th>`).join("")
    }<th class="tot">R</th><th class="tot">H</th><th class="tot">E</th></tr></thead>
    <tbody>${order.map(t => `<tr class="${t.ku ? "ku" : ""}">
      <td class="tm">${esc(t.name)}</td>
      ${t.cells.map(v => `<td>${esc(v === "" ? "–" : v)}</td>`).join("")}
      <td class="tot">${num(t.r)}</td><td class="tot">${num(t.h)}</td><td class="tot">${num(t.e)}</td>
    </tr>`).join("")}</tbody></table></div>`;
}

/** Lineup order: by slot, the starter above whoever replaced her, then the rest. */
function inOrder(lines) {
  return lines.map((l, i) => [l, i]).sort((a, b) =>
    ((a[0].spot || 0) === 0) - ((b[0].spot || 0) === 0) ||
    (a[0].spot || 0) - (b[0].spot || 0) ||
    (a[0].sub || 0) - (b[0].sub || 0) ||
    a[1] - b[1]
  ).map(x => x[0]);
}

/**
 * One side's box score. Opponent lines carry fewer columns than Kansas lines
 * — deliberately, since they exist so an opposing box score can be read, not
 * so a season rate can be computed from two games.
 */
function boxHTML(lines, who) {
  if (!lines || !lines.length) return "";
  const ordered = inOrder(lines);
  // Every pitcher shares lineup slot 10 — softball's FLEX — so a pitcher who
  // never came to the plate would otherwise sit in the batting order.
  const batters = ordered.filter(l =>
    (l.ab || 0) > 0 || (l.bb || 0) > 0 || (l.r || 0) > 0 || (l.hbp || 0) > 0 ||
    (l.sf || 0) > 0 || (l.sh || 0) > 0 || ((l.spot || 0) > 0 && (l.spot || 0) < 10 && !l.p));
  const pitchers = ordered.filter(l => l.p);
  const nm = l => (l.sub ? "&nbsp;&nbsp;" : "") + esc(l.player) +
    (l.sub ? ' <span class="dim">(sub)</span>' : "");
  const dec = l => l.w ? "W" : l.l ? "L" : l.sv ? "S" : "";
  const n = v => v || 0;

  // Double plays turned are only on Kansas lines, and only in the minority of
  // games where one happened, so the column appears when there is one to show
  // rather than standing empty in every other box score.
  const anyDP = lines.some(l => (l.dp || 0) > 0);
  // At-bats against is only recorded on Kansas pitching lines. A column of
  // zeroes beside a pitcher who faced fifteen batters would read as a fact.
  const anyAB = lines.some(l => (l.oab || 0) > 0);

  let html = "";
  if (batters.length) {
    html += `<div class="tbl-wrap"><table>
      <thead><tr><th class="lft">${esc(who)} batting</th><th class="lft">Pos</th>
        <th>AB</th><th>R</th><th>H</th><th>2B</th><th>3B</th><th>HR</th><th>RBI</th>
        <th>BB</th><th>SO</th><th>HBP</th><th>SB</th><th>PO</th><th>A</th><th>E</th>${
          anyDP ? "<th>DP</th>" : ""}</tr></thead>
      <tbody>${batters.map(l => `<tr>
        <td class="lft">${(l.spot && !l.sub) ? '<span class="dim">' + l.spot + "</span> " : ""}${nm(l)}</td>
        <td class="lft dim">${esc(l.pos || "")}</td>
        <td>${n(l.ab)}</td><td>${n(l.r)}</td><td><b>${n(l.h)}</b></td>
        <td>${n(l["2b"])}</td><td>${n(l["3b"])}</td><td>${n(l.hr)}</td><td>${n(l.rbi)}</td>
        <td>${n(l.bb)}</td><td>${n(l.so)}</td><td>${n(l.hbp)}</td><td>${n(l.sb)}</td>
        <td class="dim">${n(l.po)}</td><td class="dim">${n(l.a)}</td><td class="dim">${n(l.e)}</td>${
          anyDP ? `<td class="dim">${n(l.dp)}</td>` : ""}
      </tr>`).join("")}</tbody></table></div>`;
  }
  if (pitchers.length) {
    html += `<div class="tbl-wrap" style="margin-top:10px"><table>
      <thead><tr><th class="lft">${esc(who)} pitching</th><th class="lft">Dec</th>
        <th>IP</th>${anyAB ? "<th>AB</th>" : ""}<th>H</th><th>R</th><th>ER</th><th>BB</th>
        <th>SO</th><th>HR</th><th>BF</th><th>NP</th></tr></thead>
      <tbody>${pitchers.map(l => `<tr>
        <td class="lft">${esc(l.player)}</td><td class="lft"><b>${dec(l)}</b></td>
        <td>${ip(n(l.outs))}</td>${anyAB ? `<td class="dim">${n(l.oab)}</td>` : ""}<td>${n(l.ha)}</td><td>${n(l.ra)}</td><td>${n(l.er)}</td>
        <td>${n(l.bba)}</td><td><b>${n(l.ks)}</b></td><td>${n(l.hra)}</td>
        <td class="dim">${n(l.bf)}</td><td class="dim">${n(l.np)}</td>
      </tr>`).join("")}</tbody></table></div>`;
  }
  return html;
}

function scoringHTML(g) {
  const plays = g.scoring || [];
  if (!plays.length) return "";
  const suffix = i => i === 1 ? "st" : i === 2 ? "nd" : i === 3 ? "rd" : "th";
  let last = null;
  return `<ul class="scoring">${plays.map(p => {
    const label = p.inn === last ? "" : p.inn + suffix(p.inn);
    last = p.inn;
    return `<li class="${p.ku ? "" : "them"}"><span class="inn">${label}</span>` +
      `<span>${esc(p.text)}</span>` +
      `<span class="sc">${p.us}–${p.them}</span></li>`;
  }).join("")}</ul>`;
}

const gmodal = document.getElementById("gmodal");
const omodal = document.getElementById("omodal");
document.addEventListener("click", ev => {
  const b = ev.target.closest("[data-close]");
  if (b) document.getElementById(b.dataset.close).close();
});

function openGame(key) {
  const g = gamesByKey.get(key);
  if (!g) return;
  const won = g.teamScore > g.opponentScore, tied = g.teamScore === g.opponentScore;
  document.getElementById("gTitle").textContent =
    (g.site === "A" ? "at " : "vs ") + oppName(g.opponent) + "  " +
    (tied ? "T" : won ? "W" : "L") + " " + g.teamScore + "–" + g.opponentScore;
  document.getElementById("gSub").textContent = [
    fmtDate(g.date) + ", " + g.date.slice(0, 4),
    isExhibition(g) ? g.season + " exhibition" : phaseOf(g),
    endingPhrase(g),
    g.event,
  ].filter(Boolean).join(" · ");

  const info = [
    ["Venue", g.stadium || g.venue],
    ["Attendance", g.attendance ? g.attendance.toLocaleString("en-US") : ""],
    ["First pitch", g.firstPitch],
    ["Duration", g.duration],
    ["Weather", g.weather],
    ["Opponent that day", [g.opponentRank ? "#" + g.opponentRank : "", g.opponentRecord].filter(Boolean).join(" ")],
    // A position nobody worked is stored as "0", not blank. The scraper now
    // drops those, but a seed written before that fix still carries them.
    ["Umpires", String(g.umpires || "").split(" · ").filter(s => !/:\s*0$/.test(s)).join(" · ")],
  ].filter(([, v]) => v);
  const links = [
    g.boxScoreUrl ? `<a href="${esc(g.boxScoreUrl)}" target="_blank" rel="noopener">Official box score</a>` : "",
    g.pdfUrl ? `<a href="${esc(g.pdfUrl)}" target="_blank" rel="noopener">PDF box score</a>` : "",
  ].filter(Boolean);

  const sec = (title, body, note) => body
    ? `<div class="m-sec"><h4>${esc(title)}</h4>${note ? `<p class="sub-h">${esc(note)}</p>` : ""}${body}</div>`
    : "";

  document.getElementById("gBody").innerHTML =
    (lineScoreHTML(g) ? `<div class="m-sec" style="margin-top:0">${lineScoreHTML(g)}</div>` : "") +
    (info.length ? `<div class="m-sec"><h4>Game information</h4><div class="ginfo">${
      info.map(([k, v]) => `<div><div class="k">${esc(k)}</div><div>${esc(v)}</div></div>`).join("")
    }</div></div>` : "") +
    sec("How the runs scored", scoringHTML(g)) +
    sec("Kansas", boxHTML(g.lines, "Kansas")) +
    sec(oppName(g.opponent), boxHTML(g.opponentLines, oppName(g.opponent)),
        // Worth saying once per box score rather than leaving a reader to
        // wonder why the errors do not add up.
        (g.opponentErrors || 0) > (g.opponentLines || []).reduce((a, l) => a + (l.e || 0), 0)
          ? "Softball charges some errors to the team rather than to a fielder, so these can sum to less than the line score."
          : "") +
    (links.length ? `<div class="m-links">${links.join("")}</div>` : "");
  gmodal.showModal();
}

// Which games have a result but no published box score, named rather than
// hardcoded — the note used to cite a March game whose box score has since
// been posted, while the two it describes today are September exhibitions.
(function missingBoxScores() {
  const gaps = allGames.filter(g => !hasDetail(g));
  if (!gaps.length) return;
  // The raw name, not oppName: stripping "(G2)" would list both halves of a
  // doubleheader as the same game twice.
  const names = gaps.map(g => fmtDate(g.date) + " " + g.opponent);
  document.getElementById("noBox").textContent =
    " No published box score for " +
    (names.length > 2 ? names.length + " games (" + names.join(", ") + ")" : names.join(" and ")) +
    " ·";
})();

// ---------- opponents ----------
// Softball is played in weekend series, so a head-to-head here is a real one:
// a team is met two or three times in three days rather than once a year. The
// halves of a doubleheader fold back together — "Baylor (G2)" is still Baylor.
(function opponents() {
  const met = new Map();
  for (const g of games) {
    const k = teamKey(g.opponent);
    if (!met.has(k)) met.set(k, []);
    met.get(k).push(g);
  }
  if (!met.size) return;

  const records = [...met.values()].map(gs => ({
    name: oppName(gs[0].opponent),
    key: teamKey(gs[0].opponent),
    gs,
    n: gs.length,
    w: gs.filter(g => g.teamScore > g.opponentScore).length,
    l: gs.filter(g => g.teamScore < g.opponentScore).length,
    rf: gs.reduce((a, g) => a + g.teamScore, 0),
    ra: gs.reduce((a, g) => a + g.opponentScore, 0),
    // Lowest rank number is the best; 0 means unranked, so it is never a
    // candidate for "best" however often it appears.
    bestRank: gs.reduce((a, g) => (g.opponentRank > 0 && (!a || g.opponentRank < a)) ? g.opponentRank : a, 0),
    last: gs.reduce((a, g) => g.date > a ? g.date : a, ""),
  })).sort((a, b) => b.n - a.n || b.w - a.w || a.name.localeCompare(b.name));

  const byKey = new Map(records.map(r => [r.key, r]));
  document.getElementById("opponents").innerHTML = records.map(r => `
    <button class="pcard ocard${r.w > r.l ? " beat" : r.l > r.w ? " lost" : ""}" data-o="${esc(r.key)}">
      <div class="nm">${r.bestRank ? '<span class="oppRank">#' + r.bestRank + "</span> " : ""}${esc(r.name)}</div>
      <div class="rec">${r.w}–${r.l}<small>${r.n} game${r.n === 1 ? "" : "s"}</small></div>
      <div class="d">${r.rf}–${r.ra} runs · ${inConference(r.gs[0]) ? "Big 12" : "Non-conference"}</div>
      <div class="d">last met ${fmtDate(r.last)}</div>
    </button>`).join("");
  document.getElementById("oppSec").hidden = false;

  document.getElementById("opponents").addEventListener("click", ev => {
    const b = ev.target.closest(".ocard[data-o]");
    if (b) openOpponent(b.dataset.o);
  });

  // Their players, summed over the meetings — and only over the meetings,
  // which is why the counting line is shown beside every rate.
  function theirPlayers(gs) {
    const by = new Map();
    for (const g of gs) for (const l of g.opponentLines || []) {
      let t = by.get(l.player);
      if (!t) {
        t = { player: l.player, number: "", pos: "", g: 0,
              ab:0, r:0, h:0, "2b":0, "3b":0, hr:0, rbi:0, bb:0, so:0, hbp:0, sb:0,
              po:0, a:0, e:0, app:0, outs:0, ha:0, ra:0, er:0, bba:0, ks:0, hra:0,
              bf:0, np:0, w:0, l:0, sv:0 };
        by.set(l.player, t);
      }
      // The first non-blank wins: a number or a position can be missing from
      // one game's box score and present in the next.
      if (!t.number && l.number) t.number = l.number;
      if (!t.pos && l.pos) t.pos = l.pos;
      t.g++;
      for (const k of ["ab","r","h","2b","3b","hr","rbi","bb","so","hbp","sb","po","a","e",
                       "outs","ha","ra","er","bba","ks","hra","bf","np","w","l","sv"]) {
        t[k] += l[k] || 0;
      }
      if (l.p) t.app++;
    }
    return [...by.values()];
  }

  // A dialog cannot sit usefully on top of another, so opening a game from
  // here replaces this one rather than stacking. Bound once, not per opening.
  document.getElementById("oBody").addEventListener("click", ev => {
    const tr = ev.target.closest("tr[data-g]");
    if (!tr) return;
    omodal.close();
    openGame(tr.dataset.g);
  });

  function openOpponent(key) {
    const r = byKey.get(key);
    if (!r) return;
    document.getElementById("oTitle").textContent = r.name;
    document.getElementById("oSub").textContent =
      "Kansas " + r.w + "–" + r.l + " · " + r.n + " game" + (r.n === 1 ? "" : "s") +
      " · " + r.rf + "–" + r.ra + " runs" + (r.bestRank ? " · ranked #" + r.bestRank : "");

    const totals = theirPlayers(r.gs);
    const bat = totals.filter(t => t.ab > 0 || t.bb > 0 || t.r > 0)
      .sort((a, b) => b.h - a.h || b.ab - a.ab || a.player.localeCompare(b.player));
    const pit = totals.filter(t => t.app > 0).sort((a, b) => b.outs - a.outs);
    const n = v => v || 0;

    document.getElementById("oBody").innerHTML =
      `<div class="m-sec" style="margin-top:0"><h4>The meetings</h4>
        <div class="tbl-wrap"><table><thead><tr>
          <th class="lft">Date</th><th class="lft"></th><th></th><th>Score</th>
          <th class="lft">Line</th><th class="lft">Kansas' best</th></tr></thead>
        <tbody>${r.gs.slice().sort((a, b) => a.date < b.date ? -1 : 1).map(g => `
          <tr class="${hasDetail(g) ? "clickable" : ""}"${hasDetail(g) ? ` data-g="${esc(gameKey(g))}"` : ""}>
            <td class="lft">${fmtDate(g.date)}</td>
            <td class="lft dim">${g.site === "A" ? "away" : g.site === "H" ? "home" : "neutral"}</td>
            <td><span class="chip ${g.won ? "W" : "L"}">${g.won ? "W" : "L"}</span></td>
            <td><b>${g.teamScore}–${g.opponentScore}</b>${
              endingPhrase(g) ? '<span class="ending">' + endingPhrase(g) + "</span>" : ""}</td>
            <td class="lft dim">${esc(g.inningScores || "")}</td>
            <td class="lft">${g.top ? esc(g.top.name) + ' <span class="dim">' + g.top.h + "-" + g.top.ab + "</span>" : '<span class="dim">—</span>'}</td>
          </tr>`).join("")}</tbody></table></div></div>` +
      (bat.length ? `<div class="m-sec"><h4>Their batters</h4>
        <p class="sub-h">Against Kansas only — ${r.n} game${r.n === 1 ? "" : "s"}, which is why the line is shown beside the average.</p>
        <div class="tbl-wrap"><table><thead><tr>
          <th class="lft">Player</th><th class="lft">Pos</th><th>G</th><th>AB</th><th>R</th><th>H</th>
          <th>2B</th><th>3B</th><th>HR</th><th>RBI</th><th>BB</th><th>SO</th><th>SB</th><th>AVG</th>
        </tr></thead><tbody>${bat.map(t => `<tr>
          <td class="lft">${t.number ? '<span class="dim">#' + esc(t.number) + "</span> " : ""}${esc(t.player)}</td>
          <td class="lft dim">${esc(t.pos || "")}</td><td class="dim">${t.g}</td>
          <td>${n(t.ab)}</td><td>${n(t.r)}</td><td><b>${n(t.h)}</b></td>
          <td>${n(t["2b"])}</td><td>${n(t["3b"])}</td><td>${n(t.hr)}</td><td>${n(t.rbi)}</td>
          <td>${n(t.bb)}</td><td>${n(t.so)}</td><td>${n(t.sb)}</td>
          <td>${t.ab ? fAvg(t.h / t.ab) : "–"}</td></tr>`).join("")}</tbody></table></div></div>` : "") +
      (pit.length ? `<div class="m-sec"><h4>Their pitchers</h4>
        <div class="tbl-wrap"><table><thead><tr>
          <th class="lft">Player</th><th>App</th><th class="lft">W–L–S</th><th>IP</th><th>H</th>
          <th>R</th><th>ER</th><th>BB</th><th>SO</th><th>HR</th><th>ERA</th>
        </tr></thead><tbody>${pit.map(t => `<tr>
          <td class="lft">${t.number ? '<span class="dim">#' + esc(t.number) + "</span> " : ""}${esc(t.player)}</td>
          <td class="dim">${t.app}</td><td class="lft">${t.w}–${t.l}${t.sv ? "–" + t.sv : ""}</td>
          <td>${ip(n(t.outs))}</td><td>${n(t.ha)}</td><td>${n(t.ra)}</td><td>${n(t.er)}</td>
          <td>${n(t.bba)}</td><td><b>${n(t.ks)}</b></td><td>${n(t.hra)}</td>
          <td>${t.outs ? f2(t.er * 21 / t.outs) : "–"}</td></tr>`).join("")}</tbody></table></div></div>` : "");
    omodal.showModal();
  }
})();

// ---------- games table ----------
(function gamesTable() {
  // Exhibitions are listed after the season, dimmed and without a game
  // number, so the fall results stay visible without being countable.
  const rows = [...games, ...exhibitions.slice().sort((a, b) => a.date < b.date ? -1 : 1)];
  const tbody = document.querySelector("#gamesTbl tbody");
  tbody.innerHTML = rows.map(g => `
    <tr class="${isExhibition(g) ? "exhib " : ""}${hasDetail(g) ? "clickable" : ""}"${
      hasDetail(g) ? ` data-g="${esc(gameKey(g))}" tabindex="0"` : ""}>
      <td class="dim">${g.n || "–"}</td>
      <td class="lft">${fmtDate(g.date)}</td>
      <td class="lft"><b>${g.site === "A" ? "at " : "vs "}${
        g.opponentRank ? '<span class="oppRank">#' + g.opponentRank + "</span> " : ""
      }${g.opponent}</b>${
        g.opponentRecord ? ' <span class="dim">(' + g.opponentRecord + ")</span>" : ""
      }</td>
      <td class="lft"><span class="chip ${g.won ? "W" : "L"}">${g.won ? "W" : "L"}</span></td>
      <td><b>${g.teamScore}–${g.opponentScore}</b>${
        endingPhrase(g) ? '<span class="ending">' + endingPhrase(g) + "</span>" : ""
      }</td>
      <td class="lft dim">${g.inningScores || ""}</td>
      <td class="lft">${g.top ? g.top.name + ' <span class="dim">' + g.top.h + "-" + g.top.ab +
        (g.top.hr ? ", " + (g.top.hr > 1 ? g.top.hr + " HR" : "HR") : "") +
        (g.top.rbi ? ", " + g.top.rbi + " RBI" : "") + "</span>" : '<span class="dim">no box score</span>'}</td>
      <td class="lft"><span class="phase-lbl">${
        isExhibition(g) ? g.season + " exhibition" : phaseOf(g)}</span></td>
    </tr>`).join("");
  tbody.addEventListener("click", ev => {
    const tr = ev.target.closest("tr[data-g]");
    if (tr) openGame(tr.dataset.g);
  });
  tbody.addEventListener("keydown", ev => {
    if (ev.key !== "Enter" && ev.key !== " ") return;
    const tr = ev.target.closest("tr[data-g]");
    if (tr) { ev.preventDefault(); openGame(tr.dataset.g); }
  });
})();

// ---------- upcoming schedule ----------
(function upcoming() {
  const pending = (DATA.games || [])
    .filter(g => g.teamScore == null)
    .sort((a, b) => a.date < b.date ? -1 : 1);
  if (!pending.length) return;
  document.getElementById("upcomingSec").style.display = "";
  const seasons = [...new Set(pending.map(g => g.season))].sort();
  document.getElementById("upcomingHead").textContent =
    seasons.length === 1 ? `${seasons[0]} schedule` : "Upcoming schedule";
  const siteLabel = { H: "Home", A: "Away", N: "Neutral" };
  document.querySelector("#upcomingTbl tbody").innerHTML = pending.map(g => `
    <tr>
      <td class="lft">${fmtDate(g.date)}<span class="dim"> ${g.date.slice(0, 4)}</span></td>
      <td class="lft"><b>${g.site === "A" ? "at " : "vs "}${g.opponent}</b></td>
      <td class="lft dim">${siteLabel[g.site] || ""}</td>
      <td class="lft dim">${g.startTime || ""}</td>
    </tr>`).join("");
})();

// ---------- Kansas in the RPI, week by week ----------
// Neither ranking source serves a past week, so this draws whatever the
// nightly scrape has filed away since archiving started. One point is not a
// trend, so the card stays hidden until a second week lands — which means it
// appears on its own partway into a season rather than needing a change here.
(function rankTrend() {
  const season = games[games.length - 1]?.season;
  const weeks = (DATA.rankingHistory || [])
    .filter(h => h.source === "rpi" && h.season === season)
    .map(h => ({ date: h.date, ku: h.teams.find(t => t.team === "Kansas") }))
    .filter(w => w.ku && w.ku.rank)
    .sort((a, b) => a.date < b.date ? -1 : 1);
  if (weeks.length < 2) return;
  document.getElementById("rankTrendCard").style.display = "";

  const W = 1000, H = 300, padL = 42, padR = 14, padT = 22, padB = 30;
  const ranks = weeks.map(w => w.ku.rank);
  // A better rank is a smaller number, so the axis is inverted: up is better.
  // The band is padded by a tenth of its own height so the best and worst
  // weeks do not sit flat against the frame.
  const best = Math.min(...ranks), worst = Math.max(...ranks);
  const pad = Math.max(Math.round((worst - best) * 0.1), 2);
  const lo = Math.max(best - pad, 1), hi = worst + pad;
  const y = r => padT + (r - lo) / (hi - lo) * (H - padT - padB);
  const x = i => padL + (weeks.length === 1 ? 0 : i / (weeks.length - 1)) * (W - padL - padR);

  let el = "";
  const step = Math.max(Math.ceil((hi - lo) / 5), 1);
  for (let r = Math.ceil(lo / step) * step; r <= hi; r += step) {
    el += `<line x1="${padL}" x2="${W - padR}" y1="${y(r)}" y2="${y(r)}" stroke="var(--chart-grid)" stroke-width="1"/>`;
    el += `<text x="${padL - 7}" y="${y(r) + 4}" text-anchor="end" font-size="11" fill="var(--muted)">#${r}</text>`;
  }
  // Top 25 is the line that matters in softball — at large bids come from
  // around there — so it is drawn whenever the season crosses it.
  if (lo <= 25 && hi >= 25) {
    el += `<line x1="${padL}" x2="${W - padR}" y1="${y(25)}" y2="${y(25)}" stroke="var(--crimson)" stroke-width="1.5" stroke-dasharray="5 4"/>`;
    el += `<text x="${W - padR}" y="${y(25) - 6}" text-anchor="end" font-size="10.5" letter-spacing="1" fill="var(--crimson)">TOP 25</text>`;
  }
  el += `<polyline fill="none" stroke="var(--blue)" stroke-width="3" stroke-linejoin="round" points="${
    weeks.map((w, i) => `${x(i)},${y(w.ku.rank)}`).join(" ")}"/>`;
  weeks.forEach((w, i) => {
    el += `<circle cx="${x(i)}" cy="${y(w.ku.rank)}" r="5" fill="var(--blue)"><title>${
      fmtDate(w.date)}: RPI #${w.ku.rank} (${w.ku.record})</title></circle>`;
  });
  // Only the ends get a date label; a weekly season would otherwise collide.
  el += `<text x="${padL}" y="${H - 8}" font-size="11" fill="var(--muted)">${fmtDate(weeks[0].date)}</text>`;
  el += `<text x="${W - padR}" y="${H - 8}" text-anchor="end" font-size="11" fill="var(--muted)">${
    fmtDate(weeks[weeks.length - 1].date)}</text>`;
  document.getElementById("rankTrend").innerHTML = el;

  const first = weeks[0].ku.rank, last = weeks[weeks.length - 1].ku.rank;
  const move = first === last ? "level over" :
    `${first > last ? "up" : "down"} ${Math.abs(first - last)} spots over`;
  document.getElementById("rankTrendSub").textContent =
    `#${last} · ${move} ${weeks.length} weekly snapshots`;
})();

// ---------- Big 12 standings + poll ----------
(function big12() {
  const season = games[games.length - 1]?.season;
  const standings = (DATA.standings || []).filter(s => s.season === season);
  const poll = (DATA.polls || []).find(p => p.season === season);
  if (!standings.length && !poll) return;
  document.getElementById("big12Sec").style.display = "";
  document.getElementById("big12Note").textContent = season + " season";

  document.querySelector("#standingsTbl tbody").innerHTML = standings.map(s => {
    const ku = s.seo === "kansas";
    const confPct = (s.confW + s.confL) ? s.confW / (s.confW + s.confL) : 0;
    return `<tr${ku ? ' style="font-weight:800; background:var(--win-bg)"' : ""}>
      <td class="lft">${ku ? "• " : ""}${s.team}</td>
      <td>${s.confW}-${s.confL}</td>
      <td>${fAvg(confPct)}</td>
      <td>${s.overallW}-${s.overallL}</td>
      <td>${s.nationalRank ? "#" + s.nationalRank : "—"}</td>
      <td>${s.rpiRank ? "#" + s.rpiRank : "—"}</td>
    </tr>`;
  }).join("");

  if (poll && poll.rows?.length) {
    document.getElementById("pollCard").style.display = "";
    document.getElementById("pollTitle").textContent = poll.name || "National Poll";
    document.getElementById("pollUpdated").textContent = poll.updated || "";
    document.querySelector("#pollTbl tbody").innerHTML = poll.rows.map(r => `
      <tr>
        <td class="lft dim">${r.rankLabel || r.rank}</td>
        <td class="lft"${r.big12 ? ' style="font-weight:700"' : ""}>${r.team}${r.firstPlaceVotes ? ` <span class="dim">(${r.firstPlaceVotes})</span>` : ""}</td>
        <td class="lft">${r.big12 ? '<span class="chip W" style="font-size:10px">B12</span>' : ""}</td>
        <td>${r.record || ""}</td>
        <td class="dim">${r.points || ""}</td>
      </tr>`).join("");
  }
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

  // Roster-page detail, which only the current roster carries — a former
  // player simply has none, and the line is dropped rather than left blank.
  const bio = p => {
    const line1 = [p.academicYear, p.height, p.batsThrows && "B/T " + p.batsThrows].filter(Boolean);
    const line2 = [p.hometown, p.lastSchool].filter(Boolean);
    if (!line1.length && !line2.length) return "";
    return `<div class="bio">${esc(line1.join(" · "))}` +
      (line1.length && line2.length ? "<br>" : "") +
      `${esc(line2.join(" — "))}</div>`;
  };

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
      ${bio(p)}
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
        ${bio(p)}
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
      ["IP", ip(pt.outs)], ["K", pt.ks], ["WHIP", f2(whip(pt))],
      ["BAA", fAvg(baa(pt))]);
  }
  document.getElementById("mTiles").innerHTML = mt.map(([l, v], i) =>
    `<div class="tile${i % 2 ? " alt" : ""}"><div class="v">${v}</div><div class="l">${l}</div></div>`).join("");

  // Recent form. A season line says what a player did; the last ten games say
  // what she is doing, which is the question anyone actually has in May.
  const formSec = document.getElementById("formSec");
  const recent = (pitcher ? p.log.filter(e => e.l.p) : p.log).slice(-10);
  if (recent.length >= 2) {
    const sum = (k) => recent.reduce((a, e) => a + (e.l[k] || 0), 0);
    document.getElementById("formSub").textContent = pitcher
      ? "Last " + recent.length + " appearances: " +
        f2(sum("outs") ? sum("er") * 21 / sum("outs") : 0) + " ERA, " +
        ip(sum("outs")) + " IP, " + sum("ks") + " K"
      : "Last " + recent.length + " games: " +
        fAvg(sum("ab") ? sum("h") / sum("ab") : 0) + " (" + sum("h") + "-for-" + sum("ab") + "), " +
        sum("hr") + " HR, " + sum("rbi") + " RBI";
    document.getElementById("formRow").innerHTML = recent.map(e => {
      const l = e.l;
      const label = pitcher
        ? ip(l.outs || 0) + " IP, " + (l.ks || 0) + " K"
        : (l.h || 0) + "-" + (l.ab || 0) + (l.hr ? ", " + l.hr + " HR" : l.rbi ? ", " + l.rbi + " RBI" : "");
      // A pitcher who was pulled before recording an out did not throw a
      // scoreless outing, whatever her earned-run line says.
      const good = pitcher ? (l.er || 0) === 0 && (l.outs || 0) > 0 : (l.h || 0) > 0;
      return `<span class="form-g${good ? " hit" : ""}" title="${esc(fmtDate(e.g.date) + " " + oppName(e.g.opponent))}">` +
        `${esc(fmtDate(e.g.date))} <b>${esc(label)}</b></span>`;
    }).join("");
    formSec.hidden = false;
  } else {
    formSec.hidden = true;
  }

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

<script>
  // Progressive enhancement: the dashboard is a plain page without this.
  if ("serviceWorker" in navigator) {
    addEventListener("load", () => {
      navigator.serviceWorker.register("sw.js").catch(() => {});
    });
  }
</script>

<!-- "Ask about the team". A module, so it is inert on a browser that cannot
     run it, and the rest of the page is unaffected either way. -->
<script type="module" src="ask.js"></script>
</body></html>
"""

html = TEMPLATE.replace("__DATA__", data_json).replace(
    "__UPDATED__", updated.replace("T", " ").replace("Z", "")
)

os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
with open(OUT_PATH, "w") as f:
    f.write(html)
print(f"dashboard written: {OUT_PATH} ({len(html)} bytes, data updated {updated})")
