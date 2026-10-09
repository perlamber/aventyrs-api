-- Wide, denormalized facts. Every row carries all the context it needs as columns: scene,
-- encounter, campaign, session, and the full sheet block of the combatants involved,
-- including the raw sheet JSON for drill-down. Analysis never needs a join.

-- Damage rolled up onto the action that dealt it.
CREATE TEMP TABLE action_damage AS
SELECT l.action_event_id,
       count(*) AS damage_events,
       sum(d.raw_damage) AS damage_raw_total,
       sum(d.final_damage) AS damage_final_total,
       max(d.final_damage) AS damage_max_single_hit
FROM staging.damage_link l
JOIN staging.damage d ON d.event_id = l.damage_event_id
GROUP BY l.action_event_id;

-- One row per action (an attack or a Perícia roll). The actor_* block is the sheet that
-- rolled, as it stood at that moment. The target_* block is the primary target's.
CREATE TABLE fact_action_wide AS
SELECT
    a.event_id,
    a.occurred_at,
    a.campaign_id,
    a.campaign_name,
    a.session_number,
    a.scene_id,
    a.scene_name,
    a.encounter_id,
    a.combat_scene,
    a.round,
    a.turn_index,
    a.history_index,
    a.turn_owner_id,
    a.on_own_turn,
    a.skill,
    a.governing_domain,
    a.attack_source_kind,
    a.is_attack,
    a.cost_kind,
    a.action_points,
    a.dice,
    a.total,
    a.required_total,
    a.succeeded,
    a.margin,
    a.difficulty_reached,
    a.critical_result,
    a.critical_result IN ('ACERTO_CRITICO_MENOR', 'ACERTO_CRITICO_MAIOR') AS was_critical,
    a.critical_result IN ('FALHA_CRITICA_MENOR', 'FALHA_CRITICA_MAIOR') AS was_critical_failure,
    a.critical_margin,
    CASE WHEN a.critical_margin > 18 THEN 0.0 ELSE dice.p_at_least END AS critical_rate_theoretical,
    sum(CASE WHEN a.is_attack AND a.critical_result IN ('ACERTO_CRITICO_MENOR', 'ACERTO_CRITICO_MAIOR') THEN 1 ELSE 0 END)
        OVER actor_history
        / nullif(sum(CASE WHEN a.is_attack THEN 1 ELSE 0 END) OVER actor_history, 0) AS critical_rate_observed_to_date,
    a.critical_effect_triggered,
    a.critical_effects,
    a.effect_chain_triggered,
    a.chained_effects,
    a.weapon_item_id,
    a.weapon_name,
    a.weapon_category,
    a.spell_key,
    a.activated_feats,
    len(a.activated_feats) AS activated_feat_count,
    a.additional_target_ids,
    count(*) FILTER (WHERE a.is_attack) OVER actor_round AS attacks_by_actor_this_round,
    coalesce(ad.damage_events, 0) AS damage_events,
    coalesce(ad.damage_raw_total, 0) AS damage_raw_total,
    coalesce(ad.damage_final_total, 0) AS damage_final_total,
    ad.damage_max_single_hit,
    a.actor_id,
    a.target_id,
    @@sheet_block(actor_, sa)@@,
    @@sheet_block(target_, st)@@
FROM staging.action a
LEFT JOIN staging.sheet_snapshot sa ON sa.snapshot_id = a.actor_snapshot_id
LEFT JOIN staging.sheet_snapshot st ON st.snapshot_id = a.target_snapshot_id
LEFT JOIN staging.dice_3d6 dice ON dice.margin = greatest(3, a.critical_margin)
LEFT JOIN action_damage ad ON ad.action_event_id = a.event_id
WINDOW actor_history AS (PARTITION BY a.actor_id ORDER BY a.occurred_at, a.event_seq
                         ROWS UNBOUNDED PRECEDING),
       actor_round AS (PARTITION BY a.encounter_id, a.round, a.actor_id);

