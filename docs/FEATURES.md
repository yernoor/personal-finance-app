# FEATURES.md

Sources: owner's 14 UI screenshots (`docs/ui-references/`), the owner's chat
answers (2026-10-25), and the 2026-10-25 backup. Rating: **must** / **nice** / **skip**.
"Seen" = visible in a screenshot. "Unverified" = inferred, confirm with the owner before building.

Target device: Android 16 (API 36) -> `minSdk = 36` is possible; owner to confirm no older device is needed.

## Daily core (owner: used every day)

| Feature | Rating | Notes |
|---|---|---|
| Add expense / income | must | Seen. Amount field with calculator, account picker, category grid (+ "More"), date chips (today / yesterday / 2 days ago / calendar), tags (with search + "Add tag"), comment. Fast entry is the daily habit. |
| Add transfer between accounts | must | Seen. From, To, amount, date, comment. Cross-currency amounts supported by the data model; the screenshot shows one amount field only (second amount UI unverified). |
| Accounts list with balances | must | Seen. Per-account balance, "Total" = sum of accounts not hidden, second figure = total including hidden accounts. |
| Transfer history | must | Seen. Day / Week / Month / Year / Period filter, search. |
| Home: per-period category breakdown | must | Seen. Day / Week / Month / Year / Period tabs, Expenses / Income tabs, stacked bar, category rows with % and amount, previous-period arrow. |
| Operation history / edit / delete | must | Home has a receipt icon (top right), presumably the operation list. Screen not captured: unverified. |
| Categories: create/edit/delete, icon, color | must | Seen. Separate Expense and Income lists; reorder icon (top right, unverified). |
| Per-category planned outlay | must | Seen: "Planned outlay 50 000 KZT per month" on Досуг = `category.limitAmount`. |
| Tags | must | Seen in add-transaction. Tag management screen not captured. |
| Archive / hide accounts | must | Seen. Eye-off icon = hidden from the Total (matches `ignoreInBalance = 1`); trash-badged grey accounts in the chart picker = inactive/archived. |

## Reports / charts

| Feature | Rating | Notes |
|---|---|---|
| Charts: General (income / expenses / profit / loss) by year / month / week / day | must | Seen. |
| Charts: Expenses and Income stacked by category | must | Seen. |
| Account filter for charts | must | Seen ("Select accounts" dialog with All). |
| **Projected expenses per category and overall** | must | New (owner request). Projection for the current month from spending so far; method to be agreed (see below). |
| **Limit warnings** | must | New (owner request). Warn when the projection for a category (Досуг, Кафе, Другое, ...) exceeds its limit; threshold configurable. |

### Projection design (owner chose: linear run-rate primary, trailing 3-month average secondary). IMPLEMENTED in milestone 5
- **Primary (run-rate)**, per category, current month: `projected = spent_so_far / days_elapsed * days_in_month`.
- **Secondary (3-month average)**, per category: mean of the previous 3 full calendar months' spending. Shown next to the
  primary figure as a reference, never mixed into it. Useful early in the month, when the run-rate is noisy.
- Overall projection = sum of per-category projections, computed separately for each source.
- Warning when `primary > limit * threshold` (threshold default 100%, configurable globally and per category).
  Optional setting (default off): also warn when the secondary exceeds the limit. Show on the home screen and as a notification.
- Overall projection excludes one-off categories (owner decision). Rule of thumb: fewer than 2 operations per month.
  Implement as a computed default (a category with <2 operations per month on average over the trailing 3 full months
  is excluded) plus a per-category manual override. Excluded categories still get their own per-category projection.
  Exact averaging window is my assumption; owner to confirm.
- Warnings are delivered as notifications as well as in-app, and the 3-month average also triggers warnings (owner decision;
  this replaces the "off by default" setting above). Android 16 needs the `POST_NOTIFICATIONS` runtime permission.
- Unverified: which previous months count if fewer than 3 full months exist (not an issue for this data, which starts 2023-09).
- Limits come from `category.limitAmount` (minor units, per month, default currency; verify the currency on import).

## Reminders

| Feature | Rating | Notes |
|---|---|---|
| Daily reminder at a set time (owner: 20:00, "enter today's spending") | must | **BUILT** (More -> Daily reminder: switch + time, default 20:00; see "Session 2026-11-03" below). |
| (imported reminder, for reference) | | | Seen: reminder "Внимание!" next fire 2026-10-26 20:00. Backup table `reminding` has 2 active rows. |
| Regular payments (ипотека, plus other required payments) | must | Seen: separate "Regular Payments" screen (Upcoming list, search, enable switch, add button). Mortgage fires monthly on the 20th at 10:00 (next: 2026-11-20). Owner does not need an amount on it. Notification only; auto-adding a transaction (`addAutomatically`, currently 0) is **nice**. |

