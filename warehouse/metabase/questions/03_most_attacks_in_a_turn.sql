-- Most attacks fit into a single Rodada, and the record each combatant holds.
-- attacks_on_own_turn leaves out Reações and other off-turn swings.
SELECT combatant_name, combatant_kind, primary_title, secondary_title,
       max_attacks_in_a_round, max_attacks_in_own_turn, avg_attacks_per_attacking_round,
       rounds_attacking, record_encounter_id, record_round
FROM mart_attack_volume
ORDER BY max_attacks_in_a_round DESC;
