-- Marts: pre-aggregated answers to the questions the warehouse exists for, ready for Metabase.
-- Each is a pure aggregate over the wide facts, so a new question can always be asked of
-- those directly instead.

-- Who deals the most damage: totals, averages and the single biggest hit, with the
-- attacker's latest build alongside.
CREATE TABLE mart_damage_leaderboard AS
SELECT
    attacker_id,
    arg_max(attacker_name, occurred_at) AS attacker_name,
    arg_max(attacker_kind, occurred_at) AS attacker_kind,
    arg_max(attacker_player_login, occurred_at) AS player_login,
    arg_max(attacker_race, occurred_at) AS race,
    arg_max(attacker_primary_title, occurred_at) AS primary_title,
    arg_max(attacker_secondary_title, occurred_at) AS secondary_title,
    arg_max(attacker_total_xp, occurred_at) AS total_xp,
    count(*) AS hits,
    count(*) FILTER (WHERE was_critical) AS critical_hits,
    sum(final_damage) AS damage_total,
    round(avg(final_damage), 2) AS damage_avg_per_hit,
    max(final_damage) AS damage_top_single_hit,
    sum(raw_damage) AS damage_raw_total,
    round(avg(mitigated_damage), 2) AS mitigated_avg_per_hit,
    count(DISTINCT encounter_id || ':' || round) AS rounds_dealing_damage,
    round(sum(final_damage) / nullif(count(DISTINCT encounter_id || ':' || round), 0), 2) AS damage_per_active_round,
    count(DISTINCT encounter_id) AS encounters
FROM fact_damage_wide
WHERE attacker_id IS NOT NULL
GROUP BY attacker_id;

-- The biggest single hits ever landed, with everything about how each one happened.
CREATE TABLE mart_top_hits AS
SELECT * EXCLUDE (attacker_sheet_json, target_sheet_json)
FROM fact_damage_wide
WHERE attacker_id IS NOT NULL
ORDER BY final_damage DESC, occurred_at
LIMIT 100;

-- The most damage a combatant dealt in a single turn: one row per combatant and Rodada,
-- best first. damage_in_turn counts only hits landed on the combatant's own turn; Reações and
-- other off-turn hits in the same Rodada are in damage_in_rodada.
CREATE TABLE mart_turn_damage AS
SELECT
    row_number() OVER (ORDER BY damage_dealt_on_own_turn DESC, damage_dealt_final DESC, max_single_hit DESC) AS rank,
    combatant_name || ' (Rodada ' || round || ')' AS turn_label,
    combatant_id,
    combatant_name,
    combatant_kind,
    combatant_player_login AS player_login,
    combatant_race AS race,
    combatant_primary_title AS primary_title,
    combatant_secondary_title AS secondary_title,
    combatant_total_xp AS total_xp,
    combatant_build_hash AS build_hash,
    scene_id,
    scene_name,
    encounter_id,
    round,
    damage_dealt_on_own_turn AS damage_in_turn,
    damage_events_on_own_turn AS hits_in_turn,
    damage_dealt_final AS damage_in_rodada,
    damage_events_dealt AS hits_in_rodada,
    max_single_hit,
    attacks,
    attacks_on_own_turn,
    crits,
    critical_effects_triggered,
    chains_triggered,
    feats_activated
FROM fact_combatant_round_wide
WHERE damage_dealt_final > 0;

-- How many attacks a combatant fits into one Rodada, and where they hit their record.
CREATE TABLE mart_attack_volume AS
SELECT
    combatant_id,
    arg_max(combatant_name, attacks) AS combatant_name,
    arg_max(combatant_kind, attacks) AS combatant_kind,
    arg_max(combatant_primary_title, attacks) AS primary_title,
    arg_max(combatant_secondary_title, attacks) AS secondary_title,
    count(*) FILTER (WHERE attacks > 0) AS rounds_attacking,
    sum(attacks) AS attacks_total,
    max(attacks) AS max_attacks_in_a_round,
    max(attacks_on_own_turn) AS max_attacks_in_own_turn,
    round(avg(attacks) FILTER (WHERE attacks > 0), 2) AS avg_attacks_per_attacking_round,
    arg_max(encounter_id, attacks) AS record_encounter_id,
    arg_max(round, attacks) AS record_round
