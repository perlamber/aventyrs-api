-- Every event in one shape, straight from analytics_events. Only what the API recorded live
-- is in the warehouse: nothing is reconstructed from the Scene's own history.
--
-- round and turn_index are the Scene's Rodada and turn cursor, stamped by the server once the
-- frame was applied. They're never taken from what the client reported.
CREATE TABLE staging.event AS
WITH live AS (
    SELECT
        id AS event_id,
        type,
        occurred_at,
        scene_id,
        doc ->> '$.sceneName' AS scene_name,
        TRY_CAST(doc ->> '$.sceneRound' AS INTEGER) AS scene_round,
        TRY_CAST(doc ->> '$.sceneTurnIndex' AS INTEGER) AS scene_turn_index,
        TRY_CAST(doc ->> '$.combatScene' AS BOOLEAN) AS combat_scene,
        TRY_CAST(doc ->> '$.historyIndex' AS INTEGER) AS history_index,
        doc ->> '$.actorCharacterSheetId' AS actor_id,
        doc ->> '$.actorSheetSnapshotId' AS actor_snapshot_id,
        coalesce(json_extract_string(doc, '$.targetCharacterSheetIds[*]'), []::VARCHAR[]) AS target_ids,
        coalesce(json_extract_string(doc, '$.targetSheetSnapshotIds[*]'), []::VARCHAR[]) AS target_snapshot_ids,
        doc -> '$.payload' AS payload
    FROM raw.analytics_events
),
sequenced AS (
    SELECT *,
        row_number() OVER (PARTITION BY scene_id ORDER BY occurred_at, event_id) AS event_seq
    FROM live
),
encounters AS (
    SELECT *,
        sum(CASE WHEN type = 'COMBAT_STARTED' THEN 1 ELSE 0 END)
            OVER (PARTITION BY scene_id ORDER BY event_seq ROWS UNBOUNDED PRECEDING) AS encounter_no
    FROM sequenced
)
SELECT
    e.*,
    -- An encounter runs from one COMBAT_STARTED to its COMBAT_ENDED. A fight already under way
    -- when the API began recording has no COMBAT_STARTED, so its events go under
    -- '<scene>#0'. NULL outside combat.
    CASE
        WHEN e.combat_scene OR e.type = 'COMBAT_ENDED' THEN e.scene_id || '#' || e.encounter_no
    END AS encounter_id,
    -- Whose Turn it was when this happened: the last TURN_ADVANCED in the same encounter.
    -- NULL before the encounter's first advance.
    last_value(CASE WHEN e.type = 'TURN_ADVANCED' THEN e.actor_id END IGNORE NULLS)
        OVER (PARTITION BY e.scene_id, e.encounter_no ORDER BY e.event_seq ROWS UNBOUNDED PRECEDING) AS turn_owner_id,
    s.campaign_id,
    cs.campaign_name,
    cs.session_number
FROM encounters e
LEFT JOIN staging.sheet_snapshot s ON s.snapshot_id = e.actor_snapshot_id
LEFT JOIN raw.campaign_sessions cs
    ON cs.campaign_id = s.campaign_id
   AND e.occurred_at >= cs.started_at
   AND (cs.ended_at IS NULL OR e.occurred_at <= cs.ended_at);

