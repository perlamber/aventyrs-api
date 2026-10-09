-- Top damage in a single turn: the most damage a combatant dealt on one of their own turns.
-- damage_in_rodada adds Reações and other off-turn hits in the same Rodada.
SELECT rank, turn_label, combatant_name, combatant_kind, primary_title, secondary_title, total_xp,
       damage_in_turn, hits_in_turn, damage_in_rodada, max_single_hit, attacks_on_own_turn, crits,
       critical_effects_triggered, chains_triggered, feats_activated, scene_name, encounter_id, round
FROM mart_turn_damage
ORDER BY rank
LIMIT 25;
