# Handoff: capture Google Wallet payments as expense prompts

Status as of 2026-11-09: **research only, nothing built, nothing committed.** The owner has not yet said "go" on the logger below.
A new session should confirm that, then start at "Plan".

## Owner's request
1. A payment made with a Google Wallet pass should become a record in this app automatically (skip failed / insufficient-balance payments).
2. A top-of-screen "Android 17 style" prompt asks the owner to pick a category, showing the amount taken from the Wallet notification.

## Findings (web research + owner screenshots)

### There is no official data feed
- Google's Pay/Wallet APIs I found (Transaction Events API, token lifecycle notifications) are for merchants and payment processors, not consumer apps.
  Not read: Google's Wallet developer policies, so a policy restriction is not ruled out. Unverified.
- Workable route: `NotificationListenerService` reading the Wallet notification. Manifest needs `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`
  plus the `android.service.notification.NotificationListenerService` intent filter; the user grants "Notification access" in Settings.
  Source: developer.android.com/reference/android/service/notification/NotificationListenerService.
- Sideloaded app: since Android 13 the user must first open App info -> menu -> "Allow restricted settings" before the grant works.
  Android 15 Enhanced Confirmation Mode may add friction. Android 16/17 behaviour for this: **unverified, test on the owner's phone.**
- No Android 17 change to `NotificationListenerService` was found. The Android 17 notification changes found were custom-view memory limits
  (apps targeting API 37) and Live Update semantic colors. The official behavior-change pages were not opened. Check them before targeting API 37.

### Observed notification format (owner screenshots, Kazakhstan, Freedom bank card)
- Package: reported as `com.google.android.apps.walletnfcrel` by one third-party blog, **unverified**; one report says Wallet took over from Play services in 2024.
  Log the real package from the phone and match on it.
- Title = merchant (uppercase or mixed case, e.g. `SOME SHOP`, `Some Place`).
- Text = `<CUR><amount> with <card name> ••<last4>`, e.g. `KZT1,234.00 with Freedom Gold ••0000` (placeholder values).
  Parse into `Long` minor units, never `Double`. Thousands separator is `,`, decimals `.`.
- Notifications arrive grouped (a bundle with a count). The listener gets each child and a group summary: skip summaries (`Notification.FLAG_GROUP_SUMMARY`).
- Time: use the notification post time.
- **Not verified from screenshots:** whether the code is always a 3-letter ISO code (a USD payment might show `$`), whether declined payments post anything,
  whether pending amounts later change, how refunds look. Only successful payments were seen.
- The same purchase also produced a notification from the bank's own app (Freedom), grouped, with a different, truncated format
  (`Purchase at <merchant>…`, `-10 956 ₸…`: leading minus, space thousands separator, ₸ sign).
  Decision so far: **Wallet is the only source for v1**; the bank notification is logged for comparison only (it may show declines; that is a guess).
  `CLAUDE.md` lists bank notification parsing as a non-goal, so this needs the owner's explicit exception recorded in `docs/FEATURES.md`.

### The "top-screen widget" cannot be a Live Update
Per developer.android.com/develop/ui/views/notifications/live-update, a Live Update must be an ongoing, user-initiated, time-sensitive activity.
The page says past events should use a standard notification, that activities triggered by other parties must not generate Live Updates,
and that Live Updates are not for quick access to app features (use a widget or Quick Settings tile). Promotion also needs `POST_PROMOTED_NOTIFICATIONS`,
no `RemoteViews`, and the user can demote or revoke it. A payment is a past, third-party-triggered event, so the right shape is:
- a **heads-up notification** on a high-importance channel: amount in the title, 3-4 category action buttons (most used first), plus a "More..." action
  that opens a small category-picker activity (notifications limit visible actions).
- Optional, later: after the owner picks, `cancelNotification(key)` on the Wallet notification to reduce clutter. Wallet's reaction to that is untested.

### Privacy / battery answers already given to the owner
- Notification access is all-or-nothing at the OS level (every app's notifications are deliverable). The code must check the package name first and drop everything else unread and unstored.
- Battery: callback-driven, no polling or wake lock, so expected negligible (reasoned from the API design, **not measured**). The real risk is OEM battery managers killing the service; mitigate with a battery-optimization exemption and `requestRebind()`. Verify via Settings battery usage after a week.
- iOS-Shortcuts-style automation: Android has no built-in equivalent. Tasker/MacroDroid/Automate can trigger on notifications but use the same permission. Whether Samsung/Google routines can trigger on another app's notification: unverified.

## Plan (proposed, not yet approved)
0. Get the owner's go-ahead and their OK for the non-goal exception. Add the feature to `docs/FEATURES.md` (must / nice / skip) first, as `CLAUDE.md` requires.
1. **Commit 1: logger.** `NotificationListenerService` that records only package, title, text, post time, group-summary flag, for the Wallet package and the bank app package, into a debug screen.
   Everything else dropped before any content is read. Local only, no network. Owner runs it about a week. Tests: package filtering (other packages never stored). Docs in the same commit.
2. **Commit 2: parser + prompt**, written against the real samples the logger collected:
   pure parser (unit-tested with edge cases: `0.19`, thousands separators, a non-KZT currency, summary skipped, unknown format ignored);
   map `••last4` to an account (needs a place to store the card -> account mapping; design it, don't guess);
   heads-up notification with category actions and a picker; create the transaction only after a category is chosen; keep the three amount fields (default / real / account currency);
   dedupe key from the notification key + amount + time so a re-post does not create a second record.
3. Settings: on/off switch, a link to the notification-access screen, and the restricted-settings instructions.

## Repo rules that apply (from `CLAUDE.md`)
- One commit per feature or fix, tests and docs inside it, never lumped together.
- Money is `Long` minor units; no `Double`.
- No network calls for this feature. Do not log or commit real transaction rows; use placeholders in tests and docs (the values above are placeholders).
- Say "unverified" in code comments and docs where something was not checked.
- Android SDK / emulator may not exist in a cloud session; say plainly what could not be run.
- Develop on the branch the session was given; do not open a PR unless asked.

## Open questions for the owner
- Go-ahead for the logger, and the non-goal exception for notification parsing?
- Which category buttons should show first (most used, or fixed)?
- Should the Wallet notification be auto-dismissed after a category is chosen?
- Foreign-currency payments: does Wallet show the charged KZT amount or the original currency? Needs a real sample.
