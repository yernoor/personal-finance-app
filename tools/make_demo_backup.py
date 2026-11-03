#!/usr/bin/env python3
"""Build a fictional `.mmbackup` for README/wiki screenshots. Stdlib only, deterministic.

    python tools/make_demo_backup.py data/demo.mmbackup [--today 2026-11-04]

The schema is the one in docs/FORMAT.md (user_version 35); every row is invented. The integrity hash is a
placeholder (the app does not check it).
"""
import argparse, datetime as dt, io, json, random, sqlite3, tempfile, zipfile
from pathlib import Path

SCHEMA = """
CREATE TABLE account (uid TEXT PRIMARY KEY, created TEXT, modified TEXT, title TEXT, icon TEXT, color INTEGER,
  currencyCode TEXT, ignoreInBalance INTEGER, isActive INTEGER, isRemoved INTEGER, position INTEGER,
  comment TEXT, isArchived INTEGER NOT NULL DEFAULT 0, isFavorite INTEGER NOT NULL DEFAULT 0);
CREATE TABLE "transaction" (uid TEXT PRIMARY KEY, created TEXT, modified TEXT, type TEXT, amountInDefaultCurrency INTEGER,
  amountInRealCurrency INTEGER, realCurrencyCode TEXT, amountInAccountCurrency INTEGER, accountCurrencyCode TEXT,
  date TEXT, comment TEXT, v INTEGER, isRemoved INTEGER);
CREATE TABLE transfer (uid TEXT PRIMARY KEY, created TEXT, modified TEXT, fromAmount INTEGER, fromCurrencyCode TEXT,
  toAmount INTEGER, toCurrencyCode TEXT, date TEXT, comment TEXT, isRemoved INTEGER);
CREATE TABLE tag (uid TEXT PRIMARY KEY, created TEXT, modified TEXT, isRemoved INTEGER, name TEXT, useCount INTEGER, lastUse TEXT);
CREATE TABLE category (uid TEXT PRIMARY KEY, created TEXT, modified TEXT, title TEXT, type TEXT, icon TEXT, color INTEGER,
  useCount INTEGER, lastUse TEXT, isRemoved INTEGER, limitAmount INTEGER, position INTEGER, isArchived INTEGER NOT NULL DEFAULT 0);
CREATE TABLE sync_link (entityType TEXT, entityUid TEXT, otherType TEXT, otherUid TEXT, modified TEXT, isRemoved INTEGER,
  PRIMARY KEY (entityType, entityUid, otherType, otherUid));
CREATE TABLE reminding (uid TEXT PRIMARY KEY, created TEXT, modified TEXT, title TEXT, text TEXT, period TEXT,
  transactionType TEXT, startDate TEXT, standardUid TEXT, enabled INTEGER, isRemoved INTEGER, amount INTEGER,
  remindingType TEXT, endDate TEXT, addAutomatically INTEGER, time TEXT, customPeriodData TEXT);
CREATE TABLE rates (fromCurrencyCode TEXT, toCurrencyCode TEXT, modified TEXT, value NUMERIC, PRIMARY KEY (fromCurrencyCode, toCurrencyCode));
CREATE TABLE account_balance (uid TEXT PRIMARY KEY, value INTEGER);
PRAGMA user_version = 35;
"""

