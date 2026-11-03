# Google Sheets sync

Keeps a copy of your data in a Google Sheet you can open on a computer. The app reaches only the sheet it created itself (the `drive.file` permission), never the rest of your Drive.

## One-time setup
1. In the Google Cloud console create an Android OAuth client for the app (package name and signing SHA-1). Steps: [docs/GOOGLE_SETUP.md](../GOOGLE_SETUP.md).
2. In the app: *More → Google Sheets*.
3. **Connect Google** (step 1 of 2), then **Create or refresh the sheet** (step 2 of 2).

## Using it
- **Sync now** reads the sheet, then writes the phone's data to it.
- A switch turns on a **weekly background sync** (needs network).
- **Open the sheet** shows it in the browser; **Forget this sheet** disconnects (the sheet stays in Drive).

## Who wins
- The very first sync only pushes, so an old sheet can never overwrite the phone.
- Afterwards a row you changed or deleted on the phone since the last sync is never overwritten; otherwise the sheet's version wins.
- The `Transactions` tab keeps its column order (Date, Type, Account, Category, Tags, Amount, Currency, Net, Comment, Created, Modified, UID) so existing formulas keep working. Amounts are decimals, not minor units.

## Known limits
A push rewrites whole tabs (a dropped connection can leave the sheet empty until the next run); a row deleted on the PC without a `_deleted` entry comes back; there is no conflict log yet. Details: [docs/SYNC_DESIGN.md](../SYNC_DESIGN.md). The live Google round trip is not yet verified.