Backup check (verified): both live in table `reminding`, told apart by `remindingType` (`reminding` = "Внимание!", daily, 20:00, start 2023-09-17; `regularPayments` = "ипотека", `period` Month, start 2025-10-20, `transactionType` Expense, `time` stored as `'10:0'`, i.e. not zero-padded: parse leniently). `amount` is non-null on both; value not inspected. `regular_payment_period` has 0 rows.

Android 16 notes: `POST_NOTIFICATIONS` runtime permission and exact alarms (`SCHEDULE_EXACT_ALARM`) must be handled; unverified against current docs.

## Data and sync

| Feature | Rating | Notes |
|---|---|---|
| `.mmbackup` one-time import | must | Milestone 3. |
| Google Sheets sync | must | Milestone 6 built; "Sync now" and a weekly background sync built (milestone 7, partly): see docs/SYNC_DESIGN.md. Apps Script `onEdit` and the conflict log are not built. |
| Local DB backup / restore | must | Milestone 8; plan in "Planned, not built" item 4. |
| Multi-currency (KZT, USD), stored rates | must | `rates` table has 3 rows; how they should feed totals is unverified. |

## Milestone 5 as built (verified on the emulator with the real backup)
- Charts: General (income / expenses / profit-loss bars), Expenses and Income stacked by category, zoom By year / month / week / day,
  tap a bar for its totals and category shares. Forecast tab: overall run-rate and 3-month average, per-category cards with limit bars,
  warned categories first.
- Warnings: Home card plus notifications (channel "Spending warnings"; one per month, category and level, escalating
  3-month average -> on track -> over the limit). Per-category threshold and "in overall projection" (Automatic / Always / Never) on the
  category screen; global threshold and notification switch under More -> Spending warnings. Schema is now v2 (migration tested).
- Chart arrows slide the window one period at a time (one month, one year, ...); By month shows 3 bars, By day 7, By week 4, By year 3.
- Amount calculator (owner request): a calculator button on every amount field opens a keypad sheet like the phone's calculator
  (C, smart parentheses, %, ÷ × − +, decimals, backspace, =, live preview, "Use result"). Percent works like the phone's:
  `1000 + 10%` = 1100, `200 × 10%` = 20. Results round to cents.
- History periods (owner request): Day / Week / Month / Year switch with arrows (the next arrow stops at the present; tapping the title
  jumps back to today). Opens on the **day** by default; More -> "History opens on" changes the default.
- History "Group by category" (owner request; since 2026-11-03 it is ON by default, the toggle beside Search turns it off): adds up each category (and direction) in the shown period,
  with an operation count; tap a group to expand its entries. Transfers are grouped too, by account pair ("Main -> Deposit 2"),
  with both sides summed ("₸4,500 -> $10" for cross-currency); the reverse direction is its own group.
- Not built yet: a daily background check (a warning is only evaluated when you save an entry; WorkManager now exists for sync and the reminder, but the check is not wired to it),
  Income-side limits, charts for a custom period, per-account chart filter.
- Unverified: for a ledger whose first month is partial, that month counts as a full month in the 3-month average.

## Session 2026-11-03 as built (owner requests; unit tests and debug build pass, checked on the emulator where noted)
- **Google Sheets**: "Sync now" (More -> Google Sheets) reads the sheet, then writes the phone's data to it. A weekly background job (WorkManager,
  network required, switch on the same screen, default on) does the same. The very first sync only pushes (nothing says which side is newer yet);
  after that a phone row edited or deleted since the last sync is never overwritten by the sheet. One sync runs at a time. The standalone
  "Import from the sheet" button was removed (it duplicated hand-typed rows on every run). Unverified: the live Google round trip and the job on a real phone.
- **Overwrite balance** (owner request): Edit account -> "Overwrite balance": type the real balance; the app adds an Income/Expense for the
  difference with **no category**, dated today. This matches how imported backups store it (docs/FORMAT.md 4c). Rows without a category show as
  "Balance correction" in History, Home, Charts and warnings.
- **Daily reminder** (owner request): More -> Daily reminder, default 20:00, any time. A one-time WorkManager job books the next one when it
  fires (Android may delay it a few minutes under Doze; no exact alarm). Asks for notification permission again on Save if it is missing.