USD_RATE = 500.0  # KZT per USD, invented
# (uid, title, icon, color, currency, ignoreInBalance)
ACCOUNTS = [
    ("main", "Everyday card", "bank", 4294036486, "KZT", 0),
    ("savings", "Savings", "piggy_bank", 4285774049, "KZT", 1),
    ("usd", "Dollar stash", "dollar", 4288394894, "USD", 1),
]
# (uid, title, type, icon, color, monthly limit in major units or None)
CATEGORIES = [
    ("DefaultProducts", "Groceries", "Expense", "products", 4285774049, 180000),
    ("DefaultCafe", "Cafes", "Expense", "cafe", 4294036486, 60000),
    ("DefaultTransport", "Transport", "Expense", "transport", 4281235663, 40000),
    ("DefaultHome", "Home", "Expense", "home", 4281235663, None),
    ("DefaultLeisure", "Leisure", "Expense", "purse", 4284985157, 50000),
    ("DefaultHealth", "Health", "Expense", "heart", 4294325557, None),
    ("DefaultSport", "Sport", "Expense", "sport", 4284985157, 25000),
    ("clothes", "Clothes", "Expense", "clothes", 4285774049, None),
    ("DefaultSalary", "Salary", "Income", "prepaid", 4281235663, None),
    ("DefaultPresent", "Gifts", "Income", "present", 4292562311, None),
    ("other_income", "Other income", "Income", "other", 4284985157, None),
]
TAGS = ["work", "weekend", "family"]
# category uid -> (entries per month, min, max in KZT, comments)
SPENDING = {
    "DefaultProducts": (14, 3000, 14000, ["Supermarket", "Market", "Bakery", "Weekly shop"]),
    "DefaultCafe": (9, 1800, 7500, ["Lunch", "Coffee", "Dinner out", "Brunch"]),
    "DefaultTransport": (11, 600, 4500, ["Taxi", "Bus card", "Fuel", "Metro"]),
    "DefaultHome": (2, 9000, 60000, ["Utilities", "Internet", "Cleaning"]),
    "DefaultLeisure": (4, 4000, 20000, ["Cinema", "Concert", "Books", "Games"]),
    "DefaultHealth": (2, 3000, 18000, ["Pharmacy", "Dentist"]),
    "DefaultSport": (2, 8000, 15000, ["Gym", "Swimming pool"]),
    "clothes": (1, 12000, 45000, ["Shoes", "Jacket"]),
}


def stamp(day: dt.date, minute: int = 0) -> str:
    return f"{day.isoformat()}T{9 + minute // 60 % 12:02d}:{minute % 60:02d}:00.000Z"


