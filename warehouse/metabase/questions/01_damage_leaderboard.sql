-- Damage leaderboard: who deals the most, on average and in a single hit.
-- For the best single turn, see "Top damage in a single turn".
SELECT attacker_name, attacker_kind, primary_title, secondary_title, race, total_xp,
       hits, critical_hits, damage_total, damage_avg_per_hit, damage_top_single_hit,
       damage_per_active_round, encounters
FROM mart_damage_leaderboard
ORDER BY damage_total DESC;
