# CLAUDE.md

Personal Android expense tracker: everyday expense tracking, **no ads, no payments**, with Google Sheets sync so the data is also usable from a PC.
Single user (the repo owner). Public repo (the in-app updater reads its GitHub releases without a token). Not for Play Store distribution.

@docs/FORMAT.md
@docs/SYNC_DESIGN.md

## Identity and import
- Import file: `.mmbackup` (format in docs/FORMAT.md). Schema `user_version` was 35 at last check.
- `applicationId = "dev.yernur.moneytracker"` (owner may rename).

## Assets and sources
Do **not** copy names, icons, artwork or strings from any other app, and do not decompile or reverse-engineer APKs. The data model comes from the owner's own
backup file (docs/FORMAT.md) and the owner's description/screenshots of the UI. Assets are original (own icon artwork, own palette).

## Feature inventory (UNVERIFIED beyond the data model: confirm before building)
Known from the database: accounts (multi-currency, e.g. KZT + USD, one default account `main`), income/expense
transactions with category, optional tags, comment, date; transfers between accounts (cross-currency amounts);
categories with icon, color, usage count, optional limit (likely budgets), manual ordering; cached balances.
Not known: reports/charts, budgets UI, recurring items, widgets, search, filters, export, passcode, themes.
**First task: ask the owner to drop screenshots and a feature list into `docs/ui-references/`, then write
`docs/FEATURES.md` marking each feature must / nice / skip.** Do not invent features from memory of other apps.

## Stack (propose changes if you find a concrete reason; verify current versions, do not assume)
- Kotlin, Jetpack Compose + Material 3, single-activity, MVVM, Hilt, Coroutines/Flow
- Room (local source of truth), WorkManager (sync), Kotlin serialization
- Google Sign-In via Credential Manager + Identity Authorization; Sheets API v4 over plain HTTPS (Ktor/OkHttp)
  or the official client; pick whichever is lighter after reading current docs
- minSdk: ask the owner for the phone's Android version; targetSdk = current stable
- Money type: `Long` minor units internally (x100, matching the backup). Never `Double` for money.
  Format with `BigDecimal`/locale at the edge only.

## Architecture rules
- Entities keyed by string UID (UUID v4), same as the backup. Keep `created`/`modified` as UTC ISO-8601.
- Soft-delete locally with tombstones; sync propagates them (docs/SYNC_DESIGN.md).
- Relationships as real FKs in Room, NOT a generic edge table. The importer translates `sync_link` edges
  into FKs. Keep the three amount fields (default / real / account currency) per transaction.
- Importer (`.mmbackup` -> Room) is pure and unit-tested against `data/extracted/snapshot.json`.
- No analytics SDKs, no ads, no network calls except Google auth + Sheets/Drive, the daily GitHub update check, and one National Bank of Kazakhstan
  exchange-rate request per day (cached; owner-approved 2026-11-04).
- Secrets: no keystore, OAuth client IDs with secrets, or tokens in git. Use `local.properties`.

## Milestones (finish and verify each before the next)
0. **Verify inputs.** Run `tools/mmbackup.py inspect` on the newest file in `data/`. Compare to docs/FORMAT.md,
   update the doc for any drift (new columns/tables, changed `user_version`). Run `extract`; read
   `snapshot.json`. If `balance_diff_cached_minus_replayed` is non-zero, find out why (initial balances?
   hidden columns?) and record it in FORMAT.md before designing the balance logic.
1. Write `docs/FEATURES.md` with the owner (see above).
2. Scaffold Android project, Room schema, DAOs, DB migrations from day one.
3. `.mmbackup` importer + tests. Acceptance: imported counts, totals and per-account balances equal
   `snapshot.json` to the cent, including cross-currency transfers and removed rows excluded.
