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
| Local DB backup / restore | must | **BUILT** in 0.4.0 (More -> Back up to a file / Restore from a backup file); weekly folder backup and the 30-day warning are not. Plan and status: "Planned, not built" item 4. |
| Multi-currency (KZT, USD), stored rates | must | `rates` table has 3 rows; how they should feed totals is unverified. |

## Milestone 5 as built (verified on the emulator with the real backup)
- Charts: General (income / expenses / profit-loss bars), Expenses and Income stacked by category, zoom By year / month / week / day,
  tap a bar for its totals and category shares. Forecast (since 2026-11-10 inside the Analysis tab, Forecast segment): overall run-rate and 3-month average, per-category cards with limit bars,
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
- BUILT: all 99 category/account icons (32 from the backup, 67 added 2026-11-11, the latter not yet checked on screen) are original artwork (no Material or third-party icon paths), drawn on a 24-unit grid. Five
  owner-selectable everyday styles in More -> Icon style (2026-11-11 redesign): Solid (default), Tonal, Outline, Mono, Sticker. A 3D look was built and dropped the same day (owner: ugly).
  Custom colors (2026-11-11): the color picker has a `#` swatch that opens a color wheel (drag around the ring to pick a hue). The picker is hidden while the Mono style is on, since Mono draws every icon in the accent. A color outside the 12 stored ones is drawn with its exact hue, at the lightness each style needs (so the swatch can look a little lighter or darker than the typed hex); near-grays use the neutral slot. Colors come from the system accent
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

## Version 0.4.0 at a glance (2026-11-04)
What the release added on top of 0.3.1; details are in the sections named in brackets.
- **Wealth** (More -> Wealth): net worth with history chart, manual assets and loans, goals, bond portfolio with coupon schedule, monthly money (income and payments) with a health summary, and a net-worth projection. [Wealth tools]
- **Compound interest** (More -> Compound interest, or the button on Wealth): balance and year-by-year chart, monthly or yearly capitalization, contribution at the start or end of the month. [Wealth tools]
- **Currency switch and bank rates**: Wealth and Compound show amounts in KZT, USD, EUR, RUB and more, using National Bank of Kazakhstan rates (one request a day, cached). [Wealth tools]
- **Cross-currency transfers**: "Amount received" is filled in from the rates and stays editable. [Wealth backend as built]
- **App lock**: PIN (4-6 digits, back-off), optional fingerprint/face, auto-lock delay, recent-apps privacy. [item 1]
- **Charts drill-down**: tap a category row to see its operations in the selected period, tap one to edit. [item 2]
- **Home-screen widgets** (7, Glance): Month at a glance, Today, Quick add, Category limits, Month by category, Recent operations, Month dashboard; glass or solid look, optional dot-matrix numerals. Never show account balances. [item 3]
- **Back up to a file / Restore from a backup file**, plus an automatic copy before an in-app update. [item 4]
- Not covered by the Google Sheet: the wealth tables are phone-only (see the notes in "Wealth backend as built"); the file backup covers them.

## Wealth tools (owner request, 2026-11-04), backend first, UI mock-ups to choose a style
Beyond expense tracking: the app becomes a general personal-finance tool. Owner's picks, in build order. All of it is local; the only new
network call is the daily rate fetch.
1. **Compound interest calculator.** Start balance, yearly rate, monthly contribution, years; year-by-year table (contributed / interest / balance).
2. **Net worth over time + goals.** Snapshots of all account balances (in KZT) plus manual assets/liabilities; goals with a target amount and date.
3. **Projected net worth.** The same engine fed with salary, monthly obligations and investment returns / bond coupons.
4. **Financial health summary.** Monthly income and obligations (mortgage, loans, subscriptions) entered by hand; shows what is left to save/invest, savings rate, months of expenses covered.
5. **Investment portfolio.** Owner holds one broker account, 100% bonds: each bond has face value, price paid, coupon rate and frequency, maturity, currency. Coupon schedule, income per year, maturity ladder. Prices are entered by hand (no live quotes).
6. **Currency converter.** Rates from the National Bank of Kazakhstan feed (`nationalbank.kz/rss/get_rates.cfm?fdate=DD.MM.YYYY`, verified 2026-11-04: returns KZT per `quant` units).
   The owner allows **one request per day**, cached; the converter works offline on the cached rates. The backup's `rate` table is not used.