- **"On track" warnings** wait for 10 days of the month (`MIN_RUN_RATE_DAYS`); "over the limit" and 3-month-average warnings are unchanged.
- **Charts and History share one header**: Day/Week/Month/Year slicer, then the period row, then a segmented switch (Charts: General /
  Expenses / Income / Forecast; History: Expenses / Income). Charts bars are 104 dp tall. History opens grouped by category: icon, name,
  share of that side's total, amount; no red/green; the Expenses/Income switch picks which side is listed.
- **Home**: "All accounts" under the balance is hidden by default (More -> Home: "Show All accounts on Home").
- **Add screen**: the Add/Save button is pinned to the bottom, above the keyboard and the gesture bar. The selected category is outlined
  with the tile's own shape and the accent color. A balance correction can be edited without choosing a category.
- **Swipe left/right** on a tab screen moves to the neighbouring bottom tab (a drag a child already used, such as a chart, does not count).
- **More** is grouped into sections: Manage, Home, History, Appearance, Notifications, Data and sync, App.
- **.mmbackup import** replaces a row already on the phone only when the backup's copy has a newer `modified`; it never undoes later phone edits.

## UX polish requests (owner, 2026-10-25), milestone 5b: all built except the icon redraw (waiting for a style choice)
- Navigation transitions must not disappear/pop abruptly; polish enter/exit animations. BUILT: screens always overlap while swapping
  (tab to tab: crossfade with a slight scale; into a detail screen: slide forward while fading; back reverses), the app bar, bottom bar and
  add button animate in and out instead of vanishing, and predictive back is on. Unverified by eye: mid-animation frames could not be
  captured on the emulator; judge the feel on the phone.
- Customizable bottom navbar (tabs shown, order) and configurable Home layout; default Home keeps the monthly totals. BUILT: More ->
  Navigation bar (2 to 5 of Home, History, Charts, Accounts, Categories, Tags; checkboxes and up/down arrows; the first tab opens with
  the app) and More -> Home layout (cards: spending warnings, balance, income and expenses, top spending categories, latest entries;
  the totals can cover a day, week, month or year; default month).
- History defaults to the **day** period and zooms out to week / month / year.
  First weekday is Monday (backup `firstWeekday = monday`).
- BUILT: all 32 category/account icons are original artwork (no Material or third-party icon paths), drawn on a 24-unit grid. Three
  owner-selectable styles in More -> Icon style: vivid discs (default), tonal tiles, duotone. Colors come from the system accent
  (OKLCH hue steps around it, lightness chosen per hue so yellow stays yellow, glyph white or near-black by contrast); tests check the
  12 colors stay distinct for every accent hue in light and dark. The backup's 12 stored colors map to the 12 slots.
- BUILT (experimental, off by default): More -> Liquid glass: floating glass bottom bar and add button (Kyant0 Backdrop 2.0.1: blur,
  vibrancy, lens refraction; needs Android 13+, we run on 16), frosted sheets and dialogs (system window blur, needs cross-window blur
  enabled on the device), solid cards. With glass on, only glass icon looks are used, and More -> Icon style then offers exactly those
  two: frosted tile (default: frosted rounded tile, hue glyph with a soft glow) and clear glass (almost transparent disc with a bright
  edge, hue glyph). The three everyday looks are kept for when glass is off; the two choices are stored separately.
  (The first glass icon look, glossy blobs, was rejected by the owner and replaced.)
- (original request) Redraw all category/account icons from scratch (original art). Colors derive from the Material 3 scheme / chosen accent,
  with enough chroma and hue spread that categories stay easy to tell apart.

## Planned, not built (owner requests, 2026-11-04)

### 1. PIN lock and hidden recents. BUILT (More -> Privacy): PIN 4-6 digits with back-off, auto-lock delay, recents Off / Blur / Hide. Not built: biometric unlock, private warning notifications, "phone screen lock" fallback. Blur and Hide are not yet checked on the Nothing phone
- **Lock**: 4-6 digit PIN, stored as a salted PBKDF2 hash (never the PIN). Wrong tries back off (30 s, 1 min, 5 min...). Optional biometric unlock
  (`androidx.biometric`, new dependency). Auto-lock delay in More: immediately / 30 s / 1 min / 5 min after the app leaves the screen.
  The gate is a full-screen layer above `AppRoot` in `MainActivity`; its "unlocked" state is not saved across process death.
