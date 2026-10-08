-- The 25 biggest single hits, and how each one landed.
SELECT occurred_at, scene_name, round, attacker_name, attacker_primary_title, attacker_secondary_title,
       attacker_total_xp, weapon_category, weapon_name, spell_key, action_critical_result,
       critical_effects, chained_effects, activated_feats, raw_damage, final_damage, target_name
FROM mart_top_hits
LIMIT 25;