-- One row per hit. The attacker_* and target_* blocks are both sheets as they stood. The
-- action_* columns describe the attack the hit is linked to.
CREATE TABLE fact_damage_wide AS
SELECT
    d.event_id,
    d.occurred_at,
    d.campaign_id,
    d.campaign_name,
    d.session_number,
    d.scene_id,
    d.scene_name,
    d.encounter_id,
    d.combat_scene,
    d.round,
    d.turn_index,
    d.turn_owner_id,
    d.attacker_id = d.turn_owner_id AS on_attackers_turn,
    d.raw_damage,
    d.final_damage,
    d.mitigated_damage,
    d.ignored_damage_reduction,
    d.damage_type,
    d.elemental_type,
    d.source_kind,
    d.source_ref,
    coalesce(d.was_critical, a.was_critical) AS was_critical,
    d.target_hp_spent_after,
    l.action_event_id,
    l.link_kind,
    l.seconds_after_action,
    a.skill AS action_skill,
    a.attack_source_kind AS action_attack_source_kind,
    a.weapon_item_id,
    a.weapon_name,
    a.weapon_category,
    a.spell_key,
    a.margin AS action_margin,
    a.critical_result AS action_critical_result,
    a.critical_margin AS action_critical_margin,
    a.critical_rate_theoretical AS action_critical_rate_theoretical,
    a.critical_effect_triggered,
    a.critical_effects,
    a.effect_chain_triggered,
    a.chained_effects,
    a.activated_feats,
    d.attacker_id,
    d.target_id,
    @@sheet_block(attacker_, sa)@@,
    @@sheet_block(target_, st)@@
FROM staging.damage d
LEFT JOIN staging.damage_link l ON l.damage_event_id = d.event_id
LEFT JOIN fact_action_wide a ON a.event_id = l.action_event_id
LEFT JOIN staging.sheet_snapshot sa ON sa.snapshot_id = d.attacker_snapshot_id
LEFT JOIN staging.sheet_snapshot st ON st.snapshot_id = d.target_snapshot_id;

-- One row per Habilidade activation (Título or Competência).
CREATE TABLE fact_ability_wide AS
SELECT
    b.* EXCLUDE (actor_snapshot_id, event_seq),
    @@sheet_block(actor_, sa)@@
FROM staging.ability b
LEFT JOIN staging.sheet_snapshot sa ON sa.snapshot_id = b.actor_snapshot_id;

-- One row per combatant, encounter and Rodada: what each combatant did in a round, and
-- what was done to them. "Most attacks in one turn" reads from here. attacks_on_own_turn
-- excludes off-turn swings such as Reações.
CREATE TABLE fact_combatant_round_wide AS
WITH acts AS (
    SELECT encounter_id, scene_id, round, actor_id AS combatant_id,
           count(*) AS actions,
           count(*) FILTER (WHERE is_attack) AS attacks,
           count(*) FILTER (WHERE is_attack AND on_own_turn) AS attacks_on_own_turn,
           count(*) FILTER (WHERE is_attack AND succeeded) AS hits,
           count(*) FILTER (WHERE is_attack AND was_critical) AS crits,
           count(*) FILTER (WHERE critical_effect_triggered) AS critical_effects_triggered,
           count(*) FILTER (WHERE effect_chain_triggered) AS chains_triggered,
           coalesce(sum(action_points), 0) AS action_points_spent,
           sum(activated_feat_count) AS feats_activated,
           arg_max(actor_snapshot_id, occurred_at) AS snapshot_id,
           min(occurred_at) AS first_at,
           any_value(scene_name) AS scene_name,
           any_value(campaign_id) AS campaign_id,
           any_value(campaign_name) AS campaign_name,
           any_value(session_number) AS session_number
    FROM fact_action_wide
    GROUP BY ALL
),
dealt AS (
    SELECT encounter_id, scene_id, round, attacker_id AS combatant_id,
           count(*) AS damage_events_dealt,
           sum(raw_damage) AS damage_dealt_raw,
           sum(final_damage) AS damage_dealt_final,
           coalesce(sum(final_damage) FILTER (WHERE on_attackers_turn), 0) AS damage_dealt_on_own_turn,
           count(*) FILTER (WHERE on_attackers_turn) AS damage_events_on_own_turn,
           max(final_damage) AS max_single_hit,
           arg_max(attacker_snapshot_id, occurred_at) AS snapshot_id
    FROM fact_damage_wide WHERE attacker_id IS NOT NULL
    GROUP BY ALL
),
taken AS (
    SELECT encounter_id, scene_id, round, target_id AS combatant_id,
           count(*) AS damage_events_taken,
           sum(final_damage) AS damage_taken_final,
           arg_max(target_snapshot_id, occurred_at) AS snapshot_id
    FROM fact_damage_wide
    GROUP BY ALL
),
abil AS (
    SELECT encounter_id, scene_id, round, actor_id AS combatant_id,
           count(*) AS abilities_activated,
           sum(determination_points_spent) AS determination_points_spent,
           sum(area_damage_total) AS area_damage_dealt,
           arg_max(actor_snapshot_id, occurred_at) AS snapshot_id
    FROM fact_ability_wide
    GROUP BY ALL
),
keys AS (
    SELECT encounter_id, scene_id, round, combatant_id FROM acts
    UNION SELECT encounter_id, scene_id, round, combatant_id FROM dealt
    UNION SELECT encounter_id, scene_id, round, combatant_id FROM taken
    UNION SELECT encounter_id, scene_id, round, combatant_id FROM abil
)
SELECT
    k.encounter_id,
    k.scene_id,
    k.round,
    k.combatant_id,
    acts.scene_name,
    acts.campaign_id,
    acts.campaign_name,
    acts.session_number,
    coalesce(acts.actions, 0) AS actions,
    coalesce(acts.attacks, 0) AS attacks,
    coalesce(acts.attacks_on_own_turn, 0) AS attacks_on_own_turn,
    coalesce(acts.hits, 0) AS hits,
    coalesce(acts.crits, 0) AS crits,
    coalesce(acts.critical_effects_triggered, 0) AS critical_effects_triggered,
    coalesce(acts.chains_triggered, 0) AS chains_triggered,
    coalesce(acts.action_points_spent, 0) AS action_points_spent,
    coalesce(acts.feats_activated, 0) AS feats_activated,
    coalesce(dealt.damage_events_dealt, 0) AS damage_events_dealt,
    coalesce(dealt.damage_dealt_raw, 0) AS damage_dealt_raw,
    coalesce(dealt.damage_dealt_final, 0) AS damage_dealt_final,
    coalesce(dealt.damage_dealt_on_own_turn, 0) AS damage_dealt_on_own_turn,
    coalesce(dealt.damage_events_on_own_turn, 0) AS damage_events_on_own_turn,
    dealt.max_single_hit,
    coalesce(taken.damage_events_taken, 0) AS damage_events_taken,
    coalesce(taken.damage_taken_final, 0) AS damage_taken_final,
    coalesce(abil.abilities_activated, 0) AS abilities_activated,
    coalesce(abil.determination_points_spent, 0) AS determination_points_spent,
    coalesce(abil.area_damage_dealt, 0) AS area_damage_dealt,
    @@sheet_block(combatant_, s)@@
