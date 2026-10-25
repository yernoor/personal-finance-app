# Sync design: Room (phone) <-> Google Sheet (cloud, openable on PC)

## Principles
- The phone's **Room DB is the working copy** (offline-first). The **Google Sheet is the shared copy**
  that the user also opens on a PC. No custom backend, no server cost.
- The Sheet must stay a **human-friendly spreadsheet**: decimals not minor units, names not UIDs
  where possible, so the existing analysis formulas keep working.

## Auth and Drive scope (verify against current Google docs before building)
- Prefer the **`drive.file`** scope plus Sheets API v4: the app **creates** the spreadsheet itself, so no
  sensitive-scope verification should be needed. Wider `spreadsheets` scope is a sensitive scope.
- Known gotcha to verify: OAuth clients in "Testing" publish status get refresh tokens that expire after
  7 days. For a single-user app, either use non-sensitive scopes and publish "In production", or accept
  re-consent. Do not guess; check https://developers.google.com/identity and report what you found.
- On Android use Credential Manager for sign-in and the Google Identity Authorization API for the access token.
  Check current library names and versions before adding dependencies.
- The existing user-built sheet (from `mmbackup_to_xlsx.py`) is NOT reachable under `drive.file`
  unless the user opens it via the Picker. Default plan: the app creates a fresh sheet and the
  one-time `.mmbackup` import populates it. Offer a Picker flow only if the user wants to keep the old file.

## Sheet layout (tab `Transactions` keeps the converter's column order so existing SUMIFS keep working)

| Tab | Columns |
|-----|---------|
| Transactions | Date, Type, Account, Category, Tags, Amount, Currency, Net (KZT), Comment, Created, Modified, UID |
| Transfers | Date, From Account, To Account, From Amount, From Cur, To Amount, To Cur, Comment, UID, Created, Modified |
| Accounts | Account, Currency, Active, Cached Balance, UID |
| Categories | Category, Type, Used, UID, Icon, Color, Limit, Position |
| Tags | Tag, UID |
| `_deleted` (hidden) | Entity, UID, DeletedAt |
| `_meta` (hidden) | schemaVersion, lastSyncAt per device, deviceId list |

- Only live rows in `Transactions`/`Transfers`; deletions go to `_deleted` so analysis formulas never
  see tombstones.
- Rows are keyed by `UID`. Names (account/category) are display columns; on pull, resolve by UID via
  the Accounts/Categories tabs, and treat a changed name as a rename, not a new entity.
- `Net (KZT)` is derived from the default-currency amount (negative for Expense). Keep it as a value
  written by the app, not a formula, so pushes are idempotent.

## Sync algorithm (last-writer-wins per row)
1. Local change -> write Room row with fresh `modified` (UTC ISO-8601), mark dirty. Deletes become tombstones.
2. WorkManager job (on connectivity, periodic, and after edits): pull the sheet ranges, then
   merge by UID: higher `Modified` wins; present in `_deleted` newer than local `Modified` -> delete locally.
3. Push dirty rows with batch `values.batchUpdate` / `append`; never rewrite whole tabs.
4. Use `_meta` per-device cursor to avoid re-pulling everything. Full re-pull is acceptable at this scale
   (a few thousand rows).
5. Clock skew: compare using the stored UTC timestamps; on ties, prefer remote.

## PC edits
A person editing in the Sheet will not update `Modified`. Ship an **Apps Script `onEdit`** (in `/sheets/Code.gs`)
that stamps `Modified` on edited rows and generates a UID for rows added by hand. Sync must tolerate
a row with an empty UID by assigning one.

## Quotas and failure modes
- Sheets API has per-minute quotas: batch requests, back off on 429.
- Handle: user revokes access, sheet deleted/trashed, schema version in `_meta` newer than the app.
- Never silently drop a conflicting edit: log conflicts to a local table and show them in a debug screen.

## Out of scope for v1
Bank-notification parsing (user decided it is brittle; manual entry is the habit), exporting to `.mmbackup`,
recurring transactions unless the original app turns out to have them (see CLAUDE.md "Feature inventory").
