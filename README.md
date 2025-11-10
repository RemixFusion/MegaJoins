# MegaJoins — Bungee/Waterfall Join Analytics


Overview
--------
MegaJoins tracks player joins on a BungeeCord/Waterfall proxy, grouped by hostname.
It persists data in SQLite (default) or MySQL so you can query current counts, historical totals,
unique players, per-domain rollups (last two labels, e.g., example.com),
and per-subdomain details. You can also filter by time ranges and query by
player name (offline UUID) or UUID/prefix.

Requirements
------------
- Java 17+
- BungeeCord or Waterfall (1.20+ API target)
- Write access for the plugin’s data directory

Installation
------------
1) Place the plugin JAR into: plugins/
2) Start the proxy once to generate the data folder:
   plugins/MegaJoins/
3) Open `plugins/MegaJoins/config.yml` to choose the storage backend:
   - `sqlite` (default) writes to `plugins/MegaJoins/data.db`.
   - `mysql` connects using the host/port/database credentials you supply.
4) Restart/reload the proxy after adjusting storage settings.
5) Give your staff the permission node:
   megajoins.admin

Permissions
-----------
- megajoins.admin — required for all commands

Time Range Syntax
-----------------
Use N + unit: h (hours), d (days), w (weeks), m (months ≈ 30d), y (years).
Examples: 12h, 1d, 2w, 3m, 1y
Use 'all' for all-time where applicable.

Commands
--------
/megajoins
  Shows the help menu (also available as /megajoins help).

/megajoins current
  Displays current online counts grouped by DOMAIN (last two labels) and then
  broken down by each SUBDOMAIN/hostname under that domain.

/megajoins all
  Displays all-time totals grouped by DOMAIN and each SUBDOMAIN/hostname.

/megajoins <range>
  Displays totals for the specified time window (e.g., /megajoins 1d, /megajoins 2w).

/megajoins unique <range|all>
  Displays UNIQUE player counts (distinct offline UUIDs) grouped by DOMAIN and
  each SUBDOMAIN/hostname. Supports ranges and 'all'.

/megajoins player <name> [range]
  Displays totals for a specific player (identified via offline UUID derived from
  <name>), grouped by DOMAIN and SUBDOMAIN/hostname. Optional time range; defaults to all.
  Example: /megajoins player Notch 1m

/megajoins uuid <uuid|prefix> [range]
  Displays totals for a specific UUID (trimmed, lowercase; hyphenated OK) or a UUID
  prefix, grouped by DOMAIN and SUBDOMAIN/hostname. Optional time range; defaults to all.
  Example: /megajoins uuid abc123
  Example: /megajoins uuid 069a79f4-44e9-4726-a5be-fca90e38aaf5 1y

/megajoins domain <domain|sub.domain>
  If given a registrable domain (last two labels, e.g., example.com):
    - Shows domain totals (ALL-TIME and UNIQUE), then per-subdomain breakdowns (ALL-TIME and UNIQUE).
  If given a specific subdomain (e.g., play.example.com):
    - Shows ALL-TIME and UNIQUE for that exact subdomain.

Data Storage
------------
- Config: plugins/MegaJoins/config.yml
- SQLite: plugins/MegaJoins/data.db (default)
- MySQL: configurable host/port/database with connection pool
- Table schema: joins(hostname TEXT/VARCHAR, uuid TEXT/CHAR(32), player_name TEXT/VARCHAR, ts INTEGER seconds)

Notes & Behavior
----------------
- DOMAIN grouping uses the last two labels (e.g., sub.a.example.com -> example.com).
- Player lookups use an offline UUID based on the name to ensure stability across proxies.
- All commands require: megajoins.admin

Examples
--------
# Show joins for the last day (by domain, then each hostname)
/megajoins 1d

# Unique joins since last month
/megajoins unique 1m

# Player by name (all-time)
/megajoins player Notch

# UUID/prefix (time-scoped)
/megajoins uuid abc123 2w

# Domain rollup and subdomain breakdowns (all-time + unique)
/megajoins domain example.com

Performance
-----------
- Join inserts run on a single-threaded async worker to avoid blocking the proxy thread.
- Queries run on demand; for very large datasets consider archiving older rows periodically.

Troubleshooting
---------------
- Ensure Java 17+ and Waterfall/Bungee target 1.20+.
- Permission missing? Grant: megajoins.admin
- Database path not found? The plugin creates it on first run at: plugins/MegaJoins/data.db
- Switching to MySQL? Update config.yml, ensure credentials are correct, and allow the bundled MySQL driver network access.
