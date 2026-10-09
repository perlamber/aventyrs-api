-- Weapon categories compared (SPELL / RACIAL when the attack named no weapon).
SELECT weapon_category, attacks, combatants, hit_rate, crit_rate_observed, crit_rate_theoretical,
       critical_effect_rate, damage_per_attack, damage_per_hit, top_single_hit
FROM mart_weapon_category
ORDER BY attacks DESC;
