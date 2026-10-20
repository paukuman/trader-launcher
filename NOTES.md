# Delivery notes

Extra notes for each zip delivered to the owner, newest first. Each entry says what changed, what was **not** verified, how to test it,
and what to do if it breaks. `CHANGELOG.md` stays the formal list of changes; this file is the practical side.

## 0.4.0 - price alerts, SL/TP alerts, release build (2026-10-20)

**Not verified:** nothing was compiled or run (no Android SDK in the authoring sandbox). CI is the first real build. If it is red, send
`gh run view <id> --log-failed`.

**What to test on the phone, in this order**
1. Markets: long-press a pair, "Set price alert", pick a level a little away from the current price, close the app, wait for the
   notification. The first alert asks for notification permission on Android 13+.
2. Bell icon (top bar) -> Alerts: tap Test next to each sound. Change a sound and test again (a new notification channel is created).
3. Journal: add an open trade with an SL a few pips from the live price. You should get "Stop loss hit" and an SL HIT tag.
4. Alerts -> Background: tap Allow under battery optimisation. If alerts still arrive late with the screen off, enable Reliable mode.
5. Apps page: fast-scroll the grid (icons are preloaded now, release build is smoother than debug).
6. Chart: tap a pair. If it fails, switch the Widget / Direct chip, and try a different Private DNS (`one.one.one.one`).

**Known limits**
- Prices come from the last tick of the feed, not bid/ask. A spike between two readings can be missed.
- Without TradingView (blocked network) crypto is polled every few seconds; the poll timer does not run while the CPU sleeps, which is
  what Reliable mode is for.
- Android 14+ may show the "watching the market" notification even if you dismiss it; that is the foreground service.
- The TradingView websocket is unofficial and may stop working without notice. Binance and Yahoo are the fallbacks.

**Signing / data loss (read before touching keys)**
- CI signs the release APK with a debug key that is new on every run, so installing over an older build can fail with
  "App not installed". The only fix then is an uninstall, which **wipes the journal** (there is no backup feature yet).
- A permanent key (GitHub Secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) fixes updates for good, but the
  first switch also needs one uninstall. Export the journal (Journal -> share icon -> CSV) before that. Never commit a keystore.

**Repo visibility:** the repo is public, so the code is visible. Make it private in Settings -> Danger Zone if that is not wanted
(Actions and `ship.sh` keep working).

**Next candidates:** journal backup/restore (JSON) first, then permanent signing key, home-screen widget, economic calendar.
