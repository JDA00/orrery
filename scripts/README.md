# Scripts

Tooling that supports the Orrery but isn't part of the running application.

## `jpl/` — JPL Horizons validation tooling

These scripts fetch reference ephemeris data from the JPL Horizons API for
validating the orrery's computed positions against ground truth. Used
primarily to produce expected values for `JPLHorizonsValidationTest`.

The scripts overlap — they're successive iterations. Any of the Python
fetchers will produce usable data; pick whichever is least out of date
when you need a refresh.

- `fetch_jpl_horizons.py` — fetches vectors via the Horizons web API
  (ICRF/J2000 equatorial, barycentric)
- `get_jpl_equatorial.py` / `get_jpl_ecliptic_all.py` / `get_jpl_data.py`
  — earlier iterations of the same idea (heliocentric / ecliptic variants)
- `fetch_jpl_data.sh` / `get_jpl_ecliptic.sh` — shell wrappers around
  `curl` for the Horizons API
- `convert_jpl.py` — ecliptic→equatorial conversion with hard-coded
  reference values from a prior Horizons query
- `fetch_jpl_data.txt` — notes on manual Horizons retrieval

Consolidating these into a single canonical fetcher is a TODO.

## `textures/` — Texture asset pipeline

- `process_textures.py` — converts large planetary source textures
  (TIF/PNG, often 16K or higher) into GPU-friendly compressed DDS
  using KTX-Software's `toktx`. Requires `toktx` installed locally.

Run with `--help` for options.