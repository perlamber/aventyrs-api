"""Imports the starter questions in ``questions/*.sql`` into Metabase, through its REST API.

Each file becomes a saved native-SQL question in an "Aventyrs warehouse" collection, and all of
them are laid out on one dashboard. The file name gives the question's name (07_feat_impact.sql
is "Feat impact"), and the leading comments become its description.

Running it again updates the questions in place (matched by name), so editing a .sql file and
re-running is how a change reaches Metabase. Questions you made yourself are left alone.

    python3 warehouse/metabase/import_questions.py --user you@example.com
    # password from MB_PASSWORD, or prompted

Only the standard library is used, so any python3 runs it; no venv needed.
"""

from __future__ import annotations

import argparse
import getpass
import json
import os
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

QUESTIONS_DIR = Path(__file__).parent / "questions"
COLLECTION_NAME = "Aventyrs warehouse"
DASHBOARD_NAME = "Aventyrs combat"

def _chart(display: str, dimensions: list[str], metrics: list[str], **extra) -> tuple[str, dict]:
    return display, {"graph.dimensions": dimensions, "graph.metrics": metrics, **extra}


# How each question is drawn. "row" is a horizontal bar chart, which reads best for rankings
# by name. Questions not listed are tables. Any of them can still be switched to another
# visualization from the question itself; re-running the script puts these back.
VISUALIZATIONS = {
    "01_damage_leaderboard.sql": _chart("row", ["attacker_name"], ["damage_total"]),
    "03_most_attacks_in_a_turn.sql": _chart("row", ["combatant_name"],
                                            ["max_attacks_in_a_round", "max_attacks_in_own_turn"]),
    "05_title_pairs.sql": _chart("row", ["title_pair"], ["damage_per_attack"]),
    "06_weapon_categories.sql": _chart("bar", ["weapon_category"], ["damage_per_attack", "damage_per_hit"]),
    "07_feat_impact.sql": _chart("bar", ["feat"], ["damage_per_attack_with", "damage_per_attack_without"]),
    "08_meta_trends.sql": _chart("line", ["week", "value"], ["share_of_actions"]),
    "10_top_damage_in_a_single_turn.sql": _chart("row", ["turn_label"], ["damage_in_turn", "damage_in_rodada"]),
}

# Dashboard order: the damage questions first. Files not listed follow in name order.
DASHBOARD_FIRST = ["01_damage_leaderboard.sql", "10_top_damage_in_a_single_turn.sql"]


class Metabase:
    def __init__(self, url: str):
        self.url = url.rstrip("/")
        self.session: str | None = None

    def call(self, method: str, path: str, body: dict | None = None):
        data = None if body is None else json.dumps(body).encode()
        request = urllib.request.Request(self.url + "/api" + path, data=data, method=method)
        request.add_header("Content-Type", "application/json")
        if self.session:
            request.add_header("X-Metabase-Session", self.session)
        try:
            with urllib.request.urlopen(request) as response:
                raw = response.read()
        except urllib.error.HTTPError as ex:
            raise SystemExit(f"{method} {path} failed ({ex.code}): {ex.read().decode(errors='replace')[:500]}")
        return json.loads(raw) if raw else None

    def login(self, user: str, password: str) -> None:
        self.session = self.call("POST", "/session", {"username": user, "password": password})["id"]


def parse_question(path: Path) -> dict:
    lines = path.read_text().splitlines()
    comments = []
    for line in lines:
        if not line.startswith("--"):
            break
        comments.append(line[2:].strip())
    sql = "\n".join(lines[len(comments):]).strip().rstrip(";").strip()
    # 07_feat_impact.sql -> "Feat impact". The comments become the description.
    name = re.sub(r"^\d+_", "", path.stem).replace("_", " ").capitalize()
    description = " ".join(comments)
    display, settings = VISUALIZATIONS.get(path.name, ("table", {}))
    return {"file": path.name, "name": name, "description": description, "sql": sql,
            "display": display, "settings": settings}


