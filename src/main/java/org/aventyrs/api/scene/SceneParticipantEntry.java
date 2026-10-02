package org.aventyrs.api.scene;

import java.util.UUID;
import org.aventyrs.core.scene.grid.GridPosition;

/**
 * Persisted mirror of a core {@code InitiativeEntry}, referencing a CharacterSheet by id plus its
 * grid position.
 *
 * <p>{@code joinedAtRound} is what reproduces core's {@code Scene} active/pending split across a
 * flat persisted list: an entry belongs to the turn rotation exactly when {@code joinedAtRound <=}
 * the scene's {@code currentRound}, and is otherwise still waiting to join at the next Round
 * boundary — the same "never interrupts the Round currently in progress" rule {@code
 * Scene#addParticipant} enforces in memory. {@link SceneService} keeps rotation members ahead of
 * waiting ones in {@code SceneDocument#getParticipants()} and sorted by {@code initiativeValue}
 * descending, so {@code currentIndex} indexes that list directly, exactly as core's own
 * {@code currentIndex} indexes its {@code activeEntries}.
 *
 * <p>Scene documents persisted before this field existed deserialise it as {@code 0}, which reads
 * as "in the rotation from Round 0" — the correct reading for every scene written back then, since
 * nothing was holding anyone back at the time. That's why there's no Liquibase changeset for it.
 *
 * <p>{@code concealment} is non-{@code null} exactly while the participant is Escondido — see {@link
 * SceneConcealmentEntry}. Documents written before it existed read it as {@code null}: nobody was
 * persisted hidden then, which is exactly right.
 *
 * <p>{@code initiativeOverride} is an Iniciativa Ego point's replacement for {@code initiativeValue} — see {@link
 * SceneInitiativeOverrideEntry}; {@code null} (and on older documents) when none holds. The turn order sorts by
 * {@link #effectiveInitiative()}.
 */
public record SceneParticipantEntry(
        String characterSheetId,
        int initiativeValue,
        UUID group,
        GridPosition position,
        int joinedAtRound,
        SceneConcealmentEntry concealment,
        SceneInitiativeOverrideEntry initiativeOverride) {

    /** An entry with no Iniciativa override. */
    public SceneParticipantEntry(String characterSheetId, int initiativeValue, UUID group, GridPosition position,
                                 int joinedAtRound, SceneConcealmentEntry concealment) {
        this(characterSheetId, initiativeValue, group, position, joinedAtRound, concealment, null);
    }

    /** What the turn order sorts by: the override's value while one holds, else the rolled one. */
    public int effectiveInitiative() {
        return initiativeOverride == null ? initiativeValue : initiativeOverride.value();
    }

    /** This entry with its concealment replaced — {@code null} lifts it. */
    public SceneParticipantEntry withConcealment(SceneConcealmentEntry newConcealment) {
        return new SceneParticipantEntry(characterSheetId, initiativeValue, group, position, joinedAtRound,
                newConcealment, initiativeOverride);
    }

    /** This entry with its Iniciativa override replaced — {@code null} lifts it. */
    public SceneParticipantEntry withInitiativeOverride(SceneInitiativeOverrideEntry override) {
        return new SceneParticipantEntry(characterSheetId, initiativeValue, group, position, joinedAtRound,
                concealment, override);
    }

    /** This entry moved to position. */
    public SceneParticipantEntry withPosition(GridPosition newPosition) {
        return new SceneParticipantEntry(characterSheetId, initiativeValue, group, newPosition, joinedAtRound,
                concealment, initiativeOverride);
    }
}