-- One row per (action, target): the primary target plus everyone an area or chained attack
-- caught. A damage row links back to its action through this table.
CREATE TABLE staging.action AS
SELECT
    e.event_id,
    e.occurred_at,
    e.event_seq,
    e.scene_id,
    e.scene_name,
    e.encounter_id,
    e.campaign_id,
    e.campaign_name,
    e.session_number,
    e.scene_round AS round,
    e.scene_turn_index AS turn_index,
    e.combat_scene,
    e.history_index,
    e.turn_owner_id,
    e.actor_id,
    e.actor_id = e.turn_owner_id AS on_own_turn,
    e.actor_snapshot_id,
    p ->> '$.targetCharacterSheetId' AS target_id,
    e.target_snapshot_ids[list_position(e.target_ids, p ->> '$.targetCharacterSheetId')] AS target_snapshot_id,
    p ->> '$.skill' AS skill,
    p ->> '$.governingDomain' AS governing_domain,
    p ->> '$.attackSourceKind' AS attack_source_kind,
    coalesce(p ->> '$.skill' IN ('ATAQUE_A_DISTANCIA', 'ATAQUE_CORPO_A_CORPO'), false) AS is_attack,
    coalesce(p ->> '$.costKind', p ->> '$.cost.kind') AS cost_kind,
    TRY_CAST(coalesce(p ->> '$.actionPoints', p ->> '$.cost.actionPoints') AS INTEGER) AS action_points,
    TRY_CAST(coalesce(p ->> '$.succeeded', p ->> '$.outcome.succeeded') AS BOOLEAN) AS succeeded,
    TRY_CAST(coalesce(p ->> '$.margin', p ->> '$.outcome.margin') AS INTEGER) AS margin,
    coalesce(p ->> '$.criticalResult', p ->> '$.outcome.criticalResult') AS critical_result,
    coalesce(p ->> '$.reachedDifficultyLevel', p ->> '$.outcome.reachedDifficultyLevel') AS difficulty_reached,
    TRY_CAST(p -> '$.dice' AS INTEGER[]) AS dice,
    TRY_CAST(p ->> '$.total' AS INTEGER) AS total,
    TRY_CAST(p ->> '$.attackDetails.requiredTotal' AS INTEGER) AS required_total,
    coalesce(json_extract_string(p, '$.activatedFeats[*]'), []::VARCHAR[]) AS activated_feats,
    p ->> '$.attackDetails.weaponItemId' AS weapon_item_id,
    p ->> '$.attackDetails.weaponName' AS weapon_name,
    p ->> '$.attackDetails.weaponCategory' AS weapon_category,
    p ->> '$.attackDetails.spellKey' AS spell_key,
    TRY_CAST(p ->> '$.attackDetails.criticalMargin' AS INTEGER) AS critical_margin,
    TRY_CAST(p ->> '$.attackDetails.criticalEffectTriggered' AS BOOLEAN) AS critical_effect_triggered,
    coalesce(json_extract_string(p, '$.attackDetails.criticalEffects[*]'), []::VARCHAR[]) AS critical_effects,
    TRY_CAST(p ->> '$.attackDetails.effectChainTriggered' AS BOOLEAN) AS effect_chain_triggered,
    coalesce(json_extract_string(p, '$.attackDetails.chainedEffects[*]'), []::VARCHAR[]) AS chained_effects,
    coalesce(json_extract_string(p, '$.attackDetails.additionalTargetCharacterSheetIds[*]'), []::VARCHAR[])
        AS additional_target_ids
FROM staging.event e, LATERAL (SELECT e.payload AS p)
WHERE e.type = 'ACTION';

CREATE TABLE staging.action_target AS
SELECT event_id AS action_event_id, scene_id, occurred_at, actor_id, target_id, true AS is_primary_target
FROM staging.action WHERE target_id IS NOT NULL
UNION ALL
SELECT event_id, scene_id, occurred_at, actor_id, unnest(additional_target_ids), false
FROM staging.action;

CREATE TABLE staging.damage AS
SELECT
    e.event_id,
    e.occurred_at,
    e.event_seq,
    e.scene_id,
    e.scene_name,
    e.encounter_id,
    e.campaign_id,
    e.campaign_name,
    e.session_number,
    e.scene_round AS round,
    e.scene_turn_index AS turn_index,
    e.combat_scene,
    e.turn_owner_id,
    e.actor_id AS attacker_id,
    e.actor_snapshot_id AS attacker_snapshot_id,
    p ->> '$.targetCharacterSheetId' AS target_id,
    e.target_snapshot_ids[list_position(e.target_ids, p ->> '$.targetCharacterSheetId')] AS target_snapshot_id,
    TRY_CAST(p ->> '$.rawDamage' AS INTEGER) AS raw_damage,
    TRY_CAST(p ->> '$.finalDamage' AS INTEGER) AS final_damage,
    TRY_CAST(p ->> '$.rawDamage' AS INTEGER) - TRY_CAST(p ->> '$.finalDamage' AS INTEGER) AS mitigated_damage,
    TRY_CAST(p ->> '$.ignoreDamageReduction' AS BOOLEAN) AS ignored_damage_reduction,
    p ->> '$.damageType' AS damage_type,
    p ->> '$.elementalType' AS elemental_type,
    p ->> '$.sourceKind' AS source_kind,
    p ->> '$.sourceRef' AS source_ref,
    TRY_CAST(p ->> '$.wasCritical' AS BOOLEAN) AS was_critical,
    TRY_CAST(p ->> '$.hitPointsSpentAfter' AS INTEGER) AS target_hp_spent_after
