# Aventyrs combat warehouse

A denormalized [DuckDB](https://duckdb.org) warehouse of everything combatants do in a Scene. It's built for analysing:

- build effectiveness
- meta trends
- average and top damage
- attacks per turn

[Metabase](https://www.metabase.com) is the UI on top of it.

```
API (SceneRealtimeController) ──► Mongo analytics_events + analytics_sheet_snapshots
                                        │  python -m aventyrs_wh extract   (incremental)
                                        ▼
                                  data/raw.duckdb
                                        │  python -m aventyrs_wh build     (full rebuild)
                                        ▼
                                  data/tables/*.parquet    one file per table, each replaced atomically
                                        ▲  views
                                  data/warehouse.duckdb ──► Metabase (read-only)
```

`warehouse.duckdb` holds no data, only one view per table over `tables/<name>.parquet`. Metabase keeps its database open for good. If the tables lived inside `warehouse.duckdb`, it would keep reading the copy it opened first, which is why a load used to show up only after a Metabase restart. DuckDB opens a Parquet file again on every query, so Metabase sees each load on its next query.

The views point at the Parquet files by absolute path. The job and Metabase therefore have to see the data at the same path: in docker-compose, both mount the volume at `/warehouse`. When a model adds or removes a *table*, restart Metabase once so it reopens `warehouse.duckdb`. New data and new columns never need a restart.

## Where the data comes from

The API writes one `analytics_events` document per **accepted** realtime frame (`org.aventyrs.api.analytics.AnalyticsRecorder`). It covers these endpoints:
- actions, abilities, roll requests and responses
- `/damage`, `/status`, conditions, spell landings, combatant state
- turn, combat start and end, moves, hidden, initiative, time

Each event is stamped by the server with:
- the time
- the Scene's **Rodada** (`round`), turn index (`turn_index`) and combat flag (`combat_scene`), read once the frame was applied. The client's own `turnNumber` is never used for these.
- a content-addressed **snapshot of the actor's and targets' sheets**. That snapshot is how every row knows the build that rolled it, even after the sheet has been levelled up. A snapshot is only written when the build changes. Spent PV/PM/PD don't count as a change.

`analytics_events` is the only source of facts. Nothing is reconstructed from a Scene's own `actionHistory`/`abilityHistory`, so the warehouse starts empty and only holds what was played after the API began recording.

`round` is the Scene's own `currentRound`: 0 outside combat, and it goes up by one each time the turn order wraps during combat. A Scene's Rodada restarts with each encounter, so group by `encounter_id, round` (not `round` alone) when you want one specific Rodada.

## Tables

The facts are wide. Every row repeats all the context as columns: scene, encounter, Rodada, turn, campaign, Sessão, and a full **sheet block** for each combatant involved. Analysis needs no joins.

The sheet block is `actor_*`, `target_*`, `attacker_*` or `combatant_*` depending on the table:

| Column | Meaning |
|---|---|
| `*_name`, `*_kind` (CHARACTER/MONSTER), `*_player_login`, `*_campaign_id` | who |
| `*_race`, `*_parent_race`, `*_size_category` | |
| `*_total_xp`, `*_unused_xp`, `*_monster_kind`, `*_monster_power_degree` | power level |
| `*_primary_title`, `*_secondary_title`, `*_tertiary_title`, `*_title_abilities`, `*_title_specializations` | Títulos |
| `*_attr_vigor` … `*_attr_charisma` | attribute totals (base + racial + variable) |
| `*_feats`, `*_feat_count`, `*_spells`, `*_equipped_weapon_categories`, `*_equipped_item_names` | |
| `*_build_hash` | identifies the build: race, XP, Títulos, attributes, Talentos, skills, weapons |
| `*_sheet_json` | **the raw sheet document as it stood**, for drill-down |

| Table | Grain | Highlights |
|---|---|---|
| `fact_action_wide` | one rolled action (attack or Perícia) | `round`, `turn_index`, dice, total, margin, `was_critical`, `critical_margin`, `critical_rate_theoretical` (P(3d6 ≥ margin)), `critical_rate_observed_to_date`, `critical_effects`, `effect_chain_triggered`, `chained_effects`, `weapon_category`, `spell_key`, `activated_feats`, `on_own_turn`, `attacks_by_actor_this_round`, damage it caused, actor + target blocks |
| `fact_damage_wide` | one hit (`/damage`) | `round`, `turn_index`, raw, final, mitigated, type, element, linked attack (weapon, crit, effects, feats), attacker + target blocks |
| `fact_ability_wide` | one Habilidade activation | `round`, `turn_index`, Título, ability, PD/PV spent, Blessings, area damage, Condições inflicted |
| `fact_combatant_round_wide` | combatant × encounter × Rodada | actions, attacks (and on own turn), hits, crits, AP, damage dealt/taken, abilities |
| `fact_event_log` | every event | `round`, `turn_index`, the untyped payload, for whatever isn't modelled yet |
| `mart_damage_leaderboard` | attacker | totals, average per hit, **top single hit**, damage per active round |
| `mart_top_hits` | top 100 hits | |
| `mart_turn_damage` | combatant × Rodada, ranked | **most damage in a single turn**: `damage_in_turn` (hits on their own turn) and `damage_in_rodada` (adds Reações), hits, top hit, crits, build |
| `mart_attack_volume` | combatant | **max attacks in a round** and where it happened |
| `mart_build_effectiveness` | build | hit and crit rate (observed vs theoretical), margin, effect and chain rate, damage per round dealt and taken |
| `mart_title_effectiveness` | Título pair | |
| `mart_weapon_category` | weapon category | |
| `mart_feat_impact` | Talento | hit rate, crit rate and damage, with vs without |
| `mart_meta_trends` | week × dimension × value | usage share of Títulos, races, Perícias, weapon categories and Talentos |

How damage is attributed to an attack: a `/damage` frame is linked to the latest attack by the same attacker on the same target in the same Scene, at or before the hit (an `ASOF` join). That includes area and chained secondary targets.

## Running

### With Docker (alongside the API's compose stack)

```bash
docker compose --profile analytics run --rm warehouse        # extract + build
docker compose --profile analytics up -d metabase            # http://localhost:3000
```

To query the warehouse directly with the DuckDB CLI (read-only, safe while Metabase is up):

```bash
docker compose --profile analytics run --rm duckdb                     # interactive shell
docker compose --profile analytics run --rm duckdb -readonly /warehouse/warehouse.duckdb \
    -c "FROM mart_damage_leaderboard"
```

DuckDB is embedded, so this service isn't a server: it opens the file, runs the SQL and exits. The files themselves (`raw.duckdb`, `warehouse.duckdb`, `tables/`) live in the `warehouse-data` volume.

Re-run the first command whenever you want fresh numbers, or cron it. Metabase shows the new numbers on its next query, with no restart. Only what the API has recorded since it started writing `analytics_events` is loaded, so play some combat with the updated API first. The `analytics` profile keeps both services out of a plain `docker compose up`, so the prod deploy doesn't start them.

### Locally

```bash
cd warehouse
python3 -m venv .venv && .venv/bin/pip install -e '.[test]'
.venv/bin/python -m aventyrs_wh all            # or: extract | build
.venv/bin/pytest
```

`MONGODB_URI` defaults to the docker-compose Mongo. `WAREHOUSE_DATA_DIR` (or `--data-dir`) defaults to `./data`.

## Metabase setup (first run)

1. Open http://localhost:3000 and create the admin account.
2. **Add a database**: type **DuckDB**.
   - Database file: `/warehouse/warehouse.duckdb`
   - Turn on **read only**.
3. Import the starter questions from `metabase/questions/`:

   ```bash
   python3 warehouse/metabase/import_questions.py --user <your Metabase login email>
   ```

   It asks for your password, or reads it from `MB_PASSWORD`. It creates:
   - an **Aventyrs warehouse** collection holding one saved question per `.sql` file:
     - damage leaderboard, top damage in a single turn, top hits, most attacks in a turn
     - build effectiveness, Título pairs, weapon categories, Talento impact
     - meta trends, drill-down
   - an **Aventyrs combat** dashboard with all of them on it. Rankings are drawn as bar charts (damage leaderboard, single-turn damage, attacks per turn, Título pairs, weapons, Talentos) and meta trends as a line chart. The detail questions stay tables.

   Re-running it updates those questions in place, matched by name, so edit a `.sql` file and re-run to push the change. Questions you created yourself are never touched. It only needs plain `python3`, and `--url` points it at a Metabase other than `http://localhost:3000`.

If adding the database fails with **"Timed out after 10.0 s"**, the container is probably running an image built before the `icu` extension was included (see `metabase/Dockerfile`). Rebuild and restart it with `docker compose --profile analytics up -d --build metabase`. Your Metabase account and questions are kept, because they live in the `metabase-data` volume.

Metabase keeps its own data (accounts, questions, dashboards) in the `metabase-db` Postgres container, in the `metabase-db-data` volume. The embedded H2 default lost track of its ID counters after restarts, which broke creating collections. The old H2 file is still in the `metabase-data` volume. Migrating it again isn't needed, but `docker-compose.yml` shows the `load-from-h2` command.

Pinned versions: Metabase `v0.63.18` and DuckDB driver `1.5.5.0` (see `metabase/Dockerfile`). The job's `duckdb` stays on 1.5.x so the file it writes stays readable by the driver.

## What the game client needs to send

Some columns only fill in once `aventyrs-game-client` sends the newer fields:

- **`RecordActionMessage.attackDetails`** carries:
  - weapon item id, name and category; spell key
  - Margem Crítica; required total
  - Efeitos Críticos triggered; Corrente triggered and what it set off
  - additional targets

  All of it is already on core's `DeliveredAttackResult`. Without it, `weapon_category`, `critical_margin`, `critical_rate_theoretical`, `critical_effects` and `chained_effects` stay NULL.
- **`/app/scenes/{id}/damage`** (`DamageDealtMessage`): sent by the client owning the target, from `SceneGridController.landDamage`, from retaliation, and from `TitleEffects` area hits. Without it, `fact_damage_wide` and every damage column stay empty. Ability area damage still shows up in `fact_ability_wide`.
