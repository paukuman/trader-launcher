# AGENTS.md — instructions for AI assistants working on this repository

Read this whole file before changing anything. Update it in the same commit whenever you change
architecture, conventions, symbol syntax, or the delivery workflow.

## 1. Project and people

- **Trader Launcher**: an Android home-screen app (launcher) for traders. Kotlin + Jetpack Compose (Material 3).
- The owner works **only from a phone**: Termux + `git` + `gh` (GitHub CLI). CI is GitHub Actions.
  There is no desktop, no Android Studio, no local Gradle.
- Owner's language: **Indonesian (informal)**. Chat replies: Indonesian.
  Code, comments, commit messages, CHANGELOG, this file: **English**. UI strings in the app: **English** (international).

## 2. Hard constraints (do not break)

1. No Gradle wrapper is committed. CI installs Gradle 8.7 (`gradle/actions/setup-gradle`). Do not add `gradlew`.
2. Version lock: Kotlin 1.9.24 <-> Compose compiler extension 1.5.14 <-> Compose BOM 2024.06.00 <-> AGP 8.5.2 <-> compileSdk 34.
   Upgrading one requires upgrading the others together.
3. `minSdk 26` (java.time is used). `targetSdk 34`.
4. **No emoji in the UI.** Icons are VectorDrawable XML in `app/src/main/res/drawable/ic_*.xml`
   (24x24 viewport, Material path data, white fill) rendered with `Ico(R.drawable.ic_x)`.
5. TradingView has no official price API. Prices come from its **unofficial websocket** (same protocol as the npm package
   `@mathieuc/tradingview`), implemented in `TvFeed.kt`. The owner explicitly accepted this for personal use.
   Keep it isolated in `TvFeed.kt`, keep the on/off switch (`src_tv`) and the Yahoo/Binance fallbacks, and never put
   login credentials, session ids or tokens in the repo. Charts use the official embeddable widget in `ChartScreen`.
6. Never use `localStorage`-style tricks, never hardcode API keys. Only public, key-less endpoints are allowed.
7. AI sandboxes usually have **no network and no Android SDK -> you cannot compile**. Be conservative:
   explicit imports, no experimental APIs without `@OptIn`, no new dependencies unless required, small diffs.
   Never claim "build passes" unless a CI log proves it.

## 3. File map (`app/src/main/java/com/example/traderlauncher/`)

| File | Responsibility |
|---|---|
| `MainActivity.kt` | Activity (HOME launcher), `AppState`, `Root()` composable: `TopBar` (tab switcher), pager (3 pages), tools overlay, chart overlay, all top-level state and callbacks, back handling, 3 s poll loop (Binance / Yahoo / fallback) plus push updates from `TvFeed.updates`, default-launcher banner and role request |
| `HomeScreen.kt` | Page 0 (Markets): compact clock + market sessions (`ClockHeader`), category chips (All/Forex/Commodities/Crypto/Stocks/Indices), watchlist (`PairRow`), add dialog with one-tap suggestions, remove dialog |
| `JournalScreen.kt` | Page 1: trade list with All/Open/Wins/Losses filter, stats, equity curve (Canvas), `TradeDialog`, CSV export, shared form widgets (`NumField`, `Seg`, `StatBox`) |
| `ToolsScreen.kt` | Full-screen overlay opened from the top bar: position size calculator, pre-trade checklist, price source switch, quick links |
| `AppsScreen.kt` | Page 2 (Apps): search, pinned row (max 5), app grid, long-press menu: pin/info/uninstall |
| `ChartScreen.kt` | Full-screen overlay with the TradingView widget inside a WebView |
| `Market.kt` | Symbol parsing/routing + providers (Binance, Yahoo, open.er-api), priority logic in `fetch`, `fmtPrice` |
| `TvFeed.kt` | Unofficial TradingView websocket quote feed (OkHttp): connect, subscribe, parse `qsd`, heartbeat, status |
| `Data.kt` | `Trade` model, JSON (de)serialization, `Store` (SharedPreferences) |
| `Common.kt` | Colors, theme, `Ico`, `AppIcon`, app listing/launching helpers, `fmtN`, `toNum` |