- **Recents privacy**, setting with three modes: Off / Blur / Hide. Screenshots stay allowed inside the app in every mode, so `FLAG_SECURE` is never set while the app is in front.
  - *Hide*: `Activity.setRecentsScreenshotEnabled(false)` while the app is paused (API 33; the exact behaviour is **unverified**, the docs fetch did not cover it), or `FLAG_SECURE` set in `onPause` and cleared in `onResume` (this also blanks the thumbnail and the app in screen recordings only while backgrounded).
  - *Blur*: Android has no "blurred thumbnail" switch. Draw a frosted cover (reuse the `Glass.kt` backdrop, or `Modifier.blur`, API 31) over the whole UI in `onPause`. **Unverified**: the system takes the thumbnail after `onPause`, so the cover may not be drawn in time on every launcher; test on the Nothing 3a and fall back to Hide if it flashes the real content.
  - `onPause` also fires for permission dialogs and the system file picker, so the cover must not trigger the lock timer, only the cover.
- **Amounts outside the app**: warning notifications get a private version (no amounts) for the lock screen. Widgets: see item 3 (owner decision: no account totals on the home screen at all).
- **Forgot PIN**: there is no server. Recovery = clear app data and re-sync from the Sheet (so sync should have run). Alternative: allow the phone's own screen lock as a fallback (`DEVICE_CREDENTIAL`). Owner to choose.
- Tests: hash/verify/back-off as pure functions; Robolectric UI test that a locked app shows the keypad and never composes the screens.

### 2. Charts: expand a category to its transactions. BUILT (tap a row: its operations in the selected bar's period, tap one to edit; "Open in History" link not built)
- Today `CategoryAmountRow` ([ChartsScreen.kt](app/src/main/kotlin/dev/yernur/moneytracker/ui/ChartsScreen.kt)) is a flat row; the chart rows come from `TxLite`, which has no comment or uid.
- Plan: tap a row to expand it in place (chevron, animated) into that category's operations in the **selected bar's period**: date, account, comment, amount. Tap an operation to open the edit screen; a small "Open in History" link carries the category and period.
- Data: extend `TxLite` (or add a DAO query by `categoryUid` + date range) with uid, account, comment. Expand state keyed by category uid, reset when the bar changes. Works for Expenses and Income tabs; the "Balance correction" row (null category) expands too. Transfers never appear here.
- Tests: expanded totals equal the row amount to the cent (same invariant as `buckets`).

