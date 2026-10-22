# Delivery notes

Extra notes for each zip delivered to the owner, newest first. Each entry says what changed, what was **not** verified, how to test it,
and what to do if it breaks. `CHANGELOG.md` stays the formal list of changes; this file is the practical side.

## 0.6.0 - News tab: Forex Factory headlines and calendar (2026-10-22)

**Verification:** `NewsParser.kt` (the parsers) was compiled with Kotlin 1.9.24 and passed 41 checks on a JVM, but against sample markup that I
modelled on how the Forex Factory pages render. I could not see the raw HTML of the live pages, so a layout surprise is possible. The Android /
Compose code (NewsRepo, NewsUi, WebScreen, wiring) is not compiled here; CI is the first real build.

**First real test of auto update:** this is the first release after 0.5.0. Do **not** install the downloaded APK by hand. Open the app you
already have (0.5.0) -> Tools -> App update -> Check now. It should offer 0.6.0; install it from there.

**Test the News tab**
1. Swipe to News (second tab). Headlines should list stories with a coloured stripe for breaking news; tap one -> it opens in the in-app browser.
2. Calendar tab: events grouped by day in WIB, "Next high impact" card with a countdown. The line under the chips says where the data came
   from: "Forex Factory calendar" (has Actual values) or "Forex Factory weekly feed (forecast and previous only)".
3. "My pairs" keeps only currencies of your watchlist; High / Medium+ / All filter by impact.
4. If a list stays empty, the red line under the chips says why (Cloudflare block, HTTP code, layout changed). Use the "Open forexfactory.com/..."
   button to read it in the in-app browser, then send me the red message.

**Known limits**
- Forex Factory is behind Cloudflare. A plain HTTP request from a phone usually passes, but it can be challenged; the calendar then falls back to the
  official weekly JSON export, headlines have no such fallback (use the browser button).
- Their terms of service restrict automated access. Requests are cached (headlines 5 min, calendar 10 min, manual refresh at most every 20 s);
  keep it for personal use and do not shorten those intervals.
- The weekly JSON feed rate-limits aggressively (HTTP 429); the app waits and keeps showing the last data.
- Times come from Unix timestamps and are shown in WIB, whatever timezone Forex Factory uses.
- Headlines include FF's non-trading sections (entertainment, education) because the page cannot be told apart per section without the raw HTML.

**Next candidates:** native candlestick chart, small journal features, widgets (host vs own widget undecided).

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

**Fixed after first run:** `tools/make-keystore.sh` exited silently at the password step (`tr | head` under `pipefail` returns 141). The password is now
generated with `head | od | tr`. If you ever see a script stop with no message, run it with `bash -x`.

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