Other: `.github/workflows/build.yml` (CI), `tools/ship.sh` (deliver), `CHANGELOG.md`, `AGENTS.md`.

Navigation model: **no bottom bar and no dock**. `HorizontalPager` pages = Markets / Journal / Apps, switched by swiping or by the slim
`TopBar` pill. The calculator icon in the `TopBar` opens the Tools overlay. Tap a pair -> chart overlay.
Home button while open -> `onNewIntent` -> `AppState.homeSignal++` -> closes overlays and returns to page 0.

## 4. Persistence (`Store`, SharedPreferences file `trader`)

| Key | Content |
|---|---|
| `watch` | comma-separated watchlist symbols |
| `pinned` | comma-separated package names for the pinned row on the Apps page (absent = auto-detect `TRADING_PKGS`) |
| `journal` | JSON array of `Trade` |
| `c_bal`, `c_risk` | calculator balance / risk % |
| `cat` | selected watchlist category (`Cat` enum name, empty = All) |
| `chart_engine` | `0` = TradingView embed script (default), `1` = direct widget page |
| `dl_hide` | `1` after the user dismisses the "make this your home screen" banner |

Adding a persisted field: extend `Trade` + `toJson` + `tradeFromJson` with a backward-compatible default.

## 5. Symbols and market data (`Market.kt`, `TvFeed.kt`)

Price priority per symbol: plain Binance crypto -> Binance. Everything else -> TradingView feed (when `Market.useTv`) -> Yahoo -> open.er-api.
Each `Quote` has `src` ("TradingView", "Binance", "Yahoo", "open.er-api") shown in the row subtitle. If TradingView prices never show up,
read the "Status:" line in the Tools page (it shows the last websocket error) and fix `TvFeed.kt` (origin, URL, message format).

Watchlist entries are strings:

| Form | Example | Price source | Chart symbol (`Market.tvSymbol`) |
|---|---|---|---|
| Plain crypto | `BTCUSDT` | Binance 24hr ticker | `BINANCE:BTCUSDT` |
| Forex / metals | `EURUSD`, `XAUUSD`, `XAGUSD` | Yahoo (`XAUUSD=X`, alt `GC=F`/`SI=F`), fallback open.er-api (non-metals) | `OANDA:` for G10+metals, else `FX_IDC:` |
| Alias | `SPX`, `NDX`, `DJI`, `DXY`, `USOIL`, `UKOIL`, `NATGAS`, `GER40`, `JP225`, `VIX` | Yahoo | per `ALIAS` table |
| TradingView-style | `OANDA:XAUUSD`, `IDX:BBCA`, `NASDAQ:AAPL` | Yahoo when mappable (`IDX` -> `.JK`, `NASDAQ/NYSE/AMEX/ARCA` -> plain) | as typed |
| Unknown prefix | `COINBASE:BTCUSD` | none (row shows "Chart only") | as typed |

To add an instrument alias: edit `ALIAS` (Yahoo symbol, TradingView symbol). To add a provider: add a `Route`,
extend `route()`, add a fetch function, and wire it inside `Market.fetch` (parallel `async`, fill `out[originalSymbol]`).

External response shapes you rely on (verify if something breaks):
- Binance `GET /api/v3/ticker/24hr?symbols=["BTCUSDT","ETHUSDT"]` (hosts: `data-api.binance.vision`, `api.binance.com`) ->
  JSON array of `{ "symbol": "...", "lastPrice": "123.4", "priceChangePercent": "-1.2", ... }`. One invalid symbol makes the batch return HTTP 400.
- Yahoo `GET https://query1.finance.yahoo.com/v8/finance/chart/<sym>?interval=1d&range=1d` (needs a browser-like User-Agent) ->
  `chart.result[0].meta.{regularMarketPrice, previousClose|chartPreviousClose}`. Unofficial; can change or rate-limit.
- open.er-api `GET https://open.er-api.com/v6/latest/USD` -> `{ "rates": { "EUR": 0.92, ... } }` (daily, no change %).
- TradingView websocket `wss://data.tradingview.com/socket.io/websocket` (Origin header must be a tradingview.com origin; see `TvFeed` KDoc for
  framing and messages). Subscribe with `quote_add_symbols`; updates arrive as `qsd` with `v.lp` (last price) and `v.chp` (change %);
  rejected symbols come back with `s: "error"`. Anonymous (`unauthorized_user_token`) data may be delayed for some exchanges.
