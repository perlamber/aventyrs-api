"""raw.duckdb -> the warehouse: one Parquet file per table, plus a warehouse.duckdb of views.

Runs ``models/*.sql`` in file-name order against a fresh scratch database, with ``raw.duckdb``
attached read-only as ``raw``. Every table is rebuilt from scratch on every run: the raw layer
holds the history, and the warehouse is a pure function of it.

What readers open is ``warehouse.duckdb``, which holds no data. Each fact and mart is a view
over ``tables/<name>.parquet``, and each Parquet file is replaced atomically on every build.
DuckDB opens a Parquet file again on each query, so a reader that keeps the database open for
good (Metabase does, and so would any dashboard tool) sees each new build straight away. If
the tables were stored inside warehouse.duckdb instead, a long-lived reader would keep reading
the copy it first opened until it restarted.

The views point at the Parquet files by absolute path, so a reader must see the data
directory at the same path the job wrote it to. In docker-compose, the job and Metabase both
mount the volume at ``/warehouse`` for that reason. A table added or removed by a new model
reaches a long-lived reader only after it reopens the database (restart Metabase).

Models can use one macro, ``@@sheet_block(<prefix>, <alias>)@@``. It expands to the build
columns of a ``staging.sheet_snapshot`` row joined as ``<alias>``, each renamed
``<prefix><column>``. That's how one wide row carries the same block for the actor, the
target and the combatant.
"""

from __future__ import annotations

import os
import re
from pathlib import Path

import duckdb

MODELS_DIR = Path(__file__).parent / "models"

# The columns staging.sheet_snapshot exposes per combatant. Order is the order they appear in
# every wide table.
SHEET_BLOCK_COLUMNS = [
    "character_sheet_id", "snapshot_id", "kind", "name",
    "player_id", "player_login", "player_name", "campaign_id",
    "race", "parent_race", "size_category", "total_xp", "unused_xp",
    "monster_kind", "monster_power_degree",
    "primary_title", "secondary_title", "tertiary_title", "title_abilities", "title_specializations",
    "attr_vigor", "attr_strength", "attr_dexterity", "attr_focus", "attr_instinct", "attr_gnose",
    "attr_charisma",
    "feats", "feat_count", "spells", "equipped_weapon_categories", "equipped_item_names",
    "build_hash", "sheet_json",
]

_BLOCK = re.compile(r"@@sheet_block\(\s*(\w+)\s*,\s*(\w+)\s*\)@@")


def sheet_block(prefix: str, alias: str) -> str:
    return ",\n    ".join(f"{alias}.{c} AS {prefix}{c}" for c in SHEET_BLOCK_COLUMNS)


def render(sql: str) -> str:
    return _BLOCK.sub(lambda m: sheet_block(m.group(1), m.group(2)), sql)


def _replace_file(tmp: Path, final: Path) -> None:
    # Atomic on POSIX: a query already reading the old file keeps its handle on the old inode.
    os.replace(tmp, final)


def _remove(*paths: Path) -> None:
    for path in paths:
        if path.exists():
            path.unlink()


def build(raw_path: str, out_path: str) -> list[str]:
    """Builds the warehouse and returns the names of the tables it published."""
    out = Path(out_path).resolve()
    tables_dir = out.parent / "tables"
    tables_dir.mkdir(parents=True, exist_ok=True)
    scratch = out.with_name(out.name + ".build")
    _remove(scratch, scratch.with_name(scratch.name + ".wal"))

    con = duckdb.connect(str(scratch))
    try:
        con.execute(f"ATTACH '{raw_path}' AS raw (READ_ONLY)")
        con.execute("CREATE SCHEMA staging")
        for model in sorted(MODELS_DIR.glob("*.sql")):
            try:
                con.execute(render(model.read_text()))
            except duckdb.Error as ex:
                raise RuntimeError(f"model {model.name} failed: {ex}") from ex
        tables = [r[0] for r in con.execute(
            "SELECT table_name FROM duckdb_tables() "
            "WHERE database_name = current_database() AND schema_name = 'main' AND NOT temporary ORDER BY 1").fetchall()]
        for table in tables:
            tmp = tables_dir / f"{table}.parquet.tmp"
            con.execute(f"COPY {table} TO '{tmp}' (FORMAT parquet, COMPRESSION zstd)")
            _replace_file(tmp, tables_dir / f"{table}.parquet")
    finally:
        con.close()
        _remove(scratch, scratch.with_name(scratch.name + ".wal"))

    catalog_tmp = out.with_name(out.name + ".tmp")
    _remove(catalog_tmp, catalog_tmp.with_name(catalog_tmp.name + ".wal"))
    catalog = duckdb.connect(str(catalog_tmp))
    try:
        for table in tables:
            catalog.execute(f"CREATE VIEW {table} AS FROM read_parquet('{tables_dir / table}.parquet')")
        catalog.execute("CHECKPOINT")
    finally:
        catalog.close()
    _replace_file(catalog_tmp, out)

    # Tables a model no longer creates.
    for stale in tables_dir.glob("*.parquet"):
        if stale.stem not in tables:
            stale.unlink()
    return tables
