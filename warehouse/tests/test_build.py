"""Builds the warehouse from a hand-made raw layer and checks what the wide facts and marts
say about it. No MongoDB involved: the raw tables are filled exactly as extract.py fills them.

The fixture is one encounter in scene ``s1``:

* Rodada 1. The hero (Guerreiro/Ladino, 100 XP, Florete) attacks the foe three times: a
  critical with Sangramento and a Corrente (9 damage), a plain hit (4), and a miss. The foe
  hits back (7).
* Rodada 2. The hero has levelled up to 300 XP (a new build) and hits for 18.

The client's own ``turnNumber`` in each payload is deliberately wrong (99): the Rodada must
come from the server's stamp on the event, never from what the client reported.
"""

from __future__ import annotations

import datetime as dt
import json
from pathlib import Path

import duckdb
import pytest

from aventyrs_wh.build import build
from aventyrs_wh.extract import connect_raw

T0 = dt.datetime(2026, 10, 3, 20, 0, tzinfo=dt.timezone.utc)


def at(seconds: int) -> dt.datetime:
    return T0 + dt.timedelta(seconds=seconds)


def sheet(name, xp, primary="GUERREIRO", secondary="LADINO", weapon="LIGHT_BLADE", player="p1"):
    return {
        "_id": name,
        "playerId": player,
        "campaignId": "c1",
        "totalExperience": xp,
        "unUsedExperience": 0,
        "hitPointsSpent": 0,
        "character": {
            "name": name.title(),
            "race": {"type": "HUMAN"},
            "sizeCategory": "MEDIO",
            "attributes": {"STRENGTH": {"base": 3, "racialBonus": 1, "variable": 0},
                           "DEXTERITY": {"base": 4, "racialBonus": 0, "variable": 1}},
            "primaryTitle": {"type": primary, "abilities": ["GOLPE_PRECISO"], "specializations": []},
            "secondaryTitle": {"type": secondary, "abilities": [], "specializations": []},
            "feats": [{"type": "LUTADOR_NATO"}, {"type": "ATAQUE_RAPIDO"}],
            "equipment": [{"name": "Florete", "category": weapon}, {"name": "Couro", "category": "ARMOR"}],
            "skills": {"ATAQUE_CORPO_A_CORPO": {"graduationValue": 2 if xp < 300 else 3}},
        },
    }


MONSTER = {"_id": "foe", "blueprint": {"name": "Lobo", "kind": "BESTA", "powerDegree": 2,
                                       "sizeCategory": "MEDIO", "attributeBases": {"STRENGTH": 3}}}


def action(actor, target, idx, round_, crit="NONE", succeeded=True, margin=2, details=None, feats=()):
    return {
        "characterSheetId": actor, "skill": "ATAQUE_CORPO_A_CORPO", "governingDomain": "STRENGTH",
        "attackSourceKind": "WEAPON", "costKind": "FIXED", "actionPoints": 3, "turnNumber": 99,
        "succeeded": succeeded, "margin": margin, "criticalResult": crit, "reachedDifficultyLevel": None,
        "dice": [3, 4, 5], "total": 12, "targetCharacterSheetId": target, "activatedFeats": list(feats),
        "attackDetails": details,
    }


def damage(attacker, target, raw, final, round_):
    return {"attackerCharacterSheetId": attacker, "targetCharacterSheetId": target, "rawDamage": raw,
            "finalDamage": final, "ignoreDamageReduction": False, "damageType": "CORTANTE",
            "elementalType": None, "sourceKind": "ATTACK", "sourceRef": None, "wasCritical": None,
            "turnNumber": 99, "hitPointsSpentAfter": None}