FROM keys k
-- NULL-safe: encounter_id is NULL outside combat, and a plain equality join would drop
-- those rows' numbers and sheet.
LEFT JOIN acts ON acts.encounter_id IS NOT DISTINCT FROM k.encounter_id AND acts.scene_id = k.scene_id
    AND acts.round IS NOT DISTINCT FROM k.round AND acts.combatant_id = k.combatant_id
LEFT JOIN dealt ON dealt.encounter_id IS NOT DISTINCT FROM k.encounter_id AND dealt.scene_id = k.scene_id
    AND dealt.round IS NOT DISTINCT FROM k.round AND dealt.combatant_id = k.combatant_id
LEFT JOIN taken ON taken.encounter_id IS NOT DISTINCT FROM k.encounter_id AND taken.scene_id = k.scene_id
    AND taken.round IS NOT DISTINCT FROM k.round AND taken.combatant_id = k.combatant_id
LEFT JOIN abil ON abil.encounter_id IS NOT DISTINCT FROM k.encounter_id AND abil.scene_id = k.scene_id
    AND abil.round IS NOT DISTINCT FROM k.round AND abil.combatant_id = k.combatant_id
LEFT JOIN staging.sheet_snapshot s
    ON s.snapshot_id = coalesce(acts.snapshot_id, dealt.snapshot_id, abil.snapshot_id, taken.snapshot_id)
WHERE k.combatant_id IS NOT NULL;

-- Every event, untyped. Covers what the facts above don't model yet: moves, Condições,
-- spell landings, combatant state, rolls requested by the Narrador, and time passing.
CREATE TABLE fact_event_log AS
SELECT event_id, type, occurred_at, campaign_id, campaign_name, session_number, scene_id, scene_name,
       encounter_id, combat_scene, scene_round AS round, scene_turn_index AS turn_index, turn_owner_id, actor_id,
       target_ids, actor_snapshot_id, target_snapshot_ids, payload
FROM staging.event;
