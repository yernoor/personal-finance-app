# Plan: Quick add widget turns into "Spent this month"

**CAUSE FOUND AND FIXED, 2026-11-12.** It happens only in minified **release** builds (the owner's phone), never in debug builds, which is why the emulator debug replays below never showed it.
R8 merges `MoneyWidget` and all its subclasses (`MonthWidget`, `TodayWidget`, `QuickAddWidget`, ...) into one class (checked in `mapping.txt`: every widget's methods end up in class `zs`).
Glance files each receiver under its widget's class name and `getGlanceIds(MonthWidget)` / `updateAll` return the ids of every receiver filed under that name, so all placed widgets counted as Month widgets and Month's
`update` started a Month session on the Quick add id. Replayed on the emulator with a signed minified build and the pre-0.6.4 redraw (Month + Quick add placed, More -> Widget style: Quick add became "Spent in November / 0 / on pace for 0").
The phone, 0.6.3 (no guard yet), had Android binding the id to `QuickAddWidgetReceiver` (`dumpsys appwidget`), so it was not H2. 0.6.4's guard filtered those ids by receiver, so it already hid the symptom.
Fix: `updateAllWidgets` takes ids from Android per receiver (no Glance class bookkeeping), and `app/proguard-rules.pro` keeps the widget classes apart. Verified on the emulator with the same minified build: Quick add and Month stay themselves after the same step.
The hypotheses and replay notes below are kept as history.

Owner report, 2026-11-12: a Quick add widget (placed 2x1) showed the Spent this month content, squeezed into 2x1 so its text collapsed.
It "may happen after changes in balance" (after a transaction is saved). Not reproduced: the session that wrote this had no Android SDK,
emulator or Glance source, so **the cause is not found**. Everything under "Hypotheses" is unverified.

## What is known (read from the code)
- Each widget is its own Glance class with its own receiver (`Widgets.kt`, `RatesWidget.kt`); each placed widget has an `appWidgetId`.
- Every saved change runs `AppContainer.afterLedgerChange` -> `updateAllWidgets` (`MoneyApp.kt`, `Widgets.kt`); More -> Widget style and the
  Rates refresh button run it too. So "after changes in balance" fits: it is the moment every widget is redrawn.
- Before 0.6.4, `updateAllWidgets` made a fresh instance of each widget class and called Glance's `updateAll(context)`, which finds the ids to
  redraw from Glance's own class-to-receiver bookkeeping (`GlanceAppWidgetManager`), not from what Android says each id is.
- Quick add was declared 2x1 but drew three buttons meant for 4x1, which explains the collapsed text, not the swapped content.
- 0.6.4 added a guard: `updateAllWidgets` redraws an id only if `AppWidgetManager.getAppWidgetInfo(id).provider` is that widget's own receiver.
  If the swap came from our refresh, it cannot happen any more. If it still happens, the cause is elsewhere (H2 or H3).

## Hypotheses, most likely first
1. **H1, wrong id list from Glance.** `getGlanceIds(MonthWidget)` returned the Quick add id, so `MonthWidget.update` drew Month onto it.
   The 0.6.4 guard stops this. Confirm by logging when the guard skips an id (see "Diagnostics").
2. **H2, the launcher bound the id to another provider** (a launcher restore, or a rebind after an app update). Then Android itself says the
   id belongs to Month, and our guard would pass it through. Confirm with `dumpsys appwidget`.
3. **H3, a stale drawn view after an app update.** The new version installed over the old one and the widget kept the old RemoteViews
   until its next update. Check whether it appears right after `adb install -r`, with no ledger change at all.

## Where to look
- **On the phone, when it shows, before touching anything:** `adb shell dumpsys appwidget` and find the entries for `dev.yernur.moneytracker`:
  each `appWidgetId` with its `provider`. A Quick add id listed under `MonthWidgetReceiver` is H2; listed under `QuickAddWidgetReceiver`
  but drawn as Month is H1 or H3. (Output format not checked; read it rather than grep for exact words.)
- **App prefs:** `QuickAddKinds` (`quickadd` prefs) and `RateCurrencies` (`ratewidget`) are keyed by `appWidgetId`. A Quick add id missing
  from `quickadd` is normal (it defaults to Expense); a Month id appearing there would be odd. On a debug build:
  `adb shell run-as dev.yernur.moneytracker ls shared_prefs files/datastore`.
- **Glance bookkeeping:** `GlanceAppWidgetManager` (`getGlanceIds`, `updateReceiver`) in androidx.glance:glance-appwidget 1.2.0. Read its source at
  that tag; it was not reachable from the session that wrote this plan.
- **Our code:** `updateAllWidgets` and `WIDGETS` (`Widgets.kt`), `QuickAddWidget.provideGlance` (`Widgets.kt`), `MoneyWidget.provideGlance`.
- **Logcat:** filter on the app's package and on Glance's tags while saving a transaction. (Tag names unverified.)

## Reproduce
Place a Month widget and a Quick add widget side by side. After each step below, compare the screen with `dumpsys appwidget`:
1. Save an expense, three times in a row.
2. More -> Widget style, switch it back and forth.
3. Resize each widget.
4. `adb install -r` a new build, with no other change.
5. Reboot the phone. Then restart the launcher.
Try the Nothing launcher and the emulator's launcher: a bug seen only on one is the launcher's.

## Diagnostics worth adding (not built)
- In `updateAllWidgets`, log (debug builds only) each skipped id: id, widget class, the provider Android reports. A skip proves H1 and shows how often.
- A "Widgets" line in the existing crash/feedback screen listing placed ids and their providers, so the owner can paste it without adb.

## Decision after a sighting
| What you see | Meaning | Next |
|---|---|---|
| Guard logs a skipped id | H1 confirmed, the guard worked | Report to Glance, keep the guard |
| `dumpsys` shows the wrong provider for the id | H2, launcher or system | Remove and re-place the widget; test on another launcher |
| Wrong content right after install, no ledger change | H3 | Trigger `updateAllWidgets` at app start after an update |
| None of these | Cause is in our `provideGlance` or the 2x1 size | Reproduce on the emulator with `provideGlance` logging |

## Replay on the emulator, 2026-11-12 (not reproduced)
Emulator `moneytracker` (Android 16, Pixel launcher), a Month, a Budget and a Quick add (2x1, Expense) widget placed, debug build of 0.6.4. After each step the screen was compared
with `dumpsys appwidget` (each id kept its own provider throughout; ids were renumbered by the reboot, providers followed):
- Saved an expense from the Add screen (opened from the Quick add widget): all widgets redrawn correctly, Quick add still "Add expense".
- Saved an expense from the Wallet notification's category button (`walletstub/`, app process cold, no activity): same, Month total updated.
- `adb install -r` of the same build, app force-stop plus launcher force-stop, and a full emulator reboot: no content swap.
- The pre-0.6.4 `updateAllWidgets` (`updateAll` on fresh instances), put back temporarily, then a Wallet save: still no swap. So on this emulator the old code did not show it either; H1 is not shown to be the cause, and the guard is not shown to be the fix.
- Debug logging inside the guard: for every id Glance's list for each class matched what Android reports (no skip, in every run above).
- Resized the Quick add widget on the home screen (wider, narrower, back to 2x1, several times; vertical drags did not register with `adb input`), then saved via Wallet: still Quick add, `WidgetGuard` silent.
- Not done: vertical resize, the Nothing launcher, and a real phone.
(The debug-only `WidgetGuard` log added during this replay was removed again once the cause was found: the guard it watched no longer exists.)