- `Market.fetch` returns `Map<originalSymbol, Quote(price, changePct?)>`; a missing key means "no price".

## 6. UI conventions

- Dark green theme over a translucent gradient so the wallpaper shows through. Colors/constants live in `Common.kt` (`CardBg`, `Surface2`, `Green`, `Red`, `Muted`, `Accent`, `OnAccent`). Use `Modifier.card()` for rows/sections and `Chip` for filters.
- Compact density: row padding 9-12 dp, body text 13-15 sp, secondary text 10-11 sp. Every `AlertDialog` sets `containerColor = Surface2`.
- `Cat` (in `Market.kt`) classifies a symbol into Forex / Commodities / Crypto / Stocks / Indices via `Market.category`; extend it when adding aliases.
- Positive = `Green`, negative = `Red`. Money/price formatting via `fmtPrice`, `fmtN`, `"%+,.2f"`.
- Full-screen overlays must start with `.pointerInput(Unit) {}` to swallow touches, and use `systemBarsPadding()`.
- The launcher must never close on Back (`BackHandler` is always consumed in `Root`).
- Dialogs: `AlertDialog` with `TextButton`s. Numeric inputs: `NumField` (decimal keyboard, accepts comma).
- No new screens as separate Activities; add pages to the pager or overlays in `Root`.

## 7. How to make a change (protocol)

1. Read the relevant files fully first. Prefer editing existing code over adding abstractions.
2. Make the smallest coherent change. Keep imports explicit.
3. Add an entry under `## [Unreleased]` in `CHANGELOG.md` (Added / Changed / Fixed / Removed), in English, same commit.
4. If you touch architecture, symbols, persistence keys, or workflow, update this file too.
5. For a release: bump `versionCode` and `versionName` in `app/build.gradle.kts`, move `[Unreleased]` to `[x.y.z] - date`.

## 8. How to deliver (owner runs this in Termux)

You deliver **either** a zip of the whole project (preferred when many files changed) **or** a unified diff / full replacement files.
Then give the owner exact commands. Standard flow:

```bash
cd ~/TraderLauncher                      # the git repo
# apply the change (example with a zip in Downloads)
unzip -o ~/storage/downloads/TraderLauncher.zip -d ~/tl-new
rm -rf app/src && cp -r ~/tl-new/TraderLauncher/. .
# commit, push, wait for CI, download APK:
bash tools/ship.sh "feat(scope): short imperative summary"          # normal change
bash tools/ship.sh "feat(scope): summary" v0.3.0                    # also publishes a GitHub Release
```

Commit messages: Conventional Commits in English: `feat|fix|refactor|docs|chore|build|ci(scope): summary`
(imperative, <= 72 chars, optional body explaining why). One logical change per commit when possible.

CI (`.github/workflows/build.yml`): on push to `main`, on `v*` tags, and manually. Builds `assembleDebug`, uploads artifact
`TraderLauncher-debug` (file `TraderLauncher.apk`). On tags it also creates a GitHub Release with notes taken from `CHANGELOG.md`.

If CI fails, ask the owner to run `gh run view <id> --log-failed` and paste the output, then fix exactly that.

## 9. How to respond to the owner

- Language: informal Indonesian, short, no hype. Be honest about what is untested.
- Structure of a reply that delivers code: (1) what changed in 3-6 bullets, (2) files touched,
  (3) the exact Termux commands to apply and ship, (4) known risks / things not verified, (5) one suggested next step.
- Never say the build passes unless you saw a green CI log. Never invent API behavior: if unsure, say so and add a fallback.
- When a request conflicts with a constraint in section 2 (e.g. "scrape TradingView"), explain briefly and offer the compliant alternative.
- Don't paste huge code in chat when delivering a zip; summarize instead.

## 10. Known limitations / ideas

- Forex fallback is daily-only; Yahoo is unofficial. Real-time broker data would need the owner's own API key (store in app settings, never in git).
- Idea backlog: price alerts (foreground service + notification), widgets, per-setup stats in the journal,
  journal backup (file/Drive), drag-to-reorder watchlist, per-pair notes.
