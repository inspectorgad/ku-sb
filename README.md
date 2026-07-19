# KU Softball

An Android app for following the Kansas Jayhawks Softball team: game
results with inning-by-inning line scores, per-player batting and pitching
box scores, season aggregates, and team leaderboards — automatically
updated throughout the season. Modeled on the
[ku-wbb](https://github.com/inspectorgad/ku-wbb) and
[ku-volleyball](https://github.com/inspectorgad/ku-volleyball) apps,
seeded with the Spring 2026 season.

**Status: data validation in progress.** Before any app code is written,
`scripts/probe-ku-sb.mjs` (run by the `probe-data.yml` GitHub Actions
workflow) verifies that the full 2026 season is retrievable from the NCAA
API and kuathletics.com, and commits raw evidence to `probe/`. The verdict
will be written up in DATA-VALIDATION.md.
