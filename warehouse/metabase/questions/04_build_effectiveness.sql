-- Build effectiveness: one row per distinct build (a level-up or a new Talento is a new build).
-- Compare crit_rate_observed against crit_rate_theoretical to spot lucky (or cursed) builds.
SELECT race, primary_title, secondary_title, tertiary_title, total_xp, equipped_weapon_categories,
       used_by, attacks, hit_rate, avg_margin, crit_rate_observed, crit_rate_theoretical,
       critical_effect_rate, chain_rate, damage_per_attack, damage_dealt_per_round,
       damage_taken_per_round, attacks_per_round, encounters
FROM mart_build_effectiveness
WHERE attacks >= 5
ORDER BY damage_dealt_per_round DESC NULLS LAST;
