#!/usr/bin/env python3
"""Read-only tooling for innim "Money manager & expenses" (.mmbackup) files.

Subcommands:
  inspect BACKUP            dump container, schema, row counts, link shapes; warn on drift
  extract BACKUP -o OUTDIR  write normalized CSVs + snapshot.json (golden test data)

Container: 8 marker bytes (0x08 x8) + ZIP{ MyFinance.db (SQLite), backup_meta (JSON) }.
Relationships live in the generic edge table `sync_link`, not in foreign keys.
Amounts are integer minor units (x100). Reading never needs backup_meta.hash.

Nothing here writes a .mmbackup. The hash algorithm is unknown (see docs/FORMAT.md).
"""
import argparse
import csv
import json
import os
import sqlite3
import sys
import tempfile
import zipfile
from collections import Counter, defaultdict
from io import BytesIO

EXPECTED_USER_VERSION = 31
ZIP_MAGIC = b"PK\x03\x04"


def open_backup(path):
    """Return (sqlite connection [read-only], marker bytes, zip entry names, meta dict)."""
    with open(path, "rb") as f:
        data = f.read()
    if (idx := data.find(ZIP_MAGIC)) < 0:
        sys.exit("No ZIP header found - not a valid .mmbackup")
    marker = data[:idx]
    with zipfile.ZipFile(BytesIO(data[idx:])) as z:
        names = z.namelist()
        if (db_name := next((n for n in names if n.lower().endswith(".db")), None)) is None:
            sys.exit(f"No .db inside backup. Entries: {names}")
        meta_name = next((n for n in names if n == "backup_meta"), None)
        meta = json.loads(z.read(meta_name)) if meta_name is not None else {}
        db_path = os.path.join(tempfile.mkdtemp(), "MyFinance.db")
        with open(db_path, "wb") as out:
            out.write(z.read(db_name))
    con = sqlite3.connect(f"file:{db_path}?mode=ro&immutable=1", uri=True)
    con.row_factory = sqlite3.Row
    return con, marker, names, meta


def units(v):
    """Minor units (x100) -> decimal. Sub-unit precision exists (e.g. 0.19), so never shortcut."""
    return None if v is None else round(v / 100, 2)


# ----------------------------------------------------------------------------- inspect
def cmd_inspect(args):
    con, marker, names, meta = open_backup(args.backup)
    print(f"marker bytes : {marker.hex()} (expected 0808080808080808)")
    print(f"zip entries  : {names}")
    print(f"backup_meta  : {meta}")
    uv = con.execute("PRAGMA user_version").fetchone()[0]
    flag = "" if uv == EXPECTED_USER_VERSION else f"  <-- DRIFT: docs/FORMAT.md was written for {EXPECTED_USER_VERSION}"
    print(f"user_version : {uv}{flag}\n")

    for name, sql in con.execute("SELECT name, sql FROM sqlite_master WHERE type='table' ORDER BY name"):
        n = con.execute(f'SELECT COUNT(*) FROM "{name}"').fetchone()[0]
        cols = [r["name"] for r in con.execute(f'PRAGMA table_info("{name}")')]
        removed = ""
        if "isRemoved" in cols:
            r = con.execute(f'SELECT COUNT(*) FROM "{name}" WHERE isRemoved=1').fetchone()[0]
            removed = f" (removed={r})"
        print(f"== {name}: {n} rows{removed}\n{sql}\n")

    print("== sync_link shapes (entityType -> otherType : count, active only)")
    for r in con.execute("SELECT entityType, otherType, COUNT(*) c FROM sync_link WHERE isRemoved=0 "
                         "GROUP BY 1,2 ORDER BY 1,2"):
        print(f"  {r['entityType']:<12} -> {r['otherType']:<12} {r['c']}")

    print("\n== transaction types:", dict(Counter(r[0] for r in con.execute(
        "SELECT type FROM 'transaction' WHERE isRemoved=0"))))
    print("== account currencies:", [(r["title"], r["currencyCode"]) for r in con.execute(
        "SELECT title, currencyCode FROM account WHERE isRemoved=0")])
    lo, hi = con.execute("SELECT MIN(date), MAX(date) FROM 'transaction' WHERE isRemoved=0").fetchone()
    print(f"== date range: {lo} .. {hi}")
    orphan = con.execute("""SELECT COUNT(*) FROM 'transaction' t WHERE t.isRemoved=0 AND NOT EXISTS
        (SELECT 1 FROM sync_link l WHERE l.entityType='Transaction' AND l.entityUid=t.uid
         AND l.otherType='Category' AND l.isRemoved=0)""").fetchone()[0]
    print(f"== active transactions without a Category link: {orphan}")


