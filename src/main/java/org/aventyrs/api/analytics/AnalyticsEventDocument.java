package org.aventyrs.api.analytics;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One accepted realtime frame, kept for the analytics warehouse ({@code warehouse/} in this repo) — the
 * append-only, timestamped log the Scene document itself can't be: {@code actionHistory} carries no time, {@code
 * /status} overwrites the sheet, and {@code /conditions}, {@code /spells} and {@code /state} are never stored at all.
 *
 * <p>Nothing in the API reads this back. It is written once, after the frame was accepted, and the downstream job
 * reads it incrementally by {@code occurredAt}.
 *
 * @param schemaVersion          bumped whenever a field's meaning changes, so the job can branch on it
 * @param sceneRound             the Scene's {@code currentRound} once the frame was applied
 * @param sceneTurnIndex         the Scene's {@code currentIndex} once the frame was applied
 * @param historyIndex           for {@code ACTION}/{@code ABILITY}, the entry's position in the Scene's {@code
 *                               actionHistory}/{@code abilityHistory} — the key the job de-duplicates a backfill
 *                               of those arrays on; {@code null} otherwise
 * @param actorSheetSnapshotId   the {@link SheetSnapshotService} snapshot of the actor's sheet at this moment
 * @param targetSheetSnapshotIds one per {@code targetCharacterSheetIds} entry, in order ({@code null} where the sheet
 *                               couldn't be found)
 * @param payload                the frame as the handler accepted it
 */
@Document(collection = "analytics_events")
public record AnalyticsEventDocument(
        @Id String id,
        AnalyticsEventType type,
        int schemaVersion,
        Instant occurredAt,
        String sceneId,
        String sceneName,
        Integer sceneRound,
        Integer sceneTurnIndex,
        Boolean combatScene,
        Integer historyIndex,
        String actorCharacterSheetId,
        String actorSheetSnapshotId,
        List<String> targetCharacterSheetIds,
        List<String> targetSheetSnapshotIds,
        Object payload) {

    public static final int SCHEMA_VERSION = 1;
}