FROM fact_combatant_round_wide
GROUP BY combatant_id;

-- Build effectiveness. One row per distinct build (build_hash), described by its choices,
-- rated by how its attacks land and how much damage it trades per Rodada.
CREATE TABLE mart_build_effectiveness AS
WITH builds AS (
    SELECT build_hash,
           arg_max(kind, coalesce(first_seen_at, TIMESTAMPTZ '1970-01-01')) AS kind,
           arg_max(race, coalesce(first_seen_at, TIMESTAMPTZ '1970-01-01')) AS race,
           arg_max(primary_title, coalesce(first_seen_at, TIMESTAMPTZ '1970-01-01')) AS primary_title,
           arg_max(secondary_title, coalesce(first_seen_at, TIMESTAMPTZ '1970-01-01')) AS secondary_title,
           arg_max(tertiary_title, coalesce(first_seen_at, TIMESTAMPTZ '1970-01-01')) AS tertiary_title,
           arg_max(total_xp, coalesce(first_seen_at, TIMESTAMPTZ '1970-01-01')) AS total_xp,
           arg_max(feats, coalesce(first_seen_at, TIMESTAMPTZ '1970-01-01')) AS feats,
           arg_max(equipped_weapon_categories, coalesce(first_seen_at, TIMESTAMPTZ '1970-01-01'))
               AS equipped_weapon_categories,
           list_distinct(list(name)) AS used_by
    FROM staging.sheet_snapshot
    GROUP BY build_hash
),
attacks AS (
    SELECT actor_build_hash AS build_hash,
           count(*) AS attacks,
           round(avg(succeeded::INTEGER), 4) AS hit_rate,
           round(avg(was_critical::INTEGER), 4) AS crit_rate_observed,
           round(avg(critical_rate_theoretical), 4) AS crit_rate_theoretical,
           round(avg(margin), 2) AS avg_margin,
           round(avg(critical_effect_triggered::INTEGER), 4) AS critical_effect_rate,
           round(avg(effect_chain_triggered::INTEGER), 4) AS chain_rate,
           round(sum(damage_final_total) / count(*), 2) AS damage_per_attack
    FROM fact_action_wide
    WHERE is_attack
    GROUP BY 1
),
rounds AS (
    SELECT combatant_build_hash AS build_hash,
           count(*) AS combat_rounds,
           round(avg(damage_dealt_final), 2) AS damage_dealt_per_round,
           round(avg(damage_taken_final), 2) AS damage_taken_per_round,
           round(avg(attacks), 2) AS attacks_per_round,
           count(DISTINCT encounter_id) AS encounters
    FROM fact_combatant_round_wide
    GROUP BY 1
)
SELECT b.*, a.* EXCLUDE (build_hash), r.* EXCLUDE (build_hash)
FROM builds b
JOIN attacks a USING (build_hash)
LEFT JOIN rounds r USING (build_hash);

-- The same effectiveness numbers per Título pair: which combinations perform best.
CREATE TABLE mart_title_effectiveness AS
SELECT
    actor_primary_title AS primary_title,
    actor_secondary_title AS secondary_title,
    count(DISTINCT actor_character_sheet_id) AS combatants,
    count(*) AS attacks,
    round(avg(succeeded::INTEGER), 4) AS hit_rate,
    round(avg(was_critical::INTEGER), 4) AS crit_rate_observed,
    round(avg(critical_rate_theoretical), 4) AS crit_rate_theoretical,
    round(avg(margin), 2) AS avg_margin,
    round(sum(damage_final_total) / count(*), 2) AS damage_per_attack,
    max(damage_max_single_hit) AS top_single_hit
