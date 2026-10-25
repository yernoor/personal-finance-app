# FEATURES.md

Sources: owner's 14 screenshots of the original app v1.15.0+4246 (`docs/original-app/`), the owner's chat
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
| Daily reminder at a set time (owner: 20:00, "enter today's spending") | must | Seen: reminder "Внимание!" next fire 2026-10-26 20:00. Backup table `reminding` has 2 active rows. |
| Regular payments (ипотека, plus other required payments) | must | Seen: separate "Regular Payments" screen (Upcoming list, search, enable switch, add button). Mortgage fires monthly on the 20th at 10:00 (next: 2026-11-20). Owner does not need an amount on it. Notification only; auto-adding a transaction (`addAutomatically`, currently 0) is **nice**. |

Backup check (verified): both live in table `reminding`, told apart by `remindingType` (`reminding` = "Внимание!", daily, 20:00, start 2023-09-17; `regularPayments` = "ипотека", `period` Month, start 2025-10-20, `transactionType` Expense, `time` stored as `'10:0'`, i.e. not zero-padded: parse leniently). `amount` is non-null on both; value not inspected. `regular_payment_period` has 0 rows.

Android 16 notes: `POST_NOTIFICATIONS` runtime permission and exact alarms (`SCHEDULE_EXACT_ALARM`) must be handled; unverified against current docs.

## Data and sync

| Feature | Rating | Notes |
|---|---|---|
| `.mmbackup` one-time import | must | Milestone 3. |
| Google Sheets sync | must | Milestones 6-7, per docs/SYNC_DESIGN.md. |
| Local DB backup / restore | must | Milestone 8. |
| Multi-currency (KZT, USD), stored rates | must | `rates` table has 3 rows; how the original uses them for totals is unverified. |

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
  jumps back to today). Opens on the **day** by default, matching the original; More -> "History opens on" changes the default.
- History "Group by category" (owner request): a toggle beside Search adds up each category (and direction) in the shown period,
  with an operation count; tap a group to expand its entries. Transfers are grouped too, by account pair ("Main -> Deposit 2"),
  with both sides summed ("₸4,500 -> $10" for cross-currency); the reverse direction is its own group.
- Not built yet: a daily background check (a warning is only evaluated when you save an entry; WorkManager arrives with sync/reminders),
  Income-side limits, charts for a custom period, per-account chart filter (original had one).
- Unverified: for a ledger whose first month is partial, that month counts as a full month in the 3-month average.

## UX polish requests (owner, 2026-10-25), scheduled as milestone 5b
- Navigation transitions must not disappear/pop abruptly; polish enter/exit animations.
- Customizable bottom navbar (tabs shown, order) and configurable Home layout; default Home keeps the monthly totals.
- History defaults to the **day** period (matches the original's `defaultPeriod = day`) and zooms out to week / month / year.
  First weekday is Monday (backup `firstWeekday = monday`).
- Redraw all category/account icons from scratch (original art). Colors derive from the Material 3 scheme / chosen accent,
  with enough chroma and hue spread that categories stay easy to tell apart.

## Other / unverified, decide later

| Feature | Rating | Notes |
|---|---|---|
| PIN lock | nice | Settings menu has PIN. Not opened. |
| Themes / appearance | nice | Original is dark green. Own palette; follow system dark mode. |
| Photo attachments on transactions | skip | "Photo" row exists in add-transaction; no table seen in backup (`sync_file` has 0 rows). |
| Transaction search and filters | nice | Search icons seen (tag search, transfers). Operation-list filters not captured. |
| Home-screen widgets | skip | Not seen. |
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

## Navigation proposal (replaces the original's drawer; Material 3)
- **Bottom navigation bar, 4 tabs:** Home (period breakdown + projection/limit warnings), History (operations and
  transfers, search/filters), Charts, Accounts. The original hides most of these behind a hamburger drawer.
- **One add button** (FAB) on Home and History opens the add sheet with Expense / Income / Transfer as a segmented
  control, so a transfer is one tap away instead of a separate trip through Accounts. Fast entry is the daily habit.
- **Secondary screens** from a "More" entry on Home's top bar (or Settings): Categories, Regular Payments, Reminders,
  Tags, Settings, Sync status.
- **Material 3 dynamic color + dark theme** following the system (Compose `dynamicLightColorScheme` / `dynamicDarkColorScheme`,
  Android 12+; always available with minSdk 36). Accent comes from the system's wallpaper-derived palette. Unverified on the
  owner's phone: OEM skins sometimes change how the system accent is chosen. Settings toggle "Use system colors" with a static
  green fallback scheme. Category colors are user data and stay fixed; over-limit / warning colors use fixed semantic colors
  (not dynamic) so they stay recognizable. Original palette is dark green; ours is original anyway.
- Rationale: the owner's daily actions (add, glance at balances/limits) are all reachable in one tap from the bar.
  This is a judgment call, not a verified usability finding.

## Balance reconciliation (resolved)
- Screenshot totals match the backup's cached `account_balance`: Main 4,597 (459671), Deposit 36,138 (3613822),
  Deposit 2 2.31M (230507718), Freedom USD $13.56 (1356). So `account_balance` is what the original displays.
- The replay gap was a data-format quirk, not missing operations: 60 USD rows keep their amount in the real-currency
  field. With that rule every account reconciles to the cent (docs/FORMAT.md 4b). No opening-balance adjustment needed.
