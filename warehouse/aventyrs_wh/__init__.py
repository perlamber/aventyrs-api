"""Aventyrs combat warehouse: MongoDB analytics_events -> DuckDB.

Two steps, each runnable alone:

* ``extract`` copies MongoDB into ``raw.duckdb`` (incremental for the append-only
  analytics collections, a full refresh for the small mutable ones).
* ``build`` materializes the denormalized warehouse into ``warehouse.duckdb`` from
  ``raw.duckdb``, writing to a temporary file and renaming it over the old one so a
  reader (Metabase) never sees a half-built file.
"""
