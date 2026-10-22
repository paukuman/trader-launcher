# Changelog

All notable changes to this project are recorded here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), versioning: [SemVer](https://semver.org/).
Every change MUST be added under `## [Unreleased]` in the same commit that makes it.
On release, rename `[Unreleased]` to `[x.y.z] - YYYY-MM-DD` and add a fresh empty `[Unreleased]` above it.
The CI uses the section matching the pushed tag (`vX.Y.Z`) as GitHub Release notes.

## [Unreleased]

## [0.6.0] - 2026-10-22

### Added

- News page (page 2 of 4, between Markets and Journal) with two tabs: Forex Factory headlines (all / breaking, impact stripe, source, age, comments) and the economic calendar (this week / next week, High / Medium+ / All, "My pairs" filter that follows the watchlist currencies, countdown to the next high-impact event, actual / forecast / previous with better / worse colour).
- Tapping a story or event opens it in an in-app browser (`WebScreen`); a button opens the whole Forex Factory page when scraping is blocked.
- Calendar sources: the Forex Factory calendar page (includes released actual values) with automatic fallback to the official weekly JSON export (forecast and previous only).
- `NewsParser.kt` is pure Kotlin (no Android classes) and was compiled and tested on a JVM against sample markup.

### Changed

- Pager pages are now Markets, News, Journal, Apps (`Pages` constants); SL/TP notifications open Journal. The tab bar shows the label only for the selected tab.

## [0.5.0] - 2026-10-20

### Added

- Backup & restore (Tools): save a JSON backup (journal, alerts, watchlist, settings) to any location with the system file picker; restore from a file or pasted text. Older journal CSV exports can be imported too (trades are added, duplicates skipped).
- In-app update from GitHub Releases: banner when a newer release exists, "Check now" and automatic check at most every 6 hours (Tools -> App update), download with progress, then the system installer.
- `tools/make-keystore.sh`: one-time creation of the permanent signing key, stored in GitHub Secrets.

### Changed

- CI passes `-Pvc` (run number + 100, always increasing) and `-Pvn` (the tag) to Gradle; tagged builds refuse to run without the permanent key. `tools/ship.sh` keeps `versionName` in `build.gradle.kts` equal to the tag.

## [0.4.0] - 2026-10-20

### Added

- Price alerts: set a level (above / below) with an optional custom message; one notification when price reaches it, then the alert can be re-armed. Long-press a pair, or use the bell in the top bar.
- SL / TP alerts: open journal trades with a stop loss or take profit notify you when price touches either level; the trade shows an SL HIT / TP HIT tag. Wrong-side levels are ignored.
- Notification settings: separate sound for price and SL/TP alerts (system ringtone picker), vibration, loud mode (alarm volume), test buttons, notification permission prompt.
- `MonitorService` (foreground, `specialUse`) keeps the price feed alive while alerts or SL/TP targets exist, stops itself when nothing is left to watch, restarts after boot. Optional reliable mode (partial wake lock) and a battery optimisation shortcut.
- Release builds: R8 minify and resource shrinking, optional real signing key from CI secrets; debug APK is still published as a fallback artifact.
- Default launcher: banner on first run and a "Home screen" section in Tools that requests the HOME role (system settings fallback).
- Top pill switcher (Markets / Journal / Apps) synced with the pager; calculator icon opens the Tools overlay.
- Markets: category filter chips (All, Forex, Commodities, Crypto, Stocks, Indices) via `Cat` / `Market.category`; selected category is persisted (`cat`).
- Markets: add dialog with one-tap suggestions per category; change-% pill and category badge on each row.
- Journal: equity curve, All/Open/Wins/Losses filter, colored side stripe per trade.
- Apps page: pinned row and compact search field; the app grid is now the third pager page.
- Tools: quick risk presets (0.5 / 1 / 2 %).

### Changed

- CI builds `assembleRelease` (artifact `TraderLauncher`) after `assembleDebug` (artifact `TraderLauncher-debug`); `tools/ship.sh` downloads the release APK.
- Version bumped to 0.4.0 (versionCode 4).
- TradingView prices now appear as soon as a tick arrives (`TvFeed.updates`, UI refresh capped at about 4/s) instead of waiting for the next poll; the poll loop runs every 3 s instead of 5 s.
- `Market.fetch` starts Binance crypto before waiting on TradingView, so crypto no longer waits for the TV first-tick timeout.
- App icons are decoded once on a background thread and cached (`loadApps`), which fixes the stutter when fast-scrolling the Apps grid.
- Chart screen: loading spinner, error message, third-party cookies enabled for the embedded widget, console errors shown, reload button and a Widget/Direct engine switch.
- Whole UI redesigned: green accent, compact density, translucent green-black gradient, rounded cards.
- All in-app text is now English; `AGENTS.md` updated accordingly.
- Tools moved from page 3 to a full-screen overlay; Apps is now page 3.
- Default watchlist now covers forex, gold, crypto, an index and a stock.

### Removed

- Bottom dock, page dots and the swipe-up app drawer overlay.

### Fixed

- Build: opt in to ExperimentalFoundationApi in MainActivity.kt (compileDebugKotlin failed in CI).

## [0.3.0] - 2026-10-18

### Added
- `TvFeed.kt`: unofficial TradingView quote feed over websocket (OkHttp), same protocol as the npm package `@mathieuc/tradingview`
  (`~m~len~m~` framing, heartbeat echo, `quote_create_session` / `quote_add_symbols`, `qsd` updates). Origin header rotates on connect failure,
  reconnect back-off after repeated failures, socket closed while the launcher is paused.
- Tools page: "Sumber harga" switch (TradingView on/off, default on) and a live connection status line for debugging.
- Each quote now carries its source (`Quote.src`); watchlist rows show "TradingView / Binance / Yahoo / Kurs harian".
- Dependency: `com.squareup.okhttp3:okhttp:4.12.0`.

### Changed
- Price priority: plain crypto -> Binance; everything else -> TradingView feed (if enabled) -> Yahoo -> open.er-api.
- Rows with an unknown exchange prefix (e.g. `COINBASE:BTCUSD`) can now get a price from the TradingView feed.
- Version bumped to 0.3.0 (versionCode 3).

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