@pytest.fixture(scope="module")
def warehouse(tmp_path_factory) -> duckdb.DuckDBPyConnection:
    data: Path = tmp_path_factory.mktemp("wh")
    raw_path, out_path = str(data / "raw.duckdb"), str(data / "warehouse.duckdb")
    raw = connect_raw(raw_path)

    snapshots = {"snapA": ("hero", "CHARACTER", sheet("hero", 100)),
                 "snapB": ("hero", "CHARACTER", sheet("hero", 300)),
                 "snapM": ("foe", "MONSTER", MONSTER)}
    for sid, (owner, kind, doc) in snapshots.items():
        raw.execute("INSERT INTO sheet_snapshots VALUES (?, ?, ?, ?, ?)", [sid, owner, kind, T0, json.dumps(doc)])
    raw.execute("INSERT INTO players VALUES ('p1', 'elara', 'Elara', 'PLAYER')")
    raw.execute("INSERT INTO campaign_sessions VALUES ('c1', 'Crônicas', 1, 'ONGOING', ?, NULL)", [T0 - dt.timedelta(hours=1)])

    crit = {"weaponItemId": "item-1", "weaponName": "Florete", "weaponCategory": "LIGHT_BLADE", "spellKey": None,
            "criticalMargin": 16, "requiredTotal": 10, "criticalEffectTriggered": True,
            "criticalEffects": ["SANGRAMENTO"], "effectChainTriggered": True, "chainedEffects": ["INFLAMAR"],
            "additionalTargetCharacterSheetIds": []}
    plain = dict(crit, criticalEffectTriggered=False, criticalEffects=[], effectChainTriggered=False,
                 chainedEffects=[])
    events = [
        # (id, seconds, type, round, combat, historyIndex, actor, actorSnap, targets, targetSnaps, payload)
        ("e00", 0, "COMBAT_STARTED", 0, True, None, None, None, ["hero", "foe"], ["snapA", "snapM"], {}),
        ("e01", 1, "TURN_ADVANCED", 1, True, None, "hero", "snapA", [], [], {"characterSheetId": "hero"}),
        ("e02", 2, "ACTION", 1, True, 0, "hero", "snapA", ["foe"], ["snapM"],
         action("hero", "foe", 0, 1, crit="ACERTO_CRITICO_MENOR", margin=6, details=crit, feats=["LUTADOR_NATO"])),
        ("e03", 3, "DAMAGE", 1, True, None, "hero", "snapA", ["foe"], ["snapM"], damage("hero", "foe", 12, 9, 1)),
        ("e04", 4, "ACTION", 1, True, 1, "hero", "snapA", ["foe"], ["snapM"], action("hero", "foe", 1, 1, details=plain)),
        ("e05", 5, "DAMAGE", 1, True, None, "hero", "snapA", ["foe"], ["snapM"], damage("hero", "foe", 5, 4, 1)),
        ("e06", 6, "ACTION", 1, True, 2, "hero", "snapA", ["foe"], ["snapM"],
         action("hero", "foe", 2, 1, succeeded=False, margin=-3, details=plain)),
        ("e07", 7, "TURN_ADVANCED", 1, True, None, "foe", "snapM", [], [], {"characterSheetId": "foe"}),
        ("e08", 8, "ACTION", 1, True, 3, "foe", "snapM", ["hero"], ["snapA"], action("foe", "hero", 3, 1)),
        ("e09", 9, "DAMAGE", 1, True, None, "foe", "snapM", ["hero"], ["snapA"], damage("foe", "hero", 7, 7, 1)),
        ("e10", 10, "TURN_ADVANCED", 2, True, None, "hero", "snapB", [], [], {"characterSheetId": "hero"}),
        ("e11", 11, "ACTION", 2, True, 4, "hero", "snapB", ["foe"], ["snapM"], action("hero", "foe", 4, 2, details=plain)),
        ("e12", 12, "DAMAGE", 2, True, None, "hero", "snapB", ["foe"], ["snapM"], damage("hero", "foe", 20, 18, 2)),
        ("e13", 13, "COMBAT_ENDED", 0, False, None, None, None, ["hero", "foe"], ["snapB", "snapM"], {}),
    ]
    for (eid, sec, type_, round_, combat, hidx, actor, asnap, targets, tsnaps, payload) in events:
        doc = {"_id": eid, "type": type_, "schemaVersion": 1, "occurredAt": at(sec).isoformat(), "sceneId": "s1",
               "sceneName": "Arena", "sceneRound": round_, "sceneTurnIndex": 0, "combatScene": combat,
               "historyIndex": hidx, "actorCharacterSheetId": actor, "actorSheetSnapshotId": asnap,
               "targetCharacterSheetIds": targets, "targetSheetSnapshotIds": tsnaps, "payload": payload}
        raw.execute("INSERT INTO analytics_events VALUES (?, ?, ?, 's1', ?)", [eid, type_, at(sec), json.dumps(doc)])

    raw.close()

    build(raw_path, out_path)
    con = duckdb.connect(out_path, read_only=True)
    yield con
    con.close()


def one(con, sql, *params):
    cursor = con.execute(sql, list(params))
    names = [d[0] for d in cursor.description]
    rows = cursor.fetchall()
    assert len(rows) == 1, f"expected one row, got {len(rows)}"
    return dict(zip(names, rows[0]))


def test_every_fact_row_carries_the_round_the_server_stamped(warehouse):
    actions = warehouse.execute("SELECT event_id, round FROM fact_action_wide ORDER BY event_id").fetchall()
    assert actions == [("e02", 1), ("e04", 1), ("e06", 1), ("e08", 1), ("e11", 2)]
    hits = warehouse.execute("SELECT event_id, round FROM fact_damage_wide ORDER BY event_id").fetchall()
    assert hits == [("e03", 1), ("e05", 1), ("e09", 1), ("e12", 2)]
    for table in ("fact_action_wide", "fact_damage_wide", "fact_ability_wide", "fact_combatant_round_wide",
                  "fact_event_log"):
        columns = {r[0] for r in warehouse.execute(f"DESCRIBE {table}").fetchall()}
        assert "round" in columns, table


