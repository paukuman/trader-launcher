# Delivery notes

Extra notes for each zip delivered to the owner, newest first. Each entry says what changed, what was **not** verified, how to test it,
and what to do if it breaks. `CHANGELOG.md` stays the formal list of changes; this file is the practical side.

## 0.5.0 - backup, auto update, permanent signing key (2026-10-20)

**Not verified:** nothing was compiled or run. CI is the first real build.

**One-time migration (needed because the installed app is signed with a throwaway key; the new permanent key cannot update over it)**
1. In the app you have now: Journal -> share icon -> send the CSV text to yourself (Telegram Saved Messages, WhatsApp "message yourself",
   email). This is the only way to carry the journal across; alerts and watchlist are not in that CSV.
2. Run once: `bash tools/make-keystore.sh` (needs `pkg install openjdk-17` and a logged-in `gh`). It creates the key, stores it as GitHub
   Secrets and writes `~/trader-launcher-release.jks` and `~/trader-launcher-release-password.txt`. **Copy both files to a private
   place now.** If they are lost the app can never be updated in place again.
3. Apply the zip and publish a release: `bash tools/ship.sh "feat: backup, auto update, signing" v0.5.0`
4. Uninstall the old app, install `TraderLauncher.apk` from Downloads, open Tools -> Backup & restore -> Paste (or From file) and import
   the CSV.
5. From now on every release updates in place: either the in-app banner (after you publish a tag, e.g. `ship.sh "..." v0.5.1`) or
   installing the APK from Downloads over the old one.

**Test**
- Tools -> Backup & restore -> Save backup, then Restore from that file: counts in the dialog should match.
- Tools -> App update -> Check now. With only v0.5.0 published it should say you are up to date. After publishing v0.5.1 the banner should
  appear on the next resume (or after Check now), download, and open the system installer. Android asks once to "allow installs from this
  app".
- Check the version shown in Tools matches the tag.

**Known limits**
- Auto update needs the repository (and its releases) to be public. For a private repo the check reports "No public release found".
- A tag build fails on purpose when the `KEYSTORE_BASE64` secret is missing; push builds still work (signed with a debug key).
- Debug and release builds use different signing in that case; install the release APK (`TraderLauncher.apk`), not the debug fallback,
  once the permanent key exists.

**Next candidates:** small journal features (per-setup stats, per-pair notes, screenshots), native candlestick chart with trade and alert
levels, home-screen widgets (decision pending: host widgets inside the launcher, or provide a widget for other launchers).

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