### Wealth backend as built (2026-11-04; unit-tested on Robolectric)
Code in `data/wealth/`, schema 2 -> 6 (one migration per step, each tested). All money is `Long` minor units in KZT unless a row names its currency.
- `Compound.kt`: monthly or yearly capitalization (yearly = interest accrues monthly on the principal and is credited in month 12), contributions at start or end of month. Checked against hand-computed annuity values.
- `Fx.kt` / `FxRepository`: National Bank feed parsed per `quant`, cached in `fx_rate`, at most one request per local day; an unreachable network does not use up the day, a bad HTTP answer does. Converts through KZT.
- `WealthRepository`: net worth = every active account (also those hidden from the Total) + manual assets + bonds at current value - liabilities, snapshotted once a day (skipped while a currency has no rate, so a gap never looks like a loss); goals on net worth or one account with the monthly amount needed to arrive on time.
- `Bonds.kt`: coupons counted back from maturity (assumption: real schedules may differ by days), current yield, yield to maturity (bisection, actual/365, ignores accrued interest, tax and fees: an estimate), maturity ladder. Prices are typed in by hand.
  A broker account tracked as an app account AND as bonds would count twice in net worth: use one of the two.
- `Health.kt`: available per month = income - average ledger spending (last 3 full months, balance corrections skipped) - obligations not already in the ledger (`inLedger` flag per item); savings rate, obligations share, months of expenses covered. `projectNetWorth` compounds accounts and investments at their own rates and splits the monthly surplus between them.
- Transfers live in their own table and are never income or expense: a regression test (`HealthTest`) moves money between accounts and checks spending, the health summary and net worth do not change.
- UI (owner chose the Material style from the mock-ups): `ui/WealthScreen` (More > Wealth, or add it as a bottom-bar tab in Navigation bar settings) and `ui/CompoundScreen` (More > Compound interest, or the button on Wealth).
  Both have a currency switch (`CurrencySwitch`; KZT plus the cached USD/EUR/RUB/...); the choice is remembered (`AppSettings.wealthCurrency`) and stored amounts stay in KZT. The calculator converts its typed amounts when the currency changes.
  Items are added and edited in dialogs (`FormDialog`): manual assets and loans, goals, bonds, income and payments. Covered by Robolectric flow tests (`WealthScreenTest`, `CompoundScreenTest`).
  Not checked: the screens on the emulator or the phone (only Robolectric).
- Transfers between accounts of different currencies (owner request, 2026-11-04): "Amount received" is filled in from the sent amount with the bank rates (backup rates for currencies the bank does not publish; through tenge for pairs like EUR to USD). It stays editable: typing keeps your figure, clearing the field gives it back to the calculation, changing an account recalculates. With no known rate nothing is filled and saving asks for the amount.
- Phone only: goals, bonds, manual assets, income/payments and the net worth history live in the local database only. The Sheets sync and the `.mmbackup` importer do not know these tables, and the local database backup (milestone 8) is not built, so clearing app data or losing the phone loses them, history included. A `Wealth` tab in the sheet is the planned fix.
- Known gap: an investment logged as an expense category (current practice, question 6 above) counts as spending in the health summary.
- The file backup (item 4 below) copies the whole database, so it covers these phone-only wealth tables too.

## Planned, not built (owner requests, 2026-11-04)

### 1. PIN lock and hidden recents. BUILT (More -> Privacy): PIN 4-6 digits with back-off, auto-lock delay, recents Off / Blur / Hide. BIOMETRIC UNLOCK built (More -> Privacy, only shown when the phone has an enrolled strong biometric; androidx.biometric 1.1.0 = newest stable, newer ones are alpha; MainActivity is now a FragmentActivity for it; the prompt appears when the lock screen shows, "Use PIN" falls back to the keypad; it skips the wrong-PIN wait; removing the PIN switches it off; convenience gate with no crypto object). **The prompt itself has never been run**, on no device or emulator: only the lock logic is unit-tested. Not built: private warning notifications, "phone screen lock" fallback. Recents Hide checked on the emulator (Android 16) 2026-11-09, not yet on the Nothing phone (Android 17); Blur was removed, see below
- **Lock**: 4-6 digit PIN, stored as a salted PBKDF2 hash (never the PIN). Wrong tries back off (30 s, 1 min, 5 min...). Optional biometric unlock
  (`androidx.biometric`, new dependency). Auto-lock delay in More: immediately / 30 s / 1 min / 5 min after the app leaves the screen.
  The gate is a full-screen layer above `AppRoot` in `MainActivity`; its "unlocked" state is not saved across process death.
