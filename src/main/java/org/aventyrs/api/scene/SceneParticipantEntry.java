package org.aventyrs.api.scene;

import org.aventyrs.core.scene.grid.GridPosition;

/** Persisted mirror of a core {@code InitiativeEntry}, referencing a CharacterSheet by id plus its grid position. */
public record SceneParticipantEntry(String characterSheetId, int initiativeValue, String group, GridPosition position) {
}
