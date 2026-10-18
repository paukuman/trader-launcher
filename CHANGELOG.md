# Changelog

All notable changes to this project are recorded here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), versioning: [SemVer](https://semver.org/).
Every change MUST be added under `## [Unreleased]` in the same commit that makes it.
On release, rename `[Unreleased]` to `[x.y.z] - YYYY-MM-DD` and add a fresh empty `[Unreleased]` above it.
The CI uses the section matching the pushed tag (`vX.Y.Z`) as GitHub Release notes.

## [Unreleased]

## [0.2.0] - 2026-10-18

### Added
- Horizontal pager navigation (swipe between Watchlist, Journal, Tools) with page indicator dots.
- Persistent dock with a grid button; swipe up on the dock area (or tap the button) to open the full-screen app drawer.
- In-app TradingView chart: tap a pair to open the official TradingView Advanced Chart widget in a WebView
  (any symbol, e.g. XAUUSD, XAGUSD, indices, stocks); button to open the same symbol in the TradingView app.
- Yahoo Finance quote provider (unofficial public endpoint) for forex, metals (XAUUSD, XAGUSD, XPTUSD, XPDUSD),
  indices, oil, and stocks, with 24h change and a 15 s cache.
- Symbol syntax: plain (`BTCUSDT`, `XAUUSD`), aliases (`SPX`, `NDX`, `DJI`, `DXY`, `USOIL`, `UKOIL`, `NATGAS`, `GER40`, `JP225`, `VIX`),
  and TradingView-style `EXCHANGE:SYMBOL` (`OANDA:XAUUSD`, `IDX:BBCA`, `NASDAQ:AAPL`). Unknown prefixes become chart-only rows.
- Vector drawable icons (Material path data) for all UI icons.
- `AGENTS.md` (instructions for AI assistants), `tools/ship.sh` (commit, push, wait for CI, download APK).
- CI: release notes are extracted from this changelog for tagged builds.

### Changed
- Removed the bottom navigation bar in favor of the pager + dock layout.
- `Market.fetch` is now a `suspend` function and fetches providers in parallel.
- Forex fallback (open.er-api.com) only used when Yahoo fails and the pair is not a metal.
- Version bumped to 0.2.0 (versionCode 2).

### Removed
- All emoji used as icons.

## [0.1.0] - 2026-10-18

### Added
- Initial Trader Launcher: clock with Sydney/Tokyo/London/New York session status (in WIB), Binance live watchlist,
  trading journal (stats + CSV export), position size calculator, pre-trade checklist, trading-app dock, app drawer.
- GitHub Actions workflow building a debug APK without a Gradle wrapper.
