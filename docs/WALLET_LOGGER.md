# Wallet Logger (research tool for Wallet payment capture)

A separate app (`walletlogger/`, applicationId `dev.yernur.moneytracker.walletlogger`) that records the notifications of one allow-listed app so the real Wallet
format can be read before any parser is written. Plan and findings: `docs/HANDOFF_WALLET_CAPTURE.md`; feature entry: `docs/FEATURES.md` "Wallet payment capture".

## What it does and does not do
- Reads title, text, big text, post time, id and the group-summary flag, **only** for allow-listed packages (default `com.google.android.apps.walletnfcrel`; unverified on the phone).
- For every other app it stores the **package name and a count only**; the notification is never read (unit-tested: `RecorderTest`). Use the "seen" list to find the bank app's real package, then add it to the allow-list.
- No INTERNET permission (checked in the built APK with `aapt2 dump permissions`). Data lives in the app's private storage until you tap Export.
- Does not touch the main app or its database.

## Using it
1. Install the pre-release APK from GitHub (tag `wallet-logger-v*`), beside the main app.
2. Play Protect may block a browser install of the APK (seen 2026-11-10 on the Nothing Phone 3a). `adb install` worked, and an adb-installed app showed **no** "Allow restricted settings" entry in App info
   (installer was `com.android.shell`); the notification access switch was available directly. Observed on that one phone; the browser-install path was not completed.
   Wireless adb: `adb pair <ip:pairing-port> <code>`, then `adb connect <ip:connect-port>` (restart the adb server if pairing fails with "protocol fault").
3. Open it, tap "Open this app's info", menu -> "Allow restricted settings" (sideloaded apps need this first on Android 13+; behaviour on Android 16/17 is unverified), then "Open notification access settings" and switch it on.
4. Pay with Wallet a few times (also try a declined one, a foreign-currency one, a refund if you can).
5. Export to a file and keep it out of git (`data/` is git-ignored). It holds real merchants and amounts.
