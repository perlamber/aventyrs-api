-- The meta over time. Visualization: Line, x = week, y = share_of_actions, series = value.
-- Change the dimension filter to: primary_title, secondary_title, race, skill, weapon_category, activated_feat.
SELECT week, value, share_of_actions, actions, combatants
FROM mart_meta_trends
WHERE dimension = 'primary_title'
ORDER BY week, share_of_actions DESC;
