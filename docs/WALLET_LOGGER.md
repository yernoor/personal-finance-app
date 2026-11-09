# Wallet Logger (research tool for Wallet payment capture)

A separate app (`walletlogger/`, applicationId `dev.yernur.moneytracker.walletlogger`) that records the notifications of one allow-listed app so the real Wallet
format can be read before any parser is written. Plan and findings: `docs/HANDOFF_WALLET_CAPTURE.md`; feature entry: `docs/FEATURES.md` "Wallet payment capture".

## What it does and does not do
- Reads title, text, big text, post time, id and the group-summary flag, **only** for allow-listed packages (default `com.google.android.apps.walletnfcrel`; unverified on the phone).
- For every other app it stores the **package name and a count only**; the notification is never read (unit-tested: `RecorderTest`). Use the "seen" list to find the bank app's real package, then add it to the allow-list.
- No INTERNET permission (checked in the built APK with `aapt2 dump permissions`; the only permission is `POST_NOTIFICATIONS`). Data lives in the app's private storage until you tap Export.
- While reading is possible (notification access granted and the "Reading" switch on) it shows a **silent** ongoing notification, "Wallet Logger is reading" (low-importance channel: no sound, no pop-up). It disappears when access is revoked or reading is paused.
  Needs the notification permission (`POST_NOTIFICATIONS`, asked on first open; on adb installs `adb shell pm grant dev.yernur.moneytracker.walletlogger android.permission.POST_NOTIFICATIONS`). Not checked on other phones.
- The "Reading" switch pauses everything: nothing is read, stored or counted while paused.
- Does not touch the main app or its database.

## Using it
1. Build and install over adb, beside the main app: `./gradlew :walletlogger:assembleDebug`, then `adb -s <serial> install -r walletlogger/build/outputs/apk/debug/walletlogger-debug.apk` (not published anywhere).
2. Play Protect may block a browser install of the APK (seen 2026-11-10 on the Nothing Phone 3a). `adb install` worked, and an adb-installed app showed **no** "Allow restricted settings" entry in App info
   (installer was `com.android.shell`); the notification access switch was available directly. Observed on that one phone; the browser-install path was not completed.
   Wireless adb: `adb pair <ip:pairing-port> <code>`, then `adb connect <ip:connect-port>` (restart the adb server if pairing fails with "protocol fault").
3. Open it, tap "Open this app's info", menu -> "Allow restricted settings" (sideloaded apps need this first on Android 13+; behaviour on Android 16/17 is unverified), then "Open notification access settings" and switch it on.
4. Pay with Wallet a few times (also try a declined one, a foreign-currency one, a refund if you can).
5. Export to a file and keep it out of git (`data/` is git-ignored). It holds real merchants and amounts.