def test_only_live_events_reach_the_warehouse(warehouse):
    assert warehouse.execute("SELECT count(*) FROM fact_action_wide").fetchone()[0] == 5
    columns = {r[0] for r in warehouse.execute("DESCRIBE fact_action_wide").fetchall()}
    assert not {"source", "actor_snapshot_is_exact", "reported_turn_number"} & columns


def test_an_action_row_carries_the_attackers_build_and_how_the_hit_landed(warehouse):
    row = one(warehouse, "SELECT * FROM fact_action_wide WHERE event_id = 'e02'")
    assert row["actor_name"] == "Hero"
    assert row["actor_player_login"] == "elara"
    assert row["actor_total_xp"] == 100
    assert row["actor_primary_title"] == "GUERREIRO"
    assert row["actor_secondary_title"] == "LADINO"
    assert row["actor_attr_strength"] == 4
    assert row["actor_equipped_weapon_categories"] == ["LIGHT_BLADE"]
    assert row["weapon_category"] == "LIGHT_BLADE"
    assert row["was_critical"] is True
    assert row["critical_effects"] == ["SANGRAMENTO"]
    assert row["effect_chain_triggered"] is True
    assert row["chained_effects"] == ["INFLAMAR"]
    assert row["critical_rate_theoretical"] == pytest.approx(10 / 216)  # 3d6 >= 16
    assert row["activated_feats"] == ["LUTADOR_NATO"]
    assert row["damage_final_total"] == 9
    assert row["target_name"] == "Lobo" and row["target_kind"] == "MONSTER"
    assert row["on_own_turn"] is True
    assert row["campaign_name"] == "Crônicas" and row["session_number"] == 1
    assert json.loads(row["actor_sheet_json"])["character"]["name"] == "Hero"


def test_a_level_up_mid_encounter_is_a_new_build(warehouse):
    before = one(warehouse, "SELECT actor_build_hash, actor_total_xp FROM fact_action_wide WHERE event_id = 'e02'")
    after = one(warehouse, "SELECT actor_build_hash, actor_total_xp FROM fact_action_wide WHERE event_id = 'e11'")
    assert after["actor_total_xp"] == 300
    assert before["actor_build_hash"] != after["actor_build_hash"]


def test_observed_crit_rate_accumulates_over_the_actors_attacks(warehouse):
    rates = [r[0] for r in warehouse.execute(
        "SELECT critical_rate_observed_to_date FROM fact_action_wide "
        "WHERE actor_character_sheet_id = 'hero' ORDER BY occurred_at").fetchall()]
    assert rates == pytest.approx([1.0, 0.5, 1 / 3, 0.25])


def test_each_hit_links_to_the_attack_that_dealt_it(warehouse):
    hit = one(warehouse, "SELECT * FROM fact_damage_wide WHERE event_id = 'e05'")
    assert hit["action_event_id"] == "e04"
    assert hit["attacker_primary_title"] == "GUERREIRO"
    assert hit["target_monster_kind"] == "BESTA"
    assert hit["mitigated_damage"] == 1
    crit_hit = one(warehouse, "SELECT * FROM fact_damage_wide WHERE event_id = 'e03'")
    assert crit_hit["was_critical"] is True
    assert crit_hit["critical_effects"] == ["SANGRAMENTO"]


def test_damage_leaderboard(warehouse):
    hero = one(warehouse, "SELECT * FROM mart_damage_leaderboard WHERE attacker_id = 'hero'")
    assert hero["hits"] == 3
    assert hero["damage_total"] == 31
    assert hero["damage_top_single_hit"] == 18
    assert hero["damage_avg_per_hit"] == pytest.approx(31 / 3, abs=0.01)
    assert hero["total_xp"] == 300
    top = warehouse.execute("SELECT final_damage FROM mart_top_hits LIMIT 1").fetchone()[0]
    assert top == 18


def test_most_attacks_in_a_round(warehouse):
    hero = one(warehouse, "SELECT * FROM mart_attack_volume WHERE combatant_id = 'hero'")
    assert hero["max_attacks_in_a_round"] == 3
    assert hero["max_attacks_in_own_turn"] == 3
    assert hero["record_round"] == 1
    round1 = one(warehouse, "SELECT * FROM fact_combatant_round_wide WHERE combatant_id = 'hero' AND round = 1")
    assert round1["damage_dealt_final"] == 13
    assert round1["damage_taken_final"] == 7
    assert round1["crits"] == 1


