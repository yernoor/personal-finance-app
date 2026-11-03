-- Fictional wealth data for README/wiki screenshots (docs: README "Demo data"). Wealth tables are phone-only, so they are
-- not part of the .mmbackup demo file. Apply to a debug build's database after importing data/demo/demo.mmbackup:
--   sqlite3 moneytracker.db < tools/demo_wealth.sql      (app stopped; copy the file in and out with `adb run-as`)
-- Amounts are minor units (x100). Today in the demo is 2026-11-04; the app writes today's net worth snapshot itself.
DELETE FROM manual_asset; DELETE FROM net_worth_snapshot; DELETE FROM goal; DELETE FROM bond; DELETE FROM cashflow_item;

INSERT INTO manual_asset VALUES
 ('demo-asset-flat','Flat','Asset','KZT',3000000000,'2026-11-04T00:00:00.000Z','2026-11-04T00:00:00.000Z',0),
 ('demo-asset-car','Car','Asset','KZT',500000000,'2026-11-04T00:00:00.000Z','2026-11-04T00:00:00.000Z',0),
 ('demo-loan-home','Home loan','Liability','KZT',1760000000,'2026-11-04T00:00:00.000Z','2026-11-04T00:00:00.000Z',0);

INSERT INTO goal VALUES
 ('demo-goal-trip','Summer trip',80000000,'2027-06-01',NULL,'2026-11-04T00:00:00.000Z','2026-11-04T00:00:00.000Z',0),
 ('demo-goal-cushion','Safety cushion',300000000,'2027-03-01','savings','2026-11-04T00:00:00.000Z','2026-11-04T00:00:00.000Z',0);

INSERT INTO bond VALUES
 ('demo-bond-usd','Treasury bond 2029','USD',100000,2,196000,425,2,'2029-08-15',201000,'2026-11-04T00:00:00.000Z','2026-11-04T00:00:00.000Z',0),
 ('demo-bond-kzt','Corporate bond 2030','KZT',10000000,5,48000000,1400,2,'2030-03-10',NULL,'2026-11-04T00:00:00.000Z','2026-11-04T00:00:00.000Z',0);

INSERT INTO cashflow_item VALUES
 ('demo-cf-salary','Salary','Income','KZT',65000000,0,'2026-11-04T00:00:00.000Z','2026-11-04T00:00:00.000Z',0),
 ('demo-cf-loan','Home loan payment','Obligation','KZT',12000000,0,'2026-11-04T00:00:00.000Z','2026-11-04T00:00:00.000Z',0),
 ('demo-cf-internet','Internet','Obligation','KZT',600000,1,'2026-11-04T00:00:00.000Z','2026-11-04T00:00:00.000Z',0),
 ('demo-cf-subs','Subscriptions','Obligation','KZT',900000,0,'2026-11-04T00:00:00.000Z','2026-11-04T00:00:00.000Z',0);

INSERT INTO net_worth_snapshot VALUES
 ('2025-11-04',150000000,3500000000,1880000000,120000000),
 ('2025-12-04',162500000,3500000000,1870000000,121600000),
 ('2026-01-04',175000000,3500000000,1860000000,123200000),
 ('2026-02-04',187500000,3500000000,1850000000,124800000),
 ('2026-03-04',200000000,3500000000,1840000000,126400000),
 ('2026-04-04',212500000,3500000000,1830000000,128000000),
 ('2026-05-04',225000000,3500000000,1820000000,129600000),
 ('2026-06-04',237500000,3500000000,1810000000,131200000),
 ('2026-07-04',250000000,3500000000,1800000000,132800000),
 ('2026-08-04',262500000,3500000000,1790000000,134400000),
 ('2026-09-04',275000000,3500000000,1780000000,136000000),
 ('2026-10-04',287500000,3500000000,1770000000,137600000);
