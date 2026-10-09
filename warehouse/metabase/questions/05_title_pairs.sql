-- Which primary/secondary Título combinations perform best when attacking.
SELECT coalesce(primary_title, '—') || ' / ' || coalesce(secondary_title, '—') AS title_pair,
       primary_title, secondary_title, combatants, attacks, hit_rate, crit_rate_observed,
       crit_rate_theoretical, avg_margin, damage_per_attack, top_single_hit
FROM mart_title_effectiveness
WHERE attacks >= 5
ORDER BY damage_per_attack DESC;