FROM staging.event e, LATERAL (SELECT e.payload AS p)
WHERE e.type = 'DAMAGE';

-- Each damage row's action: the latest attack by the same attacker on the same target in
-- the same scene, at or before the hit.
CREATE TABLE staging.damage_link AS
SELECT
    d.event_id AS damage_event_id,
    t.action_event_id,
    CASE WHEN t.is_primary_target THEN 'primary_target' ELSE 'additional_target' END AS link_kind,
    date_diff('millisecond', t.occurred_at, d.occurred_at) / 1000.0 AS seconds_after_action
FROM staging.damage d
ASOF JOIN (
    SELECT tgt.* FROM staging.action_target tgt
    JOIN staging.action a ON a.event_id = tgt.action_event_id
    WHERE a.is_attack
) t
  ON d.scene_id = t.scene_id
 AND d.attacker_id = t.actor_id
 AND d.target_id = t.target_id
 AND d.occurred_at >= t.occurred_at;

CREATE TABLE staging.ability AS
SELECT
    e.event_id,
    e.occurred_at,
    e.event_seq,
    e.scene_id,
    e.scene_name,
    e.encounter_id,
    e.campaign_id,
    e.campaign_name,
    e.session_number,
    e.scene_round AS round,
    e.scene_turn_index AS turn_index,
    e.combat_scene,
    e.turn_owner_id,
    e.actor_id,
    e.actor_id = e.turn_owner_id AS on_own_turn,
    e.actor_snapshot_id,
    p ->> '$.titleType' AS title_type,
    p ->> '$.abilityId' AS ability_id,
    p ->> '$.abilityName' AS ability_name,
    p ->> '$.skillType' AS skill_type,
    coalesce(TRY_CAST(p ->> '$.determinationPointsSpent' AS INTEGER), 0) AS determination_points_spent,
    coalesce(TRY_CAST(p ->> '$.hitPointsSpent' AS INTEGER), 0) AS hit_points_spent,
    coalesce(TRY_CAST(p ->> '$.enchantmentRounds' AS INTEGER), 0) AS enchantment_rounds,
    coalesce(json_extract_string(p, '$.boundCharacterSheetIds[*]'), []::VARCHAR[]) AS bound_ids,
    coalesce(json_extract_string(p, '$.blessings[*].modifierType'), []::VARCHAR[]) AS blessing_modifiers,
    coalesce(list_sum(TRY_CAST(json_extract_string(p, '$.effects.areaDamage[*].amount') AS INTEGER[])), 0)
        AS area_damage_total,
    coalesce(json_extract_string(p, '$.effects.areaDamage[*].targetCharacterSheetId'), []::VARCHAR[])
        AS area_damage_target_ids,
    coalesce(json_extract_string(p, '$.effects.conditions[*].conditionType'), []::VARCHAR[]) AS conditions_inflicted,
    coalesce(json_extract_string(p, '$.effects.targetEffects[*].kind'), []::VARCHAR[]) AS target_effect_kinds,
    p -> '$.blessings' AS blessings_json,
    p -> '$.effects' AS effects_json,
    e.target_ids
FROM staging.event e, LATERAL (SELECT e.payload AS p)
WHERE e.type = 'ABILITY';