- **Recents privacy**, setting with three modes: Off / Blur / Hide. Screenshots stay allowed inside the app in every mode, so `FLAG_SECURE` is never set while the app is in front.
  - *Hide*: `Activity.setRecentsScreenshotEnabled(false)` (API 33), set while the setting is Hide and kept for the whole activity, not toggled in `onPause`. Screenshots inside the app still work. **Verified on the Android 16 emulator** (recents screenshot: the card is blank, no balance). The earlier `FLAG_SECURE`-in-`onPause` version did NOT work there: the system snapshots before the flag reaches the window. Not verified on Android 17 / the Nothing phone.
  - *Blur*: **removed 2026-11-09.** A cover drawn in `onPause` or on focus loss showed the real content in the recents card on the emulator: the snapshot is taken before that frame. Android has no blurred-thumbnail switch. A stored "Blur" choice is read as Hide.
  - `onPause` also fires for permission dialogs and the system file picker, so the cover must not trigger the lock timer, only the cover.
- **Amounts outside the app**: warning notifications get a private version (no amounts) for the lock screen. Widgets: see item 3 (owner decision: no account totals on the home screen at all).
- **Forgot PIN**: there is no server. Recovery = clear app data and re-sync from the Sheet (so sync should have run). Alternative: allow the phone's own screen lock as a fallback (`DEVICE_CREDENTIAL`). Owner to choose.
- Tests: hash/verify/back-off as pure functions; Robolectric UI test that a locked app shows the keypad and never composes the screens.

### 2. Charts: expand a category to its transactions. BUILT (tap a row: its operations in the selected bar's period, tap one to edit; "Open in History" link not built)
- Today `CategoryAmountRow` ([ChartsScreen.kt](app/src/main/kotlin/dev/yernur/moneytracker/ui/ChartsScreen.kt)) is a flat row; the chart rows come from `TxLite`, which has no comment or uid.
- Plan: tap a row to expand it in place (chevron, animated) into that category's operations in the **selected bar's period**: date, account, comment, amount. Tap an operation to open the edit screen; a small "Open in History" link carries the category and period.
- Data: extend `TxLite` (or add a DAO query by `categoryUid` + date range) with uid, account, comment. Expand state keyed by category uid, reset when the bar changes. Works for Expenses and Income tabs; the "Balance correction" row (null category) expands too. Transfers never appear here.
- Tests: expanded totals equal the row amount to the cent (same invariant as `buckets`).

### 3. Home-screen widgets (Nothing Phone 3a, Nothing OS 5.0 = Android 17). BUILT (Glance 1.2.0)
Built: Month at a glance 2x2, Today 2x2, Quick add 2x1, Limits 4x2, Month by category (Budget) 4x4, Recent operations 4x4, Month dashboard 4x6; More -> Widgets: glass or solid look, Doto numerals on/off; refreshed after every saved change and every 30 min.
Doto is drawn into a bitmap (Glance cannot set a custom font); the tenge sign and all small text stay in the system font. Seen working on an Android 16 emulator: only Month at a glance was placed and looked at (Doto digits render, glass card readable over a light and a dark wallpaper). **Not yet looked at**: the other six, the solid look, and everything on the Nothing 3a itself.
Known gaps: the picker shows the app icon instead of a preview (no preview layout or generated preview yet); on the emulator's launcher the 4x6 dashboard is offered as 1x1 (its grid has no room for 6 rows: the Nothing launcher may differ); tapping an operation row is wired to open its edit screen but was never tried; no "hide when locked" (not needed: no balances are shown).

