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
 */
public record SceneParticipantEntry(
        String characterSheetId,
        int initiativeValue,
        UUID group,
        GridPosition position,
        int joinedAtRound) {
}