# ----------------------------------------------------------------------------- extract
def load(con):
    rows = lambda sql: [dict(r) for r in con.execute(sql)]
    accounts = {r["uid"]: r for r in rows("SELECT * FROM account")}
    categories = {r["uid"]: r for r in rows("SELECT * FROM category")}
    tags = {r["uid"]: r for r in rows("SELECT * FROM tag")}
    balances = {r[0]: r[1] for r in con.execute("SELECT uid, value FROM account_balance")}

    edges = defaultdict(lambda: defaultdict(list))  # (entityType, uid) -> otherType -> [otherUid]
    for r in con.execute("SELECT entityType, entityUid, otherType, otherUid FROM sync_link WHERE isRemoved=0"):
        edges[(r[0], r[1])][r[2]].append(r[3])

    txs = []
    for r in rows("SELECT * FROM 'transaction' WHERE isRemoved=0 ORDER BY date, created"):
        e = edges[("Transaction", r["uid"])]
        acct = (e.get("Account") or [None])[0]
        cat = (e.get("Category") or [None])[0]
        txs.append({
            "uid": r["uid"], "date": r["date"], "type": r["type"],
            "account_uid": acct, "category_uid": cat, "tag_uids": e.get("Tag", []),
            "amount": units(r["amountInAccountCurrency"]), "currency": r["accountCurrencyCode"],
            "amount_default": units(r["amountInDefaultCurrency"]),
            "amount_real": units(r.get("amountInRealCurrency")), "real_currency": r.get("realCurrencyCode"),
            "comment": r["comment"] or "", "created": r["created"], "modified": r["modified"],
        })

    trs = []
    for r in rows("SELECT * FROM transfer WHERE isRemoved=0 ORDER BY date, created"):
        e = edges[("Transfer", r["uid"])]
        to_amt = r["toAmount"] if r["toAmount"] is not None else r["fromAmount"]
        trs.append({
            "uid": r["uid"], "date": r["date"],
            "from_uid": (e.get("FromAccount") or [None])[0], "to_uid": (e.get("ToAccount") or [None])[0],
            "from_amount": units(r["fromAmount"]), "from_currency": r["fromCurrencyCode"],
            "to_amount": units(to_amt), "to_currency": r["toCurrencyCode"] or r["fromCurrencyCode"],
            "comment": r["comment"] or "", "created": r["created"], "modified": r["modified"],
        })
    return accounts, categories, tags, balances, txs, trs


def replay_balances(txs, trs):
    """Per-account balance by replaying history. Compare with account_balance to find hidden
    initial-balance mechanics; a non-zero diff is a finding, not necessarily a bug."""
    bal = defaultdict(float)
    for t in txs:
        sign = -1 if t["type"] == "Expense" else 1
        if t["account_uid"] is not None and t["amount"] is not None:
            bal[t["account_uid"]] += sign * t["amount"]
    for t in trs:
        if t["from_uid"] is not None:
            bal[t["from_uid"]] -= t["from_amount"] or 0
        if t["to_uid"] is not None:
            bal[t["to_uid"]] += t["to_amount"] or 0
    return {k: round(v, 2) for k, v in bal.items()}


def write_csv(path, rows):
    if not rows:
        return
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0]))
        w.writeheader()
        w.writerows(rows)


def cmd_extract(args):
    con, *_ = open_backup(args.backup)
    accounts, categories, tags, balances, txs, trs = load(con)
    os.makedirs(args.output, exist_ok=True)

    flat = lambda d: [{k: v for k, v in r.items()} for r in d.values() if not r.get("isRemoved")]
    write_csv(os.path.join(args.output, "accounts.csv"), flat(accounts))
    write_csv(os.path.join(args.output, "categories.csv"), flat(categories))
    write_csv(os.path.join(args.output, "tags.csv"), flat(tags))
    write_csv(os.path.join(args.output, "transactions.csv"),
              [{**t, "tag_uids": ";".join(t["tag_uids"])} for t in txs])
    write_csv(os.path.join(args.output, "transfers.csv"), trs)

    replay = replay_balances(txs, trs)
    cached = {u: units(v) for u, v in balances.items()}
    diffs = {u: round(cached[u] - replay.get(u, 0.0), 2) for u in cached}

    inc = round(sum(t["amount_default"] or 0 for t in txs if t["type"] == "Income"), 2)
    exp = round(sum(t["amount_default"] or 0 for t in txs if t["type"] == "Expense"), 2)
    snapshot = {
        "counts": {"transactions": len(txs), "transfers": len(trs),
                   "accounts": sum(1 for a in accounts.values() if not a["isRemoved"]),
                   "categories": sum(1 for c in categories.values() if not c["isRemoved"])},
        "totals_default_currency": {"income": inc, "expense": exp, "net": round(inc - exp, 2)},
        "balances_cached": cached, "balances_replayed": replay, "balance_diff_cached_minus_replayed": diffs,
    }
    with open(os.path.join(args.output, "snapshot.json"), "w", encoding="utf-8") as f:
        json.dump(snapshot, f, ensure_ascii=False, indent=2)
    print(json.dumps(snapshot["counts"]), json.dumps(snapshot["totals_default_currency"]))
    nonzero = {u: d for u, d in diffs.items() if d != 0}
    print("balance diffs (cached - replayed):", nonzero or "all zero")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("inspect"); p.add_argument("backup"); p.set_defaults(fn=cmd_inspect)
    p = sub.add_parser("extract"); p.add_argument("backup"); p.add_argument("-o", "--output", required=True)
    p.set_defaults(fn=cmd_extract)
    args = ap.parse_args()
    args.fn(args)


if __name__ == "__main__":
    main()
