package org.aventyrs.api.scene;

import java.time.Instant;
import java.util.List;

import org.aventyrs.core.scene.TerrainType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persisted state backing an {@code org.aventyrs.core.scene.Scene}. Participants reference a
 * CharacterSheet by id rather than embedding it, same reasoning as {@code
 * CharacterSheetDocument}'s {@code playerId}: the referenced graph is expensive and mostly
 * static, so clients that already loaded it via REST shouldn't need it re-embedded here.
 * {@code currentRound}/{@code currentIndex} mirror {@code Scene}'s own turn cursor, but nothing
 * here replays {@code Scene#next()}'s active/pending-entry merge logic yet — that's action-time
 * behavior, not a CRUD concern. {@code createdAt} exists purely to resolve "latest" (see
 * {@code SceneRepository#findTopByOrderByCreatedAtDesc}) — ids are random UUIDs, not ObjectIds,
 * so there's no implicit chronological ordering to fall back on. {@code combatScene} mirrors
 * core's own {@code Scene#isCombatScene()} — {@code false} until a caller flips it once combat
 * actually breaks out, same as core.
 */
@Document(collection = "scenes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SceneDocument {

    @Id
    private String id;

    private String name;

    private TerrainType terrain;

    private List<SceneParticipantEntry> participants;

    private int currentRound;

    /** -1 before the first {@code next()}-equivalent action, same convention as core's {@code Scene}. */
    private int currentIndex;

    private boolean combatScene;

    private Instant createdAt;
}
