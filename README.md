# Trader Launcher

Android home-screen app for traders. Swipe between pages, tap a pair for a TradingView chart.

- **Markets**: clock (WIB) + market sessions, watchlist (crypto live from Binance; forex, metals such as XAUUSD/XAGUSD, indices, stocks via an unofficial TradingView feed with Yahoo fallback), in-app TradingView chart, category filter (Forex, Commodities, Crypto, Stocks, Indices)
- **Journal**: trade journal with stats, equity curve, filters and CSV export
- **Apps**: searchable app grid with a pinned row for trading apps
- **Alerts** (bell icon in the top bar): price alerts with custom message and sound, SL / TP alerts for open journal trades, background monitoring
- **Updates and backup** (Tools): in-app update from GitHub Releases, JSON backup and restore
- **Tools** (calculator icon in the top bar): position size calculator, pre-trade checklist, price source switch, quick links
- Swipe between the three pages or tap the pill at the top; no bottom bar

See `CHANGELOG.md` for history and `AGENTS.md` for how AI assistants should edit and ship this project.

## Build from Termux

    pkg update && pkg install git gh unzip
    termux-setup-storage
    gh auth login
    cd ~/TraderLauncher
    bash tools/ship.sh "feat: my change"        # add a tag like v0.2.0 as 2nd arg to publish a Release