def test_build_and_feat_marts(warehouse):
    builds = warehouse.execute(
        "SELECT total_xp, attacks, hit_rate FROM mart_build_effectiveness "
        "WHERE primary_title = 'GUERREIRO' ORDER BY total_xp").fetchall()
    assert [(float(xp), n) for xp, n, _ in builds] == [(100.0, 3), (300.0, 1)]
    feat = one(warehouse, "SELECT * FROM mart_feat_impact WHERE feat = 'LUTADOR_NATO'")
    assert feat["uses"] == 1 and feat["crit_rate_with"] == 1.0
    weapon = one(warehouse, "SELECT * FROM mart_weapon_category WHERE weapon_category = 'LIGHT_BLADE'")
    assert weapon["attacks"] == 4
    trend = one(warehouse, "SELECT * FROM mart_meta_trends WHERE dimension = 'primary_title' AND value = 'GUERREIRO'")
    assert trend["actions"] == 4


def test_a_reader_kept_open_sees_the_next_build(tmp_path):
    """Metabase keeps the warehouse open for good. A rebuild must still reach it without a
    reconnect, which is why tables are published as Parquet behind views."""
    raw_path, out_path = str(tmp_path / "raw.duckdb"), str(tmp_path / "warehouse.duckdb")

    def add_action(event_id: str, seconds: int) -> None:
        doc = {"_id": event_id, "type": "ACTION", "occurredAt": at(seconds).isoformat(), "sceneId": "s1",
               "sceneRound": 1, "sceneTurnIndex": 0, "combatScene": True, "historyIndex": seconds,
               "actorCharacterSheetId": "hero", "targetCharacterSheetIds": [], "targetSheetSnapshotIds": [],
               "payload": action("hero", None, seconds, 1)}
        raw = connect_raw(raw_path)
        raw.execute("INSERT INTO analytics_events VALUES (?, 'ACTION', ?, 's1', ?)", [event_id, at(seconds), json.dumps(doc)])
        raw.close()

    add_action("a1", 1)
    build(raw_path, out_path)
    reader = duckdb.connect(out_path, read_only=True)
    assert reader.execute("SELECT count(*) FROM fact_action_wide").fetchone()[0] == 1

    add_action("a2", 2)
    build(raw_path, out_path)
    assert reader.execute("SELECT count(*) FROM fact_action_wide").fetchone()[0] == 2
    reader.close()


def test_a_fight_already_under_way_when_recording_began_is_still_counted(tmp_path):
    """No COMBAT_STARTED was recorded (the API began recording mid-fight): the attacks still
    count per Rodada, under encounter '<scene>#0', with the attacker's sheet."""
    raw_path, out_path = str(tmp_path / "raw.duckdb"), str(tmp_path / "warehouse.duckdb")
    raw = connect_raw(raw_path)
    raw.execute("INSERT INTO sheet_snapshots VALUES ('snapA', 'hero', 'CHARACTER', ?, ?)",
                [T0, json.dumps(sheet("hero", 100))])
    for i in range(3):
        doc = {"_id": f"m{i}", "type": "ACTION", "occurredAt": at(i).isoformat(), "sceneId": "s9",
               "sceneRound": 2, "sceneTurnIndex": 0, "combatScene": True, "historyIndex": i,
               "actorCharacterSheetId": "hero", "actorSheetSnapshotId": "snapA",
               "targetCharacterSheetIds": [], "targetSheetSnapshotIds": [], "payload": action("hero", None, i, 2)}
        raw.execute("INSERT INTO analytics_events VALUES (?, 'ACTION', ?, 's9', ?)", [f"m{i}", at(i), json.dumps(doc)])
    raw.close()
    build(raw_path, out_path)
    con = duckdb.connect(out_path, read_only=True)

    hero = one(con, "SELECT * FROM mart_attack_volume WHERE combatant_id = 'hero'")
    assert hero["combatant_name"] == "Hero"
    assert hero["primary_title"] == "GUERREIRO"
    assert hero["max_attacks_in_a_round"] == 3
    assert hero["record_encounter_id"] == "s9#0"
    assert hero["record_round"] == 2
    con.close()


def test_top_damage_in_a_single_turn(warehouse):
    rows = warehouse.execute(
        "SELECT rank, combatant_id, round, damage_in_turn, hits_in_turn, turn_label FROM mart_turn_damage ORDER BY rank"
    ).fetchall()
    assert [r[:5] for r in rows] == [(1, "hero", 2, 18, 1), (2, "hero", 1, 13, 2), (3, "foe", 1, 7, 1)]
    assert rows[0][5] == "Hero (Rodada 2)"