def find_database(mb: Metabase, name: str | None) -> dict:
    databases = [d for d in mb.call("GET", "/database")["data"] if d["engine"] == "duckdb"]
    if name:
        databases = [d for d in databases if d["name"] == name]
    if len(databases) != 1:
        found = ", ".join(repr(d["name"]) for d in databases) or "none"
        raise SystemExit(f"Expected exactly one DuckDB database{f' named {name!r}' if name else ''}, found: {found}. "
                         "Add it under Admin settings > Databases first, or pass --database.")
    return databases[0]


def ensure_collection(mb: Metabase) -> int:
    for collection in mb.call("GET", "/collection"):
        if collection.get("name") == COLLECTION_NAME and not collection.get("archived"):
            return collection["id"]
    return mb.call("POST", "/collection", {"name": COLLECTION_NAME, "color": "#509EE3"})["id"]


def upsert_cards(mb: Metabase, database_id: int, collection_id: int, questions: list[dict]) -> list[int]:
    existing = {item["name"]: item["id"]
                for item in mb.call("GET", f"/collection/{collection_id}/items?models=card")["data"]}
    ids = []
    for q in questions:
        card = {
            "name": q["name"],
            "description": q["description"],
            "collection_id": collection_id,
            "display": q["display"],
            "visualization_settings": q["settings"],
            "dataset_query": {"database": database_id, "type": "native", "native": {"query": q["sql"]}},
        }
        if q["name"] in existing:
            card_id = existing[q["name"]]
            mb.call("PUT", f"/card/{card_id}", card)
            print(f"updated  {q['file']} -> {q['name']}")
        else:
            card_id = mb.call("POST", "/card", card)["id"]
            print(f"created  {q['file']} -> {q['name']}")
        ids.append(card_id)
    return ids


def ensure_dashboard(mb: Metabase, collection_id: int, card_ids: list[int]) -> int:
    items = mb.call("GET", f"/collection/{collection_id}/items?models=dashboard")["data"]
    match = [i for i in items if i["name"] == DASHBOARD_NAME]
    dashboard_id = match[0]["id"] if match else mb.call(
        "POST", "/dashboard", {"name": DASHBOARD_NAME, "collection_id": collection_id})["id"]
    # Two questions per row, each half the 24-column grid wide. Negative ids are new dashcards;
    # the layout is replaced as a whole on every run.
    dashcards = [{"id": -(i + 1), "card_id": card_id, "row": (i // 2) * 8, "col": (i % 2) * 12,
                  "size_x": 12, "size_y": 8, "parameter_mappings": [], "visualization_settings": {}}
                 for i, card_id in enumerate(card_ids)]
    mb.call("PUT", f"/dashboard/{dashboard_id}", {"dashcards": dashcards})
    return dashboard_id


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--url", default=os.environ.get("MB_URL", "http://localhost:3000"))
    parser.add_argument("--user", default=os.environ.get("MB_USER"), help="Metabase login email (or MB_USER)")
    parser.add_argument("--database", default=os.environ.get("MB_DATABASE"),
                        help="the DuckDB database's display name in Metabase; only needed if there are several")
    args = parser.parse_args(argv)
    if not args.user:
        parser.error("--user (or MB_USER) is required")
    password = os.environ.get("MB_PASSWORD") or getpass.getpass(f"Metabase password for {args.user}: ")

    mb = Metabase(args.url)
    mb.login(args.user, password)
    database = find_database(mb, args.database)
    collection_id = ensure_collection(mb)
    files = sorted(QUESTIONS_DIR.glob("*.sql"),
                   key=lambda f: (DASHBOARD_FIRST.index(f.name) if f.name in DASHBOARD_FIRST else len(DASHBOARD_FIRST),
                                  f.name))
    questions = [parse_question(p) for p in files]
    card_ids = upsert_cards(mb, database["id"], collection_id, questions)
    dashboard_id = ensure_dashboard(mb, collection_id, card_ids)
    print(f"\n{len(card_ids)} questions in collection '{COLLECTION_NAME}' (database '{database['name']}').")
    print(f"Dashboard: {mb.url}/dashboard/{dashboard_id}")


if __name__ == "__main__":
    sys.exit(main())