#### Widget picker preview (built 2026-11-04: generated previews; the static fallback image is not)
Each widget now draws itself in the picker with fictional numbers (`sampleWidgetData`: no real amounts, no balances), in the glass or solid look and with the dot-matrix setting the owner chose.
- Look update (2026-11-10, owner request): glass now follows light/dark mode too (light frosted fill by day, smoked dark fill by night, hairline edge), the "on pace for" line uses the system accent (Material You, via `GlanceTheme`) instead of the amber warning, and the dot numerals are drawn white and tinted by the launcher so they flip with the theme. Over-limit amber stays on limit rows and the Limits title. Not checked on the phone: the exact alpha is a guess matched to the owner's screenshots.
- Widget style (2026-11-10, owner request): More -> Widget style picks one of three looks, all following light/dark mode: **Nothing OS** (lightest translucent fill, dot-matrix Doto numerals), **Glass** (frosted fill, system font) and **Usual** (opaque card, system font). The old Glass and Dot-matrix switches are gone; their stored values pick the style once (glass + dot -> Nothing OS, glass only -> Glass, glass off -> Usual). Fill alphas are guesses from the owner's screenshots, not measured on the phone.
- How: `MoneyWidget.providePreview` draws the same `Content` composable as the real widget; `previewSizeMode` is `SizeMode.Responsive` at the default size (73 dp per column and 74 dp per row, minus 16; the docs' 118 dp rows drew the previews too tall). `updateWidgetPreviews` calls `GlanceAppWidgetManager.setWidgetPreviews` for all seven receivers at launch (`MoneyApp.onCreate`) and after the glass / dot-matrix switches in More. It does nothing below Android 15 and swallows failures (a missing preview only means the app icon shows).
- Verified on the emulator (Android 16, Glance 1.2.0 has both APIs): after the first install all seven previews were registered and the picker showed Month, Today, Quick add, Limits, Month by category and Recent with the sample numbers. Not verified: the 74 dp row height (the run after that change hit the rate limit: the system allows about two `setWidgetPreviews` calls an hour, so right after reinstalling several times the picker falls back to the app icon until the limit resets), the 4x6 dashboard (this emulator launcher offers it as 1x1), and the Nothing launcher.
- Not built: a `previewImage` PNG per widget for Android 14 and lower and for the rate-limited gap. The owner's phone runs Android 16 and 17, so the generated previews are the target.
- Test: `WidgetDataTest.sampleDataFillsEveryWidgetAndHasNoBalances`.

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
  **Superseded for the app UI (owner, 2026-11-10):** the app now uses Inter for text and Inter Tight for big numbers and headings (bundled variable fonts in `res/font`, OFL text in `assets/fonts/inter-OFL.txt`; both checked to contain the tenge sign and Russian and Kazakh Cyrillic), with tabular figures. Widgets keep Doto for big numerals.
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

### 5. Bug reports and feature ideas (owner request, 2026-11-10; friends are testing the public repo). BUILT: feedback screen and crash capture (on for everyone, no switch)
- More -> App -> "Report a bug or suggest a feature": Bug / Idea, a summary and details. The button opens `https://github.com/yernoor/personal-finance-app/issues/new` pre-filled (title, body, label `bug` / `enhancement`) in the browser; the person presses Submit there. The repo constant is `UpdateChecker.REPO`.
- The app makes no request and holds no token (the "no network calls" rule stays true). The screen warns that the issue is public and tells the person to leave out amounts and account names. The body gets one line: app version, Android version, phone model. The link is capped at 5,000 characters of body (GitHub rejects very long URLs; limit not checked against GitHub).
- **Crash capture** (`CrashLog`, installed in `MoneyApp.onCreate`): an uncaught-exception handler saves the last crash to `files/crash/last-crash.txt`, then calls the previous handler, so Android's own crash behaviour is unchanged. On the next launch a dialog offers "Review report" (opens the feedback screen as a Crash issue, trace pre-filled and editable) or "Not now" (deletes the trace). The file is deleted when the issue link is opened. Only the latest crash is kept.
- Privacy of crash reports: the issue is public, so the saved text has exception class names, `at` frames (30 per exception, 4 causes) and the thread name, **no exception messages** (they can quote typed text or values). Cost: a few bugs will be harder to diagnose without the message; add it by hand if needed.
- Not covered: native crashes and ANRs (the Java handler never sees them); the handler is unit-tested with a fake previous handler, not by crashing the app on a device.
- Submitting needs a GitHub account. Labels that do not exist yet are ignored by GitHub for people without write access, so the owner creates `bug`, `enhancement` and `crash` once.

## Affordability and spending analysis (owner request 2026-11-10, all rated **must**; mock-ups on the Design canvas, sample data)
Idea taken from Anthropic's consumer "Personal Finance" launch (2026-11-05), but everything is computed on the phone from the Room data: no Plaid, no
LLM, no new network call.

| Feature | Rating | Notes |
|---|---|---|
| "Can I afford this?" check | **BUILT** (More -> Can I afford this?) | Price (default currency), category, paying-from account. Verdict Yes / Tight / No with the numbers behind it. Not built: item name, purchase date other than today (the mock-up had them), prefilling the add screen from "Log as expense" (it opens an empty one). Never run on a device; unit and Robolectric tests only. |
| Spending analysis | **BUILT** (own bottom-bar tab "Analysis", Trends segment; the Forecast segment holds the former Charts > Forecast) | Six monthly bars with a projected current month and an average line, "worth a look" insights, per-category sparkline and change vs the 3-month average. Taps open the category trend. Not run on a device. |
| Category trend screen | **BUILT** (tap a category in Analysis) | 12-month line (current month drawn as the run-rate segment) with limit and 3-month average, Friday-to-Sunday share and weekday bars, three largest operations this month. Not built: the mock-up's "Open all in History" button (History has no category filter). |
| Recurring costs, price changes, possible duplicates | **BUILT** (Analysis -> Recurring costs and duplicates) | Detected from the ledger's own history; nothing changes unless the owner confirms. "Keep both" is remembered per second operation in settings; "Delete the second" is a soft delete. Not built: the mock-up's "Add reminder" button (the app has no reminder editor yet) and the "share of monthly spending" figure. Duplicate time of day is not shown (`created` is UTC). |

Navigation (owner request 2026-11-10): Analysis is its own bottom-bar tab, not a segment of Charts; Charts keeps only General / Expenses / Income, and its old Forecast
tab moved into Analysis as a Trends / Forecast switch. The default bar is now Home, History, Charts, Accounts, Analysis (5 is the maximum). A phone that already
stored its own tab choice keeps it: add Analysis in More -> Navigation bar.

### Affordability rules (all amounts in default-currency minor units)
- `balance` = the chosen account's balance converted to the default currency.
- Horizon = the next due date of an enabled **Income** reminder, else the end of this month. The backup has no payday field: assumption, owner to confirm.
- `expected` = daily run-rate of the overall projection (`runRateIncluded / daysInMonth`) x days to the horizon; the 3-month-average variant is shown beside it. Both reuse `Forecast` (one-off categories excluded, as in the projection design).
- `scheduled` = amounts of enabled expense reminders / regular payments that fall in (today, horizon]. Only `Day` and `Month` periods are understood (custom periods unverified, skipped).
  Known overlap: a scheduled payment that is also logged as an expense in the run-rate is counted twice; the "free" figure errs on the cautious side.
- `free` = `balance - expected - scheduled`; `afterPurchase` = `free - price`.
- Verdict: **No** when `afterPurchase < 0`; **Tight** when the price takes more than 50% of `free` or the category's run-rate plus the price exceeds its limit; otherwise **Yes**. The 50% is my number, not the owner's.
- Limit impact: category run-rate + price against `limitAmount`. Goal impact: delay in days = price / (average monthly saving / 30), saving = mean (income - expenses) of the last 3 full months; shown only when that is positive.
- Not financial advice: the screen says so.

### Analysis rules
- Monthly bars: the last 6 months, all expenses; the current month shows the run-rate projection (spent so far / days elapsed x days in month) as a dashed bar. Average line = mean of the full months shown.
- Category trend: delta = current run-rate vs the mean of the previous (up to) 3 full months, shown only when that mean is not zero, at least 10 days of the month have passed, and something was spent in the category this month (a pace of 0 says nothing). The sparkline draws the unfinished month at its pace. Insights: a category at least 25% above its average (and at least 5,000 above), and a category that fell three months in a row.
- Weekday pattern: expenses of the last 3 full months by day of week.
- Recurring: an expense comment (trimmed, case-insensitive) seen in at least 3 consecutive months, amounts within 20% of the median. Price change = latest amount differs from the previous one by 5% or more. "Has a reminder" = a reminder with the same title.
- Duplicate: same type, account, category, account-currency amount and date, created within 10 minutes of each other. Keep both / delete the second (soft delete through the ledger).

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
| Bank notification parsing | skip | Non-goal. Narrow exception below: Wallet payment capture. |
| Writing `.mmbackup` | skip | Non-goal. |

## Wallet payment capture (owner request 2026-11-09, go-ahead and non-goal exception given 2026-11-10)
Owner wants a payment made with a Google Wallet card to become an expense in the app, with a top-of-screen prompt to pick the category. Research and open questions: `docs/HANDOFF_WALLET_CAPTURE.md`.
- **Exception to the "bank notification parsing" non-goal:** the owner agreed (2026-11-10) to read notifications of the Google Wallet app (`com.google.android.apps.walletnfcrel`, package name unverified on the phone)
  and, for comparison only, of the bank's own app. This is the only notification source; no other app's content is read, and nothing is sent over the network.
- **Step 1, BUILT as a separate app, `walletlogger/`** (own applicationId `dev.yernur.moneytracker.walletlogger`, no INTERNET permission, installs beside the main app and never touches its data):
  a `NotificationListenerService` that stores title, text, big text, post time, id and the group-summary flag for allow-listed packages only (default: the Wallet package). For any other package it
  records the package name alone (a "seen packages" list so the owner can find the bank app's real package) and never reads the notification. Export writes a text file through the system file picker.
  Installed over adb from a local build (no GitHub release: owner dropped that on 2026-11-10). Shows a silent status notification while reading, with a pause switch. Run on the owner's Nothing Phone 3a: an adb install showed no restricted-settings gate (one phone, observed).
- **Format verified on the owner's phone (logger export, 2026-11-10, two successful payments):** package `com.google.android.apps.walletnfcrel`; title = merchant; text = `KZT1,234.00 with <card name> ••<last4>`
  (3-letter code, `,` thousands, `.` decimals); `bigText` empty; not group summaries; **both payments reused notification id 1001**, so dedupe uses time + amount + card + merchant.
  Still unseen (so ignored, not guessed): declines, refunds, foreign-currency payments, pending amounts that change. Parser: `data/wallet/WalletParser.kt`, unit-tested with placeholder values.
- **Settings (BUILT, More -> Automation):** "Google Wallet payments" switch asks for consent first (what is read, that other apps are ignored unread, nothing leaves the phone), then opens Android's notification-access screen;
  "Default account for payments" picks where Wallet payments go. The first payment asks for the account when none is chosen. Off until the owner agrees.
- **Step 2, BUILT in the main app (unit-tested; never run on the phone yet):** `WalletListenerService` hands a notification to `WalletIntake` only with consent and only for the Wallet package (anything else is not read at all);
  group summaries and unknown formats are ignored; repeats are dropped by a persisted dedupe key (last 100). `WalletPlan` decides the next step:
  no default account -> a heads-up "Which account do you pay with?" (first three accounts as buttons, tap for the full list), the answer is saved as the default; the default account's currency differs from the payment's
  -> "not added, add it by hand" (no conversion: the foreign-currency format is unseen); otherwise -> "<amount> · <merchant>" with the two most used expense categories as buttons plus "More…" (a notification shows three buttons at most).
  Tapping the notification (or "More…") opens the app with a bottom sheet over it (`WalletPickerDialog`, no separate activity): the payment, account chips (the default is preselected; another account applies to this payment only, the default stays), the same icon tiles as the Add screen in the owner's icon style and accent, and a full-width Skip button (checked on the emulator 2026-11-11).
  Picking a category saves the expense (account = default, date = local day of the post, comment = merchant) and shows a short "Added" confirmation. Lock-screen version shows only "Wallet payment".
  Not done: per-card account mapping (all payments use the default account), converting foreign-currency payments, auto-dismissing Wallet's own notification, the PIN lock is not asked for these prompts.
  Unverified on a device: the prompts' look and buttons, the "More…" activity, what Android 17 does with the listener of a release-signed build.
- **Emulator test, `walletstub/`:** a test-only app with Wallet's own package name that posts a Wallet-shaped payment notification, so the main app's listener can be run without a phone (the listener ignores every other package). Never install it beside the real Wallet.
  Setup: install both debug APKs, `pm grant` POST_NOTIFICATIONS to both, `cmd notification allow_listener dev.yernur.moneytracker/dev.yernur.moneytracker.data.wallet.WalletListenerService`, switch "Google Wallet payments" on in More -> Automation.
  Post (wrap the whole command in quotes so `adb shell` keeps spaces in values):
  `adb shell "am broadcast -a dev.yernur.moneytracker.walletstub.POST -n com.google.android.apps.walletnfcrel/dev.yernur.moneytracker.walletstub.PostReceiver --es merchant 'Test Cafe' --es amount 1234.5"`.
  Extras: `currency`, `card`, `last4`, and `text` (replaces the text line, for formats not seen yet). Checked on the emulator 2026-11-11: the main app answered with its "Which account do you pay with?" prompt.
- Rating: **must** (owner request).

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

## UI redesign (2026-11-11), from the design canvas the owner approved
BUILT and unit-tested; looked at on the emulator, not yet on the phone.
- **Type**: Inter, with Inter Tight for headings and big numbers, tabular figures (see the font note above).
- **Cards** (`AppCard`): white surface, hairline border, 22 dp corners; cards with their own colors (warnings, results) keep them. **Titles**: large, left-aligned.
- **Bottom bar** (`FloatingTabBar`): a floating pill; the chosen tab shows icon and label, the others only their icon, one highlight slides between them. Fits 2 to 5 tabs; checked with the owner's five (Home, History, Analysis, Accounts, Wealth) in the mock-up. The add button is a round accent button above the bar's right end, still only on Home and History.
- **Selectors** (`SlidingSegmented`): a track with a thumb that slides to the chosen segment (History, Charts, Entry, Analysis, Compound).
- **Motion**: rows and cards shrink a few dp while pressed and spring back (`PressScaleIndication`, app-wide for plain clickables; Material's own buttons, chips and FAB keep their built-in state layer). Tabs slide the way you move along the bar; the add screen grows out of the add button.
- **History says "Today"** for the current day (also in Charts' day view).
- **Also built**: Home shows income and expenses as two cards; Wealth and Compound interest have an accent hero card (Compound: result on top, the two selectors side by side); bars grow up from the axis on Charts and Compound interest (the History ring already swept in); the Home balance rolls to its new value (not from zero when the app opens).
- **Wealth heading (owner, 2026-11-11)**: the "National Bank rates, updated ..." line is gone and the currency switch sits in the heading bar next to the title (in the app's top bar when Wealth is a tab, in the screen's own bar when opened from More), so it no longer shrinks on a narrow phone.
- **Glass mode redesign (2026-11-11)**: the glow behind the app is now an iridescent wash of neighbouring hues, or, with a grey accent (Nothing OS's monochrome theme), a restrained sheen instead of grey smudges; cards are frosted (translucent with a bright hairline); every screen draws its own opaque copy of the glow (Scaffolds and top bars are see-through), so a back-swipe no longer shows the screen below through the one leaving; the glass bar and add button reuse the new pill. Glass icon styles are now **Crystal** (a vivid translucent gem tile, dark glyph only on yellow and lime) and **Frost** (clear glass with a colored bloom and a vivid glyph); the saved names LiquidTile and ClearGlass are migrated. Verified on the emulator, light and dark, and the old behaviour was seen on the phone (v0.5.0); the new build has not been on the phone, because its release signing key is not on this machine.
- **Not done from the canvas**: glass mode (deliberately left for later); the + button still shows only on Home and History.
