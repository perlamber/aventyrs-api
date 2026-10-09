"""MongoDB -> raw.duckdb.

``analytics_events`` and ``analytics_sheet_snapshots`` are append-only on the API side, so
they're loaded incrementally from a watermark. They're the only source of facts: nothing is
reconstructed from a Scene's own action history. Everything else (players, campaigns) is
small and mutable lookup data, so it's replaced wholesale on every run.

Every document is stored as plain JSON (see :func:`to_plain`). The SQL models read it with
DuckDB's JSON operators and never deal with BSON types.
"""

from __future__ import annotations

import base64
import datetime as dt
import json
import uuid
from decimal import Decimal
from typing import Any, Iterable

import duckdb
from bson import Binary, Decimal128, ObjectId
from pymongo import MongoClient

RAW_SCHEMA = """
CREATE TABLE IF NOT EXISTS analytics_events (
    id VARCHAR PRIMARY KEY, type VARCHAR, occurred_at TIMESTAMPTZ, scene_id VARCHAR, doc JSON);
CREATE TABLE IF NOT EXISTS sheet_snapshots (
    id VARCHAR PRIMARY KEY, character_sheet_id VARCHAR, kind VARCHAR, first_seen_at TIMESTAMPTZ, sheet JSON);
CREATE TABLE IF NOT EXISTS players (id VARCHAR PRIMARY KEY, login VARCHAR, name VARCHAR, role VARCHAR);
CREATE TABLE IF NOT EXISTS campaign_sessions (
    campaign_id VARCHAR, campaign_name VARCHAR, session_number INTEGER, status VARCHAR,
    started_at TIMESTAMPTZ, ended_at TIMESTAMPTZ);
CREATE TABLE IF NOT EXISTS load_state (source VARCHAR PRIMARY KEY, loaded_at TIMESTAMPTZ, row_count BIGINT);
"""

# A frame recorded in the same millisecond as the watermark may land after the previous run
# read it. Re-reading a small overlap is harmless because inserts are keyed by id.
WATERMARK_OVERLAP = dt.timedelta(minutes=5)


def to_plain(value: Any) -> Any:
    """Turns a BSON document into JSON-native values: ids and decimals become strings or
    numbers, and datetimes become ISO-8601 UTC. Spring's ``_class`` hints are dropped."""
    if isinstance(value, dict):
        return {k: to_plain(v) for k, v in value.items() if k != "_class"}
    if isinstance(value, (list, tuple, set)):
        return [to_plain(v) for v in value]
    if isinstance(value, (ObjectId, uuid.UUID)):
        return str(value)
    if isinstance(value, Binary):
        return base64.b64encode(bytes(value)).decode("ascii")
    if isinstance(value, Decimal128):
        return float(value.to_decimal())
    if isinstance(value, Decimal):
        return float(value)
    if isinstance(value, dt.datetime):
        if value.tzinfo is None:
            value = value.replace(tzinfo=dt.timezone.utc)
        return value.isoformat()
    return value


def dumps(value: Any) -> str:
    return json.dumps(to_plain(value), ensure_ascii=False)


def connect_raw(path: str) -> duckdb.DuckDBPyConnection:
    con = duckdb.connect(path)
    con.execute(RAW_SCHEMA)
    return con


def _watermark(con: duckdb.DuckDBPyConnection, table: str, column: str) -> dt.datetime | None:
    row = con.execute(f"SELECT max({column}) FROM {table}").fetchone()
    return None if row[0] is None else row[0] - WATERMARK_OVERLAP


def _mark_loaded(con: duckdb.DuckDBPyConnection, source: str, rows: int) -> None:
    con.execute("INSERT OR REPLACE INTO load_state VALUES (?, now(), ?)", [source, rows])


def _insert(con: duckdb.DuckDBPyConnection, table: str, rows: Iterable[tuple], verb: str) -> int:
    rows = list(rows)
    if rows:
        placeholders = ", ".join("?" * len(rows[0]))
        con.executemany(f"{verb} INTO {table} VALUES ({placeholders})", rows)
    return len(rows)


def _upsert(con: duckdb.DuckDBPyConnection, table: str, rows: Iterable[tuple]) -> int:
    """Adds rows to a keyed table. A row whose key is already present replaces it."""
    return _insert(con, table, rows, "INSERT OR REPLACE")


def _replace(con: duckdb.DuckDBPyConnection, table: str, rows: Iterable[tuple]) -> int:
    """Replaces a table's whole content."""
    con.execute(f"DELETE FROM {table}")
    return _insert(con, table, rows, "INSERT")


def extract_events(db, con) -> int:
    since = _watermark(con, "analytics_events", "occurred_at")
    query = {} if since is None else {"occurredAt": {"$gte": since}}
    rows = (
        (str(doc["_id"]), doc.get("type"), doc.get("occurredAt"), doc.get("sceneId"), dumps(doc))
        for doc in db.analytics_events.find(query).sort("occurredAt", 1)
    )
    count = _upsert(con, "analytics_events", rows)
    _mark_loaded(con, "analytics_events", count)
    return count


def extract_snapshots(db, con) -> int:
    since = _watermark(con, "sheet_snapshots", "first_seen_at")
    query = {} if since is None else {"firstSeenAt": {"$gte": since}}
    rows = (
        (str(doc["_id"]), doc.get("characterSheetId"), doc.get("kind"), doc.get("firstSeenAt"),
         dumps(doc.get("sheet") or {}))
        for doc in db.analytics_sheet_snapshots.find(query)
    )
    count = _upsert(con, "sheet_snapshots", rows)
    _mark_loaded(con, "analytics_sheet_snapshots", count)
    return count


def extract_players_and_campaigns(db, con) -> None:
    players = [(str(d["_id"]), d.get("login"), d.get("name"), d.get("role"))
               for d in db.players.find({}, {"passwordHash": 0})]
    _replace(con, "players", players)
    sessions = []
    for campaign in db.campaigns.find({}, {"name": 1, "sessions": 1}):
        for session in campaign.get("sessions") or []:
            sessions.append((str(campaign["_id"]), campaign.get("name"), session.get("number"),
                             session.get("status"), session.get("startedAt"), session.get("endedAt")))
    _replace(con, "campaign_sessions", sessions)


def extract(mongo_uri: str, raw_path: str) -> dict[str, int]:
    # The API stores UUIDs (a participant's ally group) in the standard binary form.
    client = MongoClient(mongo_uri, tz_aware=True, uuidRepresentation="standard")
    db = client.get_default_database(default="aventyrs")
    con = connect_raw(raw_path)
    try:
        con.begin()
        counts = {
            "analytics_events": extract_events(db, con),
            "sheet_snapshots": extract_snapshots(db, con),
        }
        extract_players_and_campaigns(db, con)
        con.commit()
        return counts
    finally:
        con.close()
        client.close()
