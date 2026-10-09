-- Every 3d6 total's chance of being reached: what a Margem Crítica Menor turns into as a
-- critical rate. 216 equally likely faces.
CREATE TABLE staging.dice_3d6 AS
WITH faces AS (
    SELECT a.range + b.range + c.range + 3 AS total
    FROM range(6) a, range(6) b, range(6) c
)
SELECT m.margin::INTEGER AS margin,
       count(*) FILTER (WHERE f.total >= m.margin) / 216.0 AS p_at_least
FROM range(3, 19) m(margin), faces f
GROUP BY m.margin;

-- One attribute's total. A PJ stores base + racial + variable under character.attributes; a
-- PdN's blueprint carries only the base.
CREATE TEMP MACRO attr_total(s, domain) AS coalesce(
    TRY_CAST(json_extract_string(s, '$.character.attributes.' || domain || '.base') AS INTEGER)
        + coalesce(TRY_CAST(json_extract_string(s, '$.character.attributes.' || domain || '.racialBonus') AS INTEGER), 0)
        + coalesce(TRY_CAST(json_extract_string(s, '$.character.attributes.' || domain || '.variable') AS INTEGER), 0),
    TRY_CAST(json_extract_string(s, '$.blueprint.attributeBases.' || domain) AS INTEGER));

CREATE TEMP MACRO str_list(s, path) AS coalesce(json_extract_string(s, path), []::VARCHAR[]);

-- One row per sheet as it stood when the API recorded an event (analytics_sheet_snapshots).
CREATE TABLE staging.sheet_snapshot AS
WITH extracted AS (
    SELECT
        id AS snapshot_id,
        character_sheet_id,
        kind,
        first_seen_at,
        coalesce(sheet ->> '$.character.name', sheet ->> '$.blueprint.name') AS name,
        sheet ->> '$.playerId' AS player_id,
        sheet ->> '$.campaignId' AS campaign_id,
        sheet ->> '$.character.race.type' AS race,
        sheet ->> '$.character.race.parentRaceType' AS parent_race,
        coalesce(sheet ->> '$.character.sizeCategory', sheet ->> '$.blueprint.sizeCategory') AS size_category,
        TRY_CAST(sheet ->> '$.totalExperience' AS DECIMAL(18, 2)) AS total_xp,
        TRY_CAST(sheet ->> '$.unUsedExperience' AS DECIMAL(18, 2)) AS unused_xp,
        sheet ->> '$.blueprint.kind' AS monster_kind,
        TRY_CAST(sheet ->> '$.blueprint.powerDegree' AS INTEGER) AS monster_power_degree,
        sheet ->> '$.character.primaryTitle.type' AS primary_title,
        sheet ->> '$.character.secondaryTitle.type' AS secondary_title,
        sheet ->> '$.character.tertiaryTitle.type' AS tertiary_title,
        list_concat(str_list(sheet, '$.character.primaryTitle.abilities[*]'),
                    str_list(sheet, '$.character.secondaryTitle.abilities[*]'),
                    str_list(sheet, '$.character.tertiaryTitle.abilities[*]')) AS title_abilities,
        list_concat(str_list(sheet, '$.character.primaryTitle.specializations[*]'),
                    str_list(sheet, '$.character.secondaryTitle.specializations[*]'),
                    str_list(sheet, '$.character.tertiaryTitle.specializations[*]')) AS title_specializations,
        attr_total(sheet, 'VIGOR') AS attr_vigor,
        attr_total(sheet, 'STRENGTH') AS attr_strength,
        attr_total(sheet, 'DEXTERITY') AS attr_dexterity,
        attr_total(sheet, 'FOCUS') AS attr_focus,
        attr_total(sheet, 'INSTINCT') AS attr_instinct,
        attr_total(sheet, 'GNOSE') AS attr_gnose,
        attr_total(sheet, 'CHARISMA') AS attr_charisma,
        list_sort(list_concat(str_list(sheet, '$.character.feats[*].type'),
                              str_list(sheet, '$.blueprint.feats[*].type'))) AS feats,
        str_list(sheet, '$.character.spells[*]') AS spells,
        list_sort(list_distinct(list_filter(
            str_list(sheet, '$.character.equipment[*].category'),
            c -> c IN ('BOW', 'THROWABLE', 'CROSSBOW', 'WHIP', 'CLUB', 'NATURAL_WEAPON', 'LIGHT_BLADE',
                       'HEAVY_BLADE', 'SPEAR', 'PROJECTILE')))) AS equipped_weapon_categories,
        str_list(sheet, '$.character.equipment[*].name') AS equipped_item_names,
        sheet ->> '$.character.skills' AS skills_json,
        sheet AS sheet_json
    FROM raw.sheet_snapshots
)
SELECT
    e.* EXCLUDE (skills_json),
    p.login AS player_login,
    p.name AS player_name,
    len(e.feats) AS feat_count,
    -- What makes two snapshots "the same build": everything a player chooses, nothing that
    -- churns in play.
    md5(concat_ws('|', e.kind, e.race, e.parent_race, e.total_xp, e.primary_title, e.secondary_title,
                  e.tertiary_title, list_sort(e.title_abilities)::VARCHAR, list_sort(e.title_specializations)::VARCHAR,
                  e.attr_vigor, e.attr_strength, e.attr_dexterity, e.attr_focus, e.attr_instinct, e.attr_gnose,
                  e.attr_charisma, e.feats::VARCHAR, e.equipped_weapon_categories::VARCHAR, e.skills_json,
                  e.monster_kind, e.monster_power_degree)) AS build_hash
FROM extracted e
LEFT JOIN raw.players p ON p.id = e.player_id;
