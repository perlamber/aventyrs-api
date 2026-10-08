package org.aventyrs.api.analytics;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.project;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import org.aventyrs.api.scene.SceneDocument;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.ArrayOperators;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

/**
 * Writes one {@link AnalyticsEventDocument} per accepted realtime frame — the capture half of the analytics
 * warehouse, whose load half is the {@code warehouse/} job in this repo.
 *
 * <p>Called by {@code SceneRealtimeController} only <em>after</em> a frame was accepted, so a rejected frame never
 * shows up. And it never throws: analytics is a bystander to the game, so a failure here is logged and swallowed
 * rather than allowed to drop a frame the table is waiting on. Switched off entirely by {@code
 * aventyrs.analytics.enabled=false}.
 */
@Service
public class AnalyticsRecorder {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsRecorder.class);

    private final MongoTemplate mongoTemplate;
    private final SheetSnapshotService snapshots;
    private final boolean enabled;

    public AnalyticsRecorder(MongoTemplate mongoTemplate, SheetSnapshotService snapshots,
            @Value("${aventyrs.analytics.enabled:true}") boolean enabled) {
        this.mongoTemplate = mongoTemplate;
        this.snapshots = snapshots;
        this.enabled = enabled;
    }

    /**
     * Records one event. {@code targetIds} may be {@code null}, and may repeat or contain {@code null}s — it is
     * cleaned here so callers can hand over whatever the frame named. For {@code COMBAT_STARTED}/{@code
     * COMBAT_ENDED}, a {@code null} {@code targetIds} means "every participant", whose sheets are all snapshotted:
     * that is the build each one fought the encounter with.
     */
    public void record(AnalyticsEventType type, String sceneId, String actorId, List<String> targetIds,
            Object payload) {
        if (!enabled) {
            return;
        }
        try {
            Document scene = sceneContext(sceneId);
            List<String> targets = distinct(targetIds == null && isCombatBoundary(type)
                    ? participantIdsOf(scene) : targetIds);
            List<String> targetSnapshots = new ArrayList<>(targets.size());
            for (String target : targets) {
                targetSnapshots.add(snapshots.snapshot(target));
            }

            mongoTemplate.insert(new AnalyticsEventDocument(
                    null,
                    type,
                    AnalyticsEventDocument.SCHEMA_VERSION,
                    Instant.now(),
                    sceneId,
                    scene == null ? null : scene.getString("name"),
                    scene == null ? null : scene.getInteger("currentRound"),
                    scene == null ? null : scene.getInteger("currentIndex"),
                    scene == null ? null : scene.getBoolean("combatScene"),
                    historyIndexOf(type, scene),
                    actorId,
                    snapshots.snapshot(actorId),
                    targets,
                    targetSnapshots,
                    payload));
        } catch (RuntimeException ex) {
            log.warn("Could not record {} analytics event for scene {}: {}", type, sceneId, ex.getMessage());
        }
    }

    /**
     * The handful of Scene fields an event is stamped with, in one round trip — the history arrays are only
     * counted ({@code $size}), never shipped, since they grow for as long as the Scene is played.
     */
    private Document sceneContext(String sceneId) {
        return mongoTemplate.aggregate(newAggregation(
                        match(Criteria.where("_id").is(sceneId)),
                        project("name", "currentRound", "currentIndex", "combatScene")
                                .and("participants.characterSheetId").as("participantIds")
                                .and(ArrayOperators.Size.lengthOfArray(
                                        ConditionalOperators.ifNull("actionHistory").then(List.of())))
                                .as("actionCount")
                                .and(ArrayOperators.Size.lengthOfArray(
                                        ConditionalOperators.ifNull("abilityHistory").then(List.of())))
                                .as("abilityCount")),
                SceneDocument.class, Document.class).getUniqueMappedResult();
    }

    /** The just-appended entry's index: recording runs after the append, so it is the last one. */
    private static Integer historyIndexOf(AnalyticsEventType type, Document scene) {
        if (scene == null) {
            return null;
        }
        return switch (type) {
            case ACTION -> scene.getInteger("actionCount") - 1;
            case ABILITY -> scene.getInteger("abilityCount") - 1;
            default -> null;
        };
    }

    private static boolean isCombatBoundary(AnalyticsEventType type) {
        return type == AnalyticsEventType.COMBAT_STARTED || type == AnalyticsEventType.COMBAT_ENDED;
    }

    private static List<String> participantIdsOf(Document scene) {
        return scene == null ? List.of() : scene.getList("participantIds", String.class, List.of());
    }

    private static List<String> distinct(List<String> ids) {
        if (ids == null) {
            return List.of();
        }
        return List.copyOf(ids.stream().filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }
}
