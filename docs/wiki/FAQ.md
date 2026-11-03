# FAQ

**Is my data sent anywhere?** Only if you connect Google Sheets, and then only to the sheet the app created. There are no analytics or ads. The app also contacts GitHub (update check) and the National Bank of Kazakhstan (one exchange-rate request a day); no personal data goes in either request.

**Which Android versions work?** Android 16 (API 36). Older versions are not supported.

**Why is a hidden account missing from the Total?** "Hide from the Total" is on for it. Its money still counts in "All accounts".

**The daily reminder came a few minutes late.** It is a WorkManager job without exact alarms; Android may delay it under Doze.

**Why is there no warning yet this month?** "On track" warnings wait for 10 days of data. A category needs a limit set to be warned about.

**Can the app write a `.mmbackup` file?** No, it only imports them.

**Money amounts look off after an import.** Check that the account currency is right; amounts are stored in the account's currency with a stored conversion to the default currency (KZT).

**An entry has no category.** It is a balance correction (see [Entering data](Entering-Data.md)).

**Where are regular payments (mortgage etc.)?** Not built yet; the importer reads them but there is no screen.

**I forgot my PIN.** There is no reset (no server). Clear the app's data in Android settings and restore a backup file ([Privacy and backup](Privacy-and-Backup.md)).

**Is my wealth data in the Google Sheet?** No, it is on the phone only. *Back up to a file* copies it.

**Do widgets show my balances?** No. They show spending only, by design.

**Why does the widget picker show the app icon?** Previews are planned but not built yet ([Widgets](Widgets.md)).

**The exchange rates look old.** Rates are fetched once a day when the phone is online and cached; the Wealth screen says when they were last updated.
