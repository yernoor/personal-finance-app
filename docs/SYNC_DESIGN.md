# Sync design: Room (phone) <-> Google Sheet (cloud, openable on PC)

## Principles
- The phone's **Room DB is the working copy** (offline-first). The **Google Sheet is the shared copy**
  that the user also opens on a PC. No custom backend, no server cost.
- The Sheet must stay a **human-friendly spreadsheet**: decimals not minor units, names not UIDs
  where possible, so the existing analysis formulas keep working.

## Auth and Drive scope (verified 2026-10-26; sources and setup steps in docs/GOOGLE_SETUP.md)
- Scope: **`drive.file`** (non-sensitive) plus Sheets API v4. The app **creates** the spreadsheet itself, so it can reach only that file.
  `spreadsheets.create` accepts `drive.file`. The wider `spreadsheets` scope is sensitive and not needed.
- On Android only the **Authorization API** is needed: a one-hour access token, no refresh token on the phone, silent renewal while the
  permission stands. **Credential Manager sign-in is not used**: it proves identity (an ID token), and nothing here needs identity.
  No client id or secret is in the app; the Google Cloud Android client is matched by package name + signing SHA-1.
- **"Testing" status expires the authorization after 7 days** for any scope beyond name/email/profile, `drive.file` included. Publish the
  consent screen **In production** (non-sensitive scope; the "Unverified app" warning is for sensitive/restricted scopes).
- HTTP: plain `HttpURLConnection` (no client library); 429/5xx retried with exponential backoff, 401 gets one fresh token.
- The existing user-built sheet (from `mmbackup_to_xlsx.py`) is NOT reachable under `drive.file` unless opened through the Picker.
  The app creates a fresh sheet and uploads the local data (which came from the `.mmbackup` import) instead.

## Sheet layout (tab `Transactions` keeps the converter's column order so existing SUMIFS keep working)

| Tab | Columns |
|-----|---------|
| Transactions | Date, Type, Account, Category, Tags, Amount, Currency, Net (KZT), Comment, Created, Modified, UID |
| Transfers | Date, From Account, To Account, From Amount, From Cur, To Amount, To Cur, Comment, UID, Created, Modified |
| Accounts | Account, Currency, Active, Cached Balance, UID |
| Categories | Category, Type, Used, UID, Icon, Color, Limit, Position |
| Tags | Tag, UID |
| `_deleted` (hidden) | Entity, UID, DeletedAt |
| `_meta` (hidden) | Key, Value |

- `_meta` rows (key / value): `schemaVersion` / 1, `deviceId` / this phone's id, `lastSyncAt:<deviceId>` / UTC time of its last sync.
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

## Import from the sheet (built, one-way pull)
The pull half of "Sync now" (the standalone Import button was removed: it re-created hand-typed rows on every run) reads every tab (`values:batchGet`, unformatted values) and merges it into Room by UID
(`SheetParser`, `SheetsPull`). The sheet wins for the rows it has, except rows the phone changed or deleted after `lastSyncAt` (then the phone wins); rows only the phone has stay. Account/category/tag names resolve to UIDs
through the tabs; a row typed by hand without a UID gets one; an unknown tag name creates a tag; an unknown account skips the row (warning).
Columns the sheet does not show (account icon/colour/flags, real-currency amounts) are kept from the phone's row. UIDs in `_deleted` that are
not in their tab tombstone the transaction/transfer (accounts, categories and tags are never deleted by an import). Unverified against a live
Google round trip; covered by `SheetsPullTest` with a fake transport. Not done: automatic two-way sync (M7), writing `.mmbackup`.

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
