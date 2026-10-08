-- Drill-down: every column of one action, including both raw sheets as they stood.
-- Swap the filter for any event_id found in the other questions.
SELECT *
FROM fact_action_wide
WHERE was_critical
ORDER BY occurred_at DESC NULLS LAST
LIMIT 20;