4. Core screens: accounts, add/edit transaction (fast entry is the daily habit), transfers, categories, tags, history list.
5. Reports/budgets per FEATURES.md: period model, charts, projections (run-rate + 3-month average), limit warnings + notifications.
5a. Added 2026-11-03 (balance overwrite, daily reminder, shared Charts/History header, grouped History, swipe between tabs, pinned Add button, ...): list in docs/FEATURES.md "Session 2026-11-03".
5b. UX polish (owner requests, 2026-10-25):
   - Navigation animations: no disappearing/abrupt transitions; polished enter/exit and shared-element feel. DONE (overlapping transitions).
   - Customizable bottom navbar (which tabs, order) and configurable Home layout (default: monthly totals cards). DONE (More screen).
   - History opens on the **daily** total by default and zooms out to week / month / year (reuses M5's period model). DONE (setting in More).
   - Completely redrawn category/account icons: original artwork, driven by the Material 3 scheme so they follow the
     user's accent color, but not muted: keep hues clearly distinguishable from each other.
6. Google sign-in + Sheet creation + initial full push. Sheet layout must keep the `Transactions` column order. BUILT and unit-tested
   (fake network, real-backup totals vs snapshot); the live Google round trip needs the owner's Cloud OAuth client: docs/GOOGLE_SETUP.md.
7. Two-way sync + Apps Script `onEdit` stamping (`/sheets/Code.gs`) + conflict log. PARTLY BUILT: "Sync now" and a weekly WorkManager job
   (pull, then push; phone edits since the last sync win; first sync pushes only). Apps Script and the conflict log are not built. See docs/SYNC_DESIGN.md.
8. Polish, backup/restore of the local DB, release APK signed with the owner's local keystore (sideload). Backup/restore to a file BUILT in 0.4.0 (with app lock, widgets, wealth tools: see docs/FEATURES.md "Version 0.4.0 at a glance").

## Verification rules (the owner values checkable work)
- Every claim about the backup format or Google APIs is checked against the actual file or current docs.
  If you cannot verify, say "unverified" in code comments and docs; do not present guesses as facts.
- Prefer numbers computed independently (SQL straight on the extracted DB) over numbers computed by the code under test.
- Run unit tests and a debug build before declaring a milestone done. If the Android SDK or emulator is not
  available in your environment, say so plainly and list what you could not run.
- Money tests: use the real extracted data, plus edge cases (0.19, negative, USD accounts, cross-currency transfer).

## Git workflow (owner's rule)
- Every new feature or fix gets its own commit, even when several go onto the same feature branch and are pushed together.
  The owner squashes them later, so the branch history must keep them separate. Never lump a session's work into one commit.
- Commit each one when it is done and its tests pass, one semantic-style line each (see the global instructions).
- Tests and docs for a change go in that change's commit, not a separate one.

## Code style (owner's preferences)
- Minimal, readable code over clever abstractions; data-driven config over scattered branching.
- Explicit `!= null` checks in Kotlin where it clarifies; small functions; no speculative generality.
- Python tooling stays stdlib-only unless there is a clear need.

## Non-goals
- Writing `.mmbackup` files (the integrity hash algorithm is unknown; importing is enough).
- Bank notification parsing for Kaspi/Halyk/Freedom/ForteBank (owner judged it brittle; manual entry is the workflow).
  Narrow exception, owner-approved 2026-11-10: reading Google Wallet payment notifications (docs/FEATURES.md "Wallet payment capture").
- Multi-user, accounts, or any server component.

## Commands
```bash
python tools/mmbackup.py inspect data/<file>.mmbackup
python tools/mmbackup.py extract data/<file>.mmbackup -o data/extracted
```
```bash
# Project JDK: 25 (owner's choice). Gradle 9.6 runs on JDK 17..25 only (Gradle docs), so brew's default `openjdk` (27) will NOT work.
brew install openjdk@25   # once
export JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH
./gradlew testDebugUnitTest assembleDebug     # APK: app/build/outputs/apk/debug/app-debug.apk
```
Toolchain (this Mac): SDK at /opt/homebrew/share/android-commandlinetools (path in git-ignored `local.properties`),
platforms android-36 + android-37.0 (compileSdk 37 is required by current Compose libs; target/min stay 36),
AGP 9.4.1, Kotlin 2.4.21, KSP 2.3.12, Room 2.8.5, Compose BOM 2026.09.00, Navigation 2.10.2. DI is hand-wired
(`AppContainer`), not Hilt: no concrete need yet and it adds AGP 9/KSP risk. WorkManager runs the weekly sheet sync and the daily reminder.
Tests run on Robolectric simulating Android 36 (`app/src/test/resources/robolectric.properties`), which needs a Java 21+
test JVM: JDK 25 covers it. UI tests (`ui/AppFlowTest`, `ReportsFlowTest`) drive the real navigation against an in-memory Room DB on Robolectric's small default screen;
`PhoneScreenFlowTest` uses a phone-sized one for flows that need room (calculator keypad). Do not set a big screen globally: text-field dialogs hang under it.
Emulator (AVD `moneytracker`, Pixel 8 profile, Android 16 / API 36 google_apis arm64; image is ~4 GB):
```bash
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
$ANDROID_HOME/emulator/emulator -avd moneytracker -no-snapshot-save -no-audio &
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk   # keeps app data
adb -s emulator-5554 shell am start -n dev.yernur.moneytracker/.MainActivity
adb -s emulator-5554 exec-out screencap -p > shot.png                       # screenshots Claude can read
```
Always pass `-s emulator-5554`: this network also exposes an unrelated adb device (a smart TV). The real backup was imported
into the emulator through the app's own Import screen (push to /sdcard/Download, then media-scan it so the picker lists it).
Not yet run: instrumented (androidTest) tests, and the app on the owner's physical phone.

## Privacy
`data/` holds real financial data and is git-ignored. Don't echo transaction rows into logs, commit messages,
or PR text. Use aggregates in discussion.
