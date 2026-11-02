# Google setup for the Sheets copy (milestone 6)

The app needs **no client id, no secret and no backend**. Google matches the app to an *Android OAuth client* by its package name
and signing-certificate SHA-1, so the only things to do are in the Google Cloud console. Nothing from this guide goes into git.

## What was verified (checked against Google's docs on 2026-10-26)

| Question | Answer | Source |
|---|---|---|
| How does the app get access on Android? | Authorization API (`play-services-auth` 22.0.0): `authorize()` returns an access token, or a consent screen to show first. | [developer.android.com/identity/authorization](https://developer.android.com/identity/authorization) |
| Do tokens expire? Is there a refresh token on the phone? | Access tokens last **1 hour**. The phone holds no refresh token (Google discourages it); calling `authorize()` again is silent while the permission is not revoked. | same page |
| Is a Web client or backend needed? | Only for offline access (refresh tokens). Not used here. | same page |
| Which scope? | `drive.file`: **non-sensitive**, only files the app itself created. Google recommends it for apps that create their own files. | [Drive API scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth) |
| Can `spreadsheets.create` use it? | Yes (`drive`, `drive.file` or `spreadsheets`). | [spreadsheets.create](https://developers.google.com/workspace/sheets/api/reference/rest/v4/spreadsheets/create) |
| The "Testing" 7-day rule | In **Testing** status, a test user's authorization **expires 7 days after consent** for any scope beyond name, email and profile, so `drive.file` is affected. In production it is not. | [Manage app audience](https://support.google.com/cloud/answer/15549945) |
| Quotas | 60 write requests per minute per user; about 2 MB per request recommended; retry 429 with exponential backoff. The app uploads in batches of 1,500 rows. | [Sheets API limits](https://developers.google.com/workspace/sheets/api/limits) |

Not stated explicitly by the docs I read (so unverified): whether publishing an app that uses only non-sensitive scopes needs
Google's verification. The docs say the "Unverified app" warning appears for *sensitive or restricted* scopes, which `drive.file` is not.

## Steps for the owner (about 10 minutes)

1. Open [console.cloud.google.com](https://console.cloud.google.com) and create a project (e.g. "Money Tracker").
2. **APIs & Services → Library → Google Sheets API → Enable.** (If the app later reports that the Drive API is disabled, enable that too.)
3. **Google Auth platform → Branding:** app name, your email as support and developer contact.
4. **Audience:** user type *External*. Add your own Google account as a *test user*.
5. **Clients → Create client → Android:**
   - Package name: `dev.yernur.moneytracker`
   - SHA-1 certificate fingerprint of the build you run. For the debug build made on this Mac:
     `2A:12:B0:39:94:8E:7D:0A:BD:09:6E:A5:A3:F1:EA:EC:5F:C8:62:53`
     (from `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`).
   - The release APK (signed with the key in docs/RELEASING.md) has a different keystore: create a second Android client, same package name, with SHA-1
     `03:4B:6A:06:BC:C9:F8:8E:D7:52:54:EE:41:BF:65:9E:0A:0B:E5:5C` (a public fingerprint, not a secret).
6. **Audience → Publish app** (status *In production*). Without this, the permission lapses every 7 days and you must reconnect.
7. In the app: **More → Google Sheets**. Step 1 **Connect Google** (consent screen, creates nothing), then step 2 **Create the sheet and upload my data**.

## If something goes wrong

| What the app says | Cause |
|---|---|
| "Google sign-in is not set up for this build yet" (error code 10) | No Android OAuth client for this package + SHA-1, or the SHA-1 does not match the build you installed. |
| Google screen "Access blocked … developer-approved testers" (403 access_denied) | The consent screen is in *Testing* and your account is not under Audience → Test users (step 4), or publish it (step 6). |
| "Range … exceeds grid limits" | Fixed in the app: each tab is now sized to its data when the sheet is created. |
| "Google refused access … Sheets API" (HTTP 403) | The Google Sheets API is not enabled in the project (step 2). |
| "Google rejected the sign-in" (HTTP 401) | The token was revoked; connect again. |
| Asked to connect again after a week | The consent screen is still in *Testing* (step 6). |
| "Google Play services are not available" | The device or emulator image has no Play services. |
