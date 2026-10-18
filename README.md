# Trader Launcher

Android home-screen app for traders. Swipe between pages, tap a pair for a TradingView chart.

- **Pantau**: clock (WIB) + market sessions, watchlist (crypto live from Binance; forex, metals such as XAUUSD/XAGUSD, indices, stocks via an unofficial TradingView feed with Yahoo fallback), in-app TradingView chart
- **Jurnal**: trade journal with stats and CSV export
- **Alat**: position size calculator, pre-trade checklist, quick links
- Persistent dock for trading apps; swipe up on the dock (or tap the grid button) for the app drawer

See `CHANGELOG.md` for history and `AGENTS.md` for how AI assistants should edit and ship this project.

## Build from Termux

    pkg update && pkg install git gh unzip
    termux-setup-storage
    gh auth login
    cd ~/TraderLauncher
    bash tools/ship.sh "feat: my change"        # add a tag like v0.2.0 as 2nd arg to publish a Release
