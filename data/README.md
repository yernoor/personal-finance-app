# data/

Put the **newest** export here, e.g. `data/MoneyManager_YYYYMMDDTHHMMSS.mmbackup`.
In the original app: Settings -> Backup -> export file (exact menu names unverified; locate on your phone).

This folder is git-ignored: it contains real personal financial data. Never commit it, never
paste its contents into issues or PRs, and keep the repo private.

Then run:

```bash
python tools/mmbackup.py inspect data/<file>.mmbackup          # schema + drift check
python tools/mmbackup.py extract data/<file>.mmbackup -o data/extracted
```

`data/extracted/snapshot.json` becomes the golden reference for import tests.

The export used in the earlier analysis was dated 2026-05-28. That file is NOT in this brief
(it was an upload in a previous chat and could not be retrieved). You need a fresh one anyway,
because every operation since then is only in the live app.