### 3. Home-screen widgets (Nothing Phone 3a, Nothing OS 5.0 = Android 17)
**What the internet says** (checked 2026-11-04):
- Nothing publishes **no widget SDK or design guide for third-party apps**. Its developer programme covers Glyph (`GlyphMatrix-Developer-Kit`), which is the back LEDs, not home-screen widgets. A request for a widget design manual sits unanswered on Nothing's community forum.
- Nothing's OS 5.0 page lists "transparent widgets", wallpaper-aware colours and tinted icons for the refreshed home screen; it does **not** mention frosted glass for widgets. (The frosted glass in news about the 3a is the Community Edition's back panel and the OS translucent menus.)
- So "Nothing style" widgets are ordinary Android app widgets that we style ourselves. Standard docs: [widget layouts and sizes](https://developer.android.com/develop/ui/views/appwidgets/layouts), [enhance widgets (dynamic colours)](https://developer.android.com/develop/ui/views/appwidgets/enhance), [Jetpack Glance](https://developer.android.com/develop/ui/compose/glance).
- Nothing's own look, from reviews: monochrome, dot-matrix type (Ndot / Doto-like font), high contrast, big numerals. The Nothing launcher shows the widget background transparent or not as the user chooses.

**Frosted glass: the hard limit.** An app widget is `RemoteViews` drawn by the launcher; the app cannot blur what is behind it. Real backdrop blur is impossible from our side (unverified whether Nothing OS 5.0 blurs widgets itself: test on the phone). We can fake the look: translucent white/black fill (about 12-25% alpha) + 1 dp light edge + big rounded corners (use `system_app_widget_background_radius`) + soft inner highlight drawn as a drawable. That reads as glass on a wallpaper and also matches the in-app "Liquid glass" option.

**Build approach (proposal).** Jetpack Glance (Compose-style, RemoteViews underneath; not interoperable with normal Compose, so widgets get their own small composables and share only the data layer). Alternative: plain `RemoteViews` XML if Glance fights the glass drawables. Responsive via `SizeMode.Responsive` / `targetCellWidth/Height`, `previewLayout`, `@android:id/background` for the launch animation. Data from Room via a small read-only repository; refresh from WorkManager and after each saved transaction (`updateAll`). Style switch in More: Glass / Dot-matrix mono / Material-you colours. Tap opens the matching screen (or add-entry).
Sizes on a 5x4 grid are roughly 73n-16 by 118m-16 dp (Android docs, Pixel 4; the 3a's grid may differ, measure it).

| Widget | Size | Shows | Tap |
|---|---|---|---|
| Quick add | 2x1 (or 1x1) | Expense / Income / Transfer buttons | opens add screen on that kind |
| Month at a glance | 2x2 | Spent this month, bar vs. projection, limit warning dot | Home |
| ~~Total balance~~ | | Dropped (owner, 2026-11-04): account totals never appear on the home screen. | |
| Today | 2x2 | Spent today, last 3 operations | History |
| Limits | 4x2 | Top 3-4 categories as bars vs limit (+ projection tick) | Charts, forecast tab |
| Budget + chart | 4x4 | Month category stacked bar, top categories, projection line (the Home/Charts summary) | Charts |
| ~~Accounts~~ | | Dropped for the same reason (balances). | |
| Recent operations | 4x4 / 4x6 | Last 8-12 operations, per-row category icon, tap to edit | edit screen |
| Month dashboard | 4x6 | Totals + chart + limits + recent in one tall card | Home |

- **Wealth widgets: dropped** (owner, 2026-11-04). The wealth branch (`claude/finance-app-features-208bd5`) gets no widgets.
- **Suggested build order**: shared widget style + Month at a glance (2x2) + Quick add + Today, then Limits and Recent operations (4x4); the 4x6 Month dashboard last.
- **Owner decisions (2026-11-04)**: likes all of them. Spending amounts may show on the home screen; **account balances and totals never do**, which removed "Total balance" and "Accounts" from the table.
- **Font (owner decision, 2026-11-04)**: the system font for the whole app and for widget small text; Doto for widget big numerals. Options considered, all free fonts bundled under their licence (never copy Nothing's own Ndot files):
  | Option | Look | Licence |
  |---|---|---|
  | System / Roboto | Neutral, matches the rest of the app | Apache 2.0 (system font, nothing to bundle) |
  | Geist (Vercel) | Clean modern sans, close to Nothing OS's newer look | SIL OFL 1.1 (Vercel docs) |
  | Geist Mono | Same family, monospaced digits: amounts line up | SIL OFL 1.1 (third-party listing; check the repo's LICENSE) |
  | Doto (Google Fonts, Oliver Lalan) | True dot-matrix, variable weight 100-900, rounded-dot axis | SIL OFL 1.1 (Fontsource, Google Fonts mirror) |
  | Geist Pixel (Vercel) | Pixel variants (square, grid, circle, triangle, line) for display text | OFL inferred from the family, not stated in its repo: verify before bundling |
  **Decided (owner, 2026-11-04, after a mock-up of both):** Doto for the big numerals only (bundled font file, OFL licence text shipped with the app); system font for everything else, including small amounts, because Doto is a display face and stays at 16 sp or larger. Still to check on the phone: Cyrillic category names are not set in Doto, so only digits and the currency sign matter; verify the tenge sign (₸) exists in Doto, else fall back to the system font for it.
- Tests: Glance has no Robolectric-friendly renderer, so keep the logic (what each widget shows, formatting, no balances) in plain functions with unit tests, and check the look on the emulator and the phone.

### 4. Local database backup and restore (owner request, 2026-11-04). BUILT: manual backup/restore and the pre-update copy; not built: weekly folder backup, 30-day warning
Verified in the code today: `allowBackup="false"` ([AndroidManifest.xml](app/src/main/AndroidManifest.xml):17); the sheet push empties every tab and then rewrites it
(`SheetsSync.reuse` calls `api.clear`), so a dropped connection leaves the sheet partly empty until the next run (already listed under "Known limits" in docs/SYNC_DESIGN.md);
and the update-failure text in `ApkInstaller.kt:132` tells the owner to uninstall the app, which deletes the database. So the sheet is the only copy outside the phone and it can be the broken one.
- **More -> Back up to a file**: write a consistent copy of the Room database (checkpoint the WAL first, or `VACUUM INTO` a temp file, so the copy is one file) to a location the owner picks with the system file picker (`ACTION_CREATE_DOCUMENT`: no storage permission, can be Drive, Downloads or a USB drive). File name `moneytracker-YYYY-MM-DD.db`, with the schema version stored inside (Room's `user_version`).
- **More -> Restore from a file**: pick a file, check it is SQLite and its `user_version` is not newer than the app's, show counts (transactions, accounts, last change) and ask for confirmation, then replace the live database and restart. Older versions run through the normal Room migrations. Keep the previous database as `.bak` until the app has opened the restored one.
- **Safety nets**: an automatic backup copy before every in-app update (and shown in the update-failure message in place of the "uninstall" advice, which should say "restore from the backup instead"); optional weekly backup to a folder the owner chose (WorkManager, keeps the last 4); a warning in More when the last backup is older than 30 days.
- **PIN interaction**: a backup file is plain data. Say so on the screen; optional passphrase encryption is a later option, not part of v1.
- Not in scope: re-enabling Android's own backup (`allowBackup`), which would copy financial data to the owner's Google account outside this app's control.
- Smaller than more sync work: no new network or Google scope. Suggested order: **this first** (it protects everything else), then PIN, charts drill-down, widgets.
- Tests: round trip (back up, change data, restore: counts and per-account balances equal), refuse a file that is not SQLite or has a newer `user_version`, restore keeps `.bak` when the restored file fails to open.

## Other / unverified, decide later

| Feature | Rating | Notes |
|---|---|---|
| PIN lock + hidden recents | must | Owner request 2026-11-04, plan in "Planned, not built" below. |
| Themes / appearance | nice | Own palette; follow system dark mode. |
| Photo attachments on transactions | skip | "Photo" row exists in add-transaction; no table seen in backup (`sync_file` has 0 rows). |
| Transaction search and filters | nice | Search icons seen (tag search, transfers). Operation-list filters not captured. |
| Home-screen widgets | must | Owner request 2026-11-04 (Nothing Phone 3a, Nothing OS 5.0), plan in "Planned, not built" below. |
| Settings: Personalization, Advanced customization, Data and storage, Extra | unknown | Not opened. Owner to say if anything there matters. |
| "Restore purchases", "Turn off ads", share / rate / contact support | skip | Seen in drawer. Non-goal (no ads, no payments). |
| Navigation drawer with profile header and total balance | nice | Seen. Header shows a name and balance; last-sync timestamp at the bottom (maps to our Sheets sync status). |
| Bank notification parsing | skip | Non-goal. |
| Writing `.mmbackup` | skip | Non-goal. |

## Open questions for the owner
1. Is `minSdk = 36` fine, or must the app also install on an older device?
2. (Settled) Exclusion window: <2 operations per month over the trailing 3 full months. Default warning threshold 100%.
3. Navigation: owner is open to newer designs. Proposal below, awaiting a yes/no.
5. (Resolved: no balance adjustment needed.)
6. Investments: keep "Инвестиции" as an expense category (current practice), or add a broker account for the ~$4000 of bonds?

## Navigation proposal (Material 3)
- **Bottom navigation bar, 4 tabs:** Home (period breakdown + projection/limit warnings), History (operations and
  transfers, search/filters), Charts, Accounts.
- **One add button** (FAB) on Home and History opens the add sheet with Expense / Income / Transfer as a segmented
  control, so a transfer is one tap away instead of a separate trip through Accounts. Fast entry is the daily habit.
- **Secondary screens** from a "More" entry on Home's top bar (or Settings): Categories, Regular Payments, Reminders,
  Tags, Settings, Sync status.
- **Material 3 dynamic color + dark theme** following the system (Compose `dynamicLightColorScheme` / `dynamicDarkColorScheme`,
  Android 12+; always available with minSdk 36). Accent comes from the system's wallpaper-derived palette. Unverified on the
  owner's phone: OEM skins sometimes change how the system accent is chosen. Settings toggle "Use system colors" with a static
  green fallback scheme. Category colors are user data and stay fixed; over-limit / warning colors use fixed semantic colors
  (not dynamic) so they stay recognizable.
- Rationale: the owner's daily actions (add, glance at balances/limits) are all reachable in one tap from the bar.
  This is a judgment call, not a verified usability finding.

## Balance reconciliation (resolved)
- Screenshot totals match the backup's cached `account_balance`: Main 4,597 (459671), Deposit 36,138 (3613822),
  Deposit 2 2.31M (230507718), Freedom USD $13.56 (1356). So `account_balance` is what the owner saw on the phone.
- The replay gap was a data-format quirk, not missing operations: 60 USD rows keep their amount in the real-currency
  field. With that rule every account reconciles to the cent (docs/FORMAT.md 4b). No opening-balance adjustment needed.