def build(db_path: Path, today: dt.date) -> None:
    rnd = random.Random(7)
    db = sqlite3.connect(db_path)
    db.executescript(SCHEMA)
    t0 = stamp(today - dt.timedelta(days=200))
    now = stamp(today)

    for pos, (uid, title, icon, color, cur, ignore) in enumerate(ACCOUNTS):
        db.execute("INSERT INTO account VALUES (?,?,?,?,?,?,?,?,1,0,?,NULL,0,0)",
                   (uid, t0, now, title, icon, color, cur, ignore, pos))
    for pos, (uid, title, kind, icon, color, limit) in enumerate(CATEGORIES):
        db.execute("INSERT INTO category VALUES (?,?,?,?,?,?,?,0,NULL,0,?,?,0)",
                   (uid, t0, now, title, kind, icon, color, limit * 100 if limit else 0, pos))
    for i, name in enumerate(TAGS):
        db.execute("INSERT INTO tag VALUES (?,?,?,0,?,0,NULL)", (f"tag-{i}", t0, now, name))
    db.execute("INSERT INTO rates VALUES ('USD','KZT',?,?)", (now, USD_RATE))
    db.execute("INSERT INTO rates VALUES ('KZT','USD',?,?)", (now, 1 / USD_RATE))
    db.execute("INSERT INTO reminding VALUES ('everyDayExpenses',?,?,'Heads up!','Enter today''s spending','Day',NULL,?,"
               "'everyDayExpenses',1,0,0,'reminding',NULL,0,'20:00',NULL)", (t0, now, (today - dt.timedelta(days=200)).isoformat()))

    balance = {"main": 0, "savings": 0, "usd": 0}
    n = 0

    def link(entity, uid, other, other_uid):
        db.execute("INSERT INTO sync_link VALUES (?,?,?,?,?,0)", (entity, uid, other, other_uid, now))

    def txn(day, kind, account, category, kzt, comment, tag=None):
        nonlocal n
        n += 1
        uid = f"demo-tx-{n:05d}"
        cur = "USD" if account == "usd" else "KZT"
        minor = kzt * 100 if cur == "KZT" else round(kzt / USD_RATE * 100)
        db.execute('INSERT INTO "transaction" VALUES (?,?,?,?,?,?,?,?,?,?,?,2,0)',
                   (uid, stamp(day, n % 600), stamp(day, n % 600), kind, kzt * 100, minor, cur, minor, cur,
                    day.isoformat(), comment))
        link("Transaction", uid, "Account", account)
        if category:
            link("Transaction", uid, "Category", category)
        if tag:
            link("Transaction", uid, "Tag", tag)
        balance[account] += minor if kind == "Income" else -minor

    def transfer(day, src, dst, kzt, comment):
        nonlocal n
        n += 1
        uid = f"demo-tr-{n:05d}"
        sc = "USD" if src == "usd" else "KZT"
        dc = "USD" if dst == "usd" else "KZT"
        out = kzt * 100 if sc == "KZT" else round(kzt / USD_RATE * 100)
        inn = kzt * 100 if dc == "KZT" else round(kzt / USD_RATE * 100)
        db.execute("INSERT INTO transfer VALUES (?,?,?,?,?,?,?,?,?,0)",
                   (uid, stamp(day, n % 600), stamp(day, n % 600), out, sc, inn, dc, day.isoformat(), comment))
        link("Transfer", uid, "FromAccount", src)
        link("Transfer", uid, "ToAccount", dst)
        balance[src] -= out
        balance[dst] += inn

    start = today.replace(day=1) - dt.timedelta(days=150)
    day = start
    while day <= today:
        if day.day == 2:
            txn(day, "Income", "main", "DefaultSalary", 650000, "Monthly salary", "tag-0")
            transfer(day, "main", "savings", 150000, "Monthly saving")
        if day.day == 15 and day.month % 2 == 0:
            transfer(day, "main", "usd", 100000, "Buy dollars")
        if day.day == 22:
            txn(day, "Income", "main", "DefaultPresent", rnd.choice([15000, 30000]), "Birthday gift")
        for uid, (per_month, lo, hi, comments) in SPENDING.items():
            if rnd.random() < per_month / 30:
                tag = rnd.choice(["tag-1", "tag-2"]) if rnd.random() < 0.12 else None
                txn(day, "Expense", "main", uid, rnd.randrange(lo, hi, 100), rnd.choice(comments), tag)
        day += dt.timedelta(days=1)
    # a few fixed rows so the newest screens are never empty
    txn(today, "Expense", "main", "DefaultCafe", 3200, "Coffee")
    txn(today - dt.timedelta(days=1), "Expense", "main", "DefaultProducts", 8600, "Supermarket")
    txn(today - dt.timedelta(days=1), "Expense", "main", "DefaultTransport", 1500, "Taxi")

    # opening money so no balance goes negative; dated before the first entry
    for acct, kzt in (("main", 400000), ("savings", 900000)):
        txn(start - dt.timedelta(days=1), "Income", acct, None, kzt, "")
    txn(start - dt.timedelta(days=1), "Income", "usd", None, 600000, "")

    for acct, value in balance.items():
        db.execute("INSERT INTO account_balance VALUES (?,?)", (acct, value))
    db.commit()
    db.close()


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("out", type=Path)
    ap.add_argument("--today", type=dt.date.fromisoformat, default=dt.date.today())
    args = ap.parse_args()
    with tempfile.TemporaryDirectory() as tmp:
        db_path = Path(tmp) / "MyFinance.db"
        build(db_path, args.today)
        buf = io.BytesIO()
        with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
            z.write(db_path, "MyFinance.db")
            z.writestr("backup_meta", json.dumps({"version": 1, "hash": "0" * 64}))
    args.out.write_bytes(b"\x08" * 8 + buf.getvalue())
    print(f"wrote {args.out}")


if __name__ == "__main__":
    main()