FROM fact_action_wide
WHERE is_attack
GROUP BY ALL;

-- Weapon categories (or Magia/racial attacks when no weapon was named) compared.
CREATE TABLE mart_weapon_category AS
SELECT
    coalesce(weapon_category, attack_source_kind, 'UNKNOWN') AS weapon_category,
    count(*) AS attacks,
    count(DISTINCT actor_character_sheet_id) AS combatants,
    round(avg(succeeded::INTEGER), 4) AS hit_rate,
    round(avg(was_critical::INTEGER), 4) AS crit_rate_observed,
    round(avg(critical_rate_theoretical), 4) AS crit_rate_theoretical,
    round(avg(critical_effect_triggered::INTEGER), 4) AS critical_effect_rate,
    round(sum(damage_final_total) / count(*), 2) AS damage_per_attack,
    round(sum(damage_final_total) / nullif(count(*) FILTER (WHERE succeeded), 0), 2) AS damage_per_hit,
    max(damage_max_single_hit) AS top_single_hit
FROM fact_action_wide
WHERE is_attack
GROUP BY 1;

-- What spending a Talento on an attack is worth, compared with attacks that didn't spend it.
CREATE TABLE mart_feat_impact AS
WITH attacks AS (SELECT * FROM fact_action_wide WHERE is_attack),
     feats AS (SELECT DISTINCT unnest(activated_feats) AS feat FROM attacks)
SELECT
    f.feat,
    count(*) FILTER (WHERE list_contains(a.activated_feats, f.feat)) AS uses,
    count(DISTINCT a.actor_character_sheet_id) FILTER (WHERE list_contains(a.activated_feats, f.feat)) AS used_by_combatants,
    round(avg(a.succeeded::INTEGER) FILTER (WHERE list_contains(a.activated_feats, f.feat)), 4) AS hit_rate_with,
    round(avg(a.succeeded::INTEGER) FILTER (WHERE NOT list_contains(a.activated_feats, f.feat)), 4) AS hit_rate_without,
    round(avg(a.was_critical::INTEGER) FILTER (WHERE list_contains(a.activated_feats, f.feat)), 4) AS crit_rate_with,
    round(avg(a.was_critical::INTEGER) FILTER (WHERE NOT list_contains(a.activated_feats, f.feat)), 4) AS crit_rate_without,
    round(avg(a.damage_final_total) FILTER (WHERE list_contains(a.activated_feats, f.feat)), 2) AS damage_per_attack_with,
    round(avg(a.damage_final_total) FILTER (WHERE NOT list_contains(a.activated_feats, f.feat)), 2) AS damage_per_attack_without
FROM feats f CROSS JOIN attacks a
GROUP BY f.feat;

-- The meta over time: each week's share of actions per Título, race, Perícia, weapon
-- category and Talento spent.
CREATE TABLE mart_meta_trends AS
WITH a AS (
    SELECT date_trunc('week', occurred_at) AS week, * FROM fact_action_wide
),
dims AS (
    SELECT week, 'primary_title' AS dimension, actor_primary_title AS value, actor_character_sheet_id AS combatant FROM a
    UNION ALL SELECT week, 'secondary_title', actor_secondary_title, actor_character_sheet_id FROM a
    UNION ALL SELECT week, 'race', actor_race, actor_character_sheet_id FROM a
    UNION ALL SELECT week, 'skill', skill, actor_character_sheet_id FROM a
    UNION ALL SELECT week, 'weapon_category', weapon_category, actor_character_sheet_id FROM a WHERE is_attack
    UNION ALL SELECT week, 'activated_feat', unnest(activated_feats), actor_character_sheet_id FROM a
)
SELECT
    week,
    dimension,
    value,
    count(*) AS actions,
    count(DISTINCT combatant) AS combatants,
    round(count(*) / sum(count(*)) OVER (PARTITION BY week, dimension), 4) AS share_of_actions
FROM dims
WHERE value IS NOT NULL
GROUP BY week, dimension, value;
