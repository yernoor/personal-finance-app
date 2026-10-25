# `.mmbackup` format (innim "Money manager & expenses", `ru.innim.my_finance`)

Derived empirically from a real export (`MoneyManager_20260528T115722.mmbackup`, `user_version = 31`)
during an earlier session. **Undocumented and version-sensitive.** Always run
`python tools/mmbackup.py inspect data/<latest>.mmbackup` first and diff against this file.

## 1. Container

```
[8 bytes 0x08 x8]                      marker, purpose unknown
[standard ZIP]
   ├── MyFinance.db                    SQLite 3
   └── backup_meta                     {"version":1,"hash":"<sha256 hex>"}
```

- Read: find `PK\x03\x04`, slice from there, unzip, open the DB. `hash` is not needed to read.
- `backup_meta.hash` is NOT sha256 of the DB bytes (ruled out: raw DB, zeroed change counter,
  first page only, version-prefixed/suffixed). Algorithm unknown. **Therefore this project
  imports `.mmbackup` but does not export it.** Do not decompile the original app to find out;
  see CLAUDE.md "Non-goals".

## 2. Relational model: no foreign keys

All relationships are rows in an edge table:

```sql
CREATE TABLE sync_link (
  entityType TEXT, entityUid TEXT, otherType TEXT, otherUid TEXT,
  modified TEXT, isRemoved INTEGER,
  PRIMARY KEY (entityType, entityUid, otherType, otherUid));
```

| entity      | edges (otherType)                                   |
|-------------|-----------------------------------------------------|
| Transaction | `Account` (1), `Category` (1), `Tag` (0..n, ~1.5% of txns) |
| Transfer    | `FromAccount` (1), `ToAccount` (1)                  |

Only edges with `isRemoved = 0` are live.

## 3. Tables (columns known so far)

Confirmed from CREATE statements: `transaction`, `category`, `sync_link`.
Columns for `account`, `tag`, `transfer`, `account_balance` are only the ones the reference converter
reads; **inspect the real file for the full definitions.**

```sql
transaction(uid PK, created, modified,            -- ISO-8601 UTC e.g. 2023-09-15T02:18:07.639Z
  type,                                           -- 'Income' | 'Expense'
  amountInDefaultCurrency INT, amountInRealCurrency INT, realCurrencyCode,
  amountInAccountCurrency INT, accountCurrencyCode,
  date,                                           -- 'YYYY-MM-DD'
  comment, v INT, isRemoved INT)
category(uid PK, created, modified, title, type, icon, color INT, useCount INT, lastUse,
  isRemoved INT, limitAmount INT, position INT)   -- limitAmount looks like a per-category budget
account(uid, title, currencyCode, isActive, isRemoved, ...)   -- default account uid is literally 'main'
tag(uid, name, isRemoved, ...)
transfer(uid, created, modified, date, fromAmount, fromCurrencyCode, toAmount, toCurrencyCode,
  comment, isRemoved, ...)
account_balance(uid, value)                       -- cached balance, minor units; uid = account uid (assumed)
```

Other tables seen: `sync_data`, `sync_action`, `sync_file` (the original's cloud sync; not reproduced).

## 4. Conventions and gotchas

- **Amounts are integer minor units ×100** (`1353700` = 13,537.00). Smallest observed non-zero amount
  is 0.19, so keep 2-decimal precision; do not round to whole units.
- Amounts are stored **positive**; sign comes from `type` (Expense = negative).
- Three amount fields: default currency (the app's stored conversion, not a live rate), "real" currency
  (nullable), account currency. For USD accounts, `amountInDefaultCurrency` is the app's conversion
  at entry time. Preserve all three on import.
- Transfers can have different from/to amounts and currencies (cross-currency).
- Soft deletes via `isRemoved`. In the reference export: 45 transactions and 4 transfers were removed.
- Default categories use readable uids; user-created ones use UUID v4.
- Title strings are Russian-language (e.g. категории `Дом`, `Досуг`, `Кафе`). Keep UTF-8 end to end.

## 4b. Drift found 2026-10-25 (export `2026_10_25_21_48_27_383151.mmbackup`, `user_version = 35`)

Verified with `inspect` (counts are active rows):
- New columns: `account.comment/isArchived/isFavorite/ignoreInBalance/position/icon/color`, `category.isArchived`,
  `reminding.time/customPeriodData`. `transaction`, `transfer`, `sync_link`, `tag` (adds `useCount/lastUse`) otherwise as above.
- Tables not listed before: `reminding` (2 rows; recurring reminders exist), `regular_payment_period` (0 rows),
  `rates` (3 rows, currency pair -> value), `colors`, `syncable_settings`, `app_notifications_*`, `user`, `user_profile`,
  `in_remote_settings`, `sync_persistent`. `sync_action` has 67k rows (original's change log; not reproduced).
- Counts: 4,497 txns (47 removed), 760 transfers (4 removed), 7 accounts, 26 categories, 2 tags, 65 tag links.
- Totals (default currency): Income 30,720,226.58 / Expense 28,364,485.60 / Net 2,355,740.98.
- 21 active transactions have no Category link.
- `account.ignoreInBalance = 1` for 6 of 7 accounts. Screenshot (Accounts screen) shows an eye-off icon on exactly the
  non-Main accounts and a "Total" that counts only Main, with a second figure including all: so it means "excluded from
  the headline Total". Verified against the screenshot, not against decompiled code.
- Cached `account_balance` equals what the original app displays (Main 4,596.71, Deposit 36,138.22, Freedom USD 13.56).
  Owner says Freedom USD's replay gap is unlogged investment purchases (owner's explanation, unverified).

**Balance mismatch (unresolved):** cached `account_balance` differs from replayed history for 2 USD accounts
(`8d72febf…` +0.78, `bf805b9e…` -1349.95). Known contributing cause: 8 active Expense rows on the
Freedom USD account have `amountInAccountCurrency = NULL` (only the real/default amounts are set), so a replay
on the account-currency field skips them. The remaining part is unexplained; do not design balance logic yet.

## 5. Reference numbers (OLD export, superseded by 4b) from the 2026-05-28 export (use as a smoke test, then replace with newest)

3,653 active transactions · 609 active transfers · 7 accounts (KZT and USD) · 25 categories ·
Income 25,403,384.03 / Expense 21,089,458.35 / Net 4,313,925.68 (default currency).

These came from the earlier session's transcript, not from a file I could re-open. The newest export
will differ; `tools/mmbackup.py extract` writes the authoritative `snapshot.json` for it.
