-- What spending each Talento on an attack is worth: with vs without.
SELECT feat, uses, used_by_combatants,
       hit_rate_with, hit_rate_without, crit_rate_with, crit_rate_without,
       damage_per_attack_with, damage_per_attack_without,
       damage_per_attack_with - damage_per_attack_without AS damage_uplift
FROM mart_feat_impact
ORDER BY uses DESC;
